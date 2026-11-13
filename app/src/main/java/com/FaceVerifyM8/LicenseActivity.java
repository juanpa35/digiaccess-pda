package com.FaceVerifyM8;

import static com.FaceVerifyM8.MainActivity.REG_URL;
import static com.FaceVerifyM8.MainActivity.TAG;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.utils.HttpUtil;

import java.util.HashMap;
import java.util.Map;

public class LicenseActivity extends Activity {
    public static String strLicFileName = null; // license file
    public static String strRunCode = null; // runcode

    TextView txtRunCode;
    EditText txtRegCode;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.license);
		com.digiaccess.Encabezado.configurar(this, R.string.title_license);

        txtRunCode  = findViewById(R.id.runCode);
        txtRegCode = findViewById(R.id.regCode);

        Button btnOK = findViewById(R.id.ok);

        // check format of the regcode, if it is valid then exit the activity
        btnOK.setOnClickListener(arg0 -> {
            String text = txtRegCode.getText().toString();
            if (text.isEmpty()){
                Toast.makeText(LicenseActivity.this, R.string.checksum_error, Toast.LENGTH_LONG).show();
                return;
            }
            LicenseActivity licenseActivity = this;
            Map<String, Object> params = new HashMap<>();
            params.put("runCode", LicenseActivity.strRunCode);
            params.put("code", text);
            ObjectMapper objectMapper = new ObjectMapper();
            HttpUtil.getInstance().postJson(REG_URL, params, new HttpUtil.HttpCallback() {
                @Override
                public void onSuccess(String responseData) {
                    // 处理登录成功逻辑
                    Log.d(TAG,params.toString());
                    Log.d(TAG,responseData);
                    String msg = responseData.replaceAll(",]", "]");
                    JsonNode jsonNode = null;
                    try {
                        // 处理服务器下发的数据（如人员更新指令）
                        jsonNode = objectMapper.readValue(msg, JsonNode.class);
                        // 501:运行码为空
                        // 502:序列号为空
                        // 503:校验码不在有效期内
                        // 504:待审批
                        if (jsonNode.has("result")&&jsonNode.get("result").asInt()==200) {
                            if (jsonNode.get("errCode").asInt()==0) {
                                Intent intent = new Intent(licenseActivity, MainActivity.class);
                                startActivity(intent);
                            }else if (jsonNode.get("errCode").asInt()==501) {
                                Intent intent = new Intent(licenseActivity, MainActivity.class);
                                startActivity(intent);
                            }else if (jsonNode.get("errCode").asInt()==502) {
                                Intent intent = new Intent(licenseActivity, MainActivity.class);
                                startActivity(intent);
                            }else if (jsonNode.get("errCode").asInt()==503) {
                                Toast.makeText(LicenseActivity.this, R.string.checksum_error, Toast.LENGTH_LONG).show();
                            }else if (jsonNode.get("errCode").asInt()==504) {
                                Toast.makeText(LicenseActivity.this, R.string.pending_approval, Toast.LENGTH_LONG).show();
                                Intent intent = new Intent(licenseActivity, MainActivity.class);
                                startActivity(intent);
                            }
                        }
                    }catch (Exception e){
                        e.printStackTrace();
                    }

                }

                @Override
                public void onFailure(Exception e) {
                    // 处理登录失败逻辑
                }
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // display the runcode and read regcode from the clipboard
        if(strRunCode != null) {
            txtRunCode.setText(strRunCode);
        }
    }


}
