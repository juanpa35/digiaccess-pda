package com.FaceVerifyM8;

import android.annotation.SuppressLint;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import com.db.UserBean;
import com.db.UserDB;

import com.idfacesdk.*; // import Face-SDK

public class UserActivity extends AppCompatActivity {
	static final int RC_PICK_PHOTO = 1; // Pick a photo from system album
	static final int RC_TAKE_PHOTO = 2; // Take a photo by the system capture

	static public long uid = 0;
	String strPhotoFileName = null, strPhotoTmpFileName = null;
	ImageView imgPhoto;
	boolean bPhotoReady = false, bPhotoChanged = false;

	String strResult;

	DialogInterface.OnClickListener dlgOkFunc = (dialog, which) -> finish();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.user);
		com.digiaccess.Encabezado.configurar(this, R.string.title_user_edit);

		// Initialize UI controls
		EditText txtName, txtNo;
		txtName = findViewById(R.id.txtName);
		txtNo = findViewById(R.id.txtNo);
		imgPhoto = findViewById(R.id.imgPhoto);

		Button btnTakePhoto, btnPicture, btnOK, btnCancel;
		btnTakePhoto = findViewById(R.id.btnPhoto);
		btnPicture = findViewById(R.id.btnPicture);
		btnOK = findViewById(R.id.btnOK);
		btnCancel = findViewById(R.id.btnCancel);

		btnTakePhoto.setOnClickListener(arg0->takePhoto());
		btnPicture.setOnClickListener(arg0-> pickPhoto());
		btnOK.setOnClickListener(arg0-> {
			String strName = txtName.getText().toString(), strNo = txtNo.getText().toString();
			if(strName.isEmpty()) MainActivity.ShowDialog(this, "Rostro","Escriba el nombre.", 0x01, null);
			else if(strNo.isEmpty()) MainActivity.ShowDialog(this, "Rostro","Escriba el número de cédula.", 0x01, null);
			else if(!bPhotoReady) MainActivity.ShowDialog(this, "Rostro","Tome la foto o seleccione una imagen.", 0x01, null);
			else {
				if(bPhotoChanged) {
					long tMilliSeconds = System.currentTimeMillis();
					strPhotoFileName = MainActivity.strAppPath +  "/photo" + tMilliSeconds + ".jpg";
					if(copyFile(strPhotoTmpFileName, strPhotoFileName) != 0) strPhotoFileName = strPhotoTmpFileName;
				}
				int ret;
				synchronized (com.digiaccess.FaceStore.LOCK) {
					ret = RegUser(strName, strNo.trim(), strPhotoFileName);
				}
				MainActivity.ShowDialog(this, "Rostro", strResult, 0x01, ret == 0 ? dlgOkFunc : null);
			}
		});
		btnCancel.setOnClickListener(arg0 -> finish());

		// DIGIACCESS: al escribir la cédula se trae el nombre de la lista de autorizados
		txtNo.setOnFocusChangeListener((v, hasFocus) -> {
			if(hasFocus) return;
			String ced = txtNo.getText().toString().replace(".", "").trim();
			txtNo.setText(ced);
			if(ced.isEmpty() || !txtName.getText().toString().trim().isEmpty()) return;
			com.digiaccess.LocalStore.Autorizado a = com.digiaccess.LocalStore.get(this).buscarAutorizado(ced);
			if(a != null && a.nombre != null) txtName.setText(a.nombre);
		});

		if(uid != 0) {
			int i, n = MainActivity.nUserNum;
			for(i = 0; i < n; i++) {
				UserBean user = MainActivity.userList.get(i);
				if(user.uid == uid) {
					txtName.setText(user.uname);
					txtNo.setText(user.workno);

					Bitmap bmp = null;
					try {
						bmp = BitmapFactory.decodeFile(user.photoFileName);
					} catch(Exception ignored) { }
					if(bmp != null) {
						strPhotoFileName = user.photoFileName;
						imgPhoto.setImageBitmap(bmp);

						imgPhoto.setVisibility(View.VISIBLE);
						bPhotoReady = true;
					}
					break;
				}
			}
			if(i == n) uid = 0;
		}

		if(uid == 0) txtName.requestFocus();
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		// Close the color camera before editing the user (User editor maybe take photo)
		if(VideoActivity.isCameraOpen()) VideoActivity.closeCamera();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		uid = 0;
	}

	private int copyFile(String strSrc, String strDst)
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
		Log.d(MainActivity.TAG, "copyFile from " + strSrc + " to " + strDst + " return " + ret);
		return ret;
	}

	// Pick a photo from system album
	private void pickPhoto() {
		// Initialize intent to pick a photo
		Intent intentToPickPic = new Intent(Intent.ACTION_PICK, null);
		intentToPickPic.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");

		// Start intent to pick a photo
		startActivityForResult(intentToPickPic, RC_PICK_PHOTO);
	}

	// Take a photo by system capture
	private void takePhoto() {
		// Initialize intent to take a photo
		Intent intentToTakePhoto = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		strPhotoTmpFileName = MainActivity.strCachePath + "/face.jpg";
		File photoFile = new File(strPhotoTmpFileName);
		Uri uri = FileProvider.getUriForFile(UserActivity.this, "com.FaceVerifyM8.fileprovider", photoFile);
		intentToTakePhoto.putExtra(MediaStore.EXTRA_OUTPUT, uri);

		// Start intent to take a photo
		startActivityForResult(intentToTakePhoto, RC_TAKE_PHOTO);
	}

	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);

		if(resultCode != 0) {
			switch (requestCode) {
				case RC_PICK_PHOTO:
					if(data == null) break;
					Uri uri = data.getData();
					if(uri == null) break;
					strPhotoTmpFileName = FileUtil.getFilePathByUri(this, uri); // Get the file name to pick
					// No break
				case RC_TAKE_PHOTO:
					if(!TextUtils.isEmpty(strPhotoTmpFileName)) { // file name is valid
						int nDegree = IdFaceSdk.ReadDegreeImageFile(strPhotoTmpFileName, null, null);
						BitmapFactory.Options options = new BitmapFactory.Options();
						options.inSampleSize = 1;
						options.inJustDecodeBounds = true; // Do not load data
						try {
							BitmapFactory.decodeFile(strPhotoTmpFileName, options);
						} catch(Exception ignored) { }
						if(options.outWidth > 640 || options.outHeight > 640) {
							if (options.outWidth > options.outHeight)
								options.inSampleSize = options.outWidth / 640;
							else options.inSampleSize = options.outHeight / 640;
						}
						options.inJustDecodeBounds = false; // Load data
						Bitmap bm = null;
						try {
							bm = BitmapFactory.decodeFile(strPhotoTmpFileName, options);
						} catch(Exception ignored) { }
						if(bm != null) {
							float fScale = 1.0f;
							if(options.outWidth > 640 || options.outHeight > 640) {
								if(options.outWidth > options.outHeight)
									fScale = 640.0f / options.outWidth;
								else fScale = 640.0f / options.outHeight;
							}
							Matrix m = new Matrix();
							if(fScale < 0.9999f) m.postScale(fScale, fScale);
							m.postRotate(nDegree);
							bm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);

							strPhotoTmpFileName = MainActivity.strCachePath + "/face.jpg";
							File outFile = new File(strPhotoTmpFileName);
							try {
								if(!outFile.exists()) if(outFile.createNewFile()) {
									Log.d(MainActivity.TAG, "create file " + outFile.getAbsolutePath() + " falló.");
								}
								FileOutputStream outStream = new FileOutputStream(outFile);
								bm.compress(Bitmap.CompressFormat.JPEG, 90, outStream);
								outStream.flush();
							} catch (Exception ignored) { }
							// Display the photo
							imgPhoto.setImageDrawable(new BitmapDrawable(getResources(), bm));
							imgPhoto.setVisibility(View.VISIBLE);

							Log.d(MainActivity.TAG, "Register user with REAL photo FileName: " + strPhotoTmpFileName);

							bPhotoReady = true;
							bPhotoChanged = true;
						}
					}
					break;
				default:
					break;
			}
		}
	}

	// Regist the user
	private int RegUser(String strName, String strNo, String strPhotoFileName) {
		int nResult = -1;
		int[] width = new int[1], height = new int[1];
		strResult = "Foto inválida.\nGuardar rostro " + strName + "falló.";

		// Read the image solution
		if(IdFaceSdk.ReadImageFile(strPhotoFileName, null, 0, width, height, 24) != 0) width[0] = 0;
		if(width[0] > 0 && height[0] > 0) {
			// Read the image data
			byte[] img = new byte[width[0] * height[0] * 3];
			if(IdFaceSdk.ReadImageFile(strPhotoFileName, img, img.length, width, height, 24) != 0) width[0] = 0;
			if(width[0] > 0 && height[0] > 0) {
				nResult = -2;
				strResult = "No se detectó un rostro en la foto.\nGuardar rostro " + strName + " falló.";

				// Detect face
				FACE_DETECT_RESULT face = new FACE_DETECT_RESULT();
				int nFaceNum = IdFaceSdk.DetectFace(img, width[0], height[0], face);
				if(nFaceNum == 1) { // Face detected
					if(face.nAngleYaw < -15 || face.nAngleYaw > 15 || face.nAnglePitch < -15 || face.nAnglePitch > 15 || face.nAngleRoll < -15 || face.nAngleRoll > 15)
						strResult = "El rostro está muy girado; mire de frente.\nGuardar rostro " + strName + " falló.";
					else if((face.nRightEyeX - face.nLeftEyeX) < 40 || (face.nRightEyeX - face.nLeftEyeX) < SettingActivity.nThresholdEyes) strResult = "El rostro está muy lejos; acérquese.\nGuardar rostro " + strName + " falló."; // 人脸太小时不允许注册
					else if(face.nQuality < 60) strResult = "La calidad de la foto es muy baja.\nGuardar rostro " + strName + " falló.";
					else {
						// Detect facemask
						int nFaceMask = IdFaceSdk.FaceMask(img, width[0], height[0], face);
						if(nFaceMask > 0) strResult = "Quítese el tapabocas para enrolar.\nGuardar rostro " + strName + " falló."; // Facemask not accepted while register user
						else if(face.nConfidence < 80) strResult = "El rostro no es claro.\nGuardar rostro " + strName + " falló.";
						else {
							// Get feature
							byte[] fea = new byte[IdFaceSdk.FeatureSize()];
							if(IdFaceSdk.FeatureGet(img, width[0], height[0], face, fea) != 0)
								strResult = "No se pudo extraer la plantilla facial.\nGuardar rostro " + strName + " falló.";
							else {
								int[] pos = new int[1];
								UserBean user = null;

								// Check user by workno (new or exist)
								int i, index = -1;
								for(i = 0; i < MainActivity.nUserNum; i++) {
									user = MainActivity.userList.get(i);
									if(user == null) break; // always false
									if(user.workno.compareTo(strNo) == 0) break;
								}

								if((i < MainActivity.nUserNum) && (user == null || uid != user.uid)) { // work no used by other user
									nResult = -3;
									strResult = "La cédula " + strNo + " ya existe.\nGuardar rostro " + strName + " falló.";
								} else {
									user = null;
									if(uid != 0) {
										for(i = 0; i < MainActivity.nUserNum; i++) {
											user = MainActivity.userList.get(i);
											if(user == null) continue;
											if(user.uid == uid) {
												index = i;
												break;
											}
										}
									}
									if(uid != 0 && (user == null || index < 0)) {
										nResult = -10;
										strResult = "Update bad user " + strNo + " ya existe.\nGuardar rostro " + strName + " falló.";
									} else {
										if(user == null) user = new UserBean();

										user.uid = uid;
										user.uname = strName;
										user.workno = strNo;
										user.photoFileName = strPhotoFileName;
										user.feature = fea;

										if(uid != 0) { // Update old user
											// Update user feature to the feature list (remove old and insert new on the same position)
											pos[0] = user.featurePos;
											if(pos[0] != -1)
												IdFaceSdk.ListRemove(MainActivity.hFeatureList, pos[0], 1);
											IdFaceSdk.ListInsert(MainActivity.hFeatureList, pos, fea, 1);
											if(pos[0] == -1) {
												nResult = -4;
												strResult = "No se pudo actualizar el rostro en la lista.\nGuardar rostro " + strName + " falló.";
											} else {
												user.featurePos = pos[0];

												Log.d(MainActivity.TAG, "update user: uid=" + user.uid + ", no=" + user.workno + ", name=" + user.uname);

												// update user to Database
												if(MainActivity.db.updateUser(user) < 0) {
													Log.d(MainActivity.TAG, "No se pudo actualizar en la base de datos.");
													nResult = -5;
													strResult = "No se pudo actualizar en la base de datos.\nGuardar rostro " + strName + " falló.";
												} else {
													Log.d(MainActivity.TAG, "Update user (" + strNo + ", " + strName + ") into DB OK. feature-pos=" + user.featurePos);
													nResult = 0;
													strResult = "Rostro actualizado " + strName + " correctamente.";

													MainActivity.userList.set(index, user);
												}
											}
										} else if(MainActivity.nUserNum >= UserDB.MAX_USER) {
											Log.d(MainActivity.TAG, "No se pudo guardar: supera el límite de " + UserDB.MAX_USER);
											nResult = -6;
											strResult = "Supera el máximo de rostros: " + UserDB.MAX_USER + ".\nGuardar rostro " + strName + " falló.";
										} else { // Save new user
											// Insert user feature to the feature list
											pos[0] = -1;
											IdFaceSdk.ListInsert(MainActivity.hFeatureList, pos, fea, 1);
											if(pos[0] == -1) {
												Log.d(MainActivity.TAG, "No se pudo agregar el rostro a la lista.");
												nResult = -4;
												strResult = "No se pudo agregar el rostro a la lista.\nGuardar rostro " + strName + " falló.";
											} else {
												// update user feature position
												user.featurePos = pos[0];

												// save user to Database
												if(MainActivity.db.insertUser(user) <= 0) {
													IdFaceSdk.ListRemove(MainActivity.hFeatureList, pos[0], 1);

													Log.d(MainActivity.TAG, "No se pudo guardar en la base de datos.");
													nResult = -5;
													strResult = "No se pudo guardar en la base de datos.\nGuardar rostro " + strName + " falló.";
												} else {
													Log.d(MainActivity.TAG, "Insert user (" + strNo + ", " + strName + ") into DB OK. featurePos=" + user.featurePos);
													nResult = 0;
													strResult = "Guardar rostro " + strName + " correctamente.";

													MainActivity.userList.add(user);
													MainActivity.nUserNum++;
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
		return nResult;
	}

	////////////////////////////////////////////////////////////////////////////////////////////////////////

	// Find the true-path of file by URI
	static class FileUtil {
		/**
		 * @param context -- context
		 * @param uri -- uri
		 * @return -- uri.path
		 */
		public static String getFilePathByUri(Context context, Uri uri) {
			if ("content".equalsIgnoreCase(uri.getScheme())) {

				int sdkVersion = Build.VERSION.SDK_INT;
				if (sdkVersion >= 19) { // api >= 19
					return getRealPathFromUriAboveApi19(context, uri);
				} else { // api < 19
					return getRealPathFromUriBelowAPI19(context, uri);
				}
			} else if ("file".equalsIgnoreCase(uri.getScheme())) {
				return uri.getPath();
			}
			return null;
		}

		/**
		 *
		 * @param context -- context
		 * @param uri -- uri
		 * @return -- uri.path
		 */
		@SuppressLint("NewApi")
		private static String getRealPathFromUriAboveApi19(Context context, Uri uri) {
			String filePath = null;
			if (DocumentsContract.isDocumentUri(context, uri)) {
				String documentId = DocumentsContract.getDocumentId(uri);
				if (isMediaDocument(uri)) { // MediaProvider
					// split by ':'
					String type = documentId.split(":")[0];
					String id = documentId.split(":")[1];

					String selection = MediaStore.Images.Media._ID + "=?";
					String[] selectionArgs = {id};

					Uri contentUri = null;
					if ("image".equals(type)) {
						contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
					} else if ("video".equals(type)) {
						contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
					} else if ("audio".equals(type)) {
						contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
					}

					filePath = getDataColumn(context, contentUri, selection, selectionArgs);
				} else if (isDownloadsDocument(uri)) { // DownloadsProvider
					Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.parseLong(documentId));
					filePath = getDataColumn(context, contentUri, null, null);
				} else if (isExternalStorageDocument(uri)) {
					// ExternalStorageProvider
					final String docId = DocumentsContract.getDocumentId(uri);
					final String[] split = docId.split(":");
					final String type = split[0];
					if ("primary".equalsIgnoreCase(type)) {
						filePath = Environment.getExternalStorageDirectory() + "/" + split[1];
					}
				}
			} else if ("content".equalsIgnoreCase(uri.getScheme())) {
				filePath = getDataColumn(context, uri, null, null);
			} else if ("file".equals(uri.getScheme())) {
				filePath = uri.getPath();
			}
			return filePath;
		}

		/**
		 * @param context -- context
		 * @param uri -- uri
		 * @return -- uri.path
		 */
		private static String getRealPathFromUriBelowAPI19(Context context, Uri uri) {
			return getDataColumn(context, uri, null, null);
		}

		/**
		 * @return -- uri.path
		 */
		private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
			String path = null;

			String[] projection = new String[]{MediaStore.Images.Media.DATA};
			Cursor cursor = null;
			try {
				cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
				if (cursor != null && cursor.moveToFirst()) {
					int columnIndex = cursor.getColumnIndexOrThrow(projection[0]);
					path = cursor.getString(columnIndex);
				}
			} catch (Exception e) {
				if (cursor != null) {
					cursor.close();
				}
			}
			return path;
		}

		/**
		 * @param uri the Uri to check
		 * @return Whether the Uri authority is MediaProvider
		 */
		private static boolean isMediaDocument(Uri uri) {
			return "com.android.providers.media.documents".equals(uri.getAuthority());
		}

		private static boolean isExternalStorageDocument(Uri uri) {
			return "com.android.externalstorage.documents".equals(uri.getAuthority());
		}

		/**
		 * @param uri the Uri to check
		 * @return Whether the Uri authority is DownloadsProvider
		 */
		private static boolean isDownloadsDocument(Uri uri) {
			return "com.android.providers.downloads.documents".equals(uri.getAuthority());
		}
	}
}
