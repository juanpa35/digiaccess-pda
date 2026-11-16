package com.FaceVerifyM8;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.digiaccess.Config;
import com.digiaccess.FaceStore;
import com.digiaccess.LocalStore;
import com.digiaccess.SyncManager;
import com.digiaccess.YunattCliente;

/** Configuración de la conexión con DIGIACCESS (Funciones > DIGIACCESS). Protegida por la clave de administrador. */
public class DigiAccessActivity extends AppCompatActivity {

    private EditText etValidarUrl, etSyncUrl, etToken, etPunto, etSyncMin, etScannerAccion, etScannerExtra;
    private RadioButton rbEntrada, rbSalida, rbPeatonal, rbVehicular;
    private CheckBox cbSinConexion, cbEliminarRostros, cbAutoRostro, cbYunattActivo, cbYunattMarcas, cbRostroHabilitado;
    private EditText etYunattServidor, etYunattPuerto, etYunattSn;
    private TextView txtEstado, txtEstadoYunatt;
    private final android.os.Handler ui = new android.os.Handler();
    private final Runnable refrescarYunatt = new Runnable() {
        @Override
        public void run() {
            txtEstadoYunatt.setText(YunattCliente.estado
                    + "\nRostros en el PDA: " + MainActivity.nUserNum
                    + " · recibidos: " + YunattCliente.rostrosRecibidos
                    + " · eliminados: " + YunattCliente.rostrosEliminados
                    + (YunattCliente.ultimoError.isEmpty() || YunattCliente.conectado() ? "" : "\nÚltimo error: " + YunattCliente.ultimoError));
            ui.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.digiaccess_config);
		com.digiaccess.Encabezado.configurar(this, R.string.title_digiaccess);

        etValidarUrl = findViewById(R.id.etValidarUrl);
        etSyncUrl = findViewById(R.id.etSyncUrl);
        etToken = findViewById(R.id.etToken);
        etPunto = findViewById(R.id.etPunto);
        etSyncMin = findViewById(R.id.etSyncMin);
        etScannerAccion = findViewById(R.id.etScannerAccion);
        etScannerExtra = findViewById(R.id.etScannerExtra);
        rbEntrada = findViewById(R.id.rbEntrada);
        rbSalida = findViewById(R.id.rbSalida);
        rbPeatonal = findViewById(R.id.rbPeatonal);
        rbVehicular = findViewById(R.id.rbVehicular);
        cbSinConexion = findViewById(R.id.cbSinConexion);
        cbEliminarRostros = findViewById(R.id.cbEliminarRostros);
        txtEstado = findViewById(R.id.txtEstadoSync);
        cbRostroHabilitado = findViewById(R.id.cbRostroHabilitado);
        cbAutoRostro = findViewById(R.id.cbAutoRostro);
        cbYunattActivo = findViewById(R.id.cbYunattActivo);
        cbYunattMarcas = findViewById(R.id.cbYunattMarcas);
        etYunattServidor = findViewById(R.id.etYunattServidor);
        etYunattPuerto = findViewById(R.id.etYunattPuerto);
        etYunattSn = findViewById(R.id.etYunattSn);
        txtEstadoYunatt = findViewById(R.id.txtEstadoYunatt);
        findViewById(R.id.btnLicencia).setOnClickListener(v -> {
            guardar();
            startActivity(new Intent(this, LicenseActivity.class));
        });

        findViewById(R.id.btnPermArchivos).setOnClickListener(v -> abrirPermisoArchivos());

        Config c = Config.load(this);
        etValidarUrl.setText(c.validarUrl);
        etSyncUrl.setText(c.syncUrl);
        etToken.setText(c.token);
        etPunto.setText(c.punto);
        etSyncMin.setText(String.valueOf(c.syncMinutes));
        etScannerAccion.setText(c.scannerAction);
        etScannerExtra.setText(c.scannerExtra);
        (Config.SALIDA.equals(c.sentido) ? rbSalida : rbEntrada).setChecked(true);
        (Config.VEHICULAR.equals(c.carril) ? rbVehicular : rbPeatonal).setChecked(true);
        cbSinConexion.setChecked(c.permitirSinConexion);
        cbEliminarRostros.setChecked(c.eliminarRostrosNoAutorizados);
        cbRostroHabilitado.setChecked(c.rostroHabilitado);
        cbAutoRostro.setChecked(c.autoRostro);
        cbYunattActivo.setChecked(c.yunattActivo);
        cbYunattMarcas.setChecked(c.yunattMarcas);
        etYunattServidor.setText(c.yunattServidor);
        etYunattPuerto.setText(String.valueOf(c.yunattPuerto));
        etYunattSn.setText(c.yunattSn);
        mostrarEstadoLocal();

        findViewById(R.id.btnYunattConectar).setOnClickListener(v -> {
            guardar();
            Config cy = Config.load(this);
            MainActivity.refrescarModo(this);
            if (!cy.rostroHabilitado) {
                Toast.makeText(this, R.string.cfg_rostro_habilitar, Toast.LENGTH_LONG).show();
                cbRostroHabilitado.requestFocus();
                return;
            }
            if (cy.yunattActivo && cy.yunattSn.isEmpty()) {
                Toast.makeText(this, R.string.cfg_yunatt_sn, Toast.LENGTH_LONG).show();
                etYunattSn.requestFocus();
            }
            YunattCliente.reiniciar(this);
        });

        Button btnGuardar = findViewById(R.id.btnGuardar);
        btnGuardar.setOnClickListener(v -> {
            guardar();
            MainActivity.refrescarModo(this);
            if (cbRostroHabilitado.isChecked()) YunattCliente.reiniciar(this);
            else YunattCliente.detener();
            Toast.makeText(this, R.string.cfg_guardado, Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnProbar).setOnClickListener(v -> {
            guardar();
            txtEstado.setText(R.string.cfg_probando);
            new Thread(() -> {
                String msg = SyncManager.ping(this);
                runOnUiThread(() -> txtEstado.setText(msg));
            }).start();
        });

        findViewById(R.id.btnSincronizar).setOnClickListener(v -> {
            guardar();
            txtEstado.setText(R.string.cfg_sincronizando);
            new Thread(() -> {
                SyncManager.Resumen r = SyncManager.sincronizar(this);
                runOnUiThread(() -> txtEstado.setText(r.toString()));
            }).start();
        });

        findViewById(R.id.btnDepurar).setOnClickListener(v -> {
            LocalStore store = LocalStore.get(this);
            if (store.contarAutorizados() == 0) {
                txtEstado.setText(R.string.cfg_depurar_sin_lista);
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.cfg_depurar)
                    .setMessage(R.string.cfg_depurar_confirmar)
                    .setPositiveButton(R.string.aceptar, (d, w) -> new Thread(() -> {
                        int n = FaceStore.eliminarNoAutorizados(store.identificacionesAutorizadas());
                        runOnUiThread(() -> txtEstado.setText(getString(R.string.cfg_depurados, n)));
                    }).start())
                    .setNegativeButton(R.string.cancelar, null)
                    .show();
        });
    }

    private void guardar() {
        Config c = Config.load(this);
        c.validarUrl = etValidarUrl.getText().toString();
        c.syncUrl = etSyncUrl.getText().toString();
        c.token = etToken.getText().toString();
        c.punto = etPunto.getText().toString();
        c.sentido = rbSalida.isChecked() ? Config.SALIDA : Config.ENTRADA;
        c.carril = rbVehicular.isChecked() ? Config.VEHICULAR : Config.PEATONAL;
        c.permitirSinConexion = cbSinConexion.isChecked();
        c.eliminarRostrosNoAutorizados = cbEliminarRostros.isChecked();
        c.scannerAction = etScannerAccion.getText().toString();
        c.scannerExtra = etScannerExtra.getText().toString();
        c.rostroHabilitado = cbRostroHabilitado.isChecked();
        c.autoRostro = cbAutoRostro.isChecked();
        c.yunattActivo = cbYunattActivo.isChecked();
        c.yunattMarcas = cbYunattMarcas.isChecked();
        c.yunattServidor = etYunattServidor.getText().toString().trim()
                .replaceFirst("^(?i)(ws|wss|http|https)://", "").replaceFirst("[:/].*$", "");
        c.yunattSn = etYunattSn.getText().toString().trim();
        try {
            c.yunattPuerto = Integer.parseInt(etYunattPuerto.getText().toString().trim());
        } catch (NumberFormatException e) {
            c.yunattPuerto = 7792;
        }
        try {
            c.syncMinutes = Integer.parseInt(etSyncMin.getText().toString().trim());
        } catch (NumberFormatException e) {
            c.syncMinutes = 10;
        }
        c.save(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(refrescarYunatt);
        mostrarPermisoArchivos();
    }

    private boolean tieneAccesoArchivos() {
        return android.os.Build.VERSION.SDK_INT < 30 || android.os.Environment.isExternalStorageManager();
    }

    private void mostrarPermisoArchivos() {
        TextView t = findViewById(R.id.txtPermArchivos);
        android.widget.Button b = findViewById(R.id.btnPermArchivos);
        if (android.os.Build.VERSION.SDK_INT < 30) {
            t.setText(R.string.cfg_perm_archivos_na);
            b.setVisibility(android.view.View.GONE);
            return;
        }
        boolean ok = android.os.Environment.isExternalStorageManager();
        t.setText(ok ? R.string.cfg_perm_archivos_ok : R.string.cfg_perm_archivos_no);
        b.setVisibility(ok ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    private void abrirPermisoArchivos() {
        if (android.os.Build.VERSION.SDK_INT < 30) return;
        try {
            Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            i.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            try { startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
            catch (Exception ignored) { }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(refrescarYunatt);
    }

    private void mostrarEstadoLocal() {
        LocalStore s = LocalStore.get(this);
        String ultima = s.getMeta("ultima_sync");
        txtEstado.setText(getString(R.string.cfg_estado_local,
                s.contarAutorizados(), s.contarMarcasPendientes(), MainActivity.nUserNum,
                ultima == null ? "nunca" : ultima));
    }
}
