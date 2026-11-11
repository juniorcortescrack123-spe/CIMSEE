package com.cimse.inventario;

import com.cimse.config.ApiUtil;
import com.cimse.seguridad.AuditoriaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 3 - Asignacion de equipo al personal y devoluciones (PB-09).
 * Ciclo de vida del activo: DISPONIBLE -> ASIGNADO -> (devolucion) -> DISPONIBLE o MANTENIMIENTO.
 * Cada entrega y devolucion genera un movimiento de inventario y un acta digital.
 */
@RestController
@RequestMapping("/api/equipos")
public class AsignacionEquipoController {

    private static final List<String> ESTADOS_ENTREGA = Arrays.asList("BUENO", "REGULAR", "DANADO");
    private static final List<String> ESTADOS_DEVOLUCION = Arrays.asList("BUENO", "REGULAR", "DANADO", "INCOMPLETO", "PERDIDO");
    private static final String ABIERTA = "a.fecha_hora_devolucion IS NULL AND a.fecha_devolucion IS NULL";

    private static final String SELECT =
            "SELECT a.id_asignacion AS \"id\", a.id_empleado AS \"idEmpleado\", e.nombre_completo AS \"empleado\", "
                    + "e.cargo AS \"cargo\", a.id_activo AS \"idActivo\", i.descripcion AS \"activo\", "
                    + "i.tipo_activo AS \"tipo\", i.numero_serie_o_placa AS \"serieOPlaca\", "
                    + "a.fecha_hora_entrega AS \"fechaEntrega\", a.fecha_hora_devolucion AS \"fechaDevolucion\", "
                    + "a.kilometraje_inicial AS \"kmInicial\", a.kilometraje_final AS \"kmFinal\", "
                    + "a.estado_entrega AS \"estadoEntrega\", a.estado_devolucion AS \"estadoDevolucion\", "
                    + "(a.acta_resguardo_firmada = 'S') AS \"actaFirmada\", a.observaciones AS \"observaciones\", "
                    + "(" + ABIERTA + ") AS \"activa\" "
                    + "FROM asignacion_equipos a "
                    + "JOIN empleados e ON e.id_empleado = a.id_empleado "
                    + "JOIN inventario_activos i ON i.id_activo = a.id_activo ";

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public AsignacionEquipoController(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @GetMapping("/asignaciones")
    public List<Map<String, Object>> listar(@RequestParam(required = false) String estado) {
        StringBuilder sql = new StringBuilder(SELECT + "WHERE 1 = 1");
        if ("ACTIVA".equals(estado)) {
            sql.append(" AND ").append(ABIERTA);
        } else if ("DEVUELTA".equals(estado)) {
            sql.append(" AND NOT (").append(ABIERTA).append(")");
        }
        sql.append(" ORDER BY a.fecha_hora_entrega DESC, a.id_asignacion DESC LIMIT 300");
        return jdbc.queryForList(sql.toString());
    }

    /** Activos que se pueden entregar: disponibles, sin baja y sin una asignacion abierta (los uniformes se manejan aparte). */
    @GetMapping("/disponibles")
    public List<Map<String, Object>> disponibles() {
        return jdbc.queryForList(
                "SELECT i.id_activo AS \"id\", i.tipo_activo AS \"tipo\", i.descripcion AS \"descripcion\", "
                        + "i.numero_serie_o_placa AS \"serieOPlaca\", i.codigo_inventario AS \"codigo\" "
                        + "FROM inventario_activos i WHERE i.estado_activo = 'DISPONIBLE' AND i.fecha_baja IS NULL "
                        + "AND i.tipo_activo <> 'UNIFORME' AND NOT EXISTS (SELECT 1 FROM asignacion_equipos a "
                        + "WHERE a.id_activo = i.id_activo AND " + ABIERTA + ") ORDER BY i.tipo_activo, i.id_activo");
    }

    @GetMapping("/empleados")
    public List<Map<String, Object>> empleados() {
        return jdbc.queryForList(
                "SELECT id_empleado AS \"id\", nombre_completo AS \"nombre\", cargo AS \"cargo\" FROM empleados "
                        + "WHERE activo = 'S' ORDER BY nombre_completo");
    }

    @PostMapping("/asignaciones")
    @Transactional
    public Map<String, Object> asignar(@RequestBody Map<String, Object> body) {
        Integer idEmpleado = ApiUtil.entero(body, "idEmpleado", "empleado", true);
        Integer idActivo = ApiUtil.entero(body, "idActivo", "activo", true);
        Integer idEntrega = ApiUtil.entero(body, "idEmpleadoEntrega", "empleado que entrega", true);
        String estadoEntrega = ApiUtil.texto(body, "estadoEntrega");
        if (estadoEntrega == null) {
            estadoEntrega = "BUENO";
        }
        if (!ESTADOS_ENTREGA.contains(estadoEntrega)) {
            throw ApiUtil.error("El estado de entrega debe ser BUENO, REGULAR o DANADO.");
        }
        String observaciones = ApiUtil.textoObligatorio(body, "observaciones", "observaciones");
        if (observaciones != null && observaciones.length() > 200) {
            throw ApiUtil.error("Las observaciones no pueden pasar de 200 caracteres.");
        }
        boolean actaFirmada = !"false".equalsIgnoreCase(ApiUtil.texto(body, "actaFirmada"));

        List<Map<String, Object>> emp = jdbc.queryForList(
                "SELECT nombre_completo, cargo, activo FROM empleados WHERE id_empleado = ?", idEmpleado);
        if (emp.isEmpty() || !"S".equals(emp.get(0).get("activo"))) {
            throw ApiUtil.error("El empleado no existe o esta inactivo.");
        }
        List<Map<String, Object>> act = jdbc.queryForList(
                "SELECT tipo_activo, estado_activo, fecha_baja, descripcion, ubicacion_actual "
                        + "FROM inventario_activos WHERE id_activo = ?", idActivo);
        if (act.isEmpty()) {
            throw ApiUtil.error("El activo no existe.");
        }
        Map<String, Object> a = act.get(0);
        String tipo = (String) a.get("tipo_activo");
        if ("UNIFORME".equals(tipo)) {
            throw ApiUtil.error("Los uniformes no se asignan desde aqui; son prendas por cantidad.");
        }
        if (a.get("fecha_baja") != null) {
            throw ApiUtil.error("El activo esta dado de baja.");
        }
        if (!"DISPONIBLE".equals(a.get("estado_activo"))) {
            throw ApiUtil.error("El activo no esta DISPONIBLE (estado actual: " + a.get("estado_activo") + ").");
        }
        Integer abiertas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM asignacion_equipos a WHERE a.id_activo = ? AND " + ABIERTA, Integer.class, idActivo);
        if (abiertas != null && abiertas > 0) {
            throw ApiUtil.error("El activo ya tiene una asignacion vigente; registre primero su devolucion.");
        }
        if ("ARMA".equals(tipo)) {
            String cargo = (String) emp.get(0).get("cargo");
            if (!"GUARDIA".equals(cargo) && !"SUPERVISOR".equals(cargo)) {
                throw ApiUtil.error("Las armas solo se asignan a empleados con cargo GUARDIA o SUPERVISOR.");
            }
        }
        if (idEntrega != null) {
            Integer okEntrega = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM empleados WHERE id_empleado = ? AND activo = 'S'", Integer.class, idEntrega);
            if (okEntrega == null || okEntrega == 0) {
                throw ApiUtil.error("El empleado que entrega no existe o esta inactivo.");
            }
        }

        BigDecimal kmInicial = null;
        if ("VEHICULO".equals(tipo)) {
            BigDecimal actual = jdbc.queryForObject(
                    "SELECT COALESCE(kilometraje_actual, 0) FROM vehiculos WHERE id_activo = ?", BigDecimal.class, idActivo);
            if (ApiUtil.texto(body, "kmInicial") == null) {
                kmInicial = actual;
            } else {
                kmInicial = ApiUtil.decimal(body, "kmInicial", "kilometraje inicial");
                if (actual != null && kmInicial.compareTo(actual) < 0) {
                    throw ApiUtil.error("El kilometraje inicial no puede ser menor al actual del vehiculo (" + actual + ").");
                }
            }
        }

        Integer id = jdbc.queryForObject(
                "INSERT INTO asignacion_equipos (id_empleado, id_activo, kilometraje_inicial, acta_resguardo_firmada, "
                        + "observaciones, estado_entrega, id_empleado_entrega, id_empleado_recibe, id_usuario_asigna) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_asignacion",
                Integer.class, idEmpleado, idActivo, kmInicial, actaFirmada ? "S" : "N", observaciones, estadoEntrega,
                idEntrega, idEmpleado, auditoria.idUsuarioActual());

        jdbc.update("UPDATE inventario_activos SET estado_activo = 'ASIGNADO', responsable_actual = ? WHERE id_activo = ?",
                idEmpleado, idActivo);
        if ("VEHICULO".equals(tipo)) {
            jdbc.update("UPDATE vehiculos SET estado_vehiculo = 'ASIGNADO' WHERE id_activo = ?", idActivo);
        }
        jdbc.update("INSERT INTO movimientos_inventario (id_activo, tipo_movimiento, cantidad, ubicacion_origen, ubicacion_destino, "
                        + "motivo, id_empleado_responsable, id_usuario) VALUES (?, 'SALIDA', 1, ?, ?, ?, ?, ?)",
                idActivo, a.get("ubicacion_actual"), emp.get(0).get("nombre_completo"),
                "Entrega de equipo (acta #" + id + ")", idEmpleado, auditoria.idUsuarioActual());
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "asignacion_equipos", id, null,
                a.get("descripcion") + " -> " + emp.get(0).get("nombre_completo"));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensaje", "Equipo entregado. Acta de resguardo #" + id + " generada.");
        return r;
    }

