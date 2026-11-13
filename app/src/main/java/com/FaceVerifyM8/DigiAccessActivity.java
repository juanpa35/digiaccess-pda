package com.FaceVerifyM8;

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

/** Configuración de la conexión con DIGIACCESS (Funciones > DIGIACCESS). Protegida por la clave de administrador. */
public class DigiAccessActivity extends AppCompatActivity {

    private EditText etValidarUrl, etSyncUrl, etToken, etPunto, etSyncMin, etScannerAccion, etScannerExtra;
    private RadioButton rbEntrada, rbSalida, rbPeatonal, rbVehicular;
    private CheckBox cbSinConexion, cbEliminarRostros;
    private TextView txtEstado;

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
        mostrarEstadoLocal();

        Button btnGuardar = findViewById(R.id.btnGuardar);
        btnGuardar.setOnClickListener(v -> {
            guardar();
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
        try {
            c.syncMinutes = Integer.parseInt(etSyncMin.getText().toString().trim());
        } catch (NumberFormatException e) {
            c.syncMinutes = 10;
        }
        c.save(this);
    }

    private void mostrarEstadoLocal() {
        LocalStore s = LocalStore.get(this);
        String ultima = s.getMeta("ultima_sync");
        txtEstado.setText(getString(R.string.cfg_estado_local,
                s.contarAutorizados(), s.contarMarcasPendientes(), MainActivity.nUserNum,
                ultima == null ? "nunca" : ultima));
    }
}
