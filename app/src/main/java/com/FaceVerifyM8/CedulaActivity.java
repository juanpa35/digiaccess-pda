package com.FaceVerifyM8;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.db.RecordBean;
import com.db.UserBean;
import com.digiaccess.Acceso;
import com.digiaccess.Cedula;
import com.digiaccess.Config;
import com.digiaccess.FaceStore;
import com.digiaccess.LocalStore;
import com.digiaccess.ScannerHelper;
import com.digiaccess.Sonidos;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Validación de ingreso/salida con cédula de ciudadanía.
 *  - Cédula amarilla: se escanea el PDF417 del respaldo con el lector 2D del PDA.
 *  - Cédula digital (QR cifrado): botón "Digitar cédula".
 * La decisión la toma DIGIACCESS (validar_acceso.php), igual que para el rostro.
 */
public class CedulaActivity extends AppCompatActivity implements ScannerHelper.Listener {

    /** Extras opcionales para abrir esta pantalla con una lectura ya hecha (desde la pantalla principal). */
    public static final String EXTRA_RAW = "raw";
    public static final String EXTRA_TEXT = "text";

    private static final long VOLVER_A_ESPERA_MS = 6000;
    private static final long SALIR_POR_INACTIVIDAD_MS = 60000;

    /** Estilos del resultado */
    private static final int OK = 1, ERROR = 2, AVISO = 3;

    private ScannerHelper scanner;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean validando = false;

    private View cardEspera, cardResultado, btnDigitar, btnRostro, btnSiguiente, boxMensaje;
    private View divEmpresa, filaEmpresa, divTipo, filaTipo;
    private ImageView ivEspera, ivResultado, ivMensaje;
    private TextView txtEsperaTitulo, txtEsperaAyuda, txtResultado, txtNombre, txtCedula, txtEmpresa, txtTipo, txtHora, txtMensaje;
    private TextView txtPunto, btnSentido;

