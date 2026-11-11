package com.cimse.operaciones;

import com.cimse.config.ApiUtil;
import com.cimse.config.Permisos;
import com.cimse.seguridad.AuditoriaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Solicitudes que los guardias le hacen a su supervisor. El supervisor las aprueba o las rechaza dejando un comentario;
 * el guardia ve la respuesta en su portal. La aprobacion es la autorizacion: el cambio de turno o de equipo en si
 * lo registra el administrador en Asignacion a puestos o Asignacion de equipos.
 */
@RestController
@RequestMapping("/api/solicitudes-guardias")
public class SolicitudGuardiaController {

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public SolicitudGuardiaController(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    private Integer empleadoActual() {
        Integer idUsuario = auditoria.idUsuarioActual();
        List<Integer> r = idUsuario == null ? new ArrayList<>()
                : jdbc.queryForList("SELECT id_empleado FROM usuarios WHERE id_usuario = ?", Integer.class, idUsuario);
        return r.isEmpty() ? null : r.get(0);
    }

    private boolean esSupervisor() {
        Integer idUsuario = auditoria.idUsuarioActual();
        Integer n = idUsuario == null ? 0 : jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuario_roles ur JOIN roles r ON r.id_rol = ur.id_rol WHERE ur.id_usuario = ? AND r.nombre = 'SUPERVISOR'",
                Integer.class, idUsuario);
        return n != null && n > 0;
    }

    /** Un supervisor ve las solicitudes de sus guardias; el administrador y los gerentes ven todas. */
    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(required = false) String estado) {
        StringBuilder sql = new StringBuilder(
                "SELECT s.id_solicitud AS \"id\", s.fecha_solicitud AS \"fecha\", g.nombre_completo AS \"guardia\", s.tipo AS \"tipo\", "
                        + "s.motivo AS \"motivo\", s.estado AS \"estado\", s.respuesta AS \"respuesta\", s.fecha_respuesta AS \"fechaRespuesta\", "
                        + "sup.nombre_completo AS \"supervisor\", s.fecha_inicio AS \"fechaInicio\", s.fecha_fin AS \"fechaFin\", t.nombre AS \"turnoSolicitado\", i.descripcion AS \"equipo\", "
                        + "i.numero_serie_o_placa AS \"serieOPlaca\", "
                        + "(SELECT string_agg(DISTINCT cl.nombre, ', ') FROM asignacion_puestos a JOIN puestos_trabajo p ON p.id_puesto = a.id_puesto "
                        + "JOIN clientes cl ON cl.id_cliente = p.id_cliente WHERE a.id_empleado = s.id_empleado AND a.estado_asignacion = 'ACTIVA') AS \"cliente\", "
                        + "(SELECT string_agg(DISTINCT tt.nombre, ', ') FROM asignacion_puestos a JOIN turnos tt ON tt.id_turno = a.id_turno "
                        + "WHERE a.id_empleado = s.id_empleado AND a.estado_asignacion = 'ACTIVA') AS \"turnoActual\" "
                        + "FROM solicitudes_guardia s JOIN empleados g ON g.id_empleado = s.id_empleado "
                        + "JOIN empleados sup ON sup.id_empleado = s.id_supervisor LEFT JOIN turnos t ON t.id_turno = s.id_turno_solicitado "
                        + "LEFT JOIN inventario_activos i ON i.id_activo = s.id_activo WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (esSupervisor()) {
            sql.append(" AND s.id_supervisor = ?");
            args.add(empleadoActual());
        }
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND s.estado = ?");
            args.add(estado.trim());
        }
        sql.append(" ORDER BY CASE s.estado WHEN 'PENDIENTE' THEN 0 ELSE 1 END, s.fecha_solicitud DESC LIMIT 300");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @PutMapping("/{id}/responder")
    public Map<String, Object> responder(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> s = jdbc.queryForList(
                "SELECT id_supervisor, estado, tipo FROM solicitudes_guardia WHERE id_solicitud = ?", id);
        if (s.isEmpty()) {
            throw ApiUtil.noEncontrado("Solicitud no encontrada.");
        }
        Integer yo = empleadoActual();
        boolean esSuyo = yo != null && yo.equals(((Number) s.get(0).get("id_supervisor")).intValue());
        if (!esSuyo && !Permisos.tiene("SEGURIDAD")) {
            throw ApiUtil.error("Solo el supervisor del guardia (o el administrador) puede responder esta solicitud.");
        }
        if (!"PENDIENTE".equals(s.get(0).get("estado"))) {
            throw ApiUtil.error("Esta solicitud ya fue respondida.");
        }
        if (!(body.get("aprobar") instanceof Boolean)) {
            throw ApiUtil.error("Indique si aprueba o rechaza la solicitud.");
        }
        boolean aprobar = (Boolean) body.get("aprobar");
        String respuesta = ApiUtil.textoObligatorio(body, "respuesta", "comentario para el guardia");
        if (respuesta.length() > 300) {
            throw ApiUtil.error("El comentario no puede pasar de 300 caracteres.");
        }
        jdbc.update("UPDATE solicitudes_guardia SET estado = ?, respuesta = ?, fecha_respuesta = CURRENT_TIMESTAMP, "
                + "id_usuario_respondio = ?, visto_guardia = 'N' WHERE id_solicitud = ?",
                aprobar ? "APROBADA" : "RECHAZADA", respuesta, auditoria.idUsuarioActual(), id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "solicitudes_guardia", id, "PENDIENTE",
                (aprobar ? "APROBADA" : "RECHAZADA") + " (" + s.get(0).get("tipo") + ")");
        return Map.of("mensaje", aprobar ? "Solicitud aprobada. El guardia recibio el aviso." : "Solicitud rechazada. El guardia recibio el aviso.");
    }
}
