package com.digiaccess;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Base local DIGIACCESS del PDA (archivo digiaccess.db, separado de la base de rostros).
 *  - autorizados: copia de las autorizaciones vigentes (sin fotos) para validar sin conexión.
 *  - marcas_pendientes: ingresos/salidas hechos sin conexión, se suben con pda_sync.php.
 *  - meta: fecha de la última sincronización.
 */
public class LocalStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "digiaccess.db";
    private static final int DB_VERSION = 1;
    private static LocalStore instance;

    /** Formato de fechas igual al que usa validar_acceso.php: Y/m/d H:i:s */
    public static final String FMT_FECHA = "yyyy/MM/dd HH:mm:ss";

    public static synchronized LocalStore get(Context ctx) {
        if (instance == null) instance = new LocalStore(ctx.getApplicationContext());
        return instance;
    }

    private LocalStore(Context ctx) {
        super(ctx, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE autorizados (identificacion TEXT PRIMARY KEY, nombre TEXT, iddpto TEXT, tipo TEXT, "
                + "dias TEXT, vehicular TEXT, fechainicio TEXT, fechaterminacion TEXT)");
        db.execSQL("CREATE TABLE marcas_pendientes (id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT, identificacion TEXT, "
                + "nombre TEXT, fecha TEXT, tipo TEXT, punto TEXT, vehicular TEXT, medio TEXT)");
        db.execSQL("CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    // ------------------------------------------------------------------ autorizados

    public static class Autorizado {
        public String identificacion, nombre, iddpto, tipo, dias, vehicular, fechainicio, fechaterminacion;
    }

    /** Reemplaza toda la lista de autorizados (se llama al sincronizar). */
    public synchronized void reemplazarAutorizados(List<Autorizado> lista) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("autorizados", null, null);
            for (Autorizado a : lista) {
                // Si viene más de una autorización para la misma persona se queda la que termina más tarde
                Autorizado actual = buscarAutorizado(db, a.identificacion);
                if (actual != null && actual.fechaterminacion != null && a.fechaterminacion != null
                        && normalizarFecha(actual.fechaterminacion).compareTo(normalizarFecha(a.fechaterminacion)) >= 0)
                    continue;
                ContentValues v = new ContentValues();
                v.put("identificacion", a.identificacion);
                v.put("nombre", a.nombre);
                v.put("iddpto", a.iddpto);
                v.put("tipo", a.tipo);
                v.put("dias", a.dias);
                v.put("vehicular", a.vehicular);
                v.put("fechainicio", normalizarFecha(a.fechainicio));
                v.put("fechaterminacion", normalizarFecha(a.fechaterminacion));
                db.insertWithOnConflict("autorizados", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public synchronized Autorizado buscarAutorizado(String identificacion) {
        return buscarAutorizado(getReadableDatabase(), identificacion);
    }

    private Autorizado buscarAutorizado(SQLiteDatabase db, String identificacion) {
        try (Cursor c = db.query("autorizados", null, "identificacion = ?", new String[]{identificacion}, null, null, null)) {
            if (!c.moveToFirst()) return null;
            Autorizado a = new Autorizado();
            a.identificacion = c.getString(c.getColumnIndexOrThrow("identificacion"));
            a.nombre = c.getString(c.getColumnIndexOrThrow("nombre"));
            a.iddpto = c.getString(c.getColumnIndexOrThrow("iddpto"));
            a.tipo = c.getString(c.getColumnIndexOrThrow("tipo"));
            a.dias = c.getString(c.getColumnIndexOrThrow("dias"));
            a.vehicular = c.getString(c.getColumnIndexOrThrow("vehicular"));
            a.fechainicio = c.getString(c.getColumnIndexOrThrow("fechainicio"));
            a.fechaterminacion = c.getString(c.getColumnIndexOrThrow("fechaterminacion"));
            return a;
        }
    }

    public synchronized Set<String> identificacionesAutorizadas() {
        Set<String> s = new HashSet<>();
        try (Cursor c = getReadableDatabase().query("autorizados", new String[]{"identificacion"}, null, null, null, null, null)) {
            while (c.moveToNext()) s.add(c.getString(0));
        }
        return s;
    }

    public synchronized int contarAutorizados() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM autorizados", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /**
     * Decisión sin conexión, con las mismas reglas de validar_acceso.php que se pueden aplicar en el PDA:
     * vigencia, días de la semana y permiso vehicular. (Lista negra: el servidor no envía a quienes están en ella.)
     * El antipassback no se puede verificar sin conexión.
     * @return null si está permitido, o el motivo del rechazo.
     */
    public String motivoRechazoSinConexion(String identificacion, String carril) {
        Autorizado a = buscarAutorizado(identificacion);
        if (a == null) return "NO_REGISTRADO";
        String ahora = ahora();
        if (a.fechainicio != null && !a.fechainicio.isEmpty() && a.fechainicio.compareTo(ahora) > 0) return "NO_REGISTRADO";
        if (a.fechaterminacion != null && !a.fechaterminacion.isEmpty() && a.fechaterminacion.compareTo(ahora) < 0) return "NO_REGISTRADO";
        int dia = diaSemana();
        String dias = a.dias == null ? "" : a.dias;
        boolean fuera = (dias.equals("lunesaviernes") && !(dia >= 1 && dia <= 5))
                || (dias.equals("lunesasabado") && !(dia >= 1 && dia <= 6))
                || (dias.equals("findesemana") && !(dia == 6 || dia == 7));
        if (fuera) return "FUERA_DIA";
        if (Config.VEHICULAR.equals(carril)) {
            String v = a.vehicular == null ? "" : a.vehicular.toUpperCase(Locale.US);
            if (!(v.equals("SI") || v.equals("BICICLETA") || v.equals("MOTO"))) return "SIN_VEHICULAR";
        }
        return null;
    }

    // ------------------------------------------------------------------ marcas pendientes

    public static class Marca {
        public long id;
        public String uid, identificacion, nombre, fecha, tipo, punto, vehicular, medio;
    }

    public synchronized void agregarMarca(String identificacion, String nombre, String tipo, String punto, String vehicular, String medio) {
        ContentValues v = new ContentValues();
        v.put("uid", UUID.randomUUID().toString());
        v.put("identificacion", identificacion);
        v.put("nombre", nombre);
        v.put("fecha", ahora());
        v.put("tipo", tipo);
        v.put("punto", punto);
        v.put("vehicular", vehicular);
        v.put("medio", medio);
        getWritableDatabase().insert("marcas_pendientes", null, v);
    }

    public synchronized List<Marca> marcasPendientes(int limite) {
        List<Marca> l = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("marcas_pendientes", null, null, null, null, null, "id", String.valueOf(limite))) {
            while (c.moveToNext()) {
                Marca m = new Marca();
                m.id = c.getLong(c.getColumnIndexOrThrow("id"));
                m.uid = c.getString(c.getColumnIndexOrThrow("uid"));
                m.identificacion = c.getString(c.getColumnIndexOrThrow("identificacion"));
                m.nombre = c.getString(c.getColumnIndexOrThrow("nombre"));
                m.fecha = c.getString(c.getColumnIndexOrThrow("fecha"));
                m.tipo = c.getString(c.getColumnIndexOrThrow("tipo"));
                m.punto = c.getString(c.getColumnIndexOrThrow("punto"));
                m.vehicular = c.getString(c.getColumnIndexOrThrow("vehicular"));
                m.medio = c.getString(c.getColumnIndexOrThrow("medio"));
                l.add(m);
            }
        }
        return l;
    }

    public synchronized void borrarMarcas(Set<String> uids) {
        SQLiteDatabase db = getWritableDatabase();
        for (String u : uids) db.delete("marcas_pendientes", "uid = ?", new String[]{u});
    }

    public synchronized int contarMarcasPendientes() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM marcas_pendientes", null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    // ------------------------------------------------------------------ meta

    public synchronized void setMeta(String k, String v) {
        ContentValues cv = new ContentValues();
        cv.put("k", k);
        cv.put("v", v);
        getWritableDatabase().insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized String getMeta(String k) {
        try (Cursor c = getReadableDatabase().query("meta", new String[]{"v"}, "k = ?", new String[]{k}, null, null, null)) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    // ------------------------------------------------------------------ fechas

    public static String ahora() {
        return new SimpleDateFormat(FMT_FECHA, Locale.US).format(new Date());
    }

    /** 1 = lunes ... 7 = domingo (igual que date('N') en PHP). */
    static int diaSemana() {
        int d = Calendar.getInstance().get(Calendar.DAY_OF_WEEK); // 1 = domingo
        return d == Calendar.SUNDAY ? 7 : d - 1;
    }

    /** Lleva "2026-11-13 08:00:00" o "2026/11/13 08:00:00" al formato yyyy/MM/dd HH:mm:ss para poder comparar como texto. */
    static String normalizarFecha(String f) {
        if (f == null) return "";
        String s = f.trim().replace('-', '/').replace('T', ' ');
        if (s.length() == 10) s += " 00:00:00";
        if (s.length() > 19) s = s.substring(0, 19);
        return s;
    }
}
