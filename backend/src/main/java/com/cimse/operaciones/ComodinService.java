package com.cimse.operaciones;

import com.cimse.config.ApiUtil;
import com.cimse.seguridad.AuditoriaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Sprint 3 - Guardia comodin: crea una asignacion temporal (solo por hoy) para que un guardia de reemplazo
 * cubra el mismo puesto y turno de la asignacion original. Usa las mismas reglas de no sobreposicion de turnos.
 */
@Service
public class ComodinService {

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public ComodinService(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    /** Devuelve el id de la asignacion temporal creada para el reemplazo. */
    public Integer asignarReemplazo(Integer idAsignacionOriginal, Integer idReemplazo, String motivo) {
        List<Map<String, Object>> orig = jdbc.queryForList(
                "SELECT id_empleado, id_puesto, id_turno, estado_asignacion FROM asignacion_puestos "
                        + "WHERE id_asignacion_puesto = ?", idAsignacionOriginal);
        if (orig.isEmpty()) {
            throw ApiUtil.error("La asignacion a reemplazar no existe.");
        }
        Map<String, Object> o = orig.get(0);
        if (!"ACTIVA".equals(o.get("estado_asignacion"))) {
            throw ApiUtil.error("La asignacion a reemplazar ya no esta ACTIVA.");
        }
        if (o.get("id_turno") == null) {
            throw ApiUtil.error("La asignacion original no tiene turno definido.");
        }
        Integer idOriginal = ((Number) o.get("id_empleado")).intValue();
        if (idOriginal.equals(idReemplazo)) {
            throw ApiUtil.error("El guardia comodin no puede ser el mismo guardia que se reemplaza.");
        }
        List<Map<String, Object>> emp = jdbc.queryForList(
                "SELECT nombre_completo, cargo, activo FROM empleados WHERE id_empleado = ?", idReemplazo);
        if (emp.isEmpty() || !"S".equals(emp.get(0).get("activo")) || !"GUARDIA".equals(emp.get(0).get("cargo"))) {
            throw ApiUtil.error("El guardia comodin debe ser un empleado activo con cargo GUARDIA.");
        }
        Integer idTurno = ((Number) o.get("id_turno")).intValue();
        Integer idPuesto = ((Number) o.get("id_puesto")).intValue();

        Integer choques = jdbc.queryForObject(
                "SELECT COUNT(*) FROM asignacion_puestos WHERE id_empleado = ? AND id_turno = ? "
                        + "AND estado_asignacion = 'ACTIVA' "
                        + "AND daterange(fecha_inicio, COALESCE(fecha_fin, 'infinity'::date), '[]') "
                        + "&& daterange(CURRENT_DATE, CURRENT_DATE, '[]')",
                Integer.class, idReemplazo, idTurno);
        if (choques != null && choques > 0) {
            throw ApiUtil.error("Sobreposicion de turnos: " + emp.get(0).get("nombre_completo")
                    + " ya tiene una asignacion ACTIVA en ese turno hoy.");
        }
        String turno = jdbc.queryForObject("SELECT nombre FROM turnos WHERE id_turno = ?", String.class, idTurno);

        Integer id = jdbc.queryForObject(
                "INSERT INTO asignacion_puestos (id_empleado, id_puesto, fecha_inicio, fecha_fin, turno_real, "
                        + "observaciones, id_turno, estado_asignacion, id_usuario_asigna) "
                        + "VALUES (?, ?, CURRENT_DATE, CURRENT_DATE, ?, ?, ?, 'ACTIVA', ?) RETURNING id_asignacion_puesto",
                Integer.class, idReemplazo, idPuesto, turno, "Guardia comodin. " + motivo, idTurno,
                auditoria.idUsuarioActual());
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "asignacion_puestos", id, null,
                "Comodin " + emp.get(0).get("nombre_completo") + " (turno " + turno + ", hoy). " + motivo);
        return id;
    }
}
