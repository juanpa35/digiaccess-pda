package com.digiaccess;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Conexión con la nube yunatt (global.yunatt.com:7792) como terminal, con el protocolo
 * WebSocket+JSON de las terminales Timmy/yunatt (ruta /pub/chat).
 *
 * De la nube llegan los ROSTROS: altas con foto (setuserinfo, backupnum 50), cambios de nombre,
 * bajas (deleteuser) y borrado total (cleanuser). La nube también puede consultar qué rostros
 * tiene el PDA (getuserlist / getuserinfo).
 *
 * El número de identificación de la persona es su "enrollid" en yunatt; con ese número el PDA
 * valida en DIGIACCESS (validar_acceso.php), que es donde queda el registro de ingreso.
 */
public final class YunattCliente {
    private static final String TAG = "DigiYunatt";
    private static final int PAGINA = 40;

    private static YunattCliente instancia;

    /** Estado para mostrar en Configuración. */
    public static volatile String estado = "Desactivado";
    public static volatile int rostrosRecibidos = 0, rostrosEliminados = 0;
    public static volatile String ultimoError = "";

    private final Context ctx;
    private final ScheduledExecutorService hilo = Executors.newSingleThreadScheduledExecutor();
    private final OkHttpClient http = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(0, TimeUnit.MILLISECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .build();
    private volatile WebSocket ws;
    private volatile boolean activo = false;
    private volatile boolean registrado = false;
    private int intentos = 0;
    private int listaDesde = 0;
    private int logIndex = 1;

    private YunattCliente(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        // Latido cada 30 s (solo se envía mientras esté registrado)
        hilo.scheduleWithFixedDelay(this::latido, 30, 30, TimeUnit.SECONDS);
    }

    /** Arranca (o reinicia) la conexión según la configuración. Llamar al iniciar la app y al guardar la configuración. */
    public static synchronized void reiniciar(Context ctx) {
        if (instancia == null) instancia = new YunattCliente(ctx);
        instancia.hilo.execute(instancia::reconectar);
    }

    public static synchronized void detener() {
        if (instancia != null) instancia.hilo.execute(instancia::cerrar);
    }

    public static boolean conectado() {
        return instancia != null && instancia.registrado;
    }

    // ------------------------------------------------------------------ conexión

    private void reconectar() {
        cerrar();
        Config cfg = Config.load(ctx);
        if (!cfg.yunattConfigurado()) {
            estado = cfg.yunattActivo ? "Falta el número de serie (SN) del PDA" : "Desactivado";
            return;
        }
        activo = true;
        String url = "ws://" + cfg.yunattServidor.trim() + ":" + cfg.yunattPuerto + "/pub/chat";
        estado = "Conectando a " + cfg.yunattServidor + "…";
        Log.i(TAG, "Conectando " + url);
        ws = http.newWebSocket(new Request.Builder().url(url).build(), new Oyente());
    }

    private void cerrar() {
        activo = false;
        registrado = false;
        WebSocket w = ws;
        ws = null;
        if (w != null) {
            try { w.close(1000, "reinicio"); } catch (Exception ignored) { }
        }
    }

    private void programarReconexion() {
        if (!activo) return;
        intentos = Math.min(intentos + 1, 6);
        long espera = 5L << Math.min(intentos, 5);          // 10 s, 20 s, 40 s … hasta 160 s
        estado = "Sin conexión con yunatt. Reintento en " + espera + " s";
        hilo.schedule(() -> { if (activo) reconectar(); }, espera, TimeUnit.SECONDS);
    }

    private class Oyente extends WebSocketListener {
        @Override
        public void onOpen(WebSocket w, Response r) {
            hilo.execute(() -> enviarRegistro(w));
        }

        @Override
        public void onMessage(WebSocket w, String texto) {
            hilo.execute(() -> procesar(w, texto));
        }

        @Override
        public void onClosed(WebSocket w, int code, String reason) {
            if (w != ws) return;
            registrado = false;
            hilo.execute(YunattCliente.this::programarReconexion);
        }

        @Override
        public void onFailure(WebSocket w, Throwable t, Response r) {
            if (w != ws) return;
            registrado = false;
            ultimoError = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            Log.w(TAG, "Fallo de conexión: " + ultimoError);
            hilo.execute(YunattCliente.this::programarReconexion);
        }
    }

    private void enviarRegistro(WebSocket w) {
        try {
            Config cfg = Config.load(ctx);
            JSONObject dev = new JSONObject();
            dev.put("modelname", "DIGIACCESS-PDA");
            dev.put("usersize", 10000);
            dev.put("fpsize", 0);
            dev.put("cardsize", 0);
            dev.put("pwdsize", 0);
            dev.put("logsize", 100000);
            dev.put("useduser", FaceStore.cedulas().size());
            dev.put("usedfp", 0);
            dev.put("usedcard", 0);
            dev.put("usedpwd", 0);
            dev.put("usedlog", 0);
            dev.put("usednewlog", 0);
            dev.put("fpalgo", "");
            dev.put("firmware", "DIGIACCESS-PDA 1.0");
            dev.put("time", ahora());
            JSONObject reg = new JSONObject();
            reg.put("cmd", "reg");
            reg.put("sn", cfg.yunattSn.trim());
            reg.put("cpusn", cfg.yunattSn.trim());
            reg.put("devinfo", dev);
            w.send(reg.toString());
        } catch (Exception e) {
            Log.e(TAG, "No se pudo enviar el registro", e);
        }
    }

    private void latido() {
        WebSocket w = ws;
        if (w == null || !registrado) return;
        try {
            JSONObject hb = new JSONObject();
            hb.put("cmd", "heartbeat");
            hb.put("sn", Config.load(ctx).yunattSn.trim());
            w.send(hb.toString());
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ mensajes de la nube

    private void procesar(WebSocket w, String texto) {
        JSONObject m;
        try {
            m = new JSONObject(texto);
        } catch (Exception e) {
            return;
        }
        String ret = m.optString("ret", "");
        if (ret.length() > 0) {                       // respuesta a algo que enviamos
            if (ret.equals("reg")) {
                registrado = m.optBoolean("result", false);
                intentos = 0;
                estado = registrado ? "Conectado a yunatt" : "yunatt rechazó el registro (revise el SN en la nube)";
}
            return;
        }
        String cmd = m.optString("cmd", "");
        if (cmd.isEmpty()) return;
        try {
            JSONObject resp = atender(cmd, m);
            if (resp != null) {
                resp.put("ret", cmd);
                resp.put("sn", Config.load(ctx).yunattSn.trim());
                w.send(resp.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error atendiendo " + cmd, e);
            try {
                JSONObject err = new JSONObject();
                err.put("ret", cmd);
                err.put("result", false);
                err.put("reason", 1);
                w.send(err.toString());
            } catch (Exception ignored) { }
        }
    }

    private JSONObject atender(String cmd, JSONObject m) throws Exception {
        JSONObject r = new JSONObject();
        r.put("result", true);
        switch (cmd) {
            case "setuserinfo": {
                String id = enrollid(m);
                String nombre = m.optString("name", "");
                int backup = m.optInt("backupnum", -1);
                r.put("enrollid", m.opt("enrollid"));
                r.put("backupnum", backup);
                if (backup == 50) {                    // foto del rostro
                    String foto = m.optString("record", "");
                    int coma = foto.indexOf("base64,");
                    if (coma >= 0) foto = foto.substring(coma + 7);
                    String error = FaceStore.enrolarFoto(Base64.decode(foto, Base64.DEFAULT), id, nombre);
                    if (error != null) {
                        r.put("result", false);
                        r.put("reason", 1);
                        r.put("msg", error);
                        Log.w(TAG, "Rostro " + id + " no enrolado: " + error);
                    } else {
                        rostrosRecibidos++;
                    }
                } else if (nombre.length() > 0) {
                    FaceStore.renombrar(id, nombre);   // huella, tarjeta o clave: solo se actualiza el nombre
                }
                return r;
            }
            case "setusername": {
                JSONArray rec = m.optJSONArray("record");
                if (rec != null) for (int i = 0; i < rec.length(); i++) {
                    JSONObject u = rec.getJSONObject(i);
                    FaceStore.renombrar(enrollid(u), u.optString("name", ""));
                }
                return r;
            }
            case "deleteuser": {
                String id = enrollid(m);
                int backup = m.optInt("backupnum", 13);
                r.put("enrollid", m.opt("enrollid"));
                r.put("backupnum", backup);
                if (backup == 13 || backup == 50 || backup < 0) {   // 13 = todo el usuario, 50 = foto
                    if (FaceStore.eliminarPorCedula(id)) rostrosEliminados++;
                }
                return r;
            }
            case "cleanuser": {
                rostrosEliminados += FaceStore.eliminarTodos();
                return r;
            }
            case "getuserlist": {
                List<String> todos = FaceStore.cedulas();
                if (m.optBoolean("stn", true)) listaDesde = 0;
                int hasta = Math.min(todos.size(), listaDesde + PAGINA);
                JSONArray rec = new JSONArray();
                for (int i = listaDesde; i < hasta; i++) {
                    JSONObject u = new JSONObject();
                    u.put("enrollid", numeroOTexto(todos.get(i)));
                    u.put("admin", 0);
                    u.put("backupnum", 50);
                    rec.put(u);
                }
                r.put("count", todos.size());
                r.put("from", listaDesde);
                r.put("to", hasta);
                r.put("record", rec);
                listaDesde = hasta;
                return r;
            }
            case "getuserinfo": {
                String id = enrollid(m);
                int backup = m.optInt("backupnum", 50);
                com.db.UserBean u = FaceStore.buscarPorCedula(id);
                r.put("enrollid", m.opt("enrollid"));
                r.put("backupnum", backup);
                if (u == null) {
                    r.put("result", false);
                    r.put("reason", 1);
                    return r;
                }
                r.put("name", u.uname);
                r.put("admin", 0);
                if (backup == 50) {
                    String foto = FaceStore.fotoBase64(id);
                    if (foto == null) { r.put("result", false); r.put("reason", 1); }
                    else r.put("record", foto);
                }
                return r;
            }
            case "getusername": {
                com.db.UserBean u = FaceStore.buscarPorCedula(enrollid(m));
                r.put("enrollid", m.opt("enrollid"));
                if (u == null) r.put("result", false);
                else r.put("record", u.uname);
                return r;
            }
            case "getdevcap": {
                r.put("usersize", 10000);
                r.put("fpsize", 0);
                r.put("cardsize", 0);
                r.put("pwdsize", 0);
                r.put("logsize", 100000);
                r.put("useduser", FaceStore.cedulas().size());
                r.put("usedfp", 0);
                r.put("usedcard", 0);
                r.put("usedpwd", 0);
                r.put("usedlog", 0);
                r.put("usednewlog", 0);
                return r;
            }
            case "getdevinfo": {
                r.put("modelname", "DIGIACCESS-PDA");
                r.put("firmware", "DIGIACCESS-PDA 1.0");
                r.put("time", ahora());
                return r;
            }
            case "getnewlog":
            case "getalllog": {
                // Las marcas se registran en DIGIACCESS; a yunatt solo se envían si se activa en Configuración.
                r.put("count", 0);
                r.put("from", 0);
                r.put("to", 0);
                r.put("record", new JSONArray());
                return r;
            }
            default:
                // settime, opendoor, reboot, enableuser, setdevinfo, etc.: se confirman sin cambiar nada del PDA
                return r;
        }
    }

    // ------------------------------------------------------------------ marcas (opcional)

    /** Envía una marca facial a yunatt si está activado en Configuración. */
    public static void enviarMarca(Context ctx, String cedula, String nombre, boolean salida) {
        YunattCliente c = instancia;
        if (c == null || !c.registrado || !Config.load(ctx).yunattMarcas) return;
        c.hilo.execute(() -> {
            try {
                JSONObject rec = new JSONObject();
                rec.put("enrollid", numeroOTexto(cedula));
                rec.put("name", nombre == null ? "" : nombre);
                rec.put("time", ahora());
                rec.put("mode", 8);
                rec.put("inout", salida ? 1 : 0);
                rec.put("event", 0);
                JSONObject m = new JSONObject();
                m.put("cmd", "sendlog");
                m.put("sn", Config.load(ctx).yunattSn.trim());
                m.put("count", 1);
                m.put("logindex", c.logIndex++);
                m.put("record", new JSONArray().put(rec));
                WebSocket w = c.ws;
                if (w != null) w.send(m.toString());
            } catch (Exception e) {
                Log.w(TAG, "No se pudo enviar la marca a yunatt", e);
            }
        });
    }

    // ------------------------------------------------------------------ utilidades

    private static String enrollid(JSONObject m) {
        Object o = m.opt("enrollid");
        if (o == null) return "";
        if (o instanceof Number) return String.valueOf(((Number) o).longValue());
        return o.toString().trim();
    }

    /** Devuelve el enrollid como número cuando lo es (así lo espera la nube), si no como texto. */
    private static Object numeroOTexto(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return s;
        }
    }

    private static String ahora() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }
}
