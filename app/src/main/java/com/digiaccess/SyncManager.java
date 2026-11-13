package com.digiaccess;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sincronización con pda_sync.php (sin fotos; la llave es el número de identificación):
 *   1. Sube las marcas hechas sin conexión  -> el servidor hace los INSERT en ingresos / ingresosdiarios / activos.
 *   2. Baja los autorizados vigentes        -> lista local para validar sin conexión y mostrar nombres.
 *   3. (Opcional) borra del PDA los rostros sin autorización vigente.
 *
 * NO llamar desde el hilo principal.
 */
public final class SyncManager {
    private static final String TAG = "DigiSync";
    private static final Object RUN_LOCK = new Object();

    public static class Resumen {
        public boolean ok = false;
        public int marcasSubidas = 0;
        public int marcasPendientes = 0;
        public int autorizados = 0;
        public int rostrosBorrados = 0;
        public String error = "";

        @Override
        public String toString() {
            if (!ok) return "Error de sincronización: " + error;
            String s = "Sincronizado: " + autorizados + " autorizados vigentes, " + marcasSubidas + " marcas subidas";
            if (marcasPendientes > 0) s += ", " + marcasPendientes + " pendientes";
            if (rostrosBorrados > 0) s += ", " + rostrosBorrados + " rostros borrados";
            return s + ".";
        }
    }

    private SyncManager() { }

    public static Resumen sincronizar(Context ctx) {
        synchronized (RUN_LOCK) {
            Resumen r = new Resumen();
            Config cfg = Config.load(ctx);
            LocalStore store = LocalStore.get(ctx);
            if (!cfg.syncConfigurado()) {
                r.error = "configure la URL de pda_sync.php y el token";
                r.marcasPendientes = store.contarMarcasPendientes();
                return r;
            }
            try {
                r.marcasSubidas = subirMarcas(cfg, store);
                r.marcasPendientes = store.contarMarcasPendientes();
                r.autorizados = bajarAutorizados(cfg, store);
                if (cfg.eliminarRostrosNoAutorizados && r.autorizados > 0) {
                    r.rostrosBorrados = FaceStore.eliminarNoAutorizados(store.identificacionesAutorizadas());
                }
                store.setMeta("ultima_sync", LocalStore.ahora());
                r.ok = true;
            } catch (Exception e) {
                Log.w(TAG, "Sincronización fallida", e);
                r.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                r.marcasPendientes = store.contarMarcasPendientes();
            }
            return r;
        }
    }

    /** Prueba rápida de conexión con pda_sync.php. */
    public static String ping(Context ctx) {
        Config cfg = Config.load(ctx);
        if (!cfg.syncConfigurado()) return "Falta la URL de pda_sync.php o el token.";
        try {
            JSONObject j = new JSONObject(Acceso.request(url(cfg, "ping"), null, null, cfg.token, 8000));
            if (j.optBoolean("ok")) return "Conexión correcta. Hora del servidor: " + j.optString("ahora");
            return "El servidor respondió: " + j.optString("error", j.toString());
        } catch (Exception e) {
            return "No se pudo conectar: " + e.getMessage();
        }
    }

    private static String url(Config cfg, String accion) {
        String u = cfg.syncUrl.trim();
        return u + (u.contains("?") ? "&" : "?") + "accion=" + accion;
    }

    private static int subirMarcas(Config cfg, LocalStore store) throws Exception {
        int total = 0;
        for (int vuelta = 0; vuelta < 20; vuelta++) {         // hasta 2.000 marcas por sincronización
            List<LocalStore.Marca> marcas = store.marcasPendientes(100);
            if (marcas.isEmpty()) break;
            JSONArray arr = new JSONArray();
            for (LocalStore.Marca m : marcas) {
                JSONObject o = new JSONObject();
                o.put("uid", m.uid);
                o.put("identificacion", m.identificacion);
                o.put("nombre", m.nombre == null ? "" : m.nombre);
                o.put("fecha", m.fecha);
                o.put("tipo", m.tipo);
                o.put("punto", m.punto);
                o.put("vehicular", m.vehicular);
                o.put("medio", m.medio);
                arr.put(o);
            }
            JSONObject body = new JSONObject();
            body.put("marcas", arr);
            String resp = Acceso.request(url(cfg, "marcas"), "application/json; charset=utf-8",
                    body.toString().getBytes(StandardCharsets.UTF_8), cfg.token, 20000);
            JSONObject j = new JSONObject(resp);
            if (!j.optBoolean("ok")) throw new Exception(j.optString("error", "respuesta inválida al subir marcas"));
            JSONArray recibidas = j.optJSONArray("recibidas");
            Set<String> uids = new HashSet<>();
            if (recibidas != null) for (int i = 0; i < recibidas.length(); i++) uids.add(recibidas.getString(i));
            if (uids.isEmpty()) break;                          // evita un ciclo si el servidor no confirma
            store.borrarMarcas(uids);
            total += uids.size();
        }
        return total;
    }

    private static int bajarAutorizados(Config cfg, LocalStore store) throws Exception {
        String resp = Acceso.request(url(cfg, "autorizados"), null, null, cfg.token, 30000);
        JSONObject j = new JSONObject(resp);
        if (!j.optBoolean("ok")) throw new Exception(j.optString("error", "respuesta inválida al bajar autorizados"));
        JSONArray arr = j.getJSONArray("autorizados");
        List<LocalStore.Autorizado> lista = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            LocalStore.Autorizado a = new LocalStore.Autorizado();
            a.identificacion = o.optString("identificacion");
            if (a.identificacion.isEmpty()) continue;
            a.nombre = o.optString("nombre");
            a.iddpto = o.optString("iddpto");
            a.tipo = o.optString("tipo");
            a.dias = o.optString("dias");
            a.vehicular = o.optString("vehicular");
            a.fechainicio = o.optString("fechainicio");
            a.fechaterminacion = o.optString("fechaterminacion");
            lista.add(a);
        }
        store.reemplazarAutorizados(lista);
        return store.contarAutorizados();
    }
}
