package com.db;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;

public class CustomPathDatabaseContext extends ContextWrapper
{
	private final Context ctxBase;
	private String strDatabasePath = null;

	public CustomPathDatabaseContext(Context base, String dbPath)
	{
		super(base);
		ctxBase = base;
		if(dbPath != null) if(!dbPath.isEmpty()) strDatabasePath = dbPath;
	}

	@Override
	public File getDatabasePath(String name)
	{
		File path;
		if(strDatabasePath != null) path = new File(strDatabasePath + File.separator + name);
		else path = new File(ctxBase.getDatabasePath(name).getAbsolutePath());
		if (!path.getParentFile().exists())
		{
			if(!path.getParentFile().mkdirs()) path = new File(name);
		}
		return path;
	}

	@Override
	public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory)
	{
		return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
	}

	@Override
	public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler errorHandler)
	{
		return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getAbsolutePath(), factory, errorHandler);
	}
}
