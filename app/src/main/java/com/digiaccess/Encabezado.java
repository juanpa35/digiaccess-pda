package com.digiaccess;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import com.FaceVerifyM8.R;

/** Configura el encabezado común (layout da_encabezado): botón atrás y título. */
public final class Encabezado {
    private Encabezado() { }

    public static void configurar(Activity a, int titulo) {
        View atras = a.findViewById(R.id.btnAtras);
        if (atras != null) atras.setOnClickListener(v -> a.finish());
        TextView t = a.findViewById(R.id.txtEncabezado);
        if (t != null) t.setText(titulo);
    }
}
