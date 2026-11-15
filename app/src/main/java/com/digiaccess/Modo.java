package com.digiaccess;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.FaceVerifyM8.R;

/**
 * Sentido (ENTRADA/SALIDA) y carril (PEATONAL/VEHICULAR) del punto.
 * El operador los cambia desde las pantallas de operación, sin la clave de administrador.
 */
public final class Modo {
    private Modo() { }

    // ------------------------------------------------------------------ píldoras de la barra superior

    public static void pintarSentido(TextView pill, String sentido) {
        if (pill == null) return;
        boolean entrada = Config.ENTRADA.equals(sentido);
        pill.setText(entrada ? R.string.sentido_entrada : R.string.sentido_salida);
        pill.setBackgroundResource(entrada ? R.drawable.da_pill_entrada : R.drawable.da_pill_salida);
        pill.setTextColor(pill.getResources().getColor(entrada ? R.color.da_verde_texto : R.color.da_ambar_texto));
    }

    public static void pintarCarril(TextView pill, String carril) {
        if (pill == null) return;
        boolean peatonal = !Config.VEHICULAR.equals(carril);
        pill.setText(peatonal ? R.string.pill_peatonal : R.string.pill_vehicular);
        pill.setBackgroundResource(peatonal ? R.drawable.da_pill_peatonal : R.drawable.da_pill_vehicular);
        pill.setTextColor(peatonal ? 0xFF3B82F6 : 0xFF764BA2);
    }

    /**
     * Conecta las píldoras: un toque alterna el valor, avisa con un mensaje corto y llama alCambiar.
     * @param idSentido id de la píldora de sentido (0 si la pantalla no la tiene)
     * @param idCarril id de la píldora de carril (0 si la pantalla no la tiene)
     */
    public static void conectarPildoras(Activity a, int idSentido, int idCarril, Runnable alCambiar) {
        TextView ps = idSentido != 0 ? a.findViewById(idSentido) : null;
        TextView pc = idCarril != 0 ? a.findViewById(idCarril) : null;
        if (ps != null) ps.setOnClickListener(v -> {
            Config.alternarSentido(a);
            cambiado(a, alCambiar);
        });
        if (pc != null) pc.setOnClickListener(v -> {
            Config.alternarCarril(a);
            cambiado(a, alCambiar);
        });
        pintar(a, idSentido, idCarril);
    }

    public static void pintar(Activity a, int idSentido, int idCarril) {
        Config cfg = Config.load(a);
        if (idSentido != 0) pintarSentido(a.findViewById(idSentido), cfg.sentido);
        if (idCarril != 0) pintarCarril(a.findViewById(idCarril), cfg.carril);
    }

    // ------------------------------------------------------------------ selector segmentado (pantalla principal)

    /** Selector de dos opciones: pinta la opción activa con su color y la otra sin fondo. */
    public static void pintarSegmento(TextView activa, int fondoActiva, TextView inactiva) {
        activa.setBackgroundResource(fondoActiva);
        activa.setTextColor(0xFFFFFFFF);
        inactiva.setBackgroundResource(0);
        inactiva.setTextColor(0xFF9CA3AF);
    }

    public static void conectarSegmentos(Activity a, int idEntrada, int idSalida, int idPeatonal, int idVehicular, Runnable alCambiar) {
        a.findViewById(idEntrada).setOnClickListener(v -> elegir(a, () -> Config.setSentido(a, Config.ENTRADA), alCambiar));
        a.findViewById(idSalida).setOnClickListener(v -> elegir(a, () -> Config.setSentido(a, Config.SALIDA), alCambiar));
        a.findViewById(idPeatonal).setOnClickListener(v -> elegir(a, () -> Config.setCarril(a, Config.PEATONAL), alCambiar));
        a.findViewById(idVehicular).setOnClickListener(v -> elegir(a, () -> Config.setCarril(a, Config.VEHICULAR), alCambiar));
        pintarSegmentos(a, idEntrada, idSalida, idPeatonal, idVehicular);
    }

    public static void pintarSegmentos(Activity a, int idEntrada, int idSalida, int idPeatonal, int idVehicular) {
        Config cfg = Config.load(a);
        TextView en = a.findViewById(idEntrada), sa = a.findViewById(idSalida);
        TextView pe = a.findViewById(idPeatonal), ve = a.findViewById(idVehicular);
        if (Config.ENTRADA.equals(cfg.sentido)) pintarSegmento(en, R.drawable.da_seg_verde, sa);
        else pintarSegmento(sa, R.drawable.da_seg_ambar, en);
        if (Config.VEHICULAR.equals(cfg.carril)) pintarSegmento(ve, R.drawable.da_seg_violeta, pe);
        else pintarSegmento(pe, R.drawable.da_seg_azul, ve);
    }

    private static void elegir(Activity a, Runnable cambio, Runnable alCambiar) {
        Config antes = Config.load(a);
        cambio.run();
        Config despues = Config.load(a);
        if (antes.sentido.equals(despues.sentido) && antes.carril.equals(despues.carril)) return;
        cambiado(a, alCambiar);
    }

    private static void cambiado(Activity a, Runnable alCambiar) {
        Config cfg = Config.load(a);
        String s = Config.ENTRADA.equals(cfg.sentido) ? "ENTRADA" : "SALIDA";
        String c = Config.VEHICULAR.equals(cfg.carril) ? "VEHICULAR" : "PEATONAL";
        Toast.makeText(a, a.getString(R.string.modo_actual, s, c), Toast.LENGTH_SHORT).show();
        if (alCambiar != null) alCambiar.run();
    }

    /** Texto para mostrar bajo el título: "Punto · Peatonal". */
    public static String puntoYCarril(Config cfg) {
        return cfg.punto + " · " + (Config.VEHICULAR.equals(cfg.carril) ? "Vehicular" : "Peatonal");
    }

    static void visible(View v, boolean ver) {
        if (v != null) v.setVisibility(ver ? View.VISIBLE : View.GONE);
    }
}
