package com.digiaccess;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validación de acceso contra DIGIACCESS. La misma para cédula y rostro:
 * el PDA solo identifica a la persona y el servidor decide con sus reglas
 * (vigencia, días, lista negra, permiso vehicular, antipassback) y hace el INSERT en ingresos.
 *
 * NO llamar desde el hilo principal.
 */
public final class Acceso {
    private static final String TAG = "DigiAcceso";

    public static final String MEDIO_CEDULA = "CEDULA";
    public static final String MEDIO_DIGITADA = "DIGITADA";
    public static final String MEDIO_FACIAL = "FACIAL";

    /** Resultado de la validación. */
    public static class Resultado {
        /** 1 = permitido, 2 = rechazado, 4 = antipassback, 0 = sin validar (sin conexión y sin permiso offline) */
        public int codigo;
        public String motivo = "";
        public String nombre = "";
        public String identificacion = "";
        /** true si se decidió en el PDA por falta de conexión (la marca queda pendiente de subir). */
        public boolean sinConexion = false;

        public boolean permitido() {
            return codigo == 1;
        }

        /** Texto corto para mostrar al guarda. */
        public String mensaje() {
            String base;
            switch (motivo == null ? "" : motivo) {
                case "OK": base = "Acceso autorizado"; break;
                case "NO_REGISTRADO": base = "Sin autorización vigente"; break;
                case "NO_VIGENTE": base = "Sin autorización vigente"; break;
                case "LISTA_NEGRA": base = "Acceso restringido"; break;
                case "FUERA_DIA": base = "Fuera de los días autorizados"; break;
                case "SIN_VEHICULAR": base = "Sin permiso vehicular"; break;
                case "ANTIPASSBACK_ENTRADA": base = "Ya tiene una entrada registrada"; break;
                case "ANTIPASSBACK_SALIDA": base = "No tiene entrada registrada"; break;
                case "ID_INVALIDO": base = "Identificación no válida"; break;
                case "ERROR_SERVIDOR": base = "Error en el servidor"; break;
                case "SIN_CONEXION": base = "Sin conexión: no se pudo validar"; break;
                case "SIN_CONFIGURAR": base = "Configure la URL de validación"; break;
                default: base = motivo == null || motivo.isEmpty() ? (codigo == 1 ? "Acceso autorizado" : "Acceso denegado") : motivo;
            }
            if (sinConexion && codigo == 1) base += " (sin conexión)";
            return base;
        }
    }

    private Acceso() { }

    /**
     * Valida a una persona y registra el movimiento.
     * @param identificacion número de cédula (sin puntos)
     * @param nombreLocal nombre conocido en el PDA (cédula leída o rostro enrolado), para la marca sin conexión
     * @param medio CEDULA, DIGITADA o FACIAL (informativo)
     */
    public static Resultado validar(Context ctx, String identificacion, String nombreLocal, String medio) {
        Config cfg = Config.load(ctx);
        Resultado r = new Resultado();
        r.identificacion = identificacion;
        r.nombre = nombreLocal == null ? "" : nombreLocal;

        if (!cfg.validarConfigurado()) {
            r.codigo = 0;
            r.motivo = "SIN_CONFIGURAR";
            return r;
        }

        try {
            Map<String, String> p = new LinkedHashMap<>();
            p.put("identificacion", identificacion);
            p.put("punto", cfg.punto);
            p.put("tipo", cfg.sentido);
            p.put("vehicular", cfg.carril);
            p.put("formato", "lcd");
            p.put("medio", medio);           // validar_acceso.php lo ignora; útil si luego se quiere guardar
            String resp = postForm(cfg.validarUrl, p, 5000).trim();
            Log.d(TAG, "validar " + identificacion + " -> " + resp);

            String[] parts = resp.split("\\|", 3);
            r.codigo = Integer.parseInt(parts[0].trim());
            r.motivo = parts.length > 1 ? parts[1].trim() : (r.codigo == 1 ? "OK" : "");
            if (parts.length > 2 && parts[2].trim().length() > 0) r.nombre = parts[2].trim();
            if (r.codigo != 1 && r.codigo != 2 && r.codigo != 4) {
                r.codigo = 2;
                if (r.motivo.isEmpty()) r.motivo = "ERROR_SERVIDOR";
            }
            return r;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Respuesta inesperada de validar_acceso.php", e);
            r.codigo = 2;
            r.motivo = "ERROR_SERVIDOR";
            return r;
        } catch (Exception e) {
            Log.w(TAG, "Sin conexión con DIGIACCESS: " + e.getMessage());
        }

        // ---- Sin conexión: decisión local con la última lista de autorizados
        r.sinConexion = true;
        if (!cfg.permitirSinConexion) {
            r.codigo = 0;
            r.motivo = "SIN_CONEXION";
            return r;
        }
        LocalStore store = LocalStore.get(ctx);
        String motivo = store.motivoRechazoSinConexion(identificacion, cfg.carril);
        LocalStore.Autorizado a = store.buscarAutorizado(identificacion);
        if (a != null && a.nombre != null && !a.nombre.isEmpty()) r.nombre = a.nombre;
        if (motivo == null) {
            r.codigo = 1;
            r.motivo = "OK";
            store.agregarMarca(identificacion, r.nombre, cfg.sentido, cfg.punto, cfg.carril, medio);
        } else {
            r.codigo = 2;
            r.motivo = motivo;
        }
        return r;
    }

