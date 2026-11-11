package com.cimse.operaciones;

import com.cimse.config.ApiUtil;
import com.cimse.seguridad.AuditoriaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portal del guardia. Quien entra con el rol GUARDIA ve su supervisor, su lugar y turno asignados y su equipo,
 * puede PEDIR un cambio de turno, un cambio de equipo o la devolucion de un equipo (el supervisor decide) y puede
 * reportar incidencias; el supervisor asignado recibe el aviso. El guardia nunca cambia el estado de una incidencia:
 * eso lo hace solo el supervisor desde Incidencias.
 */
@RestController
@RequestMapping("/api/guardia")
public class GuardiaController {

    private static final List<String> TIPOS_SOLICITUD = Arrays.asList("CAMBIO_TURNO", "CAMBIO_EQUIPO", "DEVOLUCION_EQUIPO", "VACACIONES");
    private static final String ASIGNACION_HOY =
            "a.estado_asignacion = 'ACTIVA' AND a.fecha_inicio <= CURRENT_DATE AND (a.fecha_fin IS NULL OR a.fecha_fin >= CURRENT_DATE)";

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public GuardiaController(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    /** El empleado de la persona que tiene la sesion iniciada. */
    private Integer yo() {
        Integer idUsuario = auditoria.idUsuarioActual();
        List<Integer> r = idUsuario == null ? new ArrayList<>()
                : jdbc.queryForList("SELECT id_empleado FROM usuarios WHERE id_usuario = ?", Integer.class, idUsuario);
        if (r.isEmpty() || r.get(0) == null) {
            throw ApiUtil.error("Su usuario no esta ligado a un empleado; pida al administrador que lo corrija.");
        }
        return r.get(0);
    }

    private List<Map<String, Object>> misAsignaciones(Integer emp) {
        return jdbc.queryForList(
                "SELECT a.id_asignacion_puesto AS \"idAsignacion\", p.id_puesto AS \"idPuesto\", p.id_cliente AS \"idCliente\", "
                        + "cl.nombre AS \"cliente\", p.descripcion AS \"puesto\", p.ubicacion AS \"ubicacion\", t.id_turno AS \"idTurno\", "
                        + "t.nombre AS \"turno\", (t.hora_inicio || ' - ' || t.hora_fin) AS \"horario\", a.fecha_inicio AS \"desde\" "
                        + "FROM asignacion_puestos a JOIN puestos_trabajo p ON p.id_puesto = a.id_puesto "
                        + "JOIN clientes cl ON cl.id_cliente = p.id_cliente LEFT JOIN turnos t ON t.id_turno = a.id_turno "
                        + "WHERE a.id_empleado = ? AND p.estado = 'ACTIVO' AND " + ASIGNACION_HOY + " ORDER BY a.fecha_inicio", emp);
    }

    private List<Map<String, Object>> supervisoresDe(Object idCliente) {
        return jdbc.queryForList(
                "SELECT e.id_empleado AS \"id\", e.nombre_completo AS \"nombre\", e.telefono AS \"telefono\" FROM supervisor_clientes sc "
                        + "JOIN empleados e ON e.id_empleado = sc.id_supervisor WHERE sc.id_cliente = ? AND e.activo = 'S' "
                        + "ORDER BY e.id_empleado", idCliente);
    }

    private Integer supervisorDe(Object idCliente) {
        List<Map<String, Object>> s = supervisoresDe(idCliente);
        if (s.isEmpty()) {
            throw ApiUtil.error("Su puesto todavia no tiene un supervisor asignado; avise al administrador.");
        }
        return ((Number) s.get(0).get("id")).intValue();
    }

    private List<Map<String, Object>> miEquipo(Integer emp) {
        return jdbc.queryForList(
                "SELECT e.id_asignacion AS \"idAsignacion\", i.id_activo AS \"idActivo\", i.descripcion AS \"descripcion\", "
                        + "i.tipo_activo AS \"tipo\", i.numero_serie_o_placa AS \"serieOPlaca\", i.codigo_inventario AS \"codigo\", "
                        + "e.fecha_entrega AS \"fechaEntrega\" FROM asignacion_equipos e JOIN inventario_activos i ON i.id_activo = e.id_activo "
                        + "WHERE e.id_empleado = ? AND e.fecha_devolucion IS NULL ORDER BY e.fecha_entrega DESC", emp);
    }

    /** Mi supervisor, mi lugar asignado y mi equipo. */
    @GetMapping("/mi-informacion")
    public Map<String, Object> miInformacion() {
        Integer emp = yo();
        Map<String, Object> r = new LinkedHashMap<>();
        Map<String, Object> datos = jdbc.queryForList(
                "SELECT nombre_completo AS \"nombre\", cargo AS \"cargo\" FROM empleados WHERE id_empleado = ?", emp).get(0);
        r.putAll(datos);
        List<Map<String, Object>> puestos = new ArrayList<>();
        for (Map<String, Object> a : misAsignaciones(emp)) {
            Map<String, Object> m = new LinkedHashMap<>(a);
            m.put("supervisores", supervisoresDe(a.get("idCliente")));
            puestos.add(m);
        }
        r.put("asignaciones", puestos);
        r.put("equipo", miEquipo(emp));
        return r;
    }

    @GetMapping("/turnos")
    public List<Map<String, Object>> turnos() {
        return jdbc.queryForList("SELECT id_turno AS \"id\", nombre AS \"nombre\", (hora_inicio || ' - ' || hora_fin) AS \"horario\" "
                + "FROM turnos ORDER BY hora_inicio");
    }

    // ---------------------------------------------------------------- solicitudes

    @PostMapping("/solicitudes")
    public Map<String, Object> pedir(@RequestBody Map<String, Object> body) {
        Integer emp = yo();
        String tipo = ApiUtil.textoObligatorio(body, "tipo", "tipo de solicitud");
        if (!TIPOS_SOLICITUD.contains(tipo)) {
            throw ApiUtil.error("Seleccione si pide cambio de turno, cambio de equipo, devolucion de equipo o vacaciones.");
        }
        String motivo = ApiUtil.textoObligatorio(body, "motivo", "motivo");
        if (motivo.length() > 300) {
            throw ApiUtil.error("El motivo no puede pasar de 300 caracteres.");
        }
        List<Map<String, Object>> asignaciones = misAsignaciones(emp);
        if (asignaciones.isEmpty()) {
            throw ApiUtil.error("No tiene un puesto asignado, por eso no hay un supervisor a quien pedirle esto.");
        }
        Map<String, Object> actual = asignaciones.get(0);
        Integer supervisor = supervisorDe(actual.get("idCliente"));

        Integer idTurno = null;
        Integer idActivo = null;
        LocalDate inicio = null;
        LocalDate fin = null;
        if ("VACACIONES".equals(tipo)) {
            inicio = ApiUtil.fecha(body, "fechaInicio", "fecha en que inician las vacaciones", true);
            fin = ApiUtil.fecha(body, "fechaFin", "fecha en que termina las vacaciones", true);
            if (inicio.isBefore(LocalDate.now())) {
                throw ApiUtil.error("Las vacaciones deben empezar hoy o en una fecha futura.");
            }
            if (fin.isBefore(inicio)) {
                throw ApiUtil.error("La fecha final no puede ser anterior a la fecha de inicio.");
            }
        } else if ("CAMBIO_TURNO".equals(tipo)) {
            idTurno = ApiUtil.entero(body, "idTurnoSolicitado", "turno que solicita", true);
            Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM turnos WHERE id_turno = ?", Integer.class, idTurno);
            if (existe == null || existe == 0) {
                throw ApiUtil.error("El turno seleccionado no existe.");
            }
            if (actual.get("idTurno") != null && idTurno.equals(((Number) actual.get("idTurno")).intValue())) {
                throw ApiUtil.error("Ya trabaja en ese turno; elija uno distinto.");
            }
        } else {
            idActivo = ApiUtil.entero(body, "idActivo", "equipo", true);
            Integer mio = jdbc.queryForObject("SELECT COUNT(*) FROM asignacion_equipos WHERE id_empleado = ? AND id_activo = ? "
                    + "AND fecha_devolucion IS NULL", Integer.class, emp, idActivo);
            if (mio == null || mio == 0) {
                throw ApiUtil.error("Ese equipo no esta asignado a usted.");
            }
        }
        Integer repetida = idActivo == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM solicitudes_guardia WHERE id_empleado = ? AND tipo = ? AND estado = 'PENDIENTE'",
                        Integer.class, emp, tipo)
                : jdbc.queryForObject("SELECT COUNT(*) FROM solicitudes_guardia WHERE id_empleado = ? AND tipo = ? AND estado = 'PENDIENTE' "
                        + "AND id_activo = ?", Integer.class, emp, tipo, idActivo);
        if (repetida != null && repetida > 0) {
            throw ApiUtil.error("Ya tiene una solicitud igual esperando la respuesta de su supervisor.");
        }
        Integer id = jdbc.queryForObject(
                "INSERT INTO solicitudes_guardia (id_empleado, id_supervisor, tipo, id_turno_solicitado, id_activo, fecha_inicio, fecha_fin, motivo) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_solicitud", Integer.class, emp, supervisor, tipo, idTurno, idActivo,
                inicio == null ? null : ApiUtil.sql(inicio), fin == null ? null : ApiUtil.sql(fin), motivo);
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "solicitudes_guardia", id, null, tipo + " solicitada al supervisor #" + supervisor);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensaje", "Solicitud enviada. Su supervisor recibio el aviso; aqui vera si la aprueba o la rechaza.");
        return r;
    }

    @GetMapping("/solicitudes")
    public List<Map<String, Object>> misSolicitudes() {
        return jdbc.queryForList(
                "SELECT s.id_solicitud AS \"id\", s.fecha_solicitud AS \"fecha\", s.tipo AS \"tipo\", s.motivo AS \"motivo\", s.estado AS \"estado\", "
                        + "s.respuesta AS \"respuesta\", s.fecha_respuesta AS \"fechaRespuesta\", sup.nombre_completo AS \"supervisor\", "
                        + "s.fecha_inicio AS \"fechaInicio\", s.fecha_fin AS \"fechaFin\", t.nombre AS \"turnoSolicitado\", i.descripcion AS \"equipo\", (s.visto_guardia = 'N' AND s.estado <> 'PENDIENTE') AS \"nueva\" "
                        + "FROM solicitudes_guardia s JOIN empleados sup ON sup.id_empleado = s.id_supervisor "
                        + "LEFT JOIN turnos t ON t.id_turno = s.id_turno_solicitado LEFT JOIN inventario_activos i ON i.id_activo = s.id_activo "
                        + "WHERE s.id_empleado = ? ORDER BY s.fecha_solicitud DESC, s.id_solicitud DESC LIMIT 100", yo());
    }

    /** Marca como vistas las respuestas, para que el aviso desaparezca. */
    @PutMapping("/solicitudes/vistas")
    public Map<String, Object> marcarVistas() {
        jdbc.update("UPDATE solicitudes_guardia SET visto_guardia = 'S' WHERE id_empleado = ? AND estado <> 'PENDIENTE'", yo());
        return Map.of("mensaje", "ok");
    }

    // ---------------------------------------------------------------- incidencias

    /** El guardia reporta; el supervisor asignado a su puesto recibe el aviso y es el unico que cambia el estado. */
    @PostMapping("/incidencias")
    public Map<String, Object> reportar(@RequestBody Map<String, Object> body) {
        Integer emp = yo();
        String nivel = ApiUtil.texto(body, "nivel") == null ? "GUARDIA" : ApiUtil.texto(body, "nivel");
        if (!"GUARDIA".equals(nivel) && !"ESTABLECIMIENTO".equals(nivel)) {
            throw ApiUtil.error("El nivel debe ser GUARDIA o ESTABLECIMIENTO.");
        }
        String tipo = ApiUtil.textoObligatorio(body, "tipo", "tipo de incidencia");
        if (!IncidenciaController.TIPOS.contains(tipo)) {
            throw ApiUtil.error("Tipo de incidencia invalido.");
        }
        String descripcion = ApiUtil.textoObligatorio(body, "descripcion", "descripcion");
        if (descripcion.length() > 500) {
            throw ApiUtil.error("La descripcion no puede pasar de 500 caracteres.");
        }
        List<Map<String, Object>> asignaciones = misAsignaciones(emp);
        if (asignaciones.isEmpty()) {
            throw ApiUtil.error("No tiene un puesto asignado, por eso no hay un supervisor a quien avisar.");
        }
        Map<String, Object> donde = asignaciones.get(0);
        Integer idPuestoPedido = ApiUtil.entero(body, "idPuesto", "puesto", false);
        if (idPuestoPedido != null) {
            donde = null;
            for (Map<String, Object> a : asignaciones) {
                if (idPuestoPedido.equals(((Number) a.get("idPuesto")).intValue())) {
                    donde = a;
                }
            }
            if (donde == null) {
                throw ApiUtil.error("Ese puesto no es uno de los suyos.");
            }
        }
        Integer idCliente = ((Number) donde.get("idCliente")).intValue();
        Integer idPuesto = ((Number) donde.get("idPuesto")).intValue();
        Integer supervisor = supervisorDe(idCliente);
        boolean deGuardia = "GUARDIA".equals(nivel);
        Integer id = jdbc.queryForObject(
                "INSERT INTO incidencias (id_empleado_reporte, id_puesto, id_supervisor_asignado, tipo, descripcion, nivel, id_cliente, "
                        + "id_empleado_afectado) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_incidencia", Integer.class,
                emp, deGuardia ? idPuesto : null, supervisor, tipo, descripcion, nivel, idCliente, deGuardia ? emp : null);
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "incidencias", id, null,
                tipo + " (" + nivel + ") reportada por el guardia; supervisor #" + supervisor);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensaje", "Incidencia reportada. Su supervisor recibio el aviso.");
        return r;
    }

    @GetMapping("/incidencias")
    public List<Map<String, Object>> misIncidencias() {
        Integer emp = yo();
        return jdbc.queryForList(
                "SELECT i.id_incidencia AS \"id\", i.fecha_hora AS \"fecha\", i.tipo AS \"tipo\", i.nivel AS \"nivel\", i.descripcion AS \"descripcion\", "
                        + "i.estado AS \"estado\", sup.nombre_completo AS \"supervisor\" FROM incidencias i "
                        + "LEFT JOIN empleados sup ON sup.id_empleado = i.id_supervisor_asignado "
                        + "WHERE i.id_empleado_reporte = ? OR i.id_empleado_afectado = ? ORDER BY i.fecha_hora DESC, i.id_incidencia DESC LIMIT 100",
                emp, emp);
    }
}
