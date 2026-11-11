package com.cimse.seguridad;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/** Guarda en la tabla auditoria quien hizo que y cuando. */
@Service
public class AuditoriaService {

    private final JdbcTemplate jdbc;

    public AuditoriaService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void registrar(Integer idUsuario, String accion, String tabla, Integer idRegistro,
                          String antes, String despues) {
        if (idUsuario == null) {
            return; // la tabla exige un usuario valido
        }
        jdbc.update(
                "INSERT INTO auditoria (id_usuario, accion, tabla_afectada, id_registro, fecha_hora, "
                        + "datos_anteriores, datos_nuevos) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?)",
                idUsuario, accion, tabla, idRegistro, recortar(antes), recortar(despues));
    }

    /** Usuario autenticado en la peticion actual (o null). */
    public String usuarioActual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || "anonymousUser".equals(String.valueOf(a.getPrincipal()))) {
            return null;
        }
        return a.getName();
    }

    public Integer idUsuarioActual() {
        return idPorUsername(usuarioActual());
    }

    public Integer idPorUsername(String username) {
        if (username == null) {
            return null;
        }
        List<Integer> ids = jdbc.queryForList(
                "SELECT id_usuario FROM usuarios WHERE nombre_usuario = ?", Integer.class, username);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private String recortar(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 3900 ? s.substring(0, 3900) : s;
    }
}
