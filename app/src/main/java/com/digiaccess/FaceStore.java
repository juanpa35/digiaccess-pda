package com.digiaccess;

import com.FaceVerifyM8.MainActivity;
import com.db.UserBean;
import com.idfacesdk.IdFaceSdk;

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