    private final Runnable volverAEspera = this::mostrarEspera;
    private final Runnable salir = this::finish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cedula);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        cardEspera = findViewById(R.id.cardEspera);
        cardResultado = findViewById(R.id.cardResultado);
        ivEspera = findViewById(R.id.ivEspera);
        txtEsperaTitulo = findViewById(R.id.txtEsperaTitulo);
        txtEsperaAyuda = findViewById(R.id.txtEsperaAyuda);
        ivResultado = findViewById(R.id.ivResultado);
        txtResultado = findViewById(R.id.txtResultado);
        txtNombre = findViewById(R.id.txtNombre);
        txtCedula = findViewById(R.id.txtCedula);
        txtEmpresa = findViewById(R.id.txtEmpresa);
        txtTipo = findViewById(R.id.txtTipo);
        txtHora = findViewById(R.id.txtHora);
        divEmpresa = findViewById(R.id.divEmpresa);
        filaEmpresa = findViewById(R.id.filaEmpresaDatos);
        divTipo = findViewById(R.id.divTipo);
        filaTipo = findViewById(R.id.filaTipo);
        boxMensaje = findViewById(R.id.boxMensaje);
        ivMensaje = findViewById(R.id.ivMensaje);
        txtMensaje = findViewById(R.id.txtMensaje);
        txtPunto = findViewById(R.id.txtPunto);
        btnSentido = findViewById(R.id.btnSentido);
        btnDigitar = findViewById(R.id.btnDigitar);
        btnRostro = findViewById(R.id.btnRostro);
        btnSiguiente = findViewById(R.id.btnSiguiente);

        btnSentido.setOnClickListener(v -> {
            Config.alternarSentido(this);
            mostrarSentido();
        });
        btnDigitar.setOnClickListener(v -> pedirNumero(null));
        btnRostro.setOnClickListener(v -> {
            startActivity(new Intent(this, VideoActivity.class));
            finish();
        });
        btnSiguiente.setOnClickListener(v -> mostrarEspera());

        scanner = new ScannerHelper(this, this);
        mostrarEspera();

        // Lectura recibida desde la pantalla principal
        Intent it = getIntent();
        byte[] raw = it.getByteArrayExtra(EXTRA_RAW);
        String text = it.getStringExtra(EXTRA_TEXT);
        if (raw != null || text != null) ui.post(() -> onScan(raw, text));
    }

    @Override
    protected void onResume() {
        super.onResume();
        mostrarSentido();
        scanner.start();
        reiniciarInactividad();
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanner.stop();
        ui.removeCallbacks(salir);
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        worker.shutdown();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Lector en modo teclado (si no está configurado en modo broadcast)
        if (getCurrentFocus() == null || !(getCurrentFocus() instanceof EditText)) {
            if (scanner.handleKeyEvent(event)) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    // ------------------------------------------------------------------ lectura

    @Override
    public void onScan(byte[] raw, String text) {
        reiniciarInactividad();
        if (validando) return;
        Cedula c = Cedula.parse(raw, text);
        if (c == null) {
            // QR de la cédula digital (cifrado) u otro código
            mostrarAviso(getString(R.string.res_no_reconocido), getString(R.string.cedula_no_reconocida_ayuda));
            Sonidos.resultado(2);
            pedirNumero(null);
            return;
        }
        validar(c, Acceso.MEDIO_CEDULA);
    }

    private void pedirNumero(String inicial) {
        reiniciarInactividad();
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
        et.setTextSize(26);
        et.setHint(R.string.cedula_numero_hint);
        et.setBackgroundResource(R.drawable.da_input);
        int p = (int) (14 * getResources().getDisplayMetrics().density);
        et.setPadding(p, p, p, p);
        if (inicial != null) et.setText(inicial);
        android.widget.FrameLayout marco = new android.widget.FrameLayout(this);
        marco.setPadding(p + p / 2, p / 2, p + p / 2, 0);
        marco.addView(et);
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(R.string.cedula_digitar)
                .setView(marco)
                .setPositiveButton(R.string.cedula_validar, (d, w) -> {
                    Cedula c = Cedula.digitada(et.getText().toString());
                    if (c == null) {
                        mostrarAviso(getString(R.string.res_numero_invalido), getString(R.string.cedula_numero_invalido));
                        Sonidos.resultado(2);
                    } else {
                        validar(c, Acceso.MEDIO_DIGITADA);
                    }
                })
                .setNegativeButton(R.string.cancelar, null)
                .create();
        dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
        et.requestFocus();
    }

    // ------------------------------------------------------------------ validación

    private void validar(Cedula c, String medio) {
        validando = true;
        ui.removeCallbacks(volverAEspera);
        String nombreLocal = c.tieneNombre() ? c.nombreCompleto() : nombreEnrolado(c.numero);
        mostrarValidando(c.numero);

        worker.execute(() -> {
            Acceso.Resultado r = Acceso.validar(this, c.numero, nombreLocal, medio);
            LocalStore.Autorizado a = LocalStore.get(this).buscarAutorizado(c.numero);
            guardarRegistroLocal(c.numero, r);
            ui.post(() -> {
                validando = false;
                mostrarResultado(r, c.numero, a);
                Sonidos.resultado(r.codigo);
                ui.postDelayed(volverAEspera, VOLVER_A_ESPERA_MS);
            });
        });
    }

    private String nombreEnrolado(String cedula) {
        UserBean u = FaceStore.buscarPorCedula(cedula);
        return u != null ? u.uname : "";
    }

    /** Deja el movimiento también en "Registros" del PDA (sin foto). */
    private void guardarRegistroLocal(String cedula, Acceso.Resultado r) {
        try {
            if (MainActivity.db == null) return;
            RecordBean rec = new RecordBean();
            UserBean u = FaceStore.buscarPorCedula(cedula);
            rec.uid = u != null ? u.uid : 0;
            rec.uname = r.nombre;
            rec.workno = cedula;
            rec.photoFileName = u != null ? u.photoFileName : "";
            rec.pictureFileName = "";
            rec.stat = r.codigo == 1 ? 0 : 1;
            rec.score = 0;
            MainActivity.db.insertRecord(rec);
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------ pantalla

    private void mostrarEspera() {
        ui.removeCallbacks(volverAEspera);
        cardResultado.setVisibility(View.GONE);
        cardEspera.setVisibility(View.VISIBLE);
        ivEspera.setImageResource(R.drawable.ic_da_scan);
        txtEsperaTitulo.setText(R.string.cedula_escanee);
        txtEsperaAyuda.setText(R.string.cedula_ayuda);
        botones(true);
    }

    private void mostrarValidando(String cedula) {
        cardResultado.setVisibility(View.GONE);
        cardEspera.setVisibility(View.VISIBLE);
        ivEspera.setImageResource(R.drawable.ic_da_sync);
        txtEsperaTitulo.setText(R.string.cedula_validando);
        txtEsperaAyuda.setText("C.C. " + formatear(cedula));
    }

    private void mostrarResultado(Acceso.Resultado r, String cedula, LocalStore.Autorizado a) {
        int estilo;
        String titulo;
        if (r.codigo == 1) {
            estilo = OK;
            titulo = getString(Config.SALIDA.equals(Config.load(this).sentido) ? R.string.res_salida_ok : R.string.res_entrada_ok);
        } else if (r.codigo == 2) {
            estilo = ERROR;
            titulo = getString(R.string.res_denegado);
        } else {  // 4 = antipassback, 0 = sin validar
            estilo = AVISO;
            titulo = getString(r.codigo == 4 ? R.string.res_denegado : R.string.res_sin_validar);
        }
        String mensaje = null;
        if (r.codigo != 1) mensaje = r.mensaje();
        else if (r.sinConexion) mensaje = getString(R.string.validado_sin_conexion);
        int estiloMensaje = (r.codigo == 1) ? AVISO : estilo;

        String empresa = a != null ? a.iddpto : null;
        String tipo = a != null ? a.tipo : null;
        pintarResultado(estilo, titulo, r.nombre, cedula, empresa, tipo, mensaje, estiloMensaje);
    }

    /** Resultado sin consulta al servidor (código no reconocido, número inválido). */
    private void mostrarAviso(String titulo, String mensaje) {
        pintarResultado(AVISO, titulo, null, null, null, null, mensaje, AVISO);
        ui.postDelayed(volverAEspera, VOLVER_A_ESPERA_MS);
    }

    private void pintarResultado(int estilo, String titulo, String nombre, String cedula,
                                 String empresa, String tipo, String mensaje, int estiloMensaje) {
        cardEspera.setVisibility(View.GONE);
        cardResultado.setVisibility(View.VISIBLE);
        botones(false);

        ivResultado.setBackgroundResource(estilo == OK ? R.drawable.da_icbox_ok : estilo == ERROR ? R.drawable.da_icbox_error : R.drawable.da_icbox_aviso);
        ivResultado.setImageResource(estilo == OK ? R.drawable.ic_da_check : estilo == ERROR ? R.drawable.ic_da_x : R.drawable.ic_da_alerta);
        txtResultado.setText(titulo);
        txtResultado.setTextColor(color(estilo == OK ? R.color.da_verde_texto : estilo == ERROR ? R.color.da_rojo_texto : R.color.da_ambar_texto));

        boolean hayNombre = nombre != null && !nombre.trim().isEmpty();
        txtNombre.setText(hayNombre ? nombre : "");
        txtNombre.setVisibility(hayNombre ? View.VISIBLE : View.GONE);
        txtCedula.setText(cedula == null ? "—" : formatear(cedula));
        fila(divEmpresa, filaEmpresa, txtEmpresa, empresa);
        fila(divTipo, filaTipo, txtTipo, tipo);
        txtHora.setText(new SimpleDateFormat("hh:mm a", MainActivity.LOCALE_CO).format(new Date()));

        if (mensaje == null || mensaje.isEmpty()) {
            boxMensaje.setVisibility(View.GONE);
        } else {
            boxMensaje.setVisibility(View.VISIBLE);
            boxMensaje.setBackgroundResource(estiloMensaje == ERROR ? R.drawable.da_msg_error : estiloMensaje == OK ? R.drawable.da_msg_ok : R.drawable.da_msg_aviso);
            int c = color(estiloMensaje == ERROR ? R.color.da_rojo_texto : estiloMensaje == OK ? R.color.da_verde_texto : R.color.da_ambar_texto);
            ivMensaje.setColorFilter(c);
            txtMensaje.setTextColor(c);
            txtMensaje.setText(mensaje);
        }
    }

    private void fila(View divisor, View fila, TextView valor, String texto) {
        boolean ver = texto != null && !texto.trim().isEmpty();
        divisor.setVisibility(ver ? View.VISIBLE : View.GONE);
        fila.setVisibility(ver ? View.VISIBLE : View.GONE);
        valor.setText(ver ? texto : "");
    }

    private void botones(boolean espera) {
        btnDigitar.setVisibility(espera ? View.VISIBLE : View.GONE);
        btnRostro.setVisibility(espera ? View.VISIBLE : View.GONE);
        btnSiguiente.setVisibility(espera ? View.GONE : View.VISIBLE);
    }

    private void mostrarSentido() {
        Config cfg = Config.load(this);
        pintarSentido(btnSentido, cfg.sentido);
        txtPunto.setText(cfg.punto + " · " + (Config.VEHICULAR.equals(cfg.carril) ? "Vehicular" : "Peatonal"));
    }

    /** Píldora ENTRADA (verde) / SALIDA (ámbar) de la barra superior. */
    static void pintarSentido(TextView pill, String sentido) {
        boolean entrada = Config.ENTRADA.equals(sentido);
        pill.setText(entrada ? R.string.sentido_entrada : R.string.sentido_salida);
        pill.setBackgroundResource(entrada ? R.drawable.da_pill_entrada : R.drawable.da_pill_salida);
        pill.setTextColor(pill.getResources().getColor(entrada ? R.color.da_verde_texto : R.color.da_ambar_texto));
    }

    private int color(int res) {
        return getResources().getColor(res);
    }

    private void reiniciarInactividad() {
        ui.removeCallbacks(salir);
        ui.postDelayed(salir, SALIR_POR_INACTIVIDAD_MS);
    }

    static String formatear(String n) {
        // 1020304050 -> 1.020.304.050
        StringBuilder sb = new StringBuilder(n);
        for (int i = sb.length() - 3; i > 0; i -= 3) sb.insert(i, '.');
        return sb.toString();
    }
}
