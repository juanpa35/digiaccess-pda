package com.digiaccess;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import java.nio.charset.Charset;
import java.util.ArrayList;

/**
 * Recibe las lecturas del lector 2D integrado del PDA.
 *
 * Soporta los dos modos que traen estos equipos:
 *  1. Broadcast (recomendado): el servicio del escáner envía un Intent con el dato.
 *     Se escuchan las acciones más comunes (Urovo, iData, Newland, Sunmi, Seuic, Honeywell,
 *     Zebra DataWedge, Chainway) más una acción personalizada configurable.
 *  2. Teclado (keyboard wedge): el escáner "teclea" el dato y termina con Enter.
 *     Nota: en modo teclado los bytes 0x00 del PDF417 se pierden; la cédula se lee igual
 *     pero los nombres pueden quedar pegados. Por eso se recomienda el modo broadcast.
 */
public class ScannerHelper {
    private static final String TAG = "DigiScanner";
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    public interface Listener {
        /** Se llama en el hilo principal. raw puede ser null si el lector solo entrega texto. */
        void onScan(byte[] raw, String text);
    }

    private static final String[] ACCIONES = {
            "android.intent.ACTION_DECODE_DATA",                  // Urovo / genéricos
            "android.intent.action.SCANRESULT",                   // iData
            "nlscan.action.SCANNER_RESULT",                       // Newland
            "com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED",        // Sunmi
            "com.android.server.scannerservice.broadcast",        // Seuic
            "com.honeywell.decode.intent.action.EDIT_DATA",       // Honeywell (perfil configurado)
            "com.symbol.datawedge.api.RESULT_ACTION",             // Zebra (perfil configurado)
            "com.scanner.broadcast",                              // Chainway / genéricos
            "com.android.scanner.broadcast",
            "com.android.scanservice.scancontext",
            "scan.rcv.message",
            "com.barcode.sendBroadcast",
            "android.intent.action.SCAN_RESULT",
            "com.digiaccess.SCAN",                                // acción propia por si el escáner permite configurarla
    };

    // Claves conocidas: primero las que traen bytes, luego las de texto
    private static final String[] CLAVES_BYTES = {
            "barcode", "dataBytes", "source_byte", "barocode", "decode_data", "data_bytes", "com.symbol.datawedge.decode_data"
    };
    private static final String[] CLAVES_TEXTO = {
            "barcode_string", "value", "SCAN_BARCODE1", "data", "scannerdata", "barcode_data",
            "com.symbol.datawedge.data_string", "scan_result", "barcodedata", "decode_rslt", "result"
    };

    private final Context ctx;
    private final Listener listener;
    private boolean registered = false;

    // Modo teclado
    private final StringBuilder keyBuffer = new StringBuilder();
    private long lastKeyTime = 0;

    // Evita procesar dos veces la misma lectura cuando el equipo envía dos broadcasts
    private String lastData = null;
    private long lastDataTime = 0;

    public ScannerHelper(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                Bundle b = intent.getExtras();
                if (b == null) return;
                Config cfg = Config.load(context);
                byte[] raw = null;
                String text = null;

                if (cfg.scannerExtra.length() > 0) {
                    Object v = b.get(cfg.scannerExtra);
                    if (v instanceof byte[]) raw = (byte[]) v;
                    else if (v instanceof String) text = (String) v;
                }
                if (raw == null) {
                    for (String k : CLAVES_BYTES) {
                        Object v = b.get(k);
                        if (v instanceof byte[] && ((byte[]) v).length > 0) { raw = (byte[]) v; break; }
                        if (v instanceof ArrayList && !((ArrayList<?>) v).isEmpty() && ((ArrayList<?>) v).get(0) instanceof byte[]) {
                            raw = (byte[]) ((ArrayList<?>) v).get(0); break;
                        }
                    }
                }
                if (text == null) {
                    for (String k : CLAVES_TEXTO) {
                        Object v = b.get(k);
                        if (v instanceof String && ((String) v).length() > 0) { text = (String) v; break; }
                    }
                }
                // Último recurso: cualquier extra de tipo byte[] o String
                if (raw == null && text == null) {
                    for (String k : b.keySet()) {
                        Object v = b.get(k);
                        if (v instanceof byte[] && ((byte[]) v).length > 2) { raw = (byte[]) v; break; }
                        if (v instanceof String && ((String) v).length() > 2 && (text == null || ((String) v).length() > text.length()))
                            text = (String) v;
                    }
                }
                // Algunos equipos envían una "length" junto al arreglo de bytes
                if (raw != null) {
                    int len = b.getInt("length", -1);
                    if (len > 0 && len < raw.length) {
                        byte[] t = new byte[len];
                        System.arraycopy(raw, 0, t, 0, len);
                        raw = t;
                    }
                }
                deliver(raw, text);
            } catch (Exception e) {
                Log.e(TAG, "Error leyendo el broadcast del escáner", e);
            }
        }
    };

    private void deliver(byte[] raw, String text) {
        if (raw == null && (text == null || text.length() == 0)) return;
        String key = raw != null ? new String(raw, LATIN1) : text;
        long now = SystemClock.elapsedRealtime();
        if (key.equals(lastData) && now - lastDataTime < 1500) return;
        lastData = key;
        lastDataTime = now;
        listener.onScan(raw, text);
    }

    /** Llamar en onResume. */
    public void start() {
        if (registered) return;
        IntentFilter f = new IntentFilter();
        for (String a : ACCIONES) f.addAction(a);
        String custom = Config.load(ctx).scannerAction;
        if (custom.length() > 0) f.addAction(custom);
        ctx.registerReceiver(receiver, f);
        registered = true;
    }

    /** Llamar en onPause. */
    public void stop() {
        if (!registered) return;
        try { ctx.unregisterReceiver(receiver); } catch (Exception ignored) { }
        registered = false;
        keyBuffer.setLength(0);
    }

    /**
     * Modo teclado: llamar desde Activity.dispatchKeyEvent. Devuelve true si consumió la tecla.
     * Solo acumula teclas que llegan muy seguidas (escáner) para no interferir con la escritura normal.
     */
    public boolean handleKeyEvent(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        long now = SystemClock.elapsedRealtime();
        if (now - lastKeyTime > 300) keyBuffer.setLength(0); // pausa larga = nueva lectura
        lastKeyTime = now;

        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_TAB) {
            if (keyBuffer.length() >= 6) {
                String data = keyBuffer.toString();
                keyBuffer.setLength(0);
                deliver(null, data);
                return true;
            }
            keyBuffer.setLength(0);
            return false;
        }
        int ch = event.getUnicodeChar();
        if (ch > 0) {
            keyBuffer.append((char) ch);
            return keyBuffer.length() > 3; // las primeras teclas pueden ser del usuario
        }
        return false;
    }
}
