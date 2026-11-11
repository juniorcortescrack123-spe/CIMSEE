package com.cimse.seguridad;

import java.security.SecureRandom;

/**
 * Politica de contrasenas (revision de la licenciada):
 * minimo 8 caracteres, una mayuscula, un numero y un caracter especial.
 * Las contrasenas se guardan cifradas con BCrypt (nunca en texto plano).
 */
public final class PoliticaClave {

    public static final String REGLAS = "Minimo 8 caracteres, una mayuscula, un numero y un caracter especial (por ejemplo @ # $ !).";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PoliticaClave() {
    }

    /** Devuelve el mensaje de error, o null si la clave cumple la politica. */
    public static String validar(String clave, String usuario) {
        if (clave == null || clave.length() < 8) {
            return "La contrasena debe tener al menos 8 caracteres.";
        }
        boolean mayuscula = false;
        boolean numero = false;
        boolean especial = false;
        for (char c : clave.toCharArray()) {
            if (Character.isUpperCase(c)) {
                mayuscula = true;
            } else if (Character.isDigit(c)) {
                numero = true;
            } else if (!Character.isLetter(c) && !Character.isWhitespace(c)) {
                especial = true;
            }
        }
        if (!mayuscula) {
            return "La contrasena debe incluir al menos una letra mayuscula.";
        }
        if (!numero) {
            return "La contrasena debe incluir al menos un numero.";
        }
        if (!especial) {
            return "La contrasena debe incluir al menos un caracter especial (por ejemplo @ # $ !).";
        }
        if (usuario != null && usuario.length() >= 3 && clave.toLowerCase().contains(usuario.toLowerCase())) {
            return "La contrasena no puede contener el nombre de usuario.";
        }
        return null;
    }

    /** Genera una contrasena temporal que cumple la politica (la crea el sistema, no el administrador). */
    public static String generarTemporal() {
        String mayusculas = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String minusculas = "abcdefghijkmnpqrstuvwxyz";
        String numeros = "23456789";
        String especiales = "@#$%&*!?";
        String todo = mayusculas + minusculas + numeros;
        StringBuilder sb = new StringBuilder();
        sb.append(mayusculas.charAt(RANDOM.nextInt(mayusculas.length())));
        sb.append(minusculas.charAt(RANDOM.nextInt(minusculas.length())));
        sb.append(numeros.charAt(RANDOM.nextInt(numeros.length())));
        sb.append(especiales.charAt(RANDOM.nextInt(especiales.length())));
        for (int i = 0; i < 6; i++) {
            sb.append(todo.charAt(RANDOM.nextInt(todo.length())));
        }
        // mezcla los caracteres para que el orden no sea predecible
        char[] c = sb.toString().toCharArray();
        for (int i = c.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char t = c[i];
            c[i] = c[j];
            c[j] = t;
        }
        return new String(c);
    }
}
