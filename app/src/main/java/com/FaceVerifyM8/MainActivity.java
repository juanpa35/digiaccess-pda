package com.FaceVerifyM8;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.idfacesdk.*; // import Face-SDK
import com.db.*; // import Database
import com.utils.HttpUtil;
import com.digiaccess.Config;
import com.digiaccess.LocalStore;
import com.digiaccess.ScannerHelper;
import com.digiaccess.SyncManager;
import android.view.KeyEvent;
import android.widget.Button;

public class MainActivity extends Activity implements View.OnClickListener {
	private static final int REQUEST_PERMISSIONS = 1;
	private static final String[] APP_PERMISSIONS = {
		"android.permission.CAMERA",
		"android.permission.DISABLE_KEYGUARD",
		"android.permission.WAKE_LOCK",
		"android.permission.READ_PHONE_STATE",
		"android.permission.READ_SYNC_SETTINGS",
		"android.permission.ACCESS_WIFI_STATE",
		"android.permission.ACCESS_NETWORK_STATE",
		"android.permission.INTERNET",
		"android.permission.BLUETOOTH",
		"android.permission.READ_EXTERNAL_STORAGE",
		"android.permission.WRITE_EXTERNAL_STORAGE",
		"android.permission.MANAGE_EXTERNAL_STORAGE",
	};

	public static final String TAG = "TagFaceVerifyM8" ;
	public static String strLicPath, strAppPath, strCachePath, strDbFileName; // License path, APP data path、Cache path、Database file name

	// Account and password for cloud license service

	public static boolean bSdkInit = false;
	public static ArrayList<UserBean> userList;
	public static int hFeatureList = 0;
	public static UserDB db = null;
	public static int nUserNum = 0;

	private TextView txtStat;
	private Timer timer;

	// DIGIACCESS
	private ScannerHelper scanner;
	private Timer syncTimer;
	private volatile long tUltimaSync = 0;
	private volatile String strSync = "";
	private TextView txtPuntoMain;

	public static final Locale LOCALE_CO = new Locale("es", "CO");

	// camera objects
	private volatile Camera camera = null; // Camera object
	private volatile SurfaceTexture surfaceTexture = new SurfaceTexture(8);// Camera surface object
	private boolean bPause = false;
	/** Detección automática con la cámara encendida en la pantalla principal (Configuración DIGIACCESS). */
	private volatile boolean bAutoRostro = false;
	private static boolean bYunattIniciado = false;

	private static MediaPlayer mpWelcomeShort = null, mpWelcomeLong = null;
	public static final String REG_URL="https://global.yunatt.com/handheldface/api/v1/applyAlgorithmLicense";
	ObjectMapper objectMapper = new ObjectMapper();

	private static void copyFile(String strSrc, String strDst)
	{
		int ret = -1;
		try(FileInputStream in = new FileInputStream(strSrc); FileOutputStream out = new FileOutputStream(strDst)) {
			byte[] buffer = new byte[1024];
			int length;
			while((length = in.read(buffer)) > 0) {
				out.write(buffer, 0, length);
			}
			ret = 0;
		} catch(IOException ignored) {
			// System.err.println("Fail to copy file：" + e.getMessage());
		}
		Log.d(TAG, "copyFile from " + strSrc + " to " + strDst + (ret == 0 ? " OK":" fail"));
	}

	private static void copyDirFiles(String strSrcRoot, String strDstRoot, File file)
	{
		if(file == null) file = new File(strSrcRoot);
		if(file.isFile()) {
			String strSrcPath = file.getAbsolutePath();
			String strDstPath = strDstRoot + strSrcPath.substring(strSrcRoot.length(), strSrcPath.length());
			copyFile(strSrcPath, strDstPath);
		} else if(file.isDirectory()) {
			File[] fList = file.listFiles();
			if(fList != null) {
				for(File f : fList) {
					if(f.isDirectory()) {
						String strPath = f.getAbsolutePath();
						String strDst = strDstRoot + strPath.substring(strSrcRoot.length(), strPath.length());
						try {
							File d = new File(strDst);
							if(!d.exists()) {
								//noinspection StatementWithEmptyBody
								Log.d(TAG, "mkdir " + strDst);
								if(!d.mkdir()) { }
							}
						} catch(Exception ignored) { }
					}
					copyDirFiles(strSrcRoot, strDstRoot, f);
				}
			}
		}
	}

