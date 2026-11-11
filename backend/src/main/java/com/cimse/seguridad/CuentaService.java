package com.cimse.seguridad;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Consultas de cuentas, roles y permisos usadas por el login y por el filtro JWT. */
@Service
public class CuentaService {

    private final JdbcTemplate jdbc;

    public CuentaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Cuenta> buscar(String username) {
        if (username == null) {
            return Optional.empty();
        }
        List<Cuenta> lista = jdbc.query(
                "SELECT u.id_usuario, u.nombre_usuario, u.contrasena_hash, u.rol, u.activo, "
                        + "u.debe_cambiar_clave, u.clave_expira, u.bloqueado, u.fecha_bloqueo, e.nombre_completo "
                        + "FROM usuarios u JOIN empleados e ON e.id_empleado = u.id_empleado "
                        + "WHERE u.nombre_usuario = ?",
                (rs, n) -> new Cuenta(
                        rs.getInt("id_usuario"),
                        rs.getString("nombre_usuario"),
                        rs.getString("contrasena_hash"),
                        rs.getString("rol"),
                        "S".equals(rs.getString("activo")),
                        "S".equals(rs.getString("debe_cambiar_clave")),
                        rs.getString("nombre_completo"),
                        rs.getTimestamp("clave_expira"),
                        "S".equals(rs.getString("bloqueado")),
                        rs.getTimestamp("fecha_bloqueo")),
                username);
        return lista.stream().findFirst();
    }

    /** Codigos de permiso de un rol (vacio si el rol esta inactivo o no existe). */
    public List<String> permisos(String rol) {
        return jdbc.queryForList(
                "SELECT rp.codigo_permiso FROM rol_permisos rp JOIN roles r ON r.id_rol = rp.id_rol "
                        + "WHERE r.nombre = ? AND r.activo = 'S' ORDER BY rp.codigo_permiso",
                String.class, rol);
    }

    /** Todos los roles activos del usuario (un usuario puede tener mas de un rol). */
    public List<String> roles(Cuenta c) {
        try {
            List<String> r = jdbc.queryForList(
                    "SELECT r.nombre FROM usuario_roles ur JOIN roles r ON r.id_rol = ur.id_rol "
                            + "WHERE ur.id_usuario = ? AND r.activo = 'S' ORDER BY r.id_rol", String.class, c.id());
            if (!r.isEmpty()) {
                return r;
            }
        } catch (DataAccessException e) {
            // si la tabla aun no existe se usa el rol principal
        }
        return List.of(c.rol());
    }

    /** Union de los permisos de todos los roles del usuario. */
    public List<String> permisosUsuario(Cuenta c) {
        try {
            List<String> p = jdbc.queryForList(
                    "SELECT DISTINCT rp.codigo_permiso FROM usuario_roles ur "
                            + "JOIN roles r ON r.id_rol = ur.id_rol AND r.activo = 'S' "
                            + "JOIN rol_permisos rp ON rp.id_rol = r.id_rol WHERE ur.id_usuario = ? ORDER BY 1",
                    String.class, c.id());
            if (!p.isEmpty()) {
                return p;
            }
        } catch (DataAccessException e) {
            // fallback abajo
        }
        return permisos(c.rol());
    }
}
