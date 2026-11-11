package com.cimse.operaciones;

import com.cimse.config.ApiUtil;
import com.cimse.seguridad.AuditoriaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 2 - Asignacion de guardias a puestos de trabajo (PB-04).
 * Regla de negocio: un guardia no puede tener dos asignaciones ACTIVAS que se traslapen en fechas
 * dentro del mismo turno (la base de datos tambien lo impide con una restriccion EXCLUDE).
 */
@RestController
@RequestMapping("/api/asignaciones")
public class AsignacionController {

    private static final String SELECT =
            "SELECT a.id_asignacion_puesto AS \"id\", a.id_empleado AS \"idEmpleado\", e.nombre_completo AS \"guardia\", "
                    + "a.id_puesto AS \"idPuesto\", p.descripcion AS \"puesto\", cl.nombre AS \"cliente\", "
                    + "a.id_turno AS \"idTurno\", t.nombre AS \"turno\", a.fecha_inicio AS \"fechaInicio\", "
                    + "a.fecha_fin AS \"fechaFin\", a.estado_asignacion AS \"estado\", a.observaciones AS \"observaciones\" "
                    + "FROM asignacion_puestos a "
                    + "JOIN empleados e ON e.id_empleado = a.id_empleado "
                    + "JOIN puestos_trabajo p ON p.id_puesto = a.id_puesto "
                    + "JOIN clientes cl ON cl.id_cliente = p.id_cliente "
                    + "LEFT JOIN turnos t ON t.id_turno = a.id_turno ";

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public AsignacionController(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(required = false) String estado,
                                            @RequestParam(required = false) Integer idPuesto) {
        StringBuilder sql = new StringBuilder(SELECT + "WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND a.estado_asignacion = ?");
            args.add(estado.trim());
        }
        if (idPuesto != null) {
            sql.append(" AND a.id_puesto = ?");
            args.add(idPuesto);
        }
        sql.append(" ORDER BY a.fecha_registro DESC, a.id_asignacion_puesto DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** Guardias activos con el numero de asignaciones vigentes que ya tienen. */
    @GetMapping("/guardias")
    public List<Map<String, Object>> guardias() {
        return jdbc.queryForList(
                "SELECT e.id_empleado AS \"id\", e.nombre_completo AS \"nombre\", e.identidad AS \"identidad\", "
                        + "(SELECT COUNT(*) FROM asignacion_puestos a WHERE a.id_empleado = e.id_empleado "
                        + "AND a.estado_asignacion = 'ACTIVA') AS \"asignaciones\" "
                        + "FROM empleados e WHERE e.activo = 'S' AND e.cargo = 'GUARDIA' ORDER BY e.nombre_completo");
    }

    @PostMapping
    public Map<String, Object> asignar(@RequestBody Map<String, Object> body) {
        Integer idEmpleado = ApiUtil.entero(body, "idEmpleado", "guardia", true);
        Integer idPuesto = ApiUtil.entero(body, "idPuesto", "puesto", true);
        Integer idTurno = ApiUtil.entero(body, "idTurno", "turno", true);
        LocalDate inicio = ApiUtil.fecha(body, "fechaInicio", "fecha de inicio", true);
        LocalDate fin = ApiUtil.fecha(body, "fechaFin", "fecha de fin", false);
        if (fin != null && fin.isBefore(inicio)) {
            throw ApiUtil.error("La fecha de fin no puede ser anterior a la fecha de inicio.");
        }

        List<Map<String, Object>> emp = jdbc.queryForList(
                "SELECT nombre_completo, cargo, activo FROM empleados WHERE id_empleado = ?", idEmpleado);
        if (emp.isEmpty()) {
            throw ApiUtil.error("El guardia seleccionado no existe.");
        }
        if (!"S".equals(emp.get(0).get("activo"))) {
            throw ApiUtil.error("El empleado esta inactivo y no puede ser asignado.");
        }
        if (!"GUARDIA".equals(emp.get(0).get("cargo"))) {
            throw ApiUtil.error("Solo se pueden asignar empleados con cargo GUARDIA a un puesto.");
        }
        List<Map<String, Object>> pue = jdbc.queryForList(
                "SELECT descripcion, estado FROM puestos_trabajo WHERE id_puesto = ?", idPuesto);
        if (pue.isEmpty()) {
            throw ApiUtil.error("El puesto seleccionado no existe.");
        }
        if (!"ACTIVO".equals(pue.get(0).get("estado"))) {
            throw ApiUtil.error("El puesto esta INACTIVO.");
        }
        List<String> tur = jdbc.queryForList("SELECT nombre FROM turnos WHERE id_turno = ?", String.class, idTurno);
        if (tur.isEmpty()) {
            throw ApiUtil.error("El turno seleccionado no existe.");
        }

        // No sobreposicion: mismo guardia, mismo turno, fechas que se cruzan, asignacion ACTIVA
        List<Object> args = new ArrayList<>();
        args.add(idEmpleado);
        args.add(idTurno);
        args.add(ApiUtil.sql(inicio));
        String finSql = "'infinity'::date";
        if (fin != null) {
            finSql = "?";
            args.add(ApiUtil.sql(fin));
        }
        Integer choques = jdbc.queryForObject(
                "SELECT COUNT(*) FROM asignacion_puestos WHERE id_empleado = ? AND id_turno = ? "
                        + "AND estado_asignacion = 'ACTIVA' "
                        + "AND daterange(fecha_inicio, COALESCE(fecha_fin, 'infinity'::date), '[]') "
                        + "&& daterange(?, " + finSql + ", '[]')",
                Integer.class, args.toArray());
        if (choques != null && choques > 0) {
            throw ApiUtil.error("Sobreposicion de turnos: " + emp.get(0).get("nombre_completo")
                    + " ya tiene una asignacion ACTIVA en el turno " + tur.get(0)
                    + " que se cruza con esas fechas.");
        }

        Integer id = jdbc.queryForObject(
                "INSERT INTO asignacion_puestos (id_empleado, id_puesto, fecha_inicio, fecha_fin, turno_real, "
                        + "observaciones, id_turno, estado_asignacion, id_usuario_asigna) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVA', ?) RETURNING id_asignacion_puesto",
                Integer.class, idEmpleado, idPuesto, ApiUtil.sql(inicio), ApiUtil.sql(fin), tur.get(0),
                ApiUtil.texto(body, "observaciones"), idTurno, auditoria.idUsuarioActual());
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "asignacion_puestos", id, null,
                emp.get(0).get("nombre_completo") + " -> " + pue.get(0).get("descripcion") + " (" + tur.get(0)
                        + ") desde " + inicio);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensaje", "Guardia asignado al puesto.");
        return r;
    }

