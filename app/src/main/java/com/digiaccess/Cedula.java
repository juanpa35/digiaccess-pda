package com.digiaccess;

import java.nio.charset.Charset;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Datos de una cédula de ciudadanía colombiana y lector del código PDF417
 * de la cédula amarilla con hologramas.
 *
 * Estructura conocida del PDF417 (posiciones en bytes, base 0, fin exclusivo):
 *   48-58  número de documento (con ceros a la izquierda)
 *   58-81  primer apellido      81-104  segundo apellido
 *   104-127 primer nombre       127-150 segundo nombre
 *   151     sexo (M/F)          152-160 fecha de nacimiento AAAAMMDD
 *   166-168 RH
 * Los campos de texto vienen rellenos con bytes 0x00.
 *
 * La cédula digital trae un QR cifrado por la Registraduría que no se puede leer:
 * para esa cédula el número se digita en el PDA.
 */
public final class Cedula {

    public static final String ORIGEN_PDF417 = "pdf417";
    public static final String ORIGEN_DIGITADA = "digitada";

    public String numero = "";
    public String primerApellido = "";
    public String segundoApellido = "";
    public String primerNombre = "";
    public String segundoNombre = "";
    /** M, F o vacío. Se lee pero NO se envía al servidor (minimización de datos). */
    public String sexo = "";
    /** AAAA-MM-DD o vacío. Se lee pero NO se envía al servidor. */
    public String fechaNacimiento = "";
    public String origen = ORIGEN_PDF417;

    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");
    private static final Pattern NUMERO_VALIDO = Pattern.compile("^[0-9]{3,10}$");

    public String nombres() {
        return join(primerNombre, segundoNombre);
    }

    public String apellidos() {
        return join(primerApellido, segundoApellido);
    }

    public String nombreCompleto() {
        return join(nombres(), apellidos());
    }

    public boolean tieneNombre() {
        return nombreCompleto().length() > 0;
    }

    /** Crea una cédula a partir de un número digitado (cédula digital). Devuelve null si no es válido. */
    public static Cedula digitada(String numero) {
        String n = soloDigitos(numero);
        n = quitarCerosIzquierda(n);
        if (!NUMERO_VALIDO.matcher(n).matches()) return null;
        Cedula c = new Cedula();
        c.numero = n;
        c.origen = ORIGEN_DIGITADA;
        return c;
    }

    /**
     * Interpreta los datos leídos por el lector 2D.
     * @param raw bytes tal como los entrega el lector (preferible), puede ser null
     * @param text texto entregado por el lector si no hay bytes, puede ser null
     * @return la cédula, o null si el código no corresponde a una cédula amarilla
     */
    public static Cedula parse(byte[] raw, String text) {
        String data = null;
        if (raw != null && raw.length > 0) data = new String(raw, LATIN1);
        else if (text != null) data = text;
        if (data == null || data.length() < 20) return null;

        Cedula c = parseOffsets(data);
        if (c == null) c = parseHeuristic(data);
        return c;
    }

    /** Lectura por posiciones fijas: funciona cuando el lector conserva los bytes 0x00. */
    static Cedula parseOffsets(String d) {
        if (d.length() < 150) return null;
        String num = quitarCerosIzquierda(soloDigitos(d.substring(48, 58)));
        if (!NUMERO_VALIDO.matcher(num).matches()) return null;
        // El número debe venir completo (10 posiciones de dígitos con ceros a la izquierda)
        if (soloDigitos(d.substring(48, 58)).length() != 10) return null;

        Cedula c = new Cedula();
        c.numero = num;
        c.primerApellido = limpiarTexto(d.substring(58, 81));
        c.segundoApellido = limpiarTexto(d.substring(81, 104));
        c.primerNombre = limpiarTexto(d.substring(104, 127));
        c.segundoNombre = limpiarTexto(d.substring(127, 150));
        if (c.primerApellido.isEmpty() || c.primerNombre.isEmpty()) return null;
        if (!soloLetras(c.primerApellido) || !soloLetras(c.primerNombre)) return null;

        if (d.length() >= 160) {
            char s = d.charAt(151);
            if (s == 'M' || s == 'F') c.sexo = String.valueOf(s);
            c.fechaNacimiento = fecha(d.substring(152, 160));
        }
        return c;
    }

