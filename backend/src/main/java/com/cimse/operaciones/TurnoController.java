package com.cimse.operaciones;

import com.cimse.config.ApiUtil;
import com.cimse.seguridad.AuditoriaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Sprint 2 - Catalogo de turnos (MATUTINO, VESPERTINO, NOCTURNO) y sus horarios. */
@RestController
@RequestMapping("/api/turnos")
public class TurnoController {

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public TurnoController(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return jdbc.queryForList(
                "SELECT t.id_turno AS \"id\", t.nombre AS \"nombre\", t.hora_inicio AS \"horaInicio\", "
                        + "t.hora_fin AS \"horaFin\", "
                        + "(SELECT COUNT(*) FROM asignacion_puestos a WHERE a.id_turno = t.id_turno "
                        + "AND a.estado_asignacion = 'ACTIVA') AS \"guardias\" "
                        + "FROM turnos t ORDER BY t.id_turno");
    }

    /** Cambia el horario de un turno (el nombre del turno no se modifica). */
    @PutMapping("/{id}")
    public Map<String, String> editar(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> actual = jdbc.queryForList(
                "SELECT nombre, hora_inicio, hora_fin FROM turnos WHERE id_turno = ?", id);
        if (actual.isEmpty()) {
            throw ApiUtil.noEncontrado("Turno no encontrado.");
        }
        String inicio = ApiUtil.textoObligatorio(body, "horaInicio", "hora de inicio");
        String fin = ApiUtil.textoObligatorio(body, "horaFin", "hora de fin");
        String formato = "^([01]\\d|2[0-3]):[0-5]\\d$";
        if (!inicio.matches(formato) || !fin.matches(formato)) {
            throw ApiUtil.error("Las horas deben tener el formato HH:mm (ejemplo 06:00).");
        }
        if (inicio.equals(fin)) {
            throw ApiUtil.error("La hora de inicio y la de fin no pueden ser iguales.");
        }
        jdbc.update("UPDATE turnos SET hora_inicio = ?, hora_fin = ? WHERE id_turno = ?", inicio, fin, id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "turnos", id,
                actual.get(0).get("nombre") + " " + actual.get(0).get("hora_inicio") + "-" + actual.get(0).get("hora_fin"),
                actual.get(0).get("nombre") + " " + inicio + "-" + fin);
        return Map.of("mensaje", "Horario del turno actualizado.");
    }
}
