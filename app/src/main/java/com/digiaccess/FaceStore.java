package com.digiaccess;

import android.util.Base64;
import android.util.Log;

import com.FaceVerifyM8.MainActivity;
import com.db.UserBean;
import com.db.UserDB;
import com.idfacesdk.FACE_DETECT_RESULT;
import com.idfacesdk.IdFaceSdk;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Operaciones sobre los rostros enrolados en el PDA.
 *
 * IMPORTANTE: la posición de cada rostro en la lista de comparación del SDK debe coincidir con su
 * posición en MainActivity.userList. Toda modificación concurrente (sincronización en segundo plano)
 * y toda comparación deben hacerse dentro de synchronized (FaceStore.LOCK).
 */
public final class FaceStore {
    public static final Object LOCK = new Object();

    private FaceStore() { }

    /** Busca un rostro enrolado por número de cédula (campo workno). */
    public static UserBean buscarPorCedula(String cedula) {
        synchronized (LOCK) {
            if (MainActivity.userList == null) return null;
            for (UserBean u : MainActivity.userList) {
                if (u != null && cedula.equals(u.workno)) return u;
            }
        }
        return null;
    }

    /**
     * Borra del PDA los rostros cuya cédula no está en la lista de autorizados vigentes.
     * @return cantidad de rostros borrados
     */
    public static int eliminarNoAutorizados(Set<String> autorizados) {
        if (!MainActivity.bSdkInit || MainActivity.db == null) return 0;
        int borrados = 0;
        synchronized (LOCK) {
            List<UserBean> aBorrar = new ArrayList<>();
            for (UserBean u : MainActivity.userList) {
                if (u != null && !autorizados.contains(u.workno)) aBorrar.add(u);
            }
            for (UserBean u : aBorrar) {
                if (eliminar(u)) borrados++;
            }
        }
        return borrados;
    }

    // ------------------------------------------------------------------ rostros que llegan de yunatt

    /**
     * Enrola (o reemplaza) un rostro a partir de una foto JPG.
     * @return null si quedó enrolado, o el motivo del error
     */
    public static String enrolarFoto(byte[] jpg, String cedula, String nombre) {
        synchronized (LOCK) {            // una sola extracción a la vez con el SDK
            return enrolarFotoInterno(jpg, cedula, nombre);
        }
    }

    private static String enrolarFotoInterno(byte[] jpg, String cedula, String nombre) {
        if (!MainActivity.bSdkInit || MainActivity.db == null) return "reconocimiento facial no iniciado (licencia)";
        if (jpg == null || jpg.length < 100) return "foto vacía";
        if (cedula == null || cedula.trim().isEmpty()) return "sin número de identificación";
        cedula = cedula.trim();
        String archivo = MainActivity.strAppPath + "/photo" + System.currentTimeMillis() + ".jpg";
        try (FileOutputStream fo = new FileOutputStream(archivo)) {
            fo.write(jpg);
        } catch (Exception e) {
            return "no se pudo guardar la foto";
        }

        int[] w = new int[1], h = new int[1];
        if (IdFaceSdk.ReadImageFile(archivo, null, 0, w, h, 24) != 0 || w[0] <= 0 || h[0] <= 0) {
            new File(archivo).delete();
            return "foto inválida";
        }
        byte[] img = new byte[w[0] * h[0] * 3];
        if (IdFaceSdk.ReadImageFile(archivo, img, img.length, w, h, 24) != 0) {
            new File(archivo).delete();
            return "foto inválida";
        }
        FACE_DETECT_RESULT[] caras = new FACE_DETECT_RESULT[5];
        for (int i = 0; i < caras.length; i++) caras[i] = new FACE_DETECT_RESULT();
        int n = IdFaceSdk.DetectAllFaces(img, w[0], h[0], caras.length, caras);
        if (n < 1) {
            new File(archivo).delete();
            return "no se detectó un rostro en la foto";
        }
        // Si la foto trae varias caras se usa la más grande
        FACE_DETECT_RESULT cara = caras[0];
        for (int i = 1; i < n && i < caras.length; i++) {
            if ((caras[i].nRightEyeX - caras[i].nLeftEyeX) > (cara.nRightEyeX - cara.nLeftEyeX)) cara = caras[i];
        }
        byte[] fea = new byte[IdFaceSdk.FeatureSize()];
        if (IdFaceSdk.FeatureGet(img, w[0], h[0], cara, fea) != 0) {
            new File(archivo).delete();
            return "no se pudo extraer la plantilla facial";
        }

        synchronized (LOCK) {
            // Si ya existe esa cédula se reemplaza el rostro
            UserBean anterior = null;
            for (UserBean u : MainActivity.userList) {
                if (u != null && cedula.equals(u.workno)) { anterior = u; break; }
            }
            if (anterior != null) eliminar(anterior);
            if (MainActivity.nUserNum >= UserDB.MAX_USER) {
                new File(archivo).delete();
                return "se alcanzó el máximo de " + UserDB.MAX_USER + " rostros";
            }
            UserBean u = new UserBean();
            u.uname = nombre == null || nombre.trim().isEmpty() ? cedula : nombre.trim();
            u.workno = cedula;
            u.photoFileName = archivo;
            u.feature = fea;
            int[] pos = {-1};
            IdFaceSdk.ListInsert(MainActivity.hFeatureList, pos, fea, 1);
            if (pos[0] == -1) {
                new File(archivo).delete();
                return "no se pudo agregar a la lista de rostros";
            }
            u.featurePos = pos[0];
            if (MainActivity.db.insertUser(u) <= 0) {
                IdFaceSdk.ListRemove(MainActivity.hFeatureList, pos[0], 1);
                new File(archivo).delete();
                return "no se pudo guardar en la base de datos";
            }
            MainActivity.userList.add(u);
            MainActivity.nUserNum = MainActivity.userList.size();
        }
        Log.d("DigiFace", "Rostro enrolado desde yunatt: " + cedula);
        return null;
    }

