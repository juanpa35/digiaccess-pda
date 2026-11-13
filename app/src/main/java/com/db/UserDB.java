package com.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

@SuppressWarnings("unused")

public class UserDB extends SQLiteOpenHelper {
	private static final String TAG = "TagUserDB";

	public static final int MAX_USER = 10000;

	// version number to upgrade database version. each time if you Add, Edit table, you need to change the version number.
	private static final int DATABASE_UPDATE = 1;

	private SQLiteDatabase db;
	private final String strDbName;

	private int nUserNum = 0, nRecordNum = 0;

	// Database file saved on direcotry “/data/.../com.xxx(package name)/xxx”
	public UserDB(Context context, String strDatabaseName)
	{
		super(context, strDatabaseName, null, DATABASE_UPDATE);
		db = null;
		strDbName = context.getDatabasePath(strDatabaseName).getAbsolutePath();
		if(!OpenDB()) Log.d(TAG, "Open DB " + strDbName + " fail");
	}

	// Database file saved on directory by param strDatabasePath
	public UserDB(Context context, String strDatabaseName, String strDatabasePath)
	{
		super(new CustomPathDatabaseContext(context, strDatabasePath), strDatabaseName, null, DATABASE_UPDATE);
		db = null;
		CustomPathDatabaseContext ctxCustom = new CustomPathDatabaseContext(context, strDatabasePath);
		strDbName = ctxCustom.getDatabasePath(strDatabaseName).toString();
		if(!OpenDB()) Log.d(TAG, "Open DB " + strDbName + " fail");
	}

	@Override
	public void finalize()
	{
		CloseDB();
	}

	@Override
	public void onCreate(SQLiteDatabase dbase)
	{
		// Create table user
		String sql = "CREATE TABLE user ("
			+ "uid INTEGER PRIMARY KEY AUTOINCREMENT, "
			+ "uname TEXT DEFAULT '', "
			+ "workno TEXT DEFAULT '', "
			+ "card TEXT DEFAULT '', "
			+ "photoFileName TEXT DEFAULT '', "
			+ "feature BLOB, "
			+ "createtime TIMESTAMP DEFAULT (DATETIME('now', 'localtime')))";
		dbase.execSQL(sql);

		// Ceate table record
		sql = "CREATE TABLE record ("
             + "rid INTEGER PRIMARY KEY AUTOINCREMENT, "
			 + "uid INTEGER DEFAULT 0, "
             + "uname TEXT DEFAULT '', "
             + "workno TEXT DEFAULT '', "
             + "photoFileName TEXT DEFAULT '', "
			 + "pictureFileName TEXT DEFAULT '', "
             + "status INTEGER DEFAULT 0, "
			 + "score INTEGER DEFAULT 0, "
             + "recordtime TIMESTAMP DEFAULT (DATETIME('now', 'localtime')))";
		dbase.execSQL(sql);

		Log.d(TAG, "CREATE database " + strDbName + ".");
	}

	@Override
	public void onUpgrade(SQLiteDatabase dbase, int oldVersion, int newVersion)
	{
		//
	}

	public boolean IsOpen()
	{
		return (db != null);
	}

	public boolean OpenDB()
	{
		if(db == null) {
			nUserNum = nRecordNum = 0;
			db = getWritableDatabase();
			if (db != null) {
				Cursor cursor = db.rawQuery("SELECT COUNT(uid) FROM user", null);
				if (cursor.moveToFirst()) {
					nUserNum = cursor.getInt(0);
				}
				cursor.close();

				cursor = db.rawQuery("SELECT COUNT(uid) FROM record", null);
				if (cursor.moveToFirst()) {
					nRecordNum = cursor.getInt(0);
				}
				cursor.close();
			}
		}

		return IsOpen();
	}

	public void CloseDB()
	{
		if(db != null) {
			db.close();
			db = null;
		}
	}

	// Clear tables
	public void ClearDB()
	{
		boolean b = IsOpen();
		if(!b) OpenDB();
		if(db != null) {
			db.execSQL("DELETE FROM user");
			db.execSQL("DELETE FROM record");
		}
		if(!b) CloseDB();
		nUserNum = nRecordNum = 0;
	}

	// Remove Database
	public void RemoveDB()
	{
		File dbFile = new File(strDbName);
		CloseDB();
		if(dbFile.delete()) Log.d(TAG, "REMOVE database " + strDbName + ".");
		nUserNum = nRecordNum = 0;
	}

	// Get database full-path
	public String GetPath()
	{
		File dbFile = new File(strDbName);
		return dbFile.getParent();
	}

	// Get user count
	public int getUserCount()
	{
		return nUserNum;
	}