	// Regist the user with a picture file
	private static int RegUser(String strPicFileName, String strBadDir) {
		int nResult = -1;
		int off, first = 0, middle1, middle2, end = strPicFileName.length();
		while(true) {
			off = strPicFileName.indexOf('/', first);
			if(off == -1) break;
			first = off + 1;
		}
		off = strPicFileName.indexOf('.', first + 1);
		if(off != -1) end = off;
		off = strPicFileName.indexOf('_', first + 1);
		if(off != -1 && off < end) {
			middle1 = off;
			middle2 = off + 1;
		} else {
			middle1 = end;
			middle2 = first;
		}
		String strFull = strPicFileName.substring(first);
		String strNo = strPicFileName.substring(first, middle1);
		String strName = strPicFileName.substring(middle2, end);

		long tMilliSeconds = System.currentTimeMillis();
		String strPhotoFileName = strAppPath +  "/photo" + tMilliSeconds + ".jpg";
		String strResult = "Load user " + strName + " fail: Invalid photo file.";

		// Read the image solution
		int[] width = new int[1], height = new int[1];
		if(IdFaceSdk.ReadImageFile(strPicFileName, null, 0, width, height, 24) != 0) width[0] = 0;
		if(width[0] > 0 && height[0] > 0) {
			// Read the image data
			byte[] img = new byte[width[0] * height[0] * 3];
			if(IdFaceSdk.ReadImageFile(strPicFileName, img, img.length, width, height, 24) != 0) width[0] = 0;
			if(width[0] > 0 && height[0] > 0) {
				// Detect face
				FACE_DETECT_RESULT[] faces = new FACE_DETECT_RESULT[2];
				for(off = 0; off < 2; off++) faces[off] = new FACE_DETECT_RESULT();
				int nFaceNum = IdFaceSdk.DetectAllFaces(img, width[0], height[0], 2, faces);
				if(nFaceNum != 1) { // Only one face can be accepted
					strResult = "Load user " + strName + " fail: Bad face.";

					String strBadFileName = strBadDir + "/badface_" + strFull;
					copyFile(strPicFileName, strBadFileName);
				} else {
					FACE_DETECT_RESULT face = faces[0];
					FACE_QUALITY_LEVEL quality = new FACE_QUALITY_LEVEL();
					IdFaceSdk.FaceQualityLevel(img, width[0], height[0], faces[0], quality);

					if(face.nAngleYaw < -15 || face.nAngleYaw > 15 || face.nAnglePitch < -15 || face.nAnglePitch > 15 || face.nAngleRoll < -15 || face.nAngleRoll > 15) {
						strResult = "Load user " + strName + " fail: The angle of the face is too wide.";

						String strBadFileName = strBadDir + "/angle_" + face.nAngleYaw + "_" + face.nAnglePitch + "_" + face.nAngleRoll + "_" + strFull;
						copyFile(strPicFileName, strBadFileName);
					} else if((face.nRightEyeX - face.nLeftEyeX) < 40 || (face.nRightEyeX - face.nLeftEyeX) < SettingActivity.nThresholdEyes) {
						strResult = "Load user " + strName + " fail: The size of the face is too small."; // 人脸太小时不允许注册

						String strBadFileName = strBadDir + "/small" + (face.nRightEyeX - face.nLeftEyeX) + "_" + strFull;
						copyFile(strPicFileName, strBadFileName);
					}
					else if(quality.bright != 0) {
						strResult = "Load user " + strName + " fail: The face bright is " + (quality.bright == 1 ? "too dark." : "over exposure");

						String strBadFileName = strBadDir + (quality.bright == 1 ? "/dark_":"/overexposure_") + strFull;
						copyFile(strPicFileName, strBadFileName);
					} else if(quality.blur) {
						strResult = "Load user " + strName + " fail: The face is blur.";

						String strBadFileName = strBadDir + "/blur_" + strFull;
						copyFile(strPicFileName, strBadFileName);
					}
					else if(face.nQuality < 60) {
						strResult = "Load user " + strName + " fail: The quality of the face is too bad. (" + face.nQuality + " < 60)";

						String strBadFileName = strBadDir + "/quality" + face.nQuality + "_" + strFull;
						copyFile(strPicFileName, strBadFileName);
					}
					else if(quality.light != 0) {
						strResult = "Load user " + strName + " fail: The face light is " + (quality.light == 1 ? "side" : quality.light == 2 ? "top" : "back");

						String strBadFileName = strBadDir + (quality.light == 1 ? "/side": (quality.light == 2 ? "/top" : "/back")) + "light_" + strFull;
						copyFile(strPicFileName, strBadFileName);
					}
					else {
						// Detect facemask
						int nFaceMask = IdFaceSdk.FaceMask(img, width[0], height[0], face);
						if(nFaceMask > 0) strResult = "Load user " + strName + " fail: Wearing a mask is not accepted."; // Facemask not accepted while register user
						else if(face.nConfidence < 80) strResult = "Load user " + strName + " fail: The confidence of the face is bad.";
						else {
							// Get feature
							byte[] fea = new byte[IdFaceSdk.FeatureSize()];
							if(IdFaceSdk.FeatureGet(img, width[0], height[0], face, fea) != 0) strResult = "Load user " + strName + " fail: Fail to extract user feature.";
							else {
								int[] pos = new int[1];
								UserBean user = null;

								// Check user by workno (new or exist)
								int i, index = -1;
								for(i = 0; i < nUserNum; i++) {
									user = userList.get(i);
									if(user == null) break; // always false
									if(user.workno.compareTo(strNo) == 0) break;
								}

								if(i < nUserNum) strResult = "Load user " + strName + " fail: Work no " + strNo + " already exist.";
								else if(nUserNum >= UserDB.MAX_USER)
									strResult = "Load user " + strName + " fail: Over the maximum number of users limit " + UserDB.MAX_USER;
								else { // Save new user
									user = new UserBean();

									user.uid = 0;
									user.uname = strName;
									user.workno = strNo;
									user.photoFileName = strPhotoFileName;
									user.feature = fea;

									copyFile(strPicFileName, strPhotoFileName);

									// Insert user feature to the feature list
									pos[0] = -1;
									IdFaceSdk.ListInsert(hFeatureList, pos, fea, 1);
									if(pos[0] == -1) strResult = "Load user " + strName + " fail: Fail to insert user feature to list.";
									else {
										// update user feature position
										user.featurePos = pos[0];

										// save user to Database
										if(db.insertUser(user) <= 0) {
											IdFaceSdk.ListRemove(hFeatureList, pos[0], 1);
											strResult = "Load user " + strName + " fail: Fail to insert user to DB.";
										} else {
											strResult = "Load user " + strName + " OK.";

											userList.add(user);
											nUserNum++;

											nResult = 0;
										}
									}
								}
							}
						}
					}
				}
			}
		}
		Log.d(TAG, strResult);
		return nResult;
	}