    /**
     * Lectura flexible: cuando el lector (modo teclado) elimina o cambia los 0x00, las posiciones se corren.
     * El número de documento son los 10 dígitos que preceden inmediatamente al primer apellido.
     */
    static Cedula parseHeuristic(String d) {
        // Normaliza: separadores (0x00 y otros controles) a '|'
        StringBuilder sb = new StringBuilder(d.length());
        for (int i = 0; i < d.length(); i++) {
            char ch = d.charAt(i);
            sb.append(ch < 0x20 ? '|' : ch);
        }
        String s = sb.toString();

        // Busca: 10 dígitos + letras (primer apellido). Se exige que antes del bloque haya algo (cabecera).
        Matcher m = Pattern.compile("([0-9]{10})([A-ZÑÁÉÍÓÚÜ][A-ZÑÁÉÍÓÚÜ '\\-]+)").matcher(s);
        int start = -1;
        String num = null;
        while (m.find()) {
            if (m.start() < 20) continue; // el número nunca va al inicio del código
            String n = quitarCerosIzquierda(m.group(1));
            if (NUMERO_VALIDO.matcher(n).matches()) {
                num = n;
                start = m.start(2);
                break;
            }
        }
        if (num == null) return null;

        Cedula c = new Cedula();
        c.numero = num;

        // Bloque de nombres hasta el sexo+fecha (ej. "0M19850312" o "M19850312")
        String resto = s.substring(start);
        Matcher mf = Pattern.compile("0?([MF])((?:19|20)[0-9]{6})").matcher(resto);
        String nombresBlk = resto;
        if (mf.find()) {
            nombresBlk = resto.substring(0, mf.start());
            c.sexo = mf.group(1);
            c.fechaNacimiento = fecha(mf.group(2));
        }

        // Si hay separadores, los 4 campos vienen divididos
        String[] partes = nombresBlk.split("\\|+");
        java.util.List<String> campos = new java.util.ArrayList<>();
        for (String p : partes) {
            String t = limpiarTexto(p);
            if (!t.isEmpty() && soloLetras(t)) campos.add(t);
        }
        if (campos.size() >= 3) {
            c.primerApellido = campos.get(0);
            c.segundoApellido = campos.get(1);
            c.primerNombre = campos.get(2);
            if (campos.size() >= 4) c.segundoNombre = campos.get(3);
        } else if (campos.size() == 2) {
            c.primerApellido = campos.get(0);
            c.primerNombre = campos.get(1);
        } else if (campos.size() == 1) {
            // Sin separadores no se pueden dividir los campos: se deja todo como apellidos+nombres juntos.
            c.primerApellido = campos.get(0);
        }
        return c;
    }

    // ---------------------------------------------------------------- utilidades

    private static String join(String a, String b) {
        a = a == null ? "" : a.trim();
        b = b == null ? "" : b.trim();
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a + " " + b;
    }

    static String soloDigitos(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') sb.append(ch);
        }
        return sb.toString();
    }

    static String quitarCerosIzquierda(String s) {
        int i = 0;
        while (i < s.length() - 1 && s.charAt(i) == '0') i++;
        return s.substring(i);
    }

    static String limpiarTexto(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            sb.append(ch < 0x20 || ch == '|' ? ' ' : ch);
        }
        return sb.toString().trim().replaceAll("\\s+", " ").toUpperCase(new Locale("es", "CO"));
    }

    private static boolean soloLetras(String s) {
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (!(Character.isLetter(ch) || ch == ' ' || ch == '-' || ch == '\'')) return false;
        }
        return s.length() > 0;
    }

    private static String fecha(String yyyymmdd) {
        String f = soloDigitos(yyyymmdd);
        if (f.length() != 8) return "";
        int mes = Integer.parseInt(f.substring(4, 6)), dia = Integer.parseInt(f.substring(6, 8));
        if (mes < 1 || mes > 12 || dia < 1 || dia > 31) return "";
        return f.substring(0, 4) + "-" + f.substring(4, 6) + "-" + f.substring(6, 8);
    }

    @Override
    public String toString() {
        return numero + " " + nombreCompleto();
    }
}