	// Insert a user into database
	public long insertUser(UserBean user)
	{
		long uid = 0;
		if(db != null && nUserNum < MAX_USER) {
			ContentValues values = new ContentValues();
			if(user.uid != 0) values.put("uid", user.uid);
			if(!user.uname.isEmpty()) values.put("uname", user.uname);
			if(!user.workno.isEmpty()) values.put("workno", user.workno);
			if(!user.card.isEmpty()) values.put("card", user.card);
			if(!user.photoFileName.isEmpty()) values.put("photoFileName", user.photoFileName);
			values.put("feature", user.feature);
			uid = db.insert("user", null, values);
			if(uid > 0) {
				if(user.uid == 0) user.uid = uid;
				else uid = user.uid;
				nUserNum++;
			}
		}
		return uid;
	}

	public long updateUser(UserBean user)
	{
		long uid = 0;
		if(db != null && user != null && user.uid != 0) {
			ContentValues values = new ContentValues();
			values.put("uid", user.uid);
			if(!user.uname.isEmpty()) values.put("uname", user.uname);
			values.put("workno", user.workno);
			values.put("card", user.card);
			values.put("photoFileName", user.photoFileName);
			values.put("feature", user.feature);
			uid = db.update("user", values, "uid=?", new String[]{String.valueOf(user.uid)});
			if(uid >= 0) uid = user.uid;
		}
		return uid;
	}

	public void deleteUser(long uid)
	{
		if(db != null) {
			UserBean user = getUser(uid);
			if(user != null) {
				if(!user.photoFileName.isEmpty()) {
					try {
						File f = new File(user.photoFileName);
						if (f.exists()) if (!f.delete()) Log.d(TAG, "Remove user photo file fail");
					} catch (Exception ignored) { }
				}
				db.delete("user", "uid=?", new String[]{String.valueOf(user.uid)});
				clearRecord(user.uid, null, null, null);
				nUserNum--;
			}
		}
	}

	public void deleteUser(UserBean user)
	{
		if(db != null && user != null) {
			if(!user.photoFileName.isEmpty()) {
				try {
					File f = new File(user.photoFileName);
					if (f.exists()) if (!f.delete()) Log.d(TAG, "Remove user photo file fail");
				} catch (Exception ignored) { }
			}
			db.delete("user", "uid=?", new String[]{String.valueOf(user.uid)});
			clearRecord(user.uid, null, null, null);
			nUserNum--;
		}
	}

	public void clearUser()
	{
		if(db != null) {
			String sql = "SELECT photoFileName from user";
			Cursor cursor = db.rawQuery(sql, null);
			if(cursor.moveToFirst()) {
				do {
					String photoFileName = cursor.getString(0);
					if(!photoFileName.isEmpty()) {
						try {
							File f = new File(photoFileName);
							if(f.exists()) if (!f.delete()) Log.d(TAG, "Remove user photo file " + photoFileName + " fail");
						} catch (Exception ignored) { }
					}
				} while (cursor.moveToNext());
			}
			cursor.close();

			db.execSQL("DELETE FROM user");
			nUserNum = 0;
		}
	}

	public UserBean getUser(long uid)
	{
		UserBean user = null;
		if(db != null && uid != 0) {
			Cursor cursor = db.rawQuery("SELECT uid, uname, workno, card, photoFileName, feature FROM user WHERE uid=? LIMIT 1", new String[] { String.valueOf(uid) });
			if (cursor.moveToFirst()) {
				int i = 0;
				user = new UserBean();
				user.uid = cursor.getLong(i++);
				user.uname = cursor.getString(i++);
				user.workno = cursor.getString(i++);
				user.card = cursor.getString(i++);
				user.photoFileName = cursor.getString(i++);
				user.feature = cursor.getBlob(i);
			}
			cursor.close();
		}
		return user;
	}

	public ArrayList<UserBean> getUserList()
	{
		ArrayList<UserBean> userBeanList = null;
		if(db != null) {
			userBeanList = new ArrayList<>();

			String sql = "SELECT uid, uname, workno, card, photoFileName, feature FROM user ORDER BY createtime DESC LIMIT " + MAX_USER;
			Cursor cursor = db.rawQuery(sql, null);
			if (cursor.moveToFirst()) {
				do {
					int i = 0;
					UserBean user = new UserBean();
					user.uid = cursor.getLong(i++);
					user.uname = cursor.getString(i++);
					user.workno = cursor.getString(i++);
					user.card = cursor.getString(i++);
					user.photoFileName = cursor.getString(i++);
					user.feature = cursor.getBlob(i);
					userBeanList.add(user);
				} while (cursor.moveToNext());
			}
			cursor.close();
		}
		return userBeanList;
	}

	public int getRecordCount()
	{
		return nRecordNum;
	}

