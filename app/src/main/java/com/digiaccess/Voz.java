package com.digiaccess;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

/**
 * Voz en español con el motor de texto a voz de Android (Google TTS u otro instalado en el PDA).
 * Intenta español de Colombia, luego Latinoamérica, España y español genérico.
 * Si el equipo no tiene voz en español, Sonidos usa tonos.
 */
public final class Voz {
    private static final String TAG = "DigiVoz";
    private static TextToSpeech tts;
    private static volatile boolean lista = false;

    private Voz() { }

    public static synchronized void iniciar(Context ctx) {
        if (tts != null) return;
        tts = new TextToSpeech(ctx.getApplicationContext(), estado -> {
            if (estado != TextToSpeech.SUCCESS) {
                Log.w(TAG, "Motor de voz no disponible");
                return;
            }
            Locale[] opciones = {new Locale("es", "CO"), new Locale("es", "US"), new Locale("es", "MX"), new Locale("es", "ES"), new Locale("es")};
            for (Locale l : opciones) {
                int r = tts.isLanguageAvailable(l);
                if (r >= TextToSpeech.LANG_AVAILABLE) {
                    int s = tts.setLanguage(l);
                    if (s != TextToSpeech.LANG_MISSING_DATA && s != TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts.setSpeechRate(1.05f);
                        lista = true;
                        Log.i(TAG, "Voz en " + l);
                        return;
                    }
                }
            }
            Log.w(TAG, "No hay voz en español instalada");
        });
    }

    public static boolean disponible() {
        return lista;
    }

    /** Dice la frase (interrumpe la anterior). @return false si no hay voz en español */
    @SuppressWarnings("deprecation")
    public static boolean decir(String frase) {
        if (!lista || tts == null || frase == null || frase.trim().isEmpty()) return false;
        tts.speak(frase, TextToSpeech.QUEUE_FLUSH, null);
        return true;
    }

    /** "JUAN PABLO PEREZ" -> "Juan" (para el saludo). */
    public static String primerNombre(String nombre) {
        if (nombre == null) return "";
        String n = nombre.trim();
        if (n.isEmpty()) return "";
        int sp = n.indexOf(' ');
        if (sp > 0) n = n.substring(0, sp);
        return n.substring(0, 1).toUpperCase(new Locale("es", "CO")) + n.substring(1).toLowerCase(new Locale("es", "CO"));
    }
}
