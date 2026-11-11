package com.cimse.operaciones;

import com.cimse.config.ApiUtil;
import com.cimse.config.Permisos;
import com.cimse.seguridad.AuditoriaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 3 - Incidencias (PB-07). Hay dos niveles (revision del 3 de noviembre):
 * GUARDIA: le pertenece a un guardia y a su puesto (falto, llego tarde...). Exige un puesto con asignacion ACTIVA.
 * ESTABLECIMIENTO: le pertenece al cliente o a la visita (vidrio roto, arma perdida, asalto...); no depende de un guardia.
 * Quien reporta es la persona que tiene la sesion iniciada. Se puede solicitar un guardia comodin en las de nivel guardia.
 */
@RestController
@RequestMapping("/api/incidencias")
public class IncidenciaController {

    static final List<String> TIPOS = Arrays.asList("AUSENCIA", "LLEGADA_TARDIA", "PROBLEMA_CLIENTE", "INCIDENTE_SEGURIDAD",
            "DANO_PROPIEDAD", "PERDIDA_ROBO_EQUIPO", "ASALTO", "OTRO");
    private static final List<String> NIVELES = Arrays.asList("GUARDIA", "ESTABLECIMIENTO");
    private static final List<String> ESTADOS = Arrays.asList("ABIERTA", "EN_PROCESO", "RESUELTA");

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;
    private final ComodinService comodin;

