package com.digiaccess;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

/** Identificación del equipo (misma lógica de la app original de yunatt para el SN). */
public final class Dispositivo {
    private Dispositivo() { }

    private static volatile String cache;

    /** SN del equipo (se calcula una vez). */
    public static String getDeviceSn(Context ctx) {
        String c = cache;
        if (c == null) cache = c = calcular(ctx);
        return c;
    }

    @SuppressWarnings("MissingPermission")
    private static String calcular(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                String sn = Build.getSerial();
                if (!TextUtils.isEmpty(sn) && !"unknown".equalsIgnoreCase(sn)) return sn;
            }
        } catch (Throwable ignored) { }
        try {
            String id = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (!TextUtils.isEmpty(id)) return id;
        } catch (Throwable ignored) { }
        return "unknown";
    }
}