    /** Elimina el rostro de una cédula. @return true si existía */
    public static boolean eliminarPorCedula(String cedula) {
        synchronized (LOCK) {
            for (UserBean u : new ArrayList<>(MainActivity.userList)) {
                if (u != null && cedula.equals(u.workno)) return eliminar(u);
            }
        }
        return false;
    }

    /** Borra todos los rostros del PDA. */
    public static int eliminarTodos() {
        int n = 0;
        synchronized (LOCK) {
            for (UserBean u : new ArrayList<>(MainActivity.userList)) {
                if (u != null && eliminar(u)) n++;
            }
        }
        return n;
    }

    /** Cambia el nombre de un rostro enrolado. */
    public static boolean renombrar(String cedula, String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) return false;
        synchronized (LOCK) {
            for (UserBean u : MainActivity.userList) {
                if (u != null && cedula.equals(u.workno)) {
                    u.uname = nombre.trim();
                    MainActivity.db.updateUser(u);
                    return true;
                }
            }
        }
        return false;
    }

    /** Cédulas enroladas, en orden. */
    public static List<String> cedulas() {
        List<String> l = new ArrayList<>();
        synchronized (LOCK) {
            for (UserBean u : MainActivity.userList) if (u != null) l.add(u.workno);
        }
        return l;
    }

    /** Foto enrolada en Base64 (sin saltos de línea), o null. */
    public static String fotoBase64(String cedula) {
        UserBean u = buscarPorCedula(cedula);
        if (u == null || u.photoFileName == null || u.photoFileName.isEmpty()) return null;
        File f = new File(u.photoFileName);
        if (!f.exists() || f.length() > 2_000_000) return null;
        byte[] b = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) {
            int off = 0, r;
            while (off < b.length && (r = in.read(b, off, b.length - off)) > 0) off += r;
        } catch (Exception e) {
            return null;
        }
        return Base64.encodeToString(b, Base64.NO_WRAP);
    }

    /** Misma lógica de borrado de UsersActivity: base de datos + lista del SDK + posiciones. Llamar con LOCK tomado. */
    private static boolean eliminar(UserBean user) {
        int idx = MainActivity.userList.indexOf(user);
        if (idx < 0) return false;
        MainActivity.db.deleteUser(user);
        int pos = user.featurePos;
        if (pos != -1) {
            IdFaceSdk.ListRemove(MainActivity.hFeatureList, pos, 1);
            for (int k = 0; k < MainActivity.userList.size(); k++) {
                UserBean u = MainActivity.userList.get(k);
                if (u.featurePos > pos) u.featurePos--;
            }
        }
        MainActivity.userList.remove(idx);
        MainActivity.nUserNum = MainActivity.userList.size();
        return true;
    }
}
