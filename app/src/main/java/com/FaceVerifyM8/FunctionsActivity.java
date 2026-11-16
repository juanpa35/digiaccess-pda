package com.FaceVerifyM8;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

public class FunctionsActivity extends AppCompatActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.functions);
		com.digiaccess.Encabezado.configurar(this, R.string.title_func);

		// Initialize UI controls
		android.view.View imgUsers, imgRecords, imgSetting, imgLoadUsers, imgExport;
		imgUsers = findViewById(R.id.imgUsers);
		imgRecords = findViewById(R.id.imgRecords);
		imgSetting = findViewById(R.id.imgSetting);
		imgLoadUsers = findViewById(R.id.imgLoadUsers);
		imgExport = findViewById(R.id.imgExport);
		if(BuildConfig.SOLO_CEDULA) {
			// Sin reconocimiento facial: no hay rostros que administrar ni cargar
			imgUsers.setVisibility(android.view.View.INVISIBLE);
			imgLoadUsers.setVisibility(android.view.View.INVISIBLE);
		}

		imgUsers.setOnClickListener(arg0-> {
			Intent intent = new Intent(this, UsersActivity.class);
			this.startActivity(intent);
		});

		imgRecords.setOnClickListener(arg0 -> {
			Intent intent = new Intent(this, RecordsActivity.class);
			this.startActivity(intent);
		});

		imgSetting.setOnClickListener(arg0 -> {
			Intent intent = new Intent(this, SettingActivity.class);
			this.startActivity(intent);
		});

		imgLoadUsers.setOnClickListener(arg0 -> {
			long nResult;
			synchronized (com.digiaccess.FaceStore.LOCK) {
				nResult = MainActivity.LoadUsers(); // archivos en /LoadUsers con nombre CEDULA_NOMBRE.jpg
			}
			int nTotal = (int)(nResult >> 32), nNum = (int)(nResult);
			MainActivity.ShowDialog(this, "Cargar rostros", "Se encontraron " + nTotal + (nTotal > 1 ? " archivos":" archivo") + " y se cargaron " + nNum + (nNum > 1 ? " rostros.":" rostro."), 0x01, null);
		});

		findViewById(R.id.btnDigiAccess).setOnClickListener(arg0 ->
			this.startActivity(new Intent(this, DigiAccessActivity.class)));

		imgExport.setOnClickListener(arg0 -> {
			MainActivity.Export();
			MainActivity.ShowDialog(this, "Exportar", "Datos exportados", 0x01, null);
		});
	}

	@Override
	protected void onResume()
	{
		super.onResume();
	}
}
