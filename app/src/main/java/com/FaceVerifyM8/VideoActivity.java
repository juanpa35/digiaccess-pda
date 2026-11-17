package com.FaceVerifyM8;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.Intent;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.graphics.*;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.db.RecordBean;
import com.db.UserBean;
import com.db.UserDB;
import com.idfacesdk.*; // import Face-SDK
import com.digiaccess.Acceso;
import com.digiaccess.Config;
import com.digiaccess.FaceStore;
import com.digiaccess.ScannerHelper;
import com.digiaccess.Sonidos;
import com.digiaccess.YunattCliente;

public class VideoActivity extends AppCompatActivity {
	static final int MAGIC_TEXTURE_ID = 10;

	public static volatile int iCameraIndex = 0;  // Camera index to work： 0-Back Camera, 1-Front Camera

	// Camera deirection and zoom
	public static int[] nRotate = { 90, 270 }; // Rotate Angle (clockwise), [ 0, 90, 180, 270 ] can be supported
	public static int[] bMirror = {  0,   1 }; // 0: Do not Mirror, 1: Left-Right Mirror
	public static final int nZoom = 0; // Use default solution

	// Camera solution (default 640x480), can not be changed on running time
//	public static final int iCaptureWidth = 1280;
//	public static final int iCaptureHeight = 720;

	public static final int iCaptureWidth = 1560;
	public static final int iCaptureHeight = 720;

	private static volatile int nCameraNum = 0; // Camera numbers
	private static volatile Camera camera = null;// Camera Object
	private static volatile boolean bPreviewing = false; // Sign to processing the frame, avoid delays the camera frames
	private static volatile boolean bCameraRunning = false, bCameraReady = false;
	public static volatile boolean bFrameReady = false, bDataReady = false;

	// Screen solution
	private volatile int iScreenWidth = 0, iScreenHeight = 0;
	// Display position and size
	private volatile int iDispX = 0, iDispY = 0, iDispWidth = 0, iDispHeight = 0;

	private boolean bFace = false, bLive = false, bPass = false, bFail = false;
	private final Rect rcFace = new Rect();
	private int nFaceResult = 0;

	// Camera picture solution (head-up)
	public static volatile int iPicWidth = 0, iPicHeight = 0;

	public static final byte[] frameRgb24 = new byte[iCaptureWidth * iCaptureHeight * 3];
	private static final int[] frameRgb32Display = new int[iCaptureWidth * iCaptureHeight];

	private final Object lock = new Object();
	private FACE_DETECT_RESULT faceResult;
	private static final byte[] frameDetect = new byte[iCaptureWidth * iCaptureHeight * 3];
	private static final byte[] frameFace = new byte[iCaptureWidth * iCaptureHeight * 3];
	private static final byte[] frameVerify = new byte[iCaptureWidth * iCaptureHeight * 3];
	private static final byte[] faceImg = new byte[iCaptureWidth * iCaptureHeight * 3 / 4];
	private static int nFaceWidth = 0, nFaceHeight = 0;

	// Camera Surface objects
	private static volatile SurfaceTexture surfaceTexture = null;
	private volatile SurfaceHolder surfaceHolder = null;

	private Paint myPaintFace = null, myPaintResult = null;

	// Start time (No face overtime to exit)
	private volatile long tVerifyStart = 0;

	// Start tasks pool
	private final ExecutorService es = Executors.newCachedThreadPool();

	private TextView txtTitle, txtStat;

	private UserBean userLast = null, userPass = null;
	private int nScore = 0;

	// DIGIACCESS: resultado de la validación en el servidor
	private volatile int nResCodigo = 0;              // 1 permitido, 2 denegado, 4 antipassback, 0 sin validar, -1 rostro no reconocido
	private volatile String strResLinea1 = "", strResLinea2 = "";
	private long tVozNoReconocido = 0;
	private String strUltimaCedula = null;            // evita marcar dos veces a la misma persona que sigue frente a la cámara
	private long tUltimaCedula = 0;
	private Acceso.Resultado ultimoResultado = null;
	private static final long REPETICION_MS = 60000; // tope de seguridad; la marca repetida se evita por ausencia del rostro
	private long tRostroVisto = 0;                    // última vez que se vio un rostro (para detectar que la persona se fue)
	private static final long AUSENCIA_MS = 2500;     // el rostro debe desaparecer este tiempo para permitir una nueva marca
	private volatile boolean consulta = false;        // modo solo consulta: no registra, muestra la autorización
	private ScannerHelper scanner;

