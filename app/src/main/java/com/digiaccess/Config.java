package com.digiaccess;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Configuración DIGIACCESS del PDA (SharedPreferences). Se edita en Funciones > DIGIACCESS.
 *
 * El PDA usa el mismo validar_acceso.php que las demás controladoras DIGIACCESS:
 *   POST identificacion, punto, tipo (ENTRADA/SALIDA), vehicular (PEATONAL/VEHICULAR), formato=lcd
 *   respuesta  1|OK|Nombre   2|MOTIVO|Nombre   4|ANTIPASSBACK_xxx|Nombre
 * y pda_sync.php para bajar autorizados vigentes y subir marcas hechas sin conexión.
 */
public final class Config {
    private static final String PREFS = "digiaccess";

    public static final String ENTRADA = "ENTRADA";
    public static final String SALIDA = "SALIDA";
    public static final String PEATONAL = "PEATONAL";
    public static final String VEHICULAR = "VEHICULAR";

    /** URL completa del script de validación, ej. https://digiaccesstibitoc.com/validar_acceso.php */
    public String validarUrl = "";
    /** URL completa de pda_sync.php, ej. https://digiaccesstibitoc.com/pda_sync.php */
    public String syncUrl = "";
    /** Token del PDA (debe estar en pda_config.php del servidor). */
    public String token = "";
    /** Nombre del punto que queda en ingresos.punto, ej. "PDA Porteria Principal". */
    public String punto = "PDA";
    /** ENTRADA | SALIDA */
    public String sentido = ENTRADA;
    /** PEATONAL | VEHICULAR (se envía como 'vehicular' a validar_acceso.php) */
    public String carril = PEATONAL;
    /** Acción broadcast adicional del lector 2D (opcional). */
    public String scannerAction = "";
    /** Nombre del extra adicional con el dato leído (opcional). */
    public String scannerExtra = "";
    /** Minutos entre sincronizaciones. */
    public int syncMinutes = 10;
    /** Sin conexión: permitir el paso si la persona está en la última lista de autorizados vigentes. */
    public boolean permitirSinConexion = true;
    /** Al sincronizar, borrar del PDA los rostros que ya no tienen autorización vigente. */
    public boolean eliminarRostrosNoAutorizados = false;

    // --- Nube yunatt: de ahí llegan los rostros (usuarios con foto, altas y bajas)
    public boolean yunattActivo = false;
    public String yunattServidor = "global.yunatt.com";
    public int yunattPuerto = 7792;
    /** Número de serie con el que el PDA está registrado en la nube yunatt. */
    public String yunattSn = "";
    /** Enviar también a yunatt las marcas de rostro (además de DIGIACCESS). */
    public boolean yunattMarcas = false;

    /**
     * Detección automática de rostros en la pantalla principal. Mantiene la cámara encendida,
     * y en PDA con lector 2D por cámara impide leer cédulas. Por eso viene apagada.
     */
    public boolean autoRostro = false;

    /** Reconocimiento facial habilitado. Apagado por defecto: así no pide licencia ni usa cámara. */
    public boolean rostroHabilitado = false;

    private Config() { }

    public static Config load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Config c = new Config();
        c.validarUrl = p.getString("validarUrl", "");
        c.syncUrl = p.getString("syncUrl", "");
        c.token = p.getString("token", "");
        c.punto = p.getString("punto", "PDA");
        c.sentido = SALIDA.equals(p.getString("sentido", ENTRADA)) ? SALIDA : ENTRADA;
        c.carril = VEHICULAR.equals(p.getString("carril", PEATONAL)) ? VEHICULAR : PEATONAL;
        c.scannerAction = p.getString("scannerAction", "");
        c.scannerExtra = p.getString("scannerExtra", "");
        c.syncMinutes = p.getInt("syncMinutes", 10);
        c.permitirSinConexion = p.getBoolean("permitirSinConexion", true);
        c.eliminarRostrosNoAutorizados = p.getBoolean("eliminarRostros", false);
        c.yunattActivo = p.getBoolean("yunattActivo", false);
        c.yunattServidor = p.getString("yunattServidor", "global.yunatt.com");
        c.yunattPuerto = p.getInt("yunattPuerto", 7792);
        c.yunattSn = p.getString("yunattSn", "");
        if (c.yunattSn.trim().isEmpty()) {
            String sn = Dispositivo.getDeviceSn(ctx);
            if (!"unknown".equals(sn)) c.yunattSn = sn;
        }
        c.yunattMarcas = p.getBoolean("yunattMarcas", false);
        c.autoRostro = p.getBoolean("autoRostro", false);
        c.rostroHabilitado = p.getBoolean("rostroHabilitado", false);
        return c;
    }

    public void save(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("validarUrl", t(validarUrl))
                .putString("syncUrl", t(syncUrl))
                .putString("token", t(token))
                .putString("punto", t(punto).isEmpty() ? "PDA" : t(punto))
                .putString("sentido", SALIDA.equals(sentido) ? SALIDA : ENTRADA)
                .putString("carril", VEHICULAR.equals(carril) ? VEHICULAR : PEATONAL)
                .putString("scannerAction", t(scannerAction))
                .putString("scannerExtra", t(scannerExtra))
                .putInt("syncMinutes", Math.max(1, syncMinutes))
                .putBoolean("permitirSinConexion", permitirSinConexion)
                .putBoolean("eliminarRostros", eliminarRostrosNoAutorizados)
                .putBoolean("yunattActivo", yunattActivo)
                .putString("yunattServidor", t(yunattServidor).isEmpty() ? "global.yunatt.com" : t(yunattServidor))
                .putInt("yunattPuerto", yunattPuerto > 0 ? yunattPuerto : 7792)
                .putString("yunattSn", t(yunattSn))
                .putBoolean("yunattMarcas", yunattMarcas)
                .putBoolean("autoRostro", autoRostro)
                .putBoolean("rostroHabilitado", rostroHabilitado)
                .apply();
    }

    /** Cambia solo el sentido (botón ENTRADA/SALIDA de las pantallas). */
    public static void setSentido(Context ctx, String sentido) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("sentido", SALIDA.equals(sentido) ? SALIDA : ENTRADA).apply();
    }

    /** Cambia solo el carril (botón PEATONAL/VEHICULAR de las pantallas de operación). */
    public static void setCarril(Context ctx, String carril) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("carril", VEHICULAR.equals(carril) ? VEHICULAR : PEATONAL).apply();
    }

    public static String alternarCarril(Context ctx) {
        String nuevo = PEATONAL.equals(load(ctx).carril) ? VEHICULAR : PEATONAL;
        setCarril(ctx, nuevo);
        return nuevo;
    }

    public static String alternarSentido(Context ctx) {
        String nuevo = ENTRADA.equals(load(ctx).sentido) ? SALIDA : ENTRADA;
        setSentido(ctx, nuevo);
        return nuevo;
    }

    public boolean validarConfigurado() {
        return t(validarUrl).startsWith("http");
    }

    public boolean yunattConfigurado() {
        return yunattActivo && t(yunattSn).length() > 0 && t(yunattServidor).length() > 0;
    }

    public boolean syncConfigurado() {
        return t(syncUrl).startsWith("http") && t(token).length() > 0;
    }

    private static String t(String s) {
        return s == null ? "" : s.trim();
    }
}
