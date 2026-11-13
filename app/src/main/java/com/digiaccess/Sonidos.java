package com.digiaccess;

import android.media.AudioManager;
import android.media.ToneGenerator;

import com.FaceVerifyM8.MainActivity;

/** Sonidos de resultado: voz de bienvenida al autorizar y tono de error al rechazar. */
public final class Sonidos {
    private static ToneGenerator tg;

    private Sonidos() { }

    public static synchronized void resultado(int codigo) {
        if (codigo == 1) {
            MainActivity.PlayVoiceWelcome(1);
            return;
        }
        try {
            if (tg == null) tg = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
            tg.startTone(codigo == 4 ? ToneGenerator.TONE_PROP_NACK : ToneGenerator.TONE_SUP_ERROR, 600);
        } catch (Exception ignored) { }
    }
}