	public static long LoadUsers()
	{
		int nTotal = 0, nNum = 0;
		String strBase = Environment.getExternalStorageDirectory().getPath();
		String strPathUsers = strBase + "/LoadUsers";
		String strBadDir =  strBase + "/Bad";
		File file = new File(strPathUsers);
		if(file.exists() && file.isDirectory()) {
			File fileBad = new File(strBadDir);
			if(!fileBad.exists()) {
				//noinspection StatementWithEmptyBody
				if(!fileBad.mkdir()) { }
			}
			File[] fList = file.listFiles();
			if(fList != null) {
				for(File f : fList) {
					if(f.isFile()) {
						String strFile = f.getAbsolutePath();
						nTotal++;
						if(RegUser(strFile, strBadDir) == 0) nNum++;
					}
				}
			}
		}
		long nResult = nTotal;
		nResult <<= 32;
		nResult += nNum;
		return nResult;
	}

	public static void Export()
	{
		boolean bImport = false;
		if(db != null) db.CloseDB();

		String strBase = Environment.getExternalStorageDirectory().getPath();
		String strIn = strBase + "/Import", strOut = strBase + "/Export";
		File f = new File(strIn);
		if(f.exists()) {
			copyDirFiles(strIn, strAppPath, f);
			bImport = true;
		}
		f = new File(strOut);
		if(!f.exists()) {
			//noinspection StatementWithEmptyBody
			if(!f.mkdir()) { }
		}
		copyDirFiles(strAppPath, strOut, null);

		if(db != null) {
			db.OpenDB();

			if(bImport) {
				userList = db.getUserList();
				nUserNum = userList.size();

				if(hFeatureList != 0) {
					IdFaceSdk.ListClearAll(hFeatureList);
					for(int i = 0; i < nUserNum; i++) {
						int[] pos = new int[]{-1};
						UserBean user = userList.get(i);
						IdFaceSdk.ListInsert(hFeatureList, pos, user.feature, 1);
						user.featurePos = pos[0];
						userList.set(i, user);
					}
				}
			}
		}
	}

