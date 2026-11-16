package com.FaceVerifyM8;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import com.db.UserBean;
import com.db.UserDB;
import com.idfacesdk.IdFaceSdk;

public class UsersActivity extends AppCompatActivity implements ListDoubleClickListen {
	private RecyclerView lvUser;
	private UserAdapter adapterUser = null;

	private int  nUserTotalCount = 0; // rostros visibles (con el filtro del buscador aplicado)
	private boolean bRemoveAll = false;
	private String strFiltro = "";
	private TextView txtSinResultados;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.users);
		com.digiaccess.Encabezado.configurar(this, R.string.title_users);

		// Initialize UI controls
		lvUser = findViewById(R.id.lvUser);
		lvUser.setLayoutManager(new GridLayoutManager(this, 2)); // 2 objects every row

		// Buscador: filtra la lista mientras se escribe (nombre o cédula, sin importar mayúsculas ni tildes)
		EditText txtBuscar = findViewById(R.id.txtBuscar);
		txtSinResultados = findViewById(R.id.txtSinResultados);
		txtBuscar.addTextChangedListener(new android.text.TextWatcher() {
			@Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
			@Override public void onTextChanged(CharSequence cs, int a, int b, int c) { }
			@Override public void afterTextChanged(android.text.Editable e) {
				strFiltro = normalizar(e.toString());
				updateUsers();
			}
		});
		txtBuscar.setOnEditorActionListener((v, actionId, event) -> {
			// "Buscar" en el teclado: solo lo esconde, el filtro ya está aplicado
			android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
			if(imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
			return true;
		});
		Button btnAdd = findViewById(R.id.btnAdd);
		Button btnEdit = findViewById(R.id.btnEdit);
		Button btnRemove = findViewById(R.id.btnRemove);
		Button btnClear = findViewById(R.id.btnClear);

		btnAdd.setOnClickListener(arg0-> {
			if(MainActivity.nUserNum < UserDB.MAX_USER) {
				Intent intent = new Intent(this, UserActivity.class);
				UserActivity.uid = 0;
				this.startActivity(intent);
			} else MainActivity.ShowDialog(this, "Nuevo rostro", "Se superó el límite de rostros: " + UserDB.MAX_USER, 0x01, null);
		});

		btnEdit.setOnClickListener(arg0-> {
			if(nUserTotalCount <= 0) MainActivity.ShowDialog(this, "Rostro", "No hay rostros para editar.", 0x01, null);
			else {
				int nUserSelCount = adapterUser.getSelectCount();
				if(nUserSelCount <= 0) MainActivity.ShowDialog(this, "Editar rostro seleccionado", "No ha seleccionado un rostro para editar.", 0x01, null);
				else if(nUserSelCount > 1) MainActivity.ShowDialog(this, "Editar rostro seleccionado", "Seleccione un solo rostro para editar.", 0x01, null);
				else {
					ListUnit unit = null;
					for(int i = 0; i < nUserTotalCount; i++) {
						if(adapterUser.isSelectItem(i)) {
							unit = adapterUser.getItem(i);
							break;
						}
					}

					if(unit != null) {
						long uid = unit.getId();

						// Edit user
						Intent intent = new Intent(this, UserActivity.class);
						UserActivity.uid = uid;
						this.startActivity(intent);
					}
				}
			}
		});

		btnRemove.setOnClickListener(arg0-> {
			bRemoveAll = false;
			if(nUserTotalCount <= 0) MainActivity.ShowDialog(this, "Eliminar rostros seleccionados", "No hay rostros para eliminar.", 0x01, null);
			else {
				int nUserSelCount = adapterUser.getSelectCount();
				if(nUserSelCount <= 0) MainActivity.ShowDialog(this, "Eliminar rostros seleccionados", "No ha seleccionado rostros para eliminar.", 0x01, null);
				else {
					ListUnit unit = null;
					if(nUserSelCount == 1) {
						for(int i = 0; i < nUserTotalCount; i++) {
							if(adapterUser.isSelectItem(i)) {
								unit = adapterUser.getItem(i);
								break;
							}
						}
					}

					if(nUserSelCount == 1 && unit != null) MainActivity.ShowDialog(this, "Eliminar rostros seleccionados", "¿Eliminar el rostro (" + unit.getWorkNo() + ", " + unit.getName() + ") ?", 0x03, funcRemoveUserSure);
					else if(nUserSelCount == MainActivity.nUserNum) MainActivity.ShowDialog(this, "Eliminar rostros seleccionados", "¿Eliminar TODOS los rostros del PDA?", 0x03, funcRemoveUserSure);
					else MainActivity.ShowDialog(this, "Eliminar rostros seleccionados", "¿Eliminar todos los rostros seleccionados?", 0x03, funcRemoveUserSure);
				}
			}
		});

		btnClear.setOnClickListener(arg0-> {
			// clear all users ?
			bRemoveAll = true;
			if(MainActivity.nUserNum <= 0) MainActivity.ShowDialog(this, "Borrar todos los rostros", "No hay rostros para eliminar.", 0x01, null);
			else {
				ListUnit unit = null;
				if(MainActivity.nUserNum == 1 && nUserTotalCount == 1) unit = adapterUser.getItem(0);
				if(unit != null) MainActivity.ShowDialog(this, "Borrar todos los rostros", "¿Eliminar el rostro (" + unit.getWorkNo() + ", " + unit.getName() + ") ?", 0x03, funcRemoveUserSure);
				else MainActivity.ShowDialog(this, "Borrar todos los rostros", "¿Eliminar TODOS los rostros del PDA?", 0x03, funcRemoveUserSure);
			}
		});
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		updateUsers();
	}

	private void updateUsers() {
		List<ListUnit> listUser = new ArrayList<>();
		int i, n = MainActivity.nUserNum;
		for(i = 0; i < n; i++) {
			UserBean user = MainActivity.userList.get(i);
			if(!strFiltro.isEmpty()
					&& !normalizar(user.uname).contains(strFiltro)
					&& !normalizar(user.workno).replace(".", "").contains(strFiltro.replace(".", "").replace(" ", ""))) continue;

			ListUnit unit = new ListUnit();
			unit.setId(user.uid);
			unit.setName(user.uname);
			unit.setWorkNo(user.workno);
			unit.setPhoto(user.photoFileName);
			listUser.add(unit);
		}

		adapterUser = new UserAdapter(listUser, this);
		lvUser.setAdapter(adapterUser);

		if(adapterUser != null) nUserTotalCount = adapterUser.getItemCount();
		else nUserTotalCount = 0;

		if(txtSinResultados != null) {
			if(n == 0) {
				txtSinResultados.setText("No hay rostros enrolados.");
				txtSinResultados.setVisibility(View.VISIBLE);
			} else if(nUserTotalCount == 0) {
				txtSinResultados.setText("Ningún rostro coincide con la búsqueda.");
				txtSinResultados.setVisibility(View.VISIBLE);
			} else if(!strFiltro.isEmpty()) {
				txtSinResultados.setText(nUserTotalCount + " de " + n + (n > 1 ? " rostros" : " rostro"));
				txtSinResultados.setVisibility(View.VISIBLE);
			} else txtSinResultados.setVisibility(View.GONE);
		}
	}

	/** Minúsculas y sin tildes, para que "jose" encuentre "José". */
	private static String normalizar(String s) {
		if(s == null) return "";
		String t = java.text.Normalizer.normalize(s.trim().toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD);
		return t.replaceAll("\\p{M}+", "");
	}

	DialogInterface.OnClickListener funcRemoveUserSure = (dialogInterface, which) -> {
		if(which == android.content.DialogInterface.BUTTON_POSITIVE) synchronized (com.digiaccess.FaceStore.LOCK) {
			int i, j, k, n = nUserTotalCount;
			if(bRemoveAll) {
				IdFaceSdk.ListClearAll(MainActivity.hFeatureList);

				MainActivity.db.clearUser();
				MainActivity.db.clearRecord(0, null, null, null);

				MainActivity.userList.clear();
				MainActivity.nUserNum = 0;

				adapterUser.clear();
				nUserTotalCount = 0;
			} else for(i = 0; i < n; i++) {
				if(adapterUser.isSelectItem(i)) {
					ListUnit unit = adapterUser.getItem(i);
					long uid = unit.getId();
					if(uid != 0) {
						for(j = 0; j < MainActivity.userList.size(); j++) {
							UserBean user = MainActivity.userList.get(j);
							if(user.uid == uid) {
								// remove user include photos from Database
								MainActivity.db.deleteUser(user);

								// remove user feature from feature compare list
								int pos = user.featurePos;
								if(pos != -1) {
									IdFaceSdk.ListRemove(MainActivity.hFeatureList, pos, 1);

									for(k = 0; k < MainActivity.userList.size(); k++) {
										UserBean u = MainActivity.userList.get(k);
										if(u.featurePos > pos) {
											u.featurePos--;
											MainActivity.userList.set(k, u);
										}
									}
								}

								// remove user from list and update the user count
								MainActivity.userList.remove(user);
								MainActivity.nUserNum--;
								nUserTotalCount--;

								adapterUser.removeItem(i);
								i--;
								n--;

								break;
							}
						}
					}
				}
			}
			updateUsers(); // refresca la lista y el contador del buscador
		}
	};

	@Override
	public void OnListClick(int position)
	{
		// Nothing to do
	}

	@Override
	public void onListDoubleClick(int position)
	{
		ListUnit unit = adapterUser.getItem(position);
		long uid = unit.getId();

		// Edit user
		Intent intent = new Intent(this, UserActivity.class);
		UserActivity.uid = uid;
		this.startActivity(intent);
	}

	/////////////////////////////////////////////////////////////////////////////////////////

	static class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {
		private static final int CLICK_DELAY = 300; // delay some milliseconds
		private final Handler handler = new Handler();
		private Runnable singleClickRunnable;
		private int clickType = 0;
		private ListDoubleClickListen clickCallback = null;

		private final List<ListUnit> list;
		private final boolean[] selList;
		private int nSelectCount;
		private int nTotalCount;

		public int getSelectCount() { return nSelectCount; }
		public boolean isSelectItem(int nItem) { if(nItem >= 0 && nItem < nTotalCount) return selList[nItem]; return false; }
		public void selectItem(int nItem) {
			if(nItem >= 0 && nItem < nTotalCount) {
				if(!selList[nItem]) {
					selList[nItem] = true;
					nSelectCount++;
				}
			}
		}

		public UserAdapter(List<ListUnit> datalist, ListDoubleClickListen listClickCallback) {
			list = datalist;
			clickCallback = listClickCallback;

			nTotalCount = getItemCount();
			selList = new boolean[nTotalCount];
			nSelectCount = 0;
		}

		@NonNull
		@Override
		public UserAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
			return new ViewHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull UserAdapter.ViewHolder holder, int position) {
			ListUnit item = list.get(position);

			Bitmap bitmap = null;
			try {
				bitmap = BitmapFactory.decodeFile(item.getPhoto());
			} catch(Exception ignored) { }
			RecordsActivity.marcador(holder.imgPhoto, bitmap, R.drawable.ic_da_usuario);
			holder.txtName.setText(item.getName());
			holder.txtNo.setText("C.C. " + item.getWorkNo());
			holder.itemView.setBackgroundResource(selList[position] ? R.drawable.da_item_sel : R.drawable.da_item);

			holder.itemView.setOnClickListener(v -> {
				clickType++;
				if(clickType == 1) {
					singleClickRunnable = () -> {
						if(clickType == 1) {
							clickType = 0; // reset click type

							// call click callback
							clickCallback.OnListClick(position);

							// revert select state
							selList[position] = !selList[position];
							if(selList[position]) nSelectCount++;
							else nSelectCount--;

							v.setBackgroundResource(selList[position] ? R.drawable.da_item_sel : R.drawable.da_item);
						}
					};
					handler.postDelayed(singleClickRunnable, CLICK_DELAY);
				} else if(clickType == 2) {
					handler.removeCallbacks(singleClickRunnable);
					clickType = 0; // reset click type

					// call double-click callback
					clickCallback.onListDoubleClick(position);
				}
			});
		}

		@Override
		public int getItemCount() {
			return list == null ? 0 : list.size();
		}

		public ListUnit getItem(int position) {
			return list.get(position);
		}

		public void removeItem(int position) {
			if(position < nTotalCount) {
				nTotalCount--;
				if(selList[position]) nSelectCount--;
				for(int i = position; i < nTotalCount; i++) selList[i] = selList[i + 1];
				list.remove(position);

				notifyItemRemoved(position);
			}
		}

		@SuppressLint("NotifyDataSetChanged")
		public void clear() {
			for(int i = 0; i < nTotalCount; i++) selList[i] = false;
			list.clear();
			nTotalCount = 0;

			notifyDataSetChanged();
		}

		public static class ViewHolder extends RecyclerView.ViewHolder {
			TextView txtName, txtNo;
			ImageView imgPhoto;
			public ViewHolder(View itemView) {
				super(itemView);
				txtName = itemView.findViewById(R.id.txtName);
				txtNo = itemView.findViewById(R.id.txtNo);
				imgPhoto = itemView.findViewById(R.id.imgPhoto);
			}
		}
	}
}