    /**
     * Solo consulta: pregunta a DIGIACCESS si la persona está autorizada SIN registrar ingreso
     * (usa prueba=1). Comprueba contra ENTRADA peatonal para mostrar la autorización base.
     * Sin conexión, mira la última lista local sin marcar.
     */
    public static Resultado consultar(Context ctx, String identificacion, String medio) {
        Config cfg = Config.load(ctx);
        Resultado r = new Resultado();
        r.identificacion = identificacion;

        if (!cfg.validarConfigurado()) {
            r.codigo = 0;
            r.motivo = "SIN_CONFIGURAR";
            return r;
        }

        try {
            Map<String, String> p = new LinkedHashMap<>();
            p.put("identificacion", identificacion);
            p.put("punto", cfg.punto);
            p.put("tipo", "ENTRADA");
            p.put("vehicular", "PEATONAL");
            p.put("formato", "lcd");
            p.put("medio", medio);
            p.put("consulta", "1");          // modo consulta: autorización base, sin compuertas ni registro
            p.put("prueba", "1");            // respaldo: si el servidor es viejo, al menos no registra
            String resp = postForm(cfg.validarUrl, p, 5000).trim();
            Log.d(TAG, "consultar " + identificacion + " -> " + resp);

            String[] parts = resp.split("\\|", 3);
            r.codigo = Integer.parseInt(parts[0].trim());
            r.motivo = parts.length > 1 ? parts[1].trim() : (r.codigo == 1 ? "OK" : "");
            if (parts.length > 2 && parts[2].trim().length() > 0) r.nombre = parts[2].trim();
            if (r.codigo != 1 && r.codigo != 2 && r.codigo != 4) {
                r.codigo = 2;
                if (r.motivo.isEmpty()) r.motivo = "ERROR_SERVIDOR";
            }
            return r;
        } catch (NumberFormatException e) {
            r.codigo = 2;
            r.motivo = "ERROR_SERVIDOR";
            return r;
        } catch (Exception e) {
            Log.w(TAG, "Sin conexión (consulta): " + e.getMessage());
        }

        r.sinConexion = true;
        LocalStore store = LocalStore.get(ctx);
        LocalStore.Autorizado a = store.buscarAutorizado(identificacion);
        if (a != null) {
            r.nombre = a.nombre != null ? a.nombre : "";
            String motivo = store.motivoRechazoSinConexion(identificacion, cfg.carril);
            r.codigo = motivo == null ? 1 : 2;
            r.motivo = motivo == null ? "OK" : motivo;
        } else {
            r.codigo = 2;
            r.motivo = "NO_REGISTRADO";
        }
        return r;
    }

    // ------------------------------------------------------------------ HTTP

    static String postForm(String url, Map<String, String> params, int timeoutMs) throws Exception {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(URLEncoder.encode(e.getKey(), "UTF-8")).append('=')
                    .append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), "UTF-8"));
        }
        return request(url, "application/x-www-form-urlencoded; charset=utf-8",
                body.toString().getBytes(StandardCharsets.UTF_8), null, timeoutMs);
    }

    static String request(String url, String contentType, byte[] body, String token, int timeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setUseCaches(false);
            if (token != null) c.setRequestProperty("X-PDA-Token", token);
            if (body != null) {
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", contentType);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body);
                }
            } else {
                c.setRequestMethod("GET");
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (in != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line).append('\n');
                }
            }
            if (code >= 400) throw new java.io.IOException("HTTP " + code + ": " + sb.toString().trim());
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }
}