    /**
     * Asignacion en lote: varios guardias a los puestos de un cliente en una sola operacion (como una tabla).
     * Si una fila falla no se guarda ninguna, y el mensaje indica cual fila tiene el problema.
     */
    @PostMapping("/lote")
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> asignarLote(@RequestBody Map<String, Object> body) {
        if (!(body.get("asignaciones") instanceof List<?>) || ((List<?>) body.get("asignaciones")).isEmpty()) {
            throw ApiUtil.error("Agregue al menos una fila (guardia, puesto y turno).");
        }
        Integer idCliente = ApiUtil.entero(body, "idCliente", "cliente", false);
        List<Integer> ids = new ArrayList<>();
        int n = 0;
        for (Object o : (List<?>) body.get("asignaciones")) {
            n++;
            if (!(o instanceof Map)) {
                throw ApiUtil.error("Fila " + n + ": datos invalidos.");
            }
            Map<String, Object> fila = new LinkedHashMap<>((Map<String, Object>) o);
            try {
                if (idCliente != null) {
                    Integer idPuesto = ApiUtil.entero(fila, "idPuesto", "puesto", true);
                    Integer pertenece = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM puestos_trabajo WHERE id_puesto = ? AND id_cliente = ?",
                            Integer.class, idPuesto, idCliente);
                    if (pertenece == null || pertenece == 0) {
                        throw ApiUtil.error("El puesto no pertenece a este cliente.");
                    }
                }
                String ubicacion = ApiUtil.textoObligatorio(fila, "ubicacion", "ubicacion exacta");
                String obs = ApiUtil.texto(fila, "observaciones");
                String texto = (ubicacion != null ? "Ubicacion exacta: " + ubicacion : "")
                        + (ubicacion != null && obs != null ? ". " : "") + (obs != null ? obs : "");
                if (texto.length() > 200) {
                    throw ApiUtil.error("La ubicacion y las observaciones no pueden pasar de 200 caracteres.");
                }
                fila.put("observaciones", texto.isEmpty() ? null : texto);
                ids.add((Integer) asignar(fila).get("id"));
            } catch (ResponseStatusException e) {
                throw ApiUtil.error("Fila " + n + ": " + e.getReason());
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ids", ids);
        r.put("mensaje", ids.size() + " guardia(s) asignado(s) correctamente.");
        return r;
    }

    private Map<String, Object> activa(Integer id) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT fecha_inicio, estado_asignacion FROM asignacion_puestos WHERE id_asignacion_puesto = ?", id);
        if (filas.isEmpty()) {
            throw ApiUtil.noEncontrado("Asignacion no encontrada.");
        }
        if (!"ACTIVA".equals(filas.get(0).get("estado_asignacion"))) {
            throw ApiUtil.error("La asignacion ya no esta ACTIVA.");
        }
        return filas.get(0);
    }

    /** Termina la asignacion (por defecto hoy, o la fecha indicada). */
    @PutMapping("/{id}/finalizar")
    public Map<String, String> finalizar(@PathVariable Integer id,
                                         @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> a = activa(id);
        LocalDate inicio = ((java.sql.Date) a.get("fecha_inicio")).toLocalDate();
        LocalDate fin = ApiUtil.fecha(body, "fechaFin", "fecha de fin", false);
        if (fin == null) {
            fin = LocalDate.now();
        }
        if (fin.isBefore(inicio)) {
            fin = inicio;
        }
        jdbc.update("UPDATE asignacion_puestos SET estado_asignacion = 'FINALIZADA', fecha_fin = ? "
                + "WHERE id_asignacion_puesto = ?", ApiUtil.sql(fin), id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "asignacion_puestos", id,
                "estado ACTIVA", "FINALIZADA el " + fin);
        return Map.of("mensaje", "Asignacion finalizada.");
    }

    @PutMapping("/{id}/cancelar")
    public Map<String, String> cancelar(@PathVariable Integer id) {
        activa(id);
        jdbc.update("UPDATE asignacion_puestos SET estado_asignacion = 'CANCELADA' WHERE id_asignacion_puesto = ?", id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "asignacion_puestos", id,
                "estado ACTIVA", "CANCELADA");
        return Map.of("mensaje", "Asignacion cancelada.");
    }
}
