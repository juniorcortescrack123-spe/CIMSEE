package com.cimse.config;

import java.util.regex.Pattern;

/**
 * Validaciones de formato pedidas en la revision: identidad (13 digitos), RTN (14 digitos),
 * telefono (8 digitos, se guarda como 9999-9999) y correo. Cada metodo devuelve el valor ya normalizado.
 */
public final class Validaciones {

    private static final Pattern CORREO = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    /** Solo se aceptan digitos; el espacio y el guion se toleran porque se quitan. */
    private static final Pattern SOLO_NUMEROS = Pattern.compile("^[0-9\\s-]*$");

    private Validaciones() {
    }

    /** Devuelve solo los digitos del valor; error si trae letras u otros simbolos. */
    private static String digitos(String s, String mensaje) {
        if (s == null || !SOLO_NUMEROS.matcher(s).matches()) {
            throw ApiUtil.error(mensaje);
        }
        return s.replaceAll("[\\s-]", "");
    }

    /** Identidad hondurena: exactamente 13 digitos. Se guarda sin guiones. */
    public static String identidad(String valor) {
        String msg = "La identidad debe tener 13 digitos (solo numeros).";
        String d = digitos(valor, msg);
        if (d.length() != 13) {
            throw ApiUtil.error(msg);
        }
        return d;
    }

    /** RTN: exactamente 14 digitos. Se guarda sin guiones. */
    public static String rtn(String valor) {
        String msg = "El RTN debe tener 14 digitos (solo numeros).";
        String d = digitos(valor, msg);
        if (d.length() != 14) {
            throw ApiUtil.error(msg);
        }
        return d;
    }

    /** Telefono de 8 digitos; se guarda siempre con el guion despues de los 4 primeros (9999-9999). */
    public static String telefono(String valor, String campo) {
        String msg = "El " + campo + " debe tener 8 digitos (por ejemplo 9999-9999).";
        String d = digitos(valor, msg);
        if (d.length() != 8) {
            throw ApiUtil.error(msg);
        }
        return d.substring(0, 4) + "-" + d.substring(4);
    }

    /** Correo valido; se guarda en minusculas. */
    public static String correo(String valor) {
        if (valor == null || !CORREO.matcher(valor.trim()).matches()) {
            throw ApiUtil.error("El correo electronico no es valido.");
        }
        return valor.trim().toLowerCase();
    }
}
