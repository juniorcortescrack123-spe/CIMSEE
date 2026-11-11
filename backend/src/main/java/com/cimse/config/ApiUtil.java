package com.cimse.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Utilidades para leer y validar los datos que llegan en el cuerpo (JSON) de las peticiones. */
public final class ApiUtil {

    private ApiUtil() {
    }

    public static ResponseStatusException error(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }

    public static ResponseStatusException noEncontrado(String mensaje) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, mensaje);
    }

    /** Texto recortado; null si no viene o esta vacio. */
    public static String texto(Map<String, Object> body, String clave) {
        Object v = body == null ? null : body.get(clave);
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    public static String textoObligatorio(Map<String, Object> body, String clave, String nombreCampo) {
        String s = texto(body, clave);
        if (s == null) {
            throw error("El campo " + nombreCampo + " es obligatorio.");
        }
        return s;
    }

    public static Integer entero(Map<String, Object> body, String clave, String nombreCampo, boolean obligatorio) {
        String s = texto(body, clave);
        if (s == null) {
            if (obligatorio) {
                throw error("El campo " + nombreCampo + " es obligatorio.");
            }
            return null;
        }
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            throw error("El campo " + nombreCampo + " debe ser un numero entero.");
        }
    }

    public static BigDecimal decimal(Map<String, Object> body, String clave, String nombreCampo) {
        String s = texto(body, clave);
        if (s == null) {
            throw error("El campo " + nombreCampo + " es obligatorio.");
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw error("El campo " + nombreCampo + " debe ser un numero.");
        }
    }

    /** Fecha en formato AAAA-MM-DD. */
    public static LocalDate fecha(Map<String, Object> body, String clave, String nombreCampo, boolean obligatoria) {
        String s = texto(body, clave);
        if (s == null) {
            if (obligatoria) {
                throw error("El campo " + nombreCampo + " es obligatorio.");
            }
            return null;
        }
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw error("La fecha " + nombreCampo + " no es valida (use AAAA-MM-DD).");
        }
    }

    public static java.sql.Date sql(LocalDate fecha) {
        return fecha == null ? null : java.sql.Date.valueOf(fecha);
    }
}