    public IncidenciaController(JdbcTemplate jdbc, AuditoriaService auditoria, ComodinService comodin) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.comodin = comodin;
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(required = false) String estado,
                                            @RequestParam(required = false) String tipo,
                                            @RequestParam(required = false) String nivel) {
        StringBuilder sql = new StringBuilder(
                "SELECT i.id_incidencia AS \"id\", i.fecha_hora AS \"fechaHora\", i.tipo AS \"tipo\", i.nivel AS \"nivel\", "
                        + "i.descripcion AS \"descripcion\", i.estado AS \"estado\", i.id_puesto AS \"idPuesto\", "
                        + "p.descripcion AS \"puesto\", COALESCE(cl.nombre, cl2.nombre) AS \"cliente\", rep.nombre_completo AS \"reporta\", "
                        + "sup.nombre_completo AS \"supervisor\", afe.nombre_completo AS \"guardia\", i.id_visita AS \"idVisita\" "
                        + "FROM incidencias i "
                        + "JOIN empleados rep ON rep.id_empleado = i.id_empleado_reporte "
                        + "LEFT JOIN puestos_trabajo p ON p.id_puesto = i.id_puesto "
                        + "LEFT JOIN clientes cl ON cl.id_cliente = p.id_cliente "
                        + "LEFT JOIN clientes cl2 ON cl2.id_cliente = i.id_cliente "
                        + "LEFT JOIN empleados afe ON afe.id_empleado = i.id_empleado_afectado "
                        + "LEFT JOIN empleados sup ON sup.id_empleado = i.id_supervisor_asignado WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND i.estado = ?");
            args.add(estado.trim());
        }
        if (tipo != null && !tipo.isBlank()) {
            sql.append(" AND i.tipo = ?");
            args.add(tipo.trim());
        }
        if (nivel != null && !nivel.isBlank()) {
            sql.append(" AND i.nivel = ?");
            args.add(nivel.trim());
        }
        sql.append(" ORDER BY i.fecha_hora DESC, i.id_incidencia DESC LIMIT 300");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** Empleados activos que pueden reportar una incidencia. */
    @GetMapping("/empleados")
    public List<Map<String, Object>> empleados() {
        return jdbc.queryForList(
                "SELECT id_empleado AS \"id\", nombre_completo AS \"nombre\", cargo AS \"cargo\" FROM empleados "
                        + "WHERE activo = 'S' ORDER BY nombre_completo");
    }

    /** Puestos en servicio (con asignacion activa), para elegir donde ocurrio la incidencia de un guardia. */
    @GetMapping("/puestos")
    public List<Map<String, Object>> puestos() {
        return jdbc.queryForList(
                "SELECT p.id_puesto AS \"id\", p.descripcion AS \"descripcion\", cl.nombre AS \"cliente\" FROM puestos_trabajo p "
                        + "JOIN clientes cl ON cl.id_cliente = p.id_cliente WHERE p.estado = 'ACTIVO' AND EXISTS "
                        + "(SELECT 1 FROM asignacion_puestos a WHERE a.id_puesto = p.id_puesto AND a.estado_asignacion = 'ACTIVA') "
                        + "ORDER BY cl.nombre, p.descripcion");
    }

    /** Clientes, para las incidencias del establecimiento. */
    @GetMapping("/clientes")
    public List<Map<String, Object>> clientes() {
        return jdbc.queryForList("SELECT id_cliente AS \"id\", nombre AS \"nombre\" FROM clientes ORDER BY nombre");
    }

    /** Supervisores a quienes se puede asignar el seguimiento. */
    @GetMapping("/supervisores")
    public List<Map<String, Object>> supervisores() {
        return jdbc.queryForList(
                "SELECT id_empleado AS \"id\", nombre_completo AS \"nombre\" FROM empleados WHERE activo = 'S' AND cargo = 'SUPERVISOR' "
                        + "ORDER BY nombre_completo");
    }

    /** Guardias que pueden cubrir como comodin. */
    @GetMapping("/comodines")
    public List<Map<String, Object>> comodines() {
        return jdbc.queryForList("SELECT id_empleado AS \"id\", nombre_completo AS \"nombre\" FROM empleados "
                + "WHERE activo = 'S' AND cargo = 'GUARDIA' ORDER BY nombre_completo");
    }

    /** Asignaciones activas del puesto de una incidencia (para elegir cual cubrir con el comodin). */
    @GetMapping("/{id}/asignaciones")
    public List<Map<String, Object>> asignacionesDeIncidencia(@PathVariable Integer id) {
        return jdbc.queryForList(
                "SELECT a.id_asignacion_puesto AS \"id\", e.nombre_completo AS \"guardia\", t.nombre AS \"turno\" "
                        + "FROM incidencias i JOIN asignacion_puestos a ON a.id_puesto = i.id_puesto AND a.estado_asignacion = 'ACTIVA' "
                        + "JOIN empleados e ON e.id_empleado = a.id_empleado LEFT JOIN turnos t ON t.id_turno = a.id_turno "
                        + "WHERE i.id_incidencia = ? ORDER BY e.nombre_completo", id);
    }

    /** Guardias con asignacion activa en un puesto (para elegir a quien le pertenece la incidencia). */
    @GetMapping("/puestos/{idPuesto}/guardias")
    public List<Map<String, Object>> guardiasDelPuesto(@PathVariable Integer idPuesto) {
        return jdbc.queryForList(
                "SELECT e.id_empleado AS \"id\", e.nombre_completo AS \"nombre\" FROM asignacion_puestos a "
                        + "JOIN empleados e ON e.id_empleado = a.id_empleado WHERE a.id_puesto = ? AND a.estado_asignacion = 'ACTIVA' "
                        + "ORDER BY e.nombre_completo", idPuesto);
    }

    @PostMapping
    public Map<String, Object> registrar(@RequestBody Map<String, Object> body) {
        // quien reporta: la persona que tiene la sesion iniciada
        Integer idUsuario = auditoria.idUsuarioActual();
        List<Integer> emp = idUsuario == null ? new ArrayList<>()
                : jdbc.queryForList("SELECT id_empleado FROM usuarios WHERE id_usuario = ?", Integer.class, idUsuario);
        Integer idReporta = emp.isEmpty() ? null : emp.get(0);
        if (idReporta == null) {
            throw ApiUtil.error("Su usuario no esta ligado a un empleado; no puede reportar incidencias.");
        }
        String nivel = ApiUtil.texto(body, "nivel") == null ? "GUARDIA" : ApiUtil.texto(body, "nivel");
        if (!NIVELES.contains(nivel)) {
            throw ApiUtil.error("El nivel de la incidencia debe ser GUARDIA o ESTABLECIMIENTO.");
        }
        Integer idSupervisor = ApiUtil.entero(body, "idSupervisorAsignado", "supervisor", true);
        String tipo = ApiUtil.textoObligatorio(body, "tipo", "tipo de incidencia");
        String descripcion = ApiUtil.textoObligatorio(body, "descripcion", "descripcion");
        if (!TIPOS.contains(tipo)) {
            throw ApiUtil.error("Tipo de incidencia invalido.");
        }
        if (descripcion.length() > 500) {
            throw ApiUtil.error("La descripcion no puede pasar de 500 caracteres.");
        }
        Integer sup = jdbc.queryForObject(
                "SELECT COUNT(*) FROM empleados WHERE id_empleado = ? AND activo = 'S'", Integer.class, idSupervisor);
        if (sup == null || sup == 0) {
            throw ApiUtil.error("El supervisor asignado no existe o esta inactivo.");
        }

        Integer idPuesto = null;
        Integer idCliente = null;
        Integer idAfectado = null;
        if ("GUARDIA".equals(nivel)) {
            idPuesto = ApiUtil.entero(body, "idPuesto", "puesto", true);
            List<String> puesto = jdbc.queryForList("SELECT estado FROM puestos_trabajo WHERE id_puesto = ?", String.class, idPuesto);
            if (puesto.isEmpty() || !"ACTIVO".equals(puesto.get(0))) {
                throw ApiUtil.error("El puesto no existe o esta INACTIVO.");
            }
            Integer activas = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM asignacion_puestos WHERE id_puesto = ? AND estado_asignacion = 'ACTIVA'",
                    Integer.class, idPuesto);
            if (activas == null || activas == 0) {
                throw ApiUtil.error("El puesto no tiene ninguna asignacion ACTIVA; una incidencia de guardia debe asociarse a un puesto en servicio.");
            }
            idAfectado = ApiUtil.entero(body, "idEmpleadoAfectado", "guardia", true);
            Integer pertenece = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM asignacion_puestos WHERE id_puesto = ? AND id_empleado = ? AND estado_asignacion = 'ACTIVA'",
                    Integer.class, idPuesto, idAfectado);
            if (pertenece == null || pertenece == 0) {
                throw ApiUtil.error("El guardia seleccionado no tiene una asignacion activa en ese puesto.");
            }
            idCliente = jdbc.queryForObject("SELECT id_cliente FROM puestos_trabajo WHERE id_puesto = ?", Integer.class, idPuesto);
        } else {
            idCliente = ApiUtil.entero(body, "idCliente", "cliente", true);
            Integer cli = jdbc.queryForObject("SELECT COUNT(*) FROM clientes WHERE id_cliente = ?", Integer.class, idCliente);
            if (cli == null || cli == 0) {
                throw ApiUtil.error("El cliente seleccionado no existe.");
            }
        }
        Integer id = jdbc.queryForObject(
                "INSERT INTO incidencias (id_empleado_reporte, id_puesto, id_supervisor_asignado, tipo, descripcion, nivel, "
                        + "id_cliente, id_empleado_afectado) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_incidencia",
                Integer.class, idReporta, idPuesto, idSupervisor, tipo, descripcion, nivel, idCliente, idAfectado);
        auditoria.registrar(idUsuario, "INSERT", "incidencias", id, null,
                tipo + " (" + nivel + ") del cliente #" + idCliente);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensaje", "Incidencia registrada.");
        return r;
    }

    /** El estado solo avanza: ABIERTA -> EN_PROCESO -> RESUELTA. */
    @PutMapping("/{id}/estado")
    public Map<String, String> cambiarEstado(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> fila = jdbc.queryForList(
                "SELECT estado, id_supervisor_asignado FROM incidencias WHERE id_incidencia = ?", id);
        if (fila.isEmpty()) {
            throw ApiUtil.noEncontrado("Incidencia no encontrada.");
        }
        // solo el supervisor asignado a la incidencia puede ponerla en proceso o resolverla (el administrador tambien, por si el
        // supervisor no esta); los guardias ni siquiera tienen acceso a esta pantalla
        Integer idUsuario = auditoria.idUsuarioActual();
        List<Integer> emp = idUsuario == null ? new ArrayList<>()
                : jdbc.queryForList("SELECT id_empleado FROM usuarios WHERE id_usuario = ?", Integer.class, idUsuario);
        Object asignado = fila.get(0).get("id_supervisor_asignado");
        boolean esElSupervisor = !emp.isEmpty() && emp.get(0) != null && asignado != null
                && emp.get(0).equals(((Number) asignado).intValue());
        if (!esElSupervisor && !Permisos.tiene("SEGURIDAD")) {
            throw ApiUtil.error("Solo el supervisor asignado a esta incidencia puede cambiar su estado.");
        }
        List<String> actual = List.of((String) fila.get(0).get("estado"));
        String nuevo = ApiUtil.textoObligatorio(body, "estado", "estado");
        if (!ESTADOS.contains(nuevo)) {
            throw ApiUtil.error("Estado invalido.");
        }
        if (ESTADOS.indexOf(nuevo) <= ESTADOS.indexOf(actual.get(0))) {
            throw ApiUtil.error("La incidencia esta " + actual.get(0) + "; el estado solo puede avanzar (ABIERTA, EN_PROCESO, RESUELTA).");
        }
        jdbc.update("UPDATE incidencias SET estado = ? WHERE id_incidencia = ?", nuevo, id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "incidencias", id, "estado " + actual.get(0), "estado " + nuevo);
        return Map.of("mensaje", "Incidencia " + nuevo + ".");
    }

    /** Solicita un guardia comodin: crea una asignacion temporal de hoy en el mismo puesto y turno. */
    @PostMapping("/{id}/comodin")
    @Transactional
    public Map<String, Object> solicitarComodin(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> inc = jdbc.queryForList(
                "SELECT id_puesto, estado FROM incidencias WHERE id_incidencia = ?", id);
        if (inc.isEmpty()) {
            throw ApiUtil.noEncontrado("Incidencia no encontrada.");
        }
        if ("RESUELTA".equals(inc.get(0).get("estado"))) {
            throw ApiUtil.error("La incidencia ya esta RESUELTA.");
        }
        if (inc.get(0).get("id_puesto") == null) {
            throw ApiUtil.error("La incidencia no esta asociada a un puesto.");
        }
        Integer idPuesto = ((Number) inc.get(0).get("id_puesto")).intValue();
        Integer idReemplazo = ApiUtil.entero(body, "idEmpleadoReemplazo", "guardia comodin", true);
        Integer idAsignacion = ApiUtil.entero(body, "idAsignacion", "asignacion a reemplazar", false);

        if (idAsignacion == null) {
            List<Integer> activas = jdbc.queryForList(
                    "SELECT id_asignacion_puesto FROM asignacion_puestos WHERE id_puesto = ? AND estado_asignacion = 'ACTIVA'",
                    Integer.class, idPuesto);
            if (activas.size() != 1) {
                throw ApiUtil.error("El puesto tiene " + activas.size() + " asignaciones activas; indique cual desea reemplazar.");
            }
            idAsignacion = activas.get(0);
        } else {
            Integer pertenece = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM asignacion_puestos WHERE id_asignacion_puesto = ? AND id_puesto = ?",
                    Integer.class, idAsignacion, idPuesto);
            if (pertenece == null || pertenece == 0) {
                throw ApiUtil.error("La asignacion indicada no pertenece al puesto de la incidencia.");
            }
        }
        Integer idTemp = comodin.asignarReemplazo(idAsignacion, idReemplazo, "Incidencia #" + id);
        if ("ABIERTA".equals(inc.get(0).get("estado"))) {
            jdbc.update("UPDATE incidencias SET estado = 'EN_PROCESO' WHERE id_incidencia = ?", id);
        }
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "incidencias", id, "estado " + inc.get(0).get("estado"),
                "EN_PROCESO (comodin asignado, asignacion #" + idTemp + ")");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", idTemp);
        r.put("mensaje", "Guardia comodin asignado para hoy (asignacion #" + idTemp + ").");
        return r;
    }
}
