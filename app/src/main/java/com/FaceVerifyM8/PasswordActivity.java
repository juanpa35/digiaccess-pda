package com.FaceVerifyM8;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;

public class PasswordActivity extends AppCompatActivity {
	private boolean bInit = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.password);
		com.digiaccess.Encabezado.configurar(this, R.string.title_password);

		// Initialize UI controls
		EditText txtPassword;
		txtPassword = findViewById(R.id.txtPassword);

		Button btnOK, btnCancel;
		btnOK = findViewById(R.id.btnOK);
		btnCancel = findViewById(R.id.btnCancel);

		btnOK.setOnClickListener(arg0-> {
			String strPassword = txtPassword.getText().toString();
			if(strPassword.isEmpty())
				MainActivity.ShowDialog(this, "Clave de administrador", "Escriba la clave", 0x01, null);
			else if(strPassword.compareTo(SettingActivity.strSystemPassword) == 0) {
				// start function
				Intent intent = new Intent(this, FunctionsActivity.class);
				this.startActivity(intent);
			} else MainActivity.ShowDialog(this, "Clave de administrador", "Clave incorrecta. Intente de nuevo.", 0x01, null);
		});

		btnCancel.setOnClickListener(arg0 -> finish());

		txtPassword.requestFocus();

		bInit = true;
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		if(bInit) bInit = false;
		else finish();
	}
}