    @PostMapping("/asignaciones/{id}/devolver")
    @Transactional
    public Map<String, Object> devolver(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT a.id_activo, a.kilometraje_inicial, a.observaciones, "
                        + "(" + ABIERTA + ") AS abierta, i.tipo_activo, i.descripcion, e.nombre_completo "
                        + "FROM asignacion_equipos a JOIN inventario_activos i ON i.id_activo = a.id_activo "
                        + "JOIN empleados e ON e.id_empleado = a.id_empleado WHERE a.id_asignacion = ?", id);
        if (filas.isEmpty()) {
            throw ApiUtil.noEncontrado("Asignacion no encontrada.");
        }
        Map<String, Object> f = filas.get(0);
        if (!Boolean.TRUE.equals(f.get("abierta"))) {
            throw ApiUtil.error("Este equipo ya fue devuelto.");
        }
        String estado = ApiUtil.textoObligatorio(body, "estadoDevolucion", "estado de la devolucion");
        if (!ESTADOS_DEVOLUCION.contains(estado)) {
            throw ApiUtil.error("El estado de devolucion debe ser BUENO, REGULAR, DANADO, INCOMPLETO o PERDIDO.");
        }
        Integer idRecibe = ApiUtil.entero(body, "idEmpleadoRecibe", "empleado que recibe", true);
        if (idRecibe != null) {
            Integer ok = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM empleados WHERE id_empleado = ? AND activo = 'S'", Integer.class, idRecibe);
            if (ok == null || ok == 0) {
                throw ApiUtil.error("El empleado que recibe no existe o esta inactivo.");
            }
        }
        String obs = ApiUtil.textoObligatorio(body, "observaciones", "observaciones");
        String observacionesFinal = (String) f.get("observaciones");
        if (obs != null) {
            observacionesFinal = (observacionesFinal == null ? "" : observacionesFinal + " | ") + "Devolucion: " + obs;
            if (observacionesFinal.length() > 200) {
                observacionesFinal = observacionesFinal.substring(0, 200);
            }
        }

