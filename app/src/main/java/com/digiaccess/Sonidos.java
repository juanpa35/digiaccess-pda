package com.digiaccess;

import android.media.AudioManager;
import android.media.ToneGenerator;

/**
 * Sonido del resultado: voz en español (Voz) con el saludo o el motivo;
 * si el PDA no tiene voz en español, un tono corto.
 */
public final class Sonidos {
    private static ToneGenerator tg;

    private Sonidos() { }

    /**
     * @param codigo 1 autorizado, 2 denegado, 4 antipassback, 0 sin validar, -1 no reconocido
     * @param frase lo que se dice en voz (si hay voz en español)
     */
    public static synchronized void resultado(int codigo, String frase) {
        if (Voz.decir(frase)) return;
        try {
            if (tg == null) tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            if (codigo == 1) tg.startTone(ToneGenerator.TONE_PROP_ACK, 300);
            else tg.startTone(codigo == 4 || codigo == 0 ? ToneGenerator.TONE_PROP_NACK : ToneGenerator.TONE_SUP_ERROR, 600);
        } catch (Exception ignored) { }
    }

    /** Frase estándar según el resultado de DIGIACCESS. */
    public static String frase(Acceso.Resultado r, boolean salida) {
        if (r.permitido()) {
            String n = Voz.primerNombre(r.nombre);
            return salida ? ("Hasta luego" + (n.isEmpty() ? "" : ", " + n)) : ("Bienvenido" + (n.isEmpty() ? "" : ", " + n));
        }
        return r.mensaje();
    }
}
