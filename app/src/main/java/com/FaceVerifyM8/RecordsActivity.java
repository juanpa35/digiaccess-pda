package com.FaceVerifyM8;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import com.db.RecordBean;

public class RecordsActivity extends AppCompatActivity {
	private ListView lvRecord;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.records);
		com.digiaccess.Encabezado.configurar(this, R.string.title_records);

		// Initialize UI controls
		lvRecord = findViewById(R.id.lvRecord);
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		updateRecords();
	}

	private void updateRecords()
	{
		List<ListUnit> listRecord = new ArrayList<>();
		ArrayList<RecordBean> recordList = MainActivity.db.getRecordList(null, null, null);
		int i, n = recordList.size();
		for (i = 0; i < n; i++) {
			RecordBean record = recordList.get(i);
			ListUnit unit = new ListUnit();
			unit.setId(record.rid);
			unit.setWorkNo(record.workno);
			unit.setName(record.uname);
			unit.setPhoto(record.photoFileName);
			unit.setTime(record.tTime);
			unit.setStatus(record.stat);
			unit.setScore(record.score);
			unit.setPicture(record.pictureFileName);
			listRecord.add(unit);
		}

		RecordAdapter adapterRecord = new RecordAdapter(lvRecord, listRecord, this);
		lvRecord.setAdapter(adapterRecord);
	}

	////////////////////////////////////////////////////////////////////////////////////

	static class RecordAdapter extends BaseAdapter {
		private final List<ListUnit> list;
		private final LayoutInflater inflater;

		public RecordAdapter(ListView view, List<ListUnit> datalist, Context context) {
			inflater = LayoutInflater.from(context);
			list = datalist;

			view.setOnItemClickListener((parent, view1, position, id) -> {
				// click item at position
				notifyDataSetChanged();// refresh view
			});
		}

		@Override
		public int getCount() {
			return list == null ? 0 : list.size();
		}

		@Override
		public ListUnit getItem(int position) {
			return list.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@SuppressLint("SetTextI18n")
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			// Load layout to a view
			if (convertView == null)
				convertView = inflater.inflate(R.layout.item_record, parent, false);
			if (convertView != null) {
				// Estilo DIGIACCESS: colores en el layout item_record

				ListUnit record = getItem(position);
				String strWorkNo = record.getWorkNo(), strName = record.getName(), strTime = record.getTime(), strPhoto = record.getPhoto(), strPicture = record.getPicture();
				int nScore = record.getScore(), nStatus = record.getStatus();



				TextView no = convertView.findViewById(R.id.txtNo);
				no.setText(strWorkNo == null || strWorkNo.isEmpty() ? "—" : "C.C. " + strWorkNo);

				TextView name = convertView.findViewById(R.id.txtName);
				name.setText(strName == null || strName.isEmpty() ? "Sin identificar" : strName);

				TextView t = convertView.findViewById(R.id.txtTime);
				t.setText(strTime);

				TextView score = convertView.findViewById(R.id.txtScore);
				score.setText(nScore > 0 ? nScore + "%" : (strPicture == null || strPicture.isEmpty() ? "Cédula" : ""));

				ImageView photo = convertView.findViewById(R.id.imgPhoto);
				Bitmap bmpPhoto = null;
				try {
					bmpPhoto = BitmapFactory.decodeFile(strPhoto);
				} catch(Exception ignored) { }
				marcador(photo, bmpPhoto, R.drawable.ic_da_usuario);

				ImageView picture = convertView.findViewById(R.id.imgPicture);
				Bitmap bmpPicture = null;
				try {
					bmpPicture = BitmapFactory.decodeFile(strPicture);
				} catch(Exception ignored) { }
				marcador(picture, bmpPicture, R.drawable.ic_da_cedula);

				ImageView stat = convertView.findViewById(R.id.imgStat);
				stat.setImageResource(nStatus == 0 ? R.drawable.ic_da_check : R.drawable.ic_da_x);
				stat.setBackgroundResource(nStatus == 0 ? R.drawable.da_icbox_ok : R.drawable.da_icbox_error);
			}

			return convertView;
		}
	}

	/** Foto real, o un ícono gris centrado si no hay foto. */
	static void marcador(ImageView iv, Bitmap bmp, int icono) {
		if(android.os.Build.VERSION.SDK_INT >= 21) iv.setImageTintList(null);
		if(bmp != null) {
			iv.clearColorFilter();
			iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
			iv.setImageBitmap(bmp);
		} else {
			iv.setColorFilter(0xFF9CA3AF);
			iv.setScaleType(ImageView.ScaleType.CENTER);
			iv.setImageResource(icono);
		}
	}
}
