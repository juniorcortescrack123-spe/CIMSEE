package com.cimse.seguridad;

import java.sql.Timestamp;

/** Datos de un usuario del sistema leidos de la BD (usuario + empleado). */
public record Cuenta(Integer id, String username, String hash, String rol, boolean activo,
                     boolean debeCambiarClave, String nombreCompleto, Timestamp claveExpira,
                     boolean bloqueado, Timestamp fechaBloqueo) {
}