	public long insertRecord(RecordBean record)
	{
		long rid = 0;
		if(db != null) {
			ContentValues values = new ContentValues();
			if(record.rid != 0) values.put("uid", record.rid);
			if(record.uid != 0) values.put("uid", record.uid);
			if(!record.uname.isEmpty()) values.put("uname", record.uname);
			if(!record.workno.isEmpty()) values.put("workno", record.workno);
			values.put("status", Integer.toString(record.stat));
			values.put("score", Integer.toString(record.score));
			if(!record.photoFileName.isEmpty()) values.put("photoFileName", record.photoFileName);
			if(!record.pictureFileName.isEmpty()) values.put("pictureFileName", record.pictureFileName);

			rid = db.insert("record", null, values);
			if(rid > 0) {
				if(record.rid == 0) record.rid = rid;
				else rid = record.rid;
				nRecordNum++;
			}
		}
		return rid;
	}

	public void clearRecord(long uid, String workno, String fromTime, String toTime)
	{
		if(db != null) {
			String strWhere = "";
			if(workno == null) workno = "";
			if(fromTime == null) fromTime = "";
			if(toTime == null) toTime = "";
			if(uid > 0 || !workno.isEmpty() || !fromTime.isEmpty() || !toTime.isEmpty()) {
				boolean bWhere = false;
				strWhere += " where ";
				if(uid > 0) {
					strWhere += "uid = " + uid;
					bWhere = true;
				}
				if(!workno.isEmpty()) {
					if(bWhere) strWhere += " and ";
					else bWhere = true;
					strWhere += "worno = ''" + workno + "'";
				}
				if(!fromTime.isEmpty()) {
					if(bWhere) strWhere += " and ";
					else bWhere = true;
					strWhere += "recordTime >= ''" + fromTime + "'";
				}
				if(!toTime.isEmpty()) {
					if(bWhere) strWhere += " and ";
					else bWhere = true;
					strWhere += "recordTime <= '" + toTime + "'";
				}
			}

			String sql = "SELECT pictureFileName from record" + strWhere;
			Cursor cursor = db.rawQuery(sql, null);
			if(cursor.moveToFirst()) {
				do {
					String pictureFileName = cursor.getString(0);
					if(!pictureFileName.isEmpty()) {
						try {
							File f = new File(pictureFileName);
							if(f.exists()) if (!f.delete()) Log.d(TAG, "Remove record picture file " + pictureFileName + " fail");
						} catch (Exception ignored) { }
					}
					nRecordNum--;
				} while (cursor.moveToNext());
			}
			cursor.close();

			db.execSQL("DELETE FROM record" + strWhere);
		}
	}

	public RecordBean getRecord(long rid)
	{
		RecordBean record = null;
		if(db != null && rid != 0) {
			Cursor cursor = db.rawQuery("SELECT rid, uid, uname, workno, recordtime, status, score, photoFileName, pictureFileName from record WHERE rid=? LIMIT 1", new String[] { String.valueOf(rid) });
			if (cursor.moveToFirst()) {
				int i = 0;
				record = new RecordBean();
				record.rid = cursor.getLong(i++);
				record.uid = cursor.getLong(i++);
				record.uname = cursor.getString(i++);
				record.workno = cursor.getString(i++);
				record.tTime = cursor.getString(i++);;
				record.stat = cursor.getInt(i++);
				record.score = cursor.getInt(i++);
				record.photoFileName = cursor.getString(i++);
				record.pictureFileName = cursor.getString(i++);
			}
			cursor.close();
		}
		return record;
	}

	public ArrayList<RecordBean> getRecordList(String workno, String fromTime, String toTime)
	{
		ArrayList<RecordBean> recordBeanList = null;
		if(db != null) {
			recordBeanList = new ArrayList<>();

			if(workno == null) workno = "";
			if(fromTime == null) fromTime = "";
			if(toTime == null) toTime = "";

			String sql = "SELECT rid, uid, uname, workno, recordtime, status, score, photoFileName, pictureFileName from record";
			if(!workno.isEmpty() || !fromTime.isEmpty() || !toTime.isEmpty()) {
				boolean bWhere = false;
				sql += " where ";
				if(!workno.isEmpty()) {
					sql += "worno = ''" + workno + "'";
					bWhere = true;
				}
				if(!fromTime.isEmpty()) {
					if(bWhere) sql += " and ";
					sql += "recordTime >= ''" + fromTime + "'";
				}
				if(!toTime.isEmpty()) {
					if(bWhere) sql += " and ";
					sql += "recordTime <= '" + toTime + "'";
				}
			}
			sql += " ORDER BY recordtime DESC";

			Cursor cursor = db.rawQuery(sql, null);
			if(cursor.moveToFirst()) {
				do {
					int i = 0;
					RecordBean record = new RecordBean();
					record.rid = cursor.getLong(i++);
					record.uid = cursor.getLong(i++);
					record.uname = cursor.getString(i++);
					record.workno = cursor.getString(i++);
					record.tTime = cursor.getString(i++);;
					record.stat = cursor.getInt(i++);
					record.score = cursor.getInt(i++);
					record.photoFileName = cursor.getString(i++);
					record.pictureFileName = cursor.getString(i++);
					recordBeanList.add(record);
				} while (cursor.moveToNext());
			}
			cursor.close();
		}
		return recordBeanList;
	}
}