        Integer idActivo = ((Number) f.get("id_activo")).intValue();
        String tipo = (String) f.get("tipo_activo");
        BigDecimal kmFinal = null;
        if ("VEHICULO".equals(tipo)) {
            kmFinal = ApiUtil.decimal(body, "kmFinal", "kilometraje final");
            BigDecimal inicial = (BigDecimal) f.get("kilometraje_inicial");
            if (inicial != null && kmFinal.compareTo(inicial) < 0) {
                throw ApiUtil.error("El kilometraje final no puede ser menor al inicial (" + inicial + ").");
            }
        }

        jdbc.update("UPDATE asignacion_equipos SET fecha_devolucion = CURRENT_DATE, fecha_hora_devolucion = CURRENT_TIMESTAMP, "
                        + "estado_devolucion = ?, kilometraje_final = ?, id_empleado_recibe = COALESCE(?, id_empleado_recibe), "
                        + "observaciones = ? WHERE id_asignacion = ?",
                estado, kmFinal, idRecibe, observacionesFinal, id);

        // Estado del activo segun como vuelve
        boolean bueno = "BUENO".equals(estado) || "REGULAR".equals(estado);
        String nuevoEstado = bueno ? "DISPONIBLE" : "MANTENIMIENTO";
        jdbc.update("UPDATE inventario_activos SET estado_activo = ?, responsable_actual = NULL WHERE id_activo = ?",
                nuevoEstado, idActivo);
        if ("PERDIDO".equals(estado)) {
            jdbc.update("UPDATE inventario_activos SET fecha_baja = CURRENT_DATE, motivo_baja = ? WHERE id_activo = ?",
                    "Reportado como perdido en la devolucion (acta #" + id + ")", idActivo);
        }
        if ("ARMA".equals(tipo)) {
            jdbc.update("UPDATE armas SET estado_arma = ? WHERE id_activo = ?",
                    "PERDIDO".equals(estado) ? "BAJA" : (bueno ? "OPERATIVA" : "MANTENIMIENTO"), idActivo);
        } else if ("VEHICULO".equals(tipo)) {
            jdbc.update("UPDATE vehiculos SET estado_vehiculo = ?, kilometraje_actual = ? WHERE id_activo = ?",
                    "PERDIDO".equals(estado) ? "BAJA" : nuevoEstado, kmFinal, idActivo);
        }
        jdbc.update("INSERT INTO movimientos_inventario (id_activo, tipo_movimiento, cantidad, ubicacion_origen, motivo, "
                        + "id_empleado_responsable, id_usuario) VALUES (?, 'DEVOLUCION', 1, ?, ?, ?, ?)",
                idActivo, f.get("nombre_completo"), "Devolucion " + estado + " (acta #" + id + ")", idRecibe,
                auditoria.idUsuarioActual());
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "asignacion_equipos", id,
                "equipo asignado a " + f.get("nombre_completo"), "devuelto en estado " + estado);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensaje", "Devolucion registrada (" + estado + "). El activo quedo " + ("PERDIDO".equals(estado) ? "dado de baja" : nuevoEstado) + ".");
        return r;
    }

    /** Datos para el acta digital de entrega o devolucion. */
    @GetMapping("/asignaciones/{id}/acta")
    public Map<String, Object> acta(@PathVariable Integer id) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT a.id_asignacion AS \"numero\", e.nombre_completo AS \"empleado\", e.identidad AS \"identidad\", "
                        + "e.cargo AS \"cargo\", i.id_activo AS \"idActivo\", i.tipo_activo AS \"tipo\", "
                        + "i.descripcion AS \"activo\", i.numero_serie_o_placa AS \"serieOPlaca\", "
                        + "i.codigo_inventario AS \"codigo\", a.fecha_hora_entrega AS \"fechaEntrega\", "
                        + "a.fecha_hora_devolucion AS \"fechaDevolucion\", a.estado_entrega AS \"estadoEntrega\", "
                        + "a.estado_devolucion AS \"estadoDevolucion\", a.kilometraje_inicial AS \"kmInicial\", "
                        + "a.kilometraje_final AS \"kmFinal\", a.observaciones AS \"observaciones\", "
                        + "(a.acta_resguardo_firmada = 'S') AS \"firmada\", ent.nombre_completo AS \"entrega\", "
                        + "rec.nombre_completo AS \"recibe\" "
                        + "FROM asignacion_equipos a "
                        + "JOIN empleados e ON e.id_empleado = a.id_empleado "
                        + "JOIN inventario_activos i ON i.id_activo = a.id_activo "
                        + "LEFT JOIN empleados ent ON ent.id_empleado = a.id_empleado_entrega "
                        + "LEFT JOIN empleados rec ON rec.id_empleado = a.id_empleado_recibe "
                        + "WHERE a.id_asignacion = ?", id);
        if (filas.isEmpty()) {
            throw ApiUtil.noEncontrado("Acta no encontrada.");
        }
        Map<String, Object> r = new LinkedHashMap<>(filas.get(0));
        String tipo = (String) r.get("tipo");
        Integer idActivo = ((Number) r.get("idActivo")).intValue();
        List<Map<String, Object>> det = new ArrayList<>();
        if ("ARMA".equals(tipo)) {
            det = jdbc.queryForList("SELECT marca AS \"marca\", modelo AS \"modelo\", calibre AS \"calibre\" FROM armas WHERE id_activo = ?", idActivo);
        } else if ("VEHICULO".equals(tipo)) {
            det = jdbc.queryForList("SELECT marca AS \"marca\", modelo AS \"modelo\", color AS \"color\" FROM vehiculos WHERE id_activo = ?", idActivo);
        }
        r.put("detalle", det.isEmpty() ? new LinkedHashMap<String, Object>() : det.get(0));
        return r;
    }
}