	private boolean bDebugFps = false;
	private static int nFpsCam = 0, nFpsDisplay = 0, nFpsDetect = 0, nFpsVerify = 0;
	private static int nCamFpsCount = 0;
	private static long tCamFps = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.video);

		// Keep screen on
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// Get screen solution
		Resources res = getResources();
		DisplayMetrics dm = res.getDisplayMetrics();
		iScreenWidth = dm.widthPixels;
		iScreenHeight = dm.heightPixels;

		nCameraNum = Camera.getNumberOfCameras();
		Log.d(MainActivity.TAG, "VideoActivity found " + nCameraNum + " cameras.");
		if (nCameraNum >= 1) if(iCameraIndex >= nCameraNum) iCameraIndex = 0; // Display camera index must be 0 while only one camera

		// New and initialize the surface object
		SurfaceView surfaceView;
		if(surfaceTexture == null) surfaceTexture = new SurfaceTexture(MAGIC_TEXTURE_ID);
		surfaceView = findViewById(R.id.video);
		surfaceHolder = surfaceView.getHolder();
		if(surfaceHolder != null) surfaceHolder.addCallback(surfaceCallback);

		myPaintFace = new Paint(Paint.ANTI_ALIAS_FLAG);
		myPaintFace.setTypeface(Typeface.DEFAULT_BOLD);
		myPaintFace.setStyle(Paint.Style.STROKE); // stroke style
		myPaintFace.setTextSize(iScreenWidth / 20.0f);

		myPaintResult = new Paint(Paint.ANTI_ALIAS_FLAG);
		myPaintResult.setTypeface(Typeface.DEFAULT_BOLD);
		myPaintResult.setStyle(Paint.Style.FILL); // fill style
		myPaintResult.setTextSize(iScreenWidth / 20.0f);

		// initialize UI controls
		TextView txtDate, txtTime, txtWeek;
		txtDate = findViewById(R.id.txtDate);
		txtTime = findViewById(R.id.txtTime);
		txtWeek = findViewById(R.id.txtWeek);

		txtTitle = findViewById(R.id.txtTitle);
		txtStat = findViewById(R.id.txtStat);

		ImageView imgSwitch = findViewById(R.id.switch_camera);
		imgSwitch.setVisibility(nCameraNum > 0 ? View.VISIBLE : View.INVISIBLE);
		imgSwitch.setOnClickListener(arg0 -> {
			if(nCameraNum > 1) {
				bCameraReady = false;
				closeCamera();
				if(iCameraIndex == 0) iCameraIndex = 1;
				else iCameraIndex = 0;
				openCamera();
				if(camera != null) bCameraReady = true;
			}
		});
		ImageView imgFunc = findViewById(R.id.func);
		imgFunc.setOnClickListener(arg0 -> {
			onFunc();
		});

		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				runOnUiThread(new Runnable() { // update UI on main thread
					@Override
					public void run() {
						SimpleDateFormat fmt;
						Date date = new Date();
						fmt = new SimpleDateFormat("hh:mm a", MainActivity.LOCALE_CO);
						txtTime.setText(fmt.format(date));
						fmt = new SimpleDateFormat("EEE", MainActivity.LOCALE_CO);
						txtWeek.setText(fmt.format(date));
						fmt = new SimpleDateFormat("d MMM yyyy", MainActivity.LOCALE_CO);
						txtDate.setText(fmt.format(date));
					}
				});
			}
		}, 10, 1000);

		// Lector 2D: si leen una cédula en esta pantalla se pasa a la validación con cédula
		scanner = new ScannerHelper(this, (raw, text) -> {
			Intent intent = new Intent(this, CedulaActivity.class);
			if(raw != null) intent.putExtra(CedulaActivity.EXTRA_RAW, raw);
			if(text != null) intent.putExtra(CedulaActivity.EXTRA_TEXT, text);
			startActivity(intent);
			finish();
		});

		// Sentido y carril: el operador los cambia tocando las píldoras. Al cambiar se olvida la última
		// persona validada para que pueda marcar de nuevo (por ejemplo, salida justo después de la entrada).
		com.digiaccess.Modo.conectarPildoras(this, R.id.txtSentidoVideo, R.id.txtCarrilVideo, () -> {
			strUltimaCedula = null;
			ultimoResultado = null;
		});

		// Pasar a cédula: se cierra la cámara (onPause) para que el lector 2D quede libre
		findViewById(R.id.btnUsarCedula).setOnClickListener(v -> {
			startActivity(new Intent(this, CedulaActivity.class));
			finish();
		});

		consulta = getIntent().getBooleanExtra(CedulaActivity.EXTRA_CONSULTA, false);
		if(consulta) {
			txtTitle.setText(R.string.consulta_titulo);
			findViewById(R.id.txtSentidoVideo).setVisibility(View.GONE);
			findViewById(R.id.txtCarrilVideo).setVisibility(View.GONE);
		}

		Log.d(MainActivity.TAG, "VideoActivity created.");

		MainActivity.PlayVoiceWelcome(0);
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		bDebugFps = SettingActivity.bDebugStat;
		Config cfg = Config.load(this);
		com.digiaccess.Modo.pintar(this, R.id.txtSentidoVideo, R.id.txtCarrilVideo);
		if(!bDebugFps) txtTitle.setText(cfg.punto);
		ocultarResultado();

		tVerifyStart = System.currentTimeMillis();
		if(scanner != null) scanner.start();

		if(surfaceHolder != null) {
			// Open the camera
			if(camera == null) openCamera();
			if(camera != null) {
				bCameraReady = bCameraRunning = true;
				es.execute(RunnableDisplayImage);
				es.execute(RunnableDetectImage);
				es.execute(RunnableVerifyImage);
			} else {
				Log.e("VideoActivity", "open camera fail.");
				nCameraNum  = 0;
			}
		}
		if(nCameraNum == 0) finish();
		else if(camera != null) bCameraRunning = true;
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		Log.d(MainActivity.TAG, "VideoActivity onPause");

		bCameraRunning = false;
		if(scanner != null) scanner.stop();

		try {
			Thread.sleep(200); // Wait 200 milliseconds
		} catch (Exception e) {
			//
		}

		bFrameReady = bDataReady = false;
		bFace = bLive = bPass = bFail = false;
		nScore = 0;

		// Liberar la cámara: si queda abierta, el lector 2D (o la cámara trasera) reporta "cámara en uso"
		bCameraReady = false;
		closeCamera();

		Log.d(MainActivity.TAG, "VideoActivity paused.");
	}

	@Override
	protected void onDestroy() {
		closeCamera();
		if(surfaceHolder != null) surfaceHolder.removeCallback(surfaceCallback);

		if (surfaceHolder != null) {
			surfaceHolder.getSurface().release();
			surfaceHolder = null;
		}

		Log.d(MainActivity.TAG, "VideoActivity destroyed.");

		super.onDestroy();
	}

	private static final int COLOR_OK = Color.rgb(16, 185, 129), COLOR_ERROR = Color.rgb(239, 68, 68),
			COLOR_AVISO = Color.rgb(245, 158, 11), COLOR_CIAN = Color.rgb(6, 182, 212);

	/** Tarjeta de resultado sobre el video. codigo: 1 permitido, 2 denegado, 4 antipassback, 0 sin validar, -1 no reconocido */
	private void mostrarResultado(int codigo, String linea1, String nombre, String detalle) {
		View card = findViewById(R.id.cardResultadoVideo);
		ImageView iv = findViewById(R.id.ivResVideo);
		TextView t1 = findViewById(R.id.txtResLinea1);
		boolean ok = codigo == 1, aviso = codigo == 4 || codigo == 0;
		iv.setBackgroundResource(ok ? R.drawable.da_icbox_ok : aviso ? R.drawable.da_icbox_aviso : R.drawable.da_icbox_error);
		iv.setImageResource(ok ? R.drawable.ic_da_check : aviso ? R.drawable.ic_da_alerta : R.drawable.ic_da_x);
		t1.setText(linea1);
		t1.setTextColor(getResources().getColor(ok ? R.color.da_verde_texto : aviso ? R.color.da_ambar_texto : R.color.da_rojo_texto));
		((TextView) findViewById(R.id.txtResNombre)).setText(nombre);
		((TextView) findViewById(R.id.txtResDetalle)).setText(detalle);
		card.setVisibility(View.VISIBLE);
		txtStat.setVisibility(View.GONE);
	}

	private void ocultarResultado() {
		View card = findViewById(R.id.cardResultadoVideo);
		if(card != null) card.setVisibility(View.GONE);
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if(scanner != null && scanner.handleKeyEvent(event)) return true;
		return super.dispatchKeyEvent(event);
	}

	private void onFunc()
	{
		// check password
		Intent intent = new Intent(this, PasswordActivity.class);
		this.startActivity(intent);
	}

	private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
		@Override
		public void surfaceCreated(@NonNull SurfaceHolder holder) {
		}

		@Override
		public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
			try {
				if(holder == surfaceHolder) {
					iScreenWidth = width;
					iScreenHeight = height;

					float fx = width * 1.0f / iPicWidth, fy = height * 1.0f / iPicHeight;
					if(fx >= fy) {
						iDispHeight = height;
						iDispWidth = iPicWidth * height / iPicHeight;
					} else {
						iDispWidth = width;
						iDispHeight = iPicHeight * width / iPicWidth;
					}
					iDispX = (width - iDispWidth) / 2;
					//iDispY = (height - iDispHeight) / 2;
					iDispY = 0;

					if (camera != null) {
						camera.stopPreview();
						camera.setPreviewCallback(cameraPreviewCallback);
						camera.setPreviewTexture(surfaceTexture);
						camera.startPreview();
					}
				}
			} catch (Exception e) {
				//
			}
		}

		@Override
		public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
		}
	};

	public static boolean isCameraOpen()
	{
		return camera != null;
	}

	public static void closeCamera()
	{
		if(camera != null) closeCamera(camera); camera = null;
	}

	private void openCamera()
	{
		camera = openCamera(iCameraIndex);
	}

	@SuppressWarnings("all")
	private Camera openCamera(int nCameraIndex) {
		Camera cam = null;

		// Open the camerq
		try {
			cam = Camera.open(nCameraIndex);
		} catch (RuntimeException ex) {
			Log.e(MainActivity.TAG, "VideoActivity open camera " + nCameraIndex + " RuntimeException !");
		} catch (Exception ex) {
			Log.e(MainActivity.TAG, "VideoActivity open camera " + nCameraIndex + " Exception !");
		}

		if (cam != null) {
			int width = iCaptureWidth, height = iCaptureHeight;
			if (nRotate[nCameraIndex] == 90 || nRotate[nCameraIndex] == 270) {
				iPicWidth = height / (nZoom + 1);
				iPicHeight = width / (nZoom + 1);
			} else {
				iPicWidth = width / (nZoom + 1);
				iPicHeight = height / (nZoom + 1);
			}

			width = iScreenWidth;
			height = iScreenHeight;
			float fx = width * 1.0f / iPicWidth, fy = height * 1.0f / iPicHeight;
			if (fx >= fy) {
				iDispHeight = height;
				iDispWidth = iPicWidth * height / iPicHeight;
			} else {
				iDispWidth = width;
				iDispHeight = iPicHeight * width / iPicWidth;
			}
			iDispX = (width - iDispWidth) / 2;
			//iDispY = (height - iDispHeight) / 2;
			iDispY = 0;

			try {
				// Set camera format and solution
				Camera.Parameters parameters = cam.getParameters();
				parameters.setPreviewFormat(ImageFormat.NV21);
				parameters.setPreviewSize(iCaptureWidth, iCaptureHeight);
				cam.setParameters(parameters);

				// Set camera preview
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
			if (cam == null) Log.e(MainActivity.TAG, "VideoActivity set camera " + nCameraIndex + " format and size fail.");
		} else Log.e(MainActivity.TAG, "VideoActivity open camera " + nCameraIndex + " fail.");

		if(cam != null) Log.d(MainActivity.TAG, "VideoActivity open camera " + nCameraIndex + " OK.");


		return cam;
	}

	private static void closeCamera(Camera cam) {
		if(cam != null) {
			cam.setPreviewCallback(null);

			try {
				Thread.sleep(50); // Wait 50 milliseconds
			} catch(Exception e) {
				//
			}
			cam.stopPreview();

			try {
				Thread.sleep(50); // Wait 50 milliseconds
			} catch(Exception e) {
				//
			}
			cam.release();

			Log.d(MainActivity.TAG, "VideoActivity close camera.");
		}
	}

	// Color Camera Listener
	private static final Camera.PreviewCallback cameraPreviewCallback = (data, camx) -> {
		nCamFpsCount++;
		if(!bPreviewing && !bFrameReady) {
			bPreviewing = true;
			if (bCameraReady) {
				// update color camera frame data to detect and display
				IdFaceSdk.YUV420P_TO_RGB24_RGB32(data, iCaptureWidth, iCaptureHeight, 2, nZoom, nRotate[iCameraIndex], bMirror[iCameraIndex], frameRgb24, frameRgb32Display);
				bFrameReady = true;
				bDataReady = true;
			}
			bPreviewing = false;
		}
		long tFps = System.currentTimeMillis();
		if((tFps - tCamFps) > 1000) {
			tCamFps = tFps;
			nFpsCam = nCamFpsCount;
			nCamFpsCount = 0;
		}
	};

	Runnable RunnableDisplayImage = () -> {
		long tFps = 0, tDisp = 0;
		int nFps = 0;
		while(bCameraRunning) {
			if(surfaceHolder != null && bCameraReady) {
				Canvas canvas = null;
				if(bFrameReady) {
					bFrameReady = false;
					canvas = surfaceHolder.lockCanvas();
				}
				if(canvas != null) {
					// Display video
					Bitmap bmp = Bitmap.createBitmap(iPicWidth, iPicHeight, Bitmap.Config.ARGB_8888);
					if(bmp != null) {
						bmp.setPixels(frameRgb32Display, 0, iPicWidth, 0, 0, iPicWidth, iPicHeight);
						canvas.drawBitmap(bmp, null, new Rect(iDispX, iDispY, iDispX + iDispWidth, iDispY + iDispHeight), null);
						bmp.recycle();
					}

					if(bPass || bFail || bFace) { // recuadro del rostro: cian buscando, verde autorizado, rojo/ámbar denegado
						float fl = iDispX + rcFace.left * iDispWidth / (float) iPicWidth, ft = iDispY + rcFace.top * iDispHeight / (float) iPicHeight;
						float fr = iDispX + rcFace.right * iDispWidth / (float) iPicWidth, fb = iDispY + rcFace.bottom * iDispHeight / (float) iPicHeight;
						int colorMarco;
						if(bPass) colorMarco = COLOR_OK;
						else if(bFail) colorMarco = (nResCodigo == 4 || nResCodigo == 0) ? COLOR_AVISO : COLOR_ERROR;
						else colorMarco = bLive ? COLOR_CIAN : Color.argb(200, 255, 255, 255);
						if(fr > fl && fb > ft) {
							myPaintFace.setStrokeWidth(8);
							myPaintFace.setColor(colorMarco);
							canvas.drawRoundRect(new RectF(fl, ft, fr, fb), 36, 36, myPaintFace);
							myPaintFace.setStrokeWidth(22);
							myPaintFace.setColor(Color.argb(46, Color.red(colorMarco), Color.green(colorMarco), Color.blue(colorMarco)));
							canvas.drawRoundRect(new RectF(fl - 11, ft - 11, fr + 11, fb + 11), 44, 44, myPaintFace);
						}
					}

					surfaceHolder.unlockCanvasAndPost(canvas);

					long tCur = System.currentTimeMillis();
					if((tCur - tFps) > 1000) {
						tFps = tCur;
						nFpsDisplay = nFps;
						nFps = 1;
					} else nFps++;
				}
			}

			// No face over 30 seconds to exit
			long tCur = System.currentTimeMillis();
			if(bFace) tVerifyStart = tCur;
			else if((tCur - tVerifyStart) > 30 * 1000) {
				Log.d(MainActivity.TAG, "Verify timeout.");
				finish();
				break;
			}

			if(bDebugFps) {
				if((tCur - tDisp) > 1000) {
					tDisp = tCur;
					runOnUiThread(() -> {
						String str = "FPS: Cam=" + nFpsCam + ", Dsp=" + nFpsDisplay + ", Det=" + nFpsDetect + ", Vrf=" + nFpsVerify;;
						if(bPass || bFail) str = "";
						txtTitle.setText(str);
					});
				}
			}

			// Thread delay to avoid CPU overload
			try {
				Thread.sleep(50); // Wait 50 millisecond
			} catch(Exception ignored) { }
		}
	};

	Runnable RunnableDetectImage = () -> {
		long tFps = 0;
		int nFps = 0;
		while(bCameraRunning) {
			if(MainActivity.nUserNum > 0 && bCameraReady) {
				if(bDataReady) {
					// detect user
					DetectUser();
					bDataReady = false;

					long tCur = System.currentTimeMillis();
					if((tCur - tFps) > 1000) {
						tFps = tCur;
						nFpsDetect = nFps;
						nFps = 1;
					} else nFps++;
				}
			} else nFpsDetect = nFps = 0;

			// Thread delay to avoid CPU overload
			try {
				Thread.sleep(50); // Wait 50 milliseconds
			} catch (Exception ignored) { }
		}
	};

	Runnable RunnableVerifyImage = () -> {
		boolean bStat, bStatLast = true;
		long tFace = 0; // Face begin time
		long tLost = 0; // Face begin time

		long tFps = 0;
		int nFps = 0;
		while(bCameraRunning) {
			if(MainActivity.nUserNum > 0 && bCameraReady) {
				long tCur = System.currentTimeMillis();
				if(tCur < tFace) tFace = tCur;

				// La persona dejó de estar frente a la cámara: se olvida la última marca
				// (así, mientras siga al frente NO se marca de nuevo, y al volver otra persona sí)
				if(strUltimaCedula != null && (tCur - tRostroVisto) > AUSENCIA_MS) {
					strUltimaCedula = null;
					ultimoResultado = null;
				}

				if(bPass || bFail) { // pausa para que el guarda lea el resultado
					if((tCur - tLost) > 2500) {
						tLost = tCur;
						tFace = 0;
						nScore = 0;
						bPass = bFail = bFace = false;
						runOnUiThread(this::ocultarResultado);
					}
				} else {
					int ret = nFaceResult = VerifyUser();
					bLive = ret >= 2;
					boolean bGuardar = true;
					if(ret == 3) { // Rostro reconocido
						userPass = userLast;
						if(consulta) { // solo consulta: mostrar la autorización, sin registrar
							final String ced = userPass.workno;
							bCameraRunning = false;
							runOnUiThread(() -> {
								Intent it = new Intent(VideoActivity.this, CedulaActivity.class);
								it.putExtra(CedulaActivity.EXTRA_CONSULTA, true);
								it.putExtra(CedulaActivity.EXTRA_TEXT, ced);
								startActivity(it);
								finish();
							});
							break;
						}
						Acceso.Resultado r;
						if(userPass.workno.equals(strUltimaCedula) && ultimoResultado != null) {
							r = ultimoResultado;          // la misma persona sigue al frente: no se marca de nuevo
							bGuardar = false;
						} else {
							runOnUiThread(() -> txtStat.setText("Validando…"));
							r = Acceso.validar(this, userPass.workno, userPass.uname, Acceso.MEDIO_FACIAL);
							strUltimaCedula = userPass.workno;
							tUltimaCedula = System.currentTimeMillis();
							ultimoResultado = r;
							boolean esSalida = Config.SALIDA.equals(Config.load(this).sentido);
							Sonidos.resultado(r.codigo, Sonidos.frase(r, esSalida));
							if(r.permitido() && !r.sinConexion) YunattCliente.enviarMarca(this, userPass.workno, userPass.uname, esSalida);
						}
						nResCodigo = r.codigo;
						String nombre = r.nombre != null && r.nombre.length() > 0 ? r.nombre : userPass.uname;
						String hora = new SimpleDateFormat("hh:mm a", MainActivity.LOCALE_CO).format(new Date());
						String cedula = "C.C. " + CedulaActivity.formatear(userPass.workno);
						if(r.permitido()) {
							strResLinea1 = Config.SALIDA.equals(Config.load(this).sentido) ? "Salida registrada" : "Bienvenido";
							strResLinea2 = nombre;
							bPass = true;
							String det = cedula + " · " + hora + (r.sinConexion ? " · sin conexión" : "");
							runOnUiThread(() -> mostrarResultado(1, strResLinea1, nombre, det));
						} else {
							strResLinea1 = r.codigo == 0 ? "No se pudo validar" : "Acceso denegado";
							strResLinea2 = r.mensaje();
							bFail = true;
							int cod = r.codigo;
							String det = cedula + " · " + r.mensaje();
							runOnUiThread(() -> mostrarResultado(cod, strResLinea1, nombre, det));
						}
						runOnUiThread(() -> txtStat.setText(R.string.mire_pantalla));
						tLost = tCur;
					} else if(tFace != 0) { // Timeout fail ?
						if(ret != 0) tLost = tCur;
						if((tCur - tLost) > 2 * 1000 || (tCur - tFace) > 5 * 1000) {
							nResCodigo = -1;
							userPass = null;
							strResLinea1 = "Rostro no reconocido";
							strResLinea2 = "Use la cédula";
							bFail = true;
							tLost = tCur;
							runOnUiThread(() -> mostrarResultado(-1, "Acceso denegado", "Rostro no reconocido", "Use la cédula o contacte al administrador"));
							if(tCur - tVozNoReconocido > 6000) {
								tVozNoReconocido = tCur;
								Sonidos.resultado(-1, "Rostro no reconocido. Use la cédula");
							}
						}
					} else if(ret != 0) { // Face detected
						tFace = tLost = tCur; // start
					}
					if((bPass || bFail) && bGuardar) { // Save result
						long tSeconds = System.currentTimeMillis() / 1000;
						String strPicture = MainActivity.strAppPath + "/pic" + tSeconds + ".jpg";
						IdFaceSdk.SaveJpegFile(strPicture, faceImg, nFaceWidth, nFaceHeight, 24, 80);

						RecordBean record = new RecordBean();
						if(userPass != null) { // persona reconocida (autorizada o denegada por DIGIACCESS)
							record.uid = userPass.uid;
							record.workno = userPass.workno;
							record.uname = userPass.uname;
							record.photoFileName = userPass.photoFileName;
						}
						record.stat = bPass ? 0 : 1;
						record.pictureFileName = strPicture;
						record.score = nScore ;
						MainActivity.db.insertRecord(record);
					}

					bStat = (tLost != tCur);
					if(bStat != bStatLast) {
						bStatLast = bStat;
						if(bStat) {
							runOnUiThread(() -> {
								if(findViewById(R.id.cardResultadoVideo).getVisibility() != View.VISIBLE) txtStat.setVisibility(View.VISIBLE);
							});
						} else {
							runOnUiThread(() -> {
								txtStat.setVisibility(View.GONE);
							});
						}
					}

					if(bFace) {
						if((tCur - tFps) > 1000) {
							tFps = tCur;
							nFpsVerify = nFps;
							nFps = 1;
						} else nFps++;
					} else nFpsVerify = nFps = 0;
				}
			}

			// Pausa corta entre intentos (más baja = reconoce más rápido)
			try {
				Thread.sleep(bPass || bFail ? 200 : 80);
			} catch(Exception ignored) { }
		}
	};

	void DetectUser()
	{
		if(!MainActivity.bSdkInit) return;

		FACE_DETECT_RESULT face = new FACE_DETECT_RESULT();
		System.arraycopy(frameRgb24, 0, frameDetect, 0, iPicWidth * iPicHeight * 3);

		long tStart = 0;
		if(bDebugFps) tStart = System.nanoTime() / 1000000;
		int nFaceNum = IdFaceSdk.DetectFace(frameDetect, iPicWidth, iPicHeight, face);
		// Presencia: se actualiza aunque se esté mostrando un resultado, para saber cuándo la persona se va
		if(nFaceNum == 1) tRostroVisto = System.currentTimeMillis();
		if(bPass || bFail) return; // mostrando resultado: no se prepara una nueva verificación
		if(bDebugFps) {
			long tEnd = System.nanoTime() / 1000000;
			Log.d(MainActivity.TAG, "Detec face return " + nFaceNum + ", Tooks " + (tEnd - tStart) + " ms");
		}

		if(nFaceNum == 1) {
			rcFace.left = face.nFaceLeft;
			rcFace.top = face.nFaceTop;
			rcFace.right = face.nFaceRight;
			rcFace.bottom = face.nFaceBottom;
			bFace = true;

			synchronized (lock) {
				faceResult = new FACE_DETECT_RESULT(face);
				System.arraycopy(frameDetect, 0, frameFace, 0, iPicWidth * iPicHeight * 3);
			}
		} else bFace = false;
	}

	// Verify user
	// Return: 0-No face. 1-Face ready, 2-Live, 3-Pass
	int VerifyUser()
	{
		int nResult = 0, nScoreMax = 1;
		if(bFace) {
			int ret, nFaceMask = 0;
			FACE_DETECT_RESULT face;
			nResult = 1;

			synchronized (lock) {
				face = new FACE_DETECT_RESULT(faceResult);
				System.arraycopy(frameFace, 0, frameVerify, 0, iPicWidth * iPicHeight * 3);
			}
			if(face.nAngleYaw < -25 || face.nAngleYaw > 25 || face.nAnglePitch < -25 || face.nAnglePitch > 25 || face.nAngleRoll < -25 || face.nAngleRoll > 25)
				Log.d(MainActivity.TAG, "VerifyUser: bad angle.");
			else if((face.nRightEyeX - face.nLeftEyeX) < SettingActivity.nThresholdEyes)
				Log.d(MainActivity.TAG, "VerifyUser: Face too small."); // 人脸太小时不比对
			else {
				if(SettingActivity.bLiveStat) {
					int[] scores = new int[]{0};
					ret = IdFaceSdk.LiveDetectOneCameraEx(0, iPicWidth, iPicHeight, frameVerify, face, scores);
					if(ret >= 0 && scores[0] >= SettingActivity.nThresholdLive)
						nResult = 2;
					else
						Log.d(MainActivity.TAG, "VerifyUser: LiveDetect fail. ret=" + ret + ", score=" + scores[0]); // 人脸太小时不比对
				} else nResult = 2;

				if(nResult == 2) {
					// Detect facemask
					nFaceMask = IdFaceSdk.FaceMask(frameVerify, iPicWidth, iPicHeight, face);

					if(face.nConfidence >= 80 || (nFaceMask > 0 && face.nConfidence >= 60)) {
						int nFeatureSize = IdFaceSdk.FeatureSize();
						byte[] fea = new byte[nFeatureSize];

						// Get feature
						ret = IdFaceSdk.FeatureGet(frameVerify, iPicWidth, iPicHeight, face, fea);

						if(ret == 0) {
							// Compare feature to find user
							int i, n, index = 0;
							int score_second = 0, score_threshold = SettingActivity.nThresholdVerify;
							byte[] scores = new byte[UserDB.MAX_USER];
							UserBean candidato = null;
							synchronized (FaceStore.LOCK) { // la sincronización puede borrar rostros en segundo plano
								n = IdFaceSdk.ListCompare(MainActivity.hFeatureList, fea, 0, 0, scores);
								if(n != MainActivity.nUserNum)
									Log.e(MainActivity.TAG, "Verify: FeatureList.length=" + n + ", UserNum=" + MainActivity.nUserNum);
								for(i = 0; i < n && i < MainActivity.nUserNum; i++) {
									if(scores[i] > nScoreMax) {
										score_second = nScoreMax; // CORREGIDO: el anterior mejor pasa a ser el segundo
										nScoreMax = scores[i];
										index = i;
									}
									else if(scores[i] > score_second) score_second = scores[i];
								}
								if(index < MainActivity.userList.size()) candidato = MainActivity.userList.get(index);
							}
							Log.d(MainActivity.TAG, "VerifyUser score = " + nScoreMax + ", " + score_second + " ..., threshold=" + score_threshold);

							if(nFaceMask == 1) score_threshold -= 10;
							else if(nFaceMask == 2) score_threshold -= 20;
							if(nScoreMax >= score_threshold && candidato != null) { // Pass
								// Get the user
								userLast = candidato;
								nResult = 3;
							}
						}
					}
				}
			}
			if(nScoreMax > nScore) {
				float fScaleX = 1.5f, fScaleTop = 1.6f, fScaleBottom = 1.8f;
				int nEyeWidth = face.nRightEyeX - face.nLeftEyeX;
				int left = face.nLeftEyeX - (int)((fScaleX - 0.5f) * nEyeWidth), right = face.nRightEyeX + (int)((fScaleX - 0.5f) * nEyeWidth);
				int top = (face.nLeftEyeY + face.nRightEyeY) / 2 - (int)(fScaleTop * 1.3f * nEyeWidth), bottom = (face.nLeftEyeY + face.nRightEyeY) / 2 + (int)(fScaleBottom * 1.7f * nEyeWidth);
				if(left < 0) left = 0;
				if(top < 0) top = 0;
				if(right > iPicWidth) right = iPicWidth;
				if((right - left) % 2 == 1) right--;
				if(bottom > iPicHeight) bottom = iPicHeight;
				if((bottom - top) % 2 == 1) bottom--;
				IdFaceSdk.CropImage(frameVerify, iPicWidth, iPicHeight, faceImg, left, top, right - left, bottom - top, 1);
				nFaceWidth = (right - left) / 2;
				nFaceHeight = (bottom - top) / 2;

				nScore = nScoreMax;
			}
		}
		return nResult;
	}
}



