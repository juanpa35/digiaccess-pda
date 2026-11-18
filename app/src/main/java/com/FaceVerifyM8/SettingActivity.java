package com.FaceVerifyM8;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.appcompat.app.AppCompatActivity;

public class SettingActivity extends AppCompatActivity {
	// nThresholdEyes es la distancia mínima entre ojos en píxeles de la imagen PROCESADA.
	// Con nZoom=1 (media resolución) se reduce a la mitad para mantener la misma distancia de lectura.
	public final static int nThresholdVerifyDefault = 60, nThresholdLiveDefault = 65, nThresholdEyesDefault = 36;
	public final static int nThresholdVerifyMin = 40, nThresholdVerifyMax = 80, nThresholdLiveMin = 40, nThresholdLiveMax = 80, nThresholdEyesMin = 30, nThresholdEyesMax = 120;

	public static int nThresholdVerify = nThresholdVerifyDefault, nThresholdLive = nThresholdLiveDefault, nThresholdEyes = nThresholdEyesDefault;
	// Prueba de vida (antispoofing) APAGADA: es el paso más pesado del reconocimiento.
	// Apagada, el rostro se valida mucho más rápido (una foto podría pasar).
	public static boolean bLiveStat = false;
	/** Clave de administrador por defecto de DIGIACCESS. */
	public static final String CLAVE_POR_DEFECTO = "010691";
	public static String strSystemPassword = CLAVE_POR_DEFECTO;

	private boolean bLive = bLiveStat;

	public static boolean bDebugStat = false;
	private boolean bDebug = bDebugStat;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.setting);
		com.digiaccess.Encabezado.configurar(this, R.string.title_setting);

		// Initialize UI controls
		TextView txtTitleThresholdLive;
		EditText txtThreshold, txtThresholdLive, txtThresholdEyes, txtPassword, txtPassword2;
		txtThreshold = findViewById(R.id.etThresholdVerify);
		txtTitleThresholdLive = findViewById(R.id.tvThresholdLive);
		txtThresholdLive = findViewById(R.id.etThresholdLive);
		txtThresholdEyes = findViewById(R.id.etThresholdEyes);
		txtPassword = findViewById(R.id.etPassword);
		txtPassword2 = findViewById(R.id.etPassword2);

		ToggleButton tbLiveStat;
		tbLiveStat = findViewById(R.id.tbLiveStat);
		tbLiveStat.setOnCheckedChangeListener((buttonnView, isChecked)-> {
			int vis = View.GONE;
			if(isChecked) vis = View.VISIBLE;
			txtTitleThresholdLive.setVisibility(vis);
			txtThresholdLive.setVisibility(vis);
			bLive = isChecked;
		});

		ToggleButton tbDebug = findViewById(R.id.tbDebug);
		tbDebug.setChecked(bDebugStat);
		tbDebug.setOnCheckedChangeListener((buttonnView, isChecked)-> bDebug = isChecked );

		Button btnOK, btnCancel;
		btnOK = findViewById(R.id.btnOK);
		btnCancel = findViewById(R.id.btnCancel);
		btnOK.setOnClickListener(arg0-> {
			boolean bValid = true;
			int nVerify = 0, nLive = 0, nEyes = 0;
			String strThreshold = txtThreshold.getText().toString(), strThresholdLive = txtThresholdLive.getText().toString(),strThresholdEyes = txtThresholdEyes.getText().toString();
			try {
				nVerify = Integer.parseInt(strThreshold);
				nLive = Integer.parseInt(strThresholdLive);
				nEyes = Integer.parseInt(strThresholdEyes);
			} catch (NumberFormatException ignored) { }
			String strPassword = txtPassword.getText().toString(), strPassword2 = txtPassword2.getText().toString();
			if(strThreshold.isEmpty()) {
				bValid = false;
				MainActivity.ShowDialog(this, "Ajustes", "Escriba el umbral de reconocimiento.", 0x01, null);
			} else if(nVerify < nThresholdVerifyMin || nVerify > nThresholdVerifyMax) {
				bValid = false;
				MainActivity.ShowDialog(this, "Ajustes", "Umbral de reconocimiento inválido. Debe estar entre " + nThresholdVerifyMin + " y " + nThresholdVerifyMax, 0x01, null);
			}
			if(bValid && bLive) {
				if(strThresholdLive.isEmpty()) {
					bValid = false;
					MainActivity.ShowDialog(this, "Ajustes","Escriba el umbral de prueba de vida.", 0x01, null);
				} else if(nLive < nThresholdLiveMin || nLive > nThresholdLiveMax) {
					bValid = false;
					MainActivity.ShowDialog(this, "Ajustes","Umbral de prueba de vida inválido. Debe estar entre " + nThresholdLiveMin + " y " + nThresholdLiveMax, 0x01, null);
				}
			}
			if(bValid) {
				if(strThresholdEyes.isEmpty()) {
					bValid = false;
					MainActivity.ShowDialog(this, "Ajustes","Escriba la distancia entre ojos.", 0x01, null);
				} else if(nEyes < nThresholdEyesMin || nEyes > nThresholdEyesMax) {
					bValid = false;
					MainActivity.ShowDialog(this, "Ajustes","Distancia entre ojos inválida. Debe estar entre " + nThresholdEyesMin + " y " + nThresholdEyesMax, 0x01, null);
				}
			}
			if(bValid) {
				if(strPassword.isEmpty()) {
					bValid = false;
					MainActivity.ShowDialog(this, "Ajustes","Escriba la clave.", 0x01, null);
				} else if(strPassword.compareTo(strPassword2) != 0) {
					bValid = false;
					MainActivity.ShowDialog(this, "Ajustes","Las claves no coinciden. Intente de nuevo.", 0x01, null);
				}
			}

			if(bValid){
				// save and finish
				bLiveStat = bLive;
				nThresholdVerify = nVerify;
				if(bLive) nThresholdLive = nLive;
				nThresholdEyes = nEyes;
				strSystemPassword = strPassword;

				bDebugStat = bDebug;

				MainActivity.WriteSetting();

				finish();
			}
		});
		btnCancel.setOnClickListener(arg0 -> finish());

		String str = Integer.toString(nThresholdVerify);
		txtThreshold.setText(str);
		str = Integer.toString(nThresholdLive);
		txtThresholdLive.setText(str);
		str = Integer.toString(nThresholdEyes);
		txtThresholdEyes.setText(str);
		txtPassword.setText(strSystemPassword);
		txtPassword2.setText(strSystemPassword);
		tbLiveStat.setChecked(bLiveStat);
		int vis = View.GONE;
		if(bLiveStat) vis = View.VISIBLE;
		txtTitleThresholdLive.setVisibility(vis);
		txtThresholdLive.setVisibility(vis);
	}
}