	private void testSdk() {
		String strBase = Environment.getExternalStorageDirectory().getPath();
		String strPhoto1 = strBase + "/1.jpg", strPhoto2 = strBase + "/2.jpg";
		FACE_DETECT_RESULT face1 = new FACE_DETECT_RESULT(), face2 = new FACE_DETECT_RESULT();
		byte[] img1 = null, img2 = null;
		int[] w1 = new int[1], h1 = new int[1], w2 = new int[1], h2 = new int[1];
		int s, ret = IdFaceSdk.ReadImageFile(strPhoto1, null, 0, w1, h1, 24);
		if(ret != 0) Log.d(TAG, "ReadImageFile decode " + strPhoto1 + " fail.");
		else {
			s = w1[0] * h1[0] * 3;
			img1 = new byte[s];
			ret = IdFaceSdk.ReadImageFile(strPhoto1, img1, s, w1, h1, 24);
			if(ret != 0) {
				img1 = null;
				Log.d(TAG, "ReadImageFile read " + strPhoto1 + " fail.");
			}
		}
		ret = IdFaceSdk.ReadImageFile(strPhoto2, null, 0, w2, h2, 24);
		if(ret != 0) Log.d(TAG, "ReadImageFile decode " + strPhoto2 + " fail.");
		else {
			s = w2[0] * h2[0] * 3;
			img2 = new byte[s];
			ret = IdFaceSdk.ReadImageFile(strPhoto2, img2, s, w2, h2, 24);
			if(ret != 0) {
				img2 = null;
				Log.d(TAG, "ReadImageFile read " + strPhoto2 + " fail.");
			}
		}
		if(img1 != null && img2 != null) {
			ret = IdFaceSdk.DetectFace(img1, w1[0], h1[0], face1);
			if(ret != 1) {
				face1 = null;
				Log.d(TAG, "DetectFace " + strPhoto1 + " fail.");
			}
			ret = IdFaceSdk.DetectFace(img2, w2[0], h2[0], face2);
			if(ret != 1) {
				face2 = null;
				Log.d(TAG, "DetectFace " + strPhoto2 + " fail.");
			}

			if(face1 != null && face2 != null) {
				s = IdFaceSdk.FeatureSize();
				byte[] fea1 = new byte[s], fea2 = new byte[s];
				ret = IdFaceSdk.FeatureGet(img1, w1[0], h1[0], face1, fea1);
				if(ret == 0) ret = IdFaceSdk.FeatureGet(img2, w2[0], h2[0], face2, fea2); // CORREGIDO: antes usaba img1
				if(ret == 0) {
					ret = IdFaceSdk.FeatureCompare(fea1, fea2);
					Log.d(TAG, "FeatureCompare " + strPhoto1 + ", " + strPhoto2 + " return " + ret);
				} else Log.d(TAG, "FeatureGet fail.");
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Request all permissions
		try {
			// Check all permissions
			int i, n = APP_PERMISSIONS.length;
			for(i = 0; i < n; i++) {
				if (ContextCompat.checkSelfPermission(this, APP_PERMISSIONS[i]) != PackageManager.PERMISSION_GRANTED)
					break;
			}
			if(i < n) {
				// not granted，request it
				ActivityCompat.requestPermissions(this, APP_PERMISSIONS, REQUEST_PERMISSIONS);
			}
		} catch (Exception ignored) { }

		// initialize directions
		strLicPath = null;
		// ANDROID 11 or later, Need permission to access the storage
		if(Build.VERSION.SDK_INT >= 30) {
			if(!Environment.isExternalStorageManager()) { // No permission to access the storage
				// ignored if the license file already exist in the APP files path, otherwise, start activity to request permission to access the storage
				String strFilesDir = getFilesDir().getAbsolutePath();
				if(ReadRegCode(strFilesDir + "/license.dat") == null) {
					Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
					startActivity(intent);
				} else strLicPath = strFilesDir;
			}
		}
		strCachePath = getCacheDir().getAbsolutePath(); // read APP cache directory
		strAppPath = getFilesDir().getAbsolutePath(); // read APP files directory
		File localFile = new File(strAppPath);
		if (!localFile.exists()) strAppPath = strCachePath;

		// initialize UI controls
		TextView txtDate, txtTime, txtWeek;
		txtDate = findViewById(R.id.txtDate);
		txtTime = findViewById(R.id.txtTime);
		txtWeek = findViewById(R.id.txtWeek);
		txtStat = findViewById(R.id.txtStat);

		View view = findViewById(R.id.screen);
		view.setOnClickListener(this);

		strDbFileName = strAppPath + "/FaceVerify.db"; // database name
		db = new UserDB(this, strDbFileName);
		userList = db.getUserList();
		nUserNum = userList.size();

		ReadSetting();

		com.digiaccess.Voz.iniciar(this);

		timer = new Timer();
		timer.schedule(new TimerTask() {
			int nTick = 0;
			@Override
			public void run() {
				// update UI on main thread
				runOnUiThread(() -> {
					SimpleDateFormat fmt;
					Date date = new Date();
					fmt = new SimpleDateFormat("HH:mm", LOCALE_CO);
					txtTime.setText(fmt.format(date));
					fmt = new SimpleDateFormat("EEEE','", LOCALE_CO);
					txtWeek.setText(fmt.format(date));
					fmt = new SimpleDateFormat("d 'de' MMMM", LOCALE_CO);
					txtDate.setText(fmt.format(date));
				});

				if(!bPause && bAutoRostro) {
					// Detect face
					if(VideoActivity.bFrameReady) {
						VideoActivity.bFrameReady = false;

						FACE_DETECT_RESULT result = new FACE_DETECT_RESULT();
						int nFaceNum = IdFaceSdk.DetectFace(VideoActivity.frameRgb24, VideoActivity.iPicWidth, VideoActivity.iPicHeight, result);
						if(nFaceNum == 1 && nUserNum > 0) {
							// Auto entry verify page
							startVerify();
						}
						nTick = 0;
					} else {
						nTick++;
						if(nTick > 5 * 10) { // No frame over 5 seconds, close and re-open the color camera
							if(VideoActivity.isCameraOpen()) VideoActivity.closeCamera();
							if(camera != null) {
								closeCamera(camera);
								camera = null;
							}

							openCamera();
							nTick = 0;
						}
					}
				} else nTick = 0;
			}
		}, 100, 100);

		// ---------------- DIGIACCESS ----------------
		txtPuntoMain = findViewById(R.id.txtPuntoMain);
		// Sentido y carril: el operador los cambia sin clave de administrador
		com.digiaccess.Modo.conectarSegmentos(this, R.id.segEntrada, R.id.segSalida, R.id.segPeatonal, R.id.segVehicular, this::mostrarSentido);
		findViewById(R.id.btnCedulaMain).setOnClickListener(v -> abrirCedula(null, null));
		// Rostro bajo demanda: la cámara solo se abre al tocar (deja libre el lector 2D)
		View.OnClickListener rostro = v -> {
			if(bSdkInit) startVerify();
			else {
				StartSdk();
				android.widget.Toast.makeText(this, bSdkInit ? getString(R.string.mire_pantalla) : getString(R.string.rostro_no_listo), android.widget.Toast.LENGTH_SHORT).show();
				if(bSdkInit) startVerify();
			}
		};
		findViewById(R.id.btnRostroMain).setOnClickListener(rostro);
		findViewById(R.id.anilloRostro).setOnClickListener(rostro);

		// Lector 2D: una lectura en la pantalla principal abre la validación con cédula
		scanner = new ScannerHelper(this, this::abrirCedula);

		// Sincronización periódica con pda_sync.php (autorizados vigentes y marcas sin conexión)
		syncTimer = new Timer();
		syncTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				Config cfg = Config.load(MainActivity.this);
				long ahora = System.currentTimeMillis();
				if(!cfg.syncConfigurado()) return;
				if(tUltimaSync != 0 && ahora - tUltimaSync < cfg.syncMinutes * 60000L) return;
				tUltimaSync = ahora;
				SyncManager.Resumen r = SyncManager.sincronizar(MainActivity.this);
				strSync = r.ok ? "" : "Sin conexión con DIGIACCESS" + (r.marcasPendientes > 0 ? " · " + r.marcasPendientes + " marcas pendientes" : "");
				runOnUiThread(() -> actualizarEstado());
			}
		}, 10000, 30000);
	}

	private void abrirCedula(byte[] raw, String text) {
		bPause = true;
		if(camera != null) {
			closeCamera(camera);
			camera = null;
		}
		Intent intent = new Intent(this, CedulaActivity.class);
		if(raw != null) intent.putExtra(CedulaActivity.EXTRA_RAW, raw);
		if(text != null) intent.putExtra(CedulaActivity.EXTRA_TEXT, text);
		startActivity(intent);
	}

	private void mostrarSentido() {
		Config cfg = Config.load(this);
		com.digiaccess.Modo.pintarSegmentos(this, R.id.segEntrada, R.id.segSalida, R.id.segPeatonal, R.id.segVehicular);
		txtPuntoMain.setText(cfg.punto);
	}

	private void actualizarEstado() {
		String str;
		if(bSdkInit) {
			if(nUserNum == 0) str = "Sin rostros enrolados: use la cédula";
			else str = nUserNum + (nUserNum > 1 ? " rostros" : " rostro");
		} else str = "Iniciando reconocimiento facial…";
		String ultima = LocalStore.get(this).getMeta("ultima_sync");
		if(ultima != null && ultima.length() >= 16) str += " · Sincronizado " + ultima.substring(11, 16);
		if(strSync.length() > 0) str += "\n" + strSync;
		txtStat.setText(str);
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		// Lector 2D en modo teclado
		if(scanner != null && scanner.handleKeyEvent(event)) return true;
		return super.dispatchKeyEvent(event);
	}

	@Override
	protected void onResume() {
		super.onResume();

		VideoActivity.bFrameReady = false;

		actualizarEstado();
		mostrarSentido();
		if(scanner != null) scanner.start();

		bPause = false;
		bAutoRostro = com.digiaccess.Config.load(this).autoRostro;
		((TextView) findViewById(R.id.txtAcerquese)).setText(bAutoRostro ? R.string.acerquese : R.string.toque_rostro);

		// Wait a moment to start sdk and camera for GUI appear quickly
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				StartSdk();
				if(bAutoRostro) openCamera();
				else if(camera != null) { closeCamera(camera); camera = null; }
				if(!bYunattIniciado) {
					bYunattIniciado = true;
					com.digiaccess.YunattCliente.reiniciar(MainActivity.this);
				}
			}
		}, 100);
	}

	@Override
	protected void onPause() {
		super.onPause();

		bPause = true;
		if(scanner != null) scanner.stop();

		if(camera != null) {
			closeCamera(camera);
			camera = null;
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		if (timer != null) {
			timer.cancel();
			timer = null; // clear reference
		}
		if (syncTimer != null) {
			syncTimer.cancel();
			syncTimer = null;
		}

		StopSdk();
		db.CloseDB();

		if(mpWelcomeShort != null) {
			mpWelcomeShort.release();
			mpWelcomeShort = null;
		}
		if(mpWelcomeLong != null) {
			mpWelcomeLong.release();
			mpWelcomeLong = null;
		}
	}

	@Override
	public void onClick(View view)
	{
		if(bSdkInit) startVerify();
		if (!bSdkInit){
			StartSdk();
		}

	}

	private void startVerify()
	{
		bPause = true; // Prevent reentry

		// close camera first
		if(camera != null) {
			closeCamera(camera);
			camera = null;
		}

		// start verify
		Intent intent = new Intent(this, VideoActivity.class);
		this.startActivity(intent);
	}

	public Camera openCamera(int nCameraIndex) {
		Camera cam = null;
		// Open the camera
		try {
			cam = Camera.open(nCameraIndex);
		} catch (RuntimeException ex) {
			Log.e(TAG, "MainActivity open camera " + nCameraIndex + " RuntimeException !");
		} catch (Exception ex) {
			Log.e(TAG, "MainActivity open camera " + nCameraIndex + " Exception !");
		}

		if (cam != null) {
			int width = VideoActivity.iCaptureWidth, height = VideoActivity.iCaptureHeight;
			if (VideoActivity.nRotate[VideoActivity.iCameraIndex] == 90 || VideoActivity.nRotate[VideoActivity.iCameraIndex] == 270) {
				VideoActivity.iPicWidth = height / (VideoActivity.nZoom + 1);
				VideoActivity.iPicHeight = width / (VideoActivity.nZoom + 1);
			} else {
				VideoActivity.iPicWidth = width / (VideoActivity.nZoom + 1);
				VideoActivity.iPicHeight = height / (VideoActivity.nZoom + 1);
			}

			try {
				// Set format and resolution
				Camera.Parameters parameters = cam.getParameters();
				//parameters.setPreviewFrameRate(30);
				parameters.setPreviewFormat(ImageFormat.NV21);
				parameters.setPreviewSize(VideoActivity.iCaptureWidth, VideoActivity.iCaptureHeight);
				cam.setParameters(parameters);

				// Set preview
				cam.setPreviewCallback(cameraPreviewCallback);
				cam.setPreviewTexture(surfaceTexture); // Preview by SurfaceTexture

				// Start preview
				cam.startPreview();
			} catch (RuntimeException ex) {
				cam.release();
				cam = null;
			} catch (Exception e) {
				cam.release();
				cam = null;
			}
			if (cam == null) Log.e(TAG, "MainActivity set camera " + nCameraIndex + " format and size fail.");
		} else Log.e(TAG, "MainActivity open camera " + nCameraIndex + " fail.");

		if(cam != null) Log.d(TAG, "MainActivity camera is on.");

		return cam;
	}

	void closeCamera(Camera cam)
	{
		if(cam == null) return;

		cam.setPreviewCallback(null);

		try {
			Thread.sleep(50); // Wait 50 milliseconds
		} catch (Exception e) {
			//
		}
		cam.stopPreview();

		try {
			Thread.sleep(50); // Wait 50 milliseconds
		} catch (Exception e) {
			//
		}
		cam.release();

		Log.d(TAG, "MainActivity camera is off.");
	}

	// Camera Listener
	Camera.PreviewCallback cameraPreviewCallback = (data, camx) -> {
		if(bSdkInit && !VideoActivity.bFrameReady) {
			IdFaceSdk.YUV420P_TO_RGB24_RGB32(data, VideoActivity.iCaptureWidth, VideoActivity.iCaptureHeight, 2, 0, VideoActivity.nRotate[VideoActivity.iCameraIndex], VideoActivity.bMirror[VideoActivity.iCameraIndex], VideoActivity.frameRgb24, null);
			VideoActivity.bFrameReady = true;
		}
	};

	private void openCamera()
	{
		// Open the camera
		if(camera == null && !VideoActivity.isCameraOpen()) {
			int nCameraNum = Camera.getNumberOfCameras();
			Log.d(TAG, "MainActivity found " + nCameraNum + " camera.");
			if(nCameraNum >= 1) {
				if(VideoActivity.iCameraIndex >= nCameraNum) VideoActivity.iCameraIndex = 0;
				camera = openCamera(VideoActivity.iCameraIndex);
			}
		}
	}

	private void StartSdk()
	{
		if(!bSdkInit) {
			String str = null;
			// check license path
			// ANDROID 11 or later, Need permission to access the storage
			if(strLicPath == null) {
				try {
					strLicPath = Environment.getExternalStorageDirectory().getPath() + "/FaceSdk";
					File localFile = new File(strLicPath);
					if(!localFile.exists()) {
						//noinspection StatementWithEmptyBody
						if(!localFile.mkdir()) { }
					}
					if(!localFile.exists()) strLicPath = null;
				} catch(Exception e) {
					strLicPath = null;
				}
				if(strLicPath == null) {
					strLicPath = getFilesDir().getAbsolutePath();
					File localFile = new File(strLicPath);
					if(!localFile.exists()) strLicPath = strCachePath;
				}
			}

			String strLicFileName = strLicPath + "/license.dat";
			String strRegCode = null;

			int i, ret = -1;
			strRegCode = ReadRegCode(strLicFileName);
			if(strRegCode != null) {
				ret = IdFaceSdk.SetRegCode(this, strRegCode);
				if(ret != 0) strRegCode = null;
			}
			if(ret >= 0) {
				// initialize SDK
				ret = IdFaceSdk.Init(strCachePath);
				if(ret == 0) {
					hFeatureList = IdFaceSdk.ListCreate(UserDB.MAX_USER);
					if(hFeatureList != 0) {
						bSdkInit = true;
						for(i = 0; i < nUserNum; i++) {
							int[] pos = new int[] { -1 };
							UserBean user = userList.get(i);
							IdFaceSdk.ListInsert(hFeatureList, pos, user.feature, 1);
							user.featurePos = pos[0];
							userList.set(i, user);
						}

						if(nUserNum == 0) str = "Sin rostros enrolados: use la cédula.";
						else str = nUserNum + (nUserNum > 1 ? " rostros enrolados." : " rostro enrolado.");

//testSdk();
					} else {
						IdFaceSdk.Uninit();
						bSdkInit = false;
						str = "Error al iniciar el reconocimiento facial (lista de rostros).";
					}
				} else str = "Reconocimiento facial sin licencia o con error (" + ret + "). La cédula sigue funcionando.";
			}
			if(str != null) {
				Log.d(TAG, str);
				txtStat.setText(str);
			}
			if(strRegCode == null) {




				LicenseActivity.strLicFileName = strLicFileName;
				LicenseActivity.strRunCode = IdFaceSdk.GetRunCode(this);

				// save runcode file
				WriteLicense(strLicPath + "/device.dat", LicenseActivity.strRunCode);
				initGetRegCode(strLicFileName);
			}
		}
	}
	private void initGetRegCode(String strLicFileName) {
		LicenseActivity.strRunCode = IdFaceSdk.GetRunCode(this);
		MainActivity mainActivity = this;
		// 准备JSON参数
		Map<String, Object> params = new HashMap<>();
		params.put("runCode", LicenseActivity.strRunCode);
//		params.put("code", 123);

		HttpUtil.getInstance().postJson(REG_URL, params, new HttpUtil.HttpCallback() {
			@Override
			public void onSuccess(String responseData) {
				// 处理登录成功逻辑
				Log.d(TAG,responseData);
				String msg = responseData.replaceAll(",]", "]");
				JsonNode jsonNode = null;
				try {
					// 处理服务器下发的数据（如人员更新指令）
					jsonNode = objectMapper.readValue(msg, JsonNode.class);
					if (jsonNode.has("result")&&jsonNode.get("result").asInt()==200) {
						if (jsonNode.get("errCode").asInt()==0) {
							Log.d(TAG,jsonNode.get("data").get("authorizeCode").asText());
							Log.d(TAG,jsonNode.get("data").get("regCode").asText());
							if (jsonNode.has("data")){
								JsonNode data = jsonNode.get("data");
								if (data.has("regCode")){
									String strRegCode=data.get("regCode").asText();
									if (strRegCode!=null&&!strRegCode.isEmpty()){
										if (strLicFileName!=null){
											WriteLicense(strLicFileName, strRegCode);
											StartSdk();
										}
									}
								}
							}
						}else if (jsonNode.get("errCode").asInt()==505) {
							LicenseActivity.strLicFileName = strLicFileName;
							// save runcode file
							WriteLicense(strLicPath + "/device.dat", LicenseActivity.strRunCode);
							// start license activity
							Intent intent = new Intent(mainActivity, LicenseActivity.class);
							startActivity(intent);
							// 501:运行码为空
							// 502:序列号为空
							// 503:校验码不在有效期内
							//504:待审批
						}else if (jsonNode.get("errCode").asInt()==504){
							Toast.makeText(mainActivity, R.string.pending_approval, Toast.LENGTH_LONG).show();
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




	}
	private void StopSdk()
	{
		if(bSdkInit) {
			IdFaceSdk.ListDestroy(hFeatureList);
			hFeatureList = 0;
			IdFaceSdk.Uninit();
			bSdkInit = false;
		}
	}

	// 显示对话框
	public static void ShowDialog(Context ctx, String strTitle, String strMsg, int nButton, DialogInterface.OnClickListener dlgFunc) {
		AlertDialog.Builder dialog = new AlertDialog.Builder(ctx);
		dialog.setTitle(strTitle).setMessage(strMsg);
		if((nButton & 0x03) == 0) nButton = 0x01;
		if((nButton & 0x01) == 0x01) {
			dialog.setPositiveButton("Aceptar", dlgFunc);
			if((nButton & 0x02) == 0x02) dialog.setNegativeButton("Cancelar", null);
		} else if((nButton & 0x02) == 0x02) {
			dialog.setNegativeButton("Cancelar", dlgFunc);
		}
		dialog.show();
	}

	private void ReadSetting() {
		String strFileName = strAppPath + "/option.cfg";
		try {
			int i, v, n = 0, N = 64;
			byte[] opt = new byte[N];
			for(i = 0; i < N; i++) opt[i] = 0;
			DataInputStream reader = new DataInputStream(new FileInputStream(strFileName));
			if(reader.read(opt, 0, N) > 0 && opt[0] == 'V' && opt[1] == 0x53) {
				i = 2;
				SettingActivity.bLiveStat = (opt[i++] == 1);
				SettingActivity.nThresholdVerify = opt[i++];
				SettingActivity.nThresholdLive = opt[i++];
				SettingActivity.nThresholdEyes = opt[i++];
				n = opt[i++];
				byte[] str = new byte[n];
				System.arraycopy(opt, i, str, 0, n);
				SettingActivity.strSystemPassword = new String(str);
				i += n;
				v = opt[i++];
				if(v == 0x51) SettingActivity.bDebugStat = true;
				else if(v == 0x50) SettingActivity.bDebugStat = false;

				// check option value
				if(SettingActivity.nThresholdVerify < SettingActivity.nThresholdVerifyMin || SettingActivity.nThresholdVerify > SettingActivity.nThresholdVerifyMax) SettingActivity.nThresholdVerify = SettingActivity.nThresholdVerifyDefault;
				if(SettingActivity.nThresholdLive < SettingActivity.nThresholdLiveMin || SettingActivity.nThresholdLive > SettingActivity.nThresholdLiveMax) SettingActivity.nThresholdLive = SettingActivity.nThresholdLiveDefault;
				if(SettingActivity.nThresholdEyes < SettingActivity.nThresholdEyesMin || SettingActivity.nThresholdEyes > SettingActivity.nThresholdEyesMax) SettingActivity.nThresholdEyes = SettingActivity.nThresholdEyesDefault;
			}
		} catch(Exception ignored) { }
	}

	public static void WriteSetting() {
		String strFileName = strAppPath + "/option.cfg";
		try {
			File f = new File(strFileName);
			if(!f.exists()) if(!f.createNewFile()) {
				Log.d(TAG, "Write option fail.");
			}

			byte[] strPW = SettingActivity.strSystemPassword.getBytes();

			int i, n = strPW.length, N = 64;
			byte[] opt = new byte[N];
			for(i = 0; i < N; i++) opt[i] = 0;
			i = 0;
			opt[i++] = 'V';
			opt[i++] = 0x53;
			opt[i++] = (byte)(SettingActivity.bLiveStat ? 1 : 0);
			opt[i++] = (byte)SettingActivity.nThresholdVerify;
			opt[i++] = (byte)SettingActivity.nThresholdLive;
			opt[i++] = (byte)SettingActivity.nThresholdEyes;
			opt[i++] = (byte)n;
			System.arraycopy(strPW, 0, opt, i, n);
			i += n;
			opt[i++] = (byte)(SettingActivity.bDebugStat ? 0x51:0x50);

			DataOutputStream writer = new DataOutputStream(new FileOutputStream(f));
			writer.write(opt, 0, N);
			writer.flush();
			writer.close();
		} catch(Exception ignored) { }
	}

	// Read regcode from license file
	String ReadRegCode(String strFileName) {
		String str = null;
		try {
			byte[] buffer = new byte[35];
			DataInputStream reader = new DataInputStream(new FileInputStream(strFileName));
			int i, len = reader.read(buffer, 0, buffer.length);
			if(len == 35 && buffer[0] == 'A' && (buffer[1] == 'K' || buffer[1] == 'T' || buffer[1] == 'L' || buffer[1] == 'F')) {
				for(i = 2; i < len; i++) {
					if(i == 8 || i == 17 || i == 26) {
						if(buffer[i] != '-') break;
					} else {
						if(!(buffer[i] >= '0' && buffer[i] <= '9' || buffer[i] >= 'A' && buffer[i] <= 'F' || buffer[i] >= 'a' && buffer[i] <= 'f')) break;
					}
				}
				if(i == len) str = new String(buffer);
			}
		} catch(Exception ignored) { }
		return str;
	}

	public static void WriteLicense(String strFileName, String strLicData) {
		try {
			File f = new File(strFileName);
			if(!f.exists()) if(!f.createNewFile()) {
				Log.d(TAG, "Write license fail.");
			}

			byte[] buffer = strLicData.getBytes();
			if(buffer.length > 0) {
				DataOutputStream writer = new DataOutputStream(new FileOutputStream(f));
				writer.write(buffer, 0, buffer.length);
				writer.flush();
				writer.close();
			}
		} catch(Exception ignored) { }
	}

	/** Mensaje de voz en español (los audios originales del fabricante no se usan). */
	public static void PlayVoiceWelcome(int nType)
	{
		com.digiaccess.Voz.decir(nType == 0 ? "Mire a la cámara" : "Bienvenido");
	}
}
