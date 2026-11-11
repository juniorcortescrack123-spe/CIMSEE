package com.cimse.comercial;

import com.cimse.config.ApiUtil;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 2 - Contratos de servicio y alertas de vencimiento (PB-05 y PB-06).
 * Semaforo: VENCIDO (ya paso la fecha), ROJO (7 dias o menos), AMARILLO (30 dias o menos), VERDE.
 */
@RestController
@RequestMapping("/api/contratos")
public class ContratoController {

    private static final String SELECT =
            "SELECT c.id_contrato AS \"id\", c.id_cliente AS \"idCliente\", cl.nombre AS \"cliente\", "
                    + "c.fecha_inicio AS \"fechaInicio\", c.fecha_vencimiento AS \"fechaVencimiento\", "
                    + "c.duracion_anios AS \"duracionAnios\", c.monto_mensual AS \"montoMensual\", "
                    + "c.dia_pago_estipulado AS \"diaPago\", c.cai_factura AS \"cai\", c.estado AS \"estado\", "
                    + "c.observaciones AS \"observaciones\", (c.fecha_vencimiento - CURRENT_DATE) AS \"diasRestantes\", "
                    + "(SELECT COUNT(*) FROM puestos_trabajo p WHERE p.id_cliente = c.id_cliente AND p.estado = 'ACTIVO') AS \"puestos\" "
                    + "FROM contratos c JOIN clientes cl ON cl.id_cliente = c.id_cliente ";

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public ContratoController(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    private static String semaforo(Object estado, Object dias) {
        if (!"VIGENTE".equals(estado)) {
            return String.valueOf(estado);
        }
        int d = ((Number) dias).intValue();
        if (d < 0) {
            return "VENCIDO";
        }
        if (d <= 7) {
            return "ROJO";
        }
        if (d <= 30) {
            return "AMARILLO";
        }
        return "VERDE";
    }

    private static List<Map<String, Object>> conSemaforo(List<Map<String, Object>> filas) {
        for (Map<String, Object> f : filas) {
            f.put("semaforo", semaforo(f.get("estado"), f.get("diasRestantes")));
        }
        return filas;
    }

    @GetMapping
    public List<Map<String, Object>> listar() {
        return conSemaforo(jdbc.queryForList(SELECT + "ORDER BY c.fecha_vencimiento"));
    }

    /** Contratos VIGENTES que vencen dentro de los proximos N dias (30 por defecto), incluidos los ya vencidos. */
    /** Clientes para elegir en el contrato (asi Contratos funciona sin el permiso de Gestion de clientes). */
    @GetMapping("/clientes")
    public List<Map<String, Object>> clientes() {
        return jdbc.queryForList("SELECT id_cliente AS \"idCliente\", nombre AS \"nombre\", rtn AS \"rtn\" FROM clientes ORDER BY nombre");
    }

    @GetMapping("/alertas")
    public List<Map<String, Object>> alertas(@RequestParam(defaultValue = "30") int dias) {
        int limite = Math.max(1, Math.min(dias, 365));
        return conSemaforo(jdbc.queryForList(
                SELECT + "WHERE c.estado = 'VIGENTE' AND c.fecha_vencimiento <= CURRENT_DATE + ? "
                        + "ORDER BY c.fecha_vencimiento", limite));
    }

    @GetMapping("/{id}")
    public Map<String, Object> obtener(@PathVariable Integer id) {
        List<Map<String, Object>> filas = conSemaforo(jdbc.queryForList(SELECT + "WHERE c.id_contrato = ?", id));
        if (filas.isEmpty()) {
            throw ApiUtil.noEncontrado("Contrato no encontrado.");
        }
        return filas.get(0);
    }

    private void validarCliente(Integer idCliente) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM clientes WHERE id_cliente = ?", Integer.class, idCliente);
        if (n == null || n == 0) {
            throw ApiUtil.error("El cliente seleccionado no existe.");
        }
    }

    /** Duracion en anios: la indicada o, si no viene, la que resulta de las fechas (solo se permite 1 o 2). */
    private int duracion(Map<String, Object> body, LocalDate inicio, LocalDate fin) {
        Integer d = ApiUtil.entero(body, "duracionAnios", "duracion en anios", false);
        if (d == null) {
            long meses = ChronoUnit.MONTHS.between(inicio, fin);
            d = meses <= 12 ? 1 : 2;
        }
        if (d != 1 && d != 2) {
            throw ApiUtil.error("La duracion del contrato debe ser de 1 o 2 anios.");
        }
        return d;
    }

    private void validarCampos(LocalDate inicio, LocalDate fin, BigDecimal monto, Integer diaPago) {
        if (!fin.isAfter(inicio)) {
            throw ApiUtil.error("La fecha de vencimiento debe ser posterior a la fecha de inicio.");
        }
        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiUtil.error("El monto mensual debe ser mayor que cero.");
        }
        if (diaPago != 15 && diaPago != 30) {
            throw ApiUtil.error("El dia de pago estipulado debe ser 15 o 30.");
        }
    }

    @PostMapping
    public Map<String, Object> crear(@RequestBody Map<String, Object> body) {
        Integer idCliente = ApiUtil.entero(body, "idCliente", "cliente", true);
        validarCliente(idCliente);
        LocalDate inicio = ApiUtil.fecha(body, "fechaInicio", "fecha de inicio", true);
        LocalDate fin = ApiUtil.fecha(body, "fechaVencimiento", "fecha de vencimiento", true);
        BigDecimal monto = ApiUtil.decimal(body, "montoMensual", "monto mensual");
        Integer diaPago = ApiUtil.entero(body, "diaPago", "dia de pago", true);
        validarCampos(inicio, fin, monto, diaPago);
        int dur = duracion(body, inicio, fin);

        Integer id = jdbc.queryForObject(
                "INSERT INTO contratos (id_cliente, fecha_inicio, fecha_vencimiento, duracion_anios, monto_mensual, "
                        + "dia_pago_estipulado, cai_factura, estado, observaciones) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'VIGENTE', ?) RETURNING id_contrato",
                Integer.class, idCliente, ApiUtil.sql(inicio), ApiUtil.sql(fin), dur, monto, diaPago,
                ApiUtil.textoObligatorio(body, "cai", "CAI de factura"), ApiUtil.textoObligatorio(body, "observaciones", "observaciones"));
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "contratos", id, null,
                "Contrato del cliente #" + idCliente + " del " + inicio + " al " + fin);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensaje", "Contrato registrado.");
        return r;
    }

    @PutMapping("/{id}")
    public Map<String, String> editar(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Map<String, Object> antes = obtener(id);
        Integer idCliente = ApiUtil.entero(body, "idCliente", "cliente", true);
        validarCliente(idCliente);
        LocalDate inicio = ApiUtil.fecha(body, "fechaInicio", "fecha de inicio", true);
        LocalDate fin = ApiUtil.fecha(body, "fechaVencimiento", "fecha de vencimiento", true);
        BigDecimal monto = ApiUtil.decimal(body, "montoMensual", "monto mensual");
        Integer diaPago = ApiUtil.entero(body, "diaPago", "dia de pago", true);
        validarCampos(inicio, fin, monto, diaPago);
        int dur = duracion(body, inicio, fin);
        String estado = ApiUtil.texto(body, "estado");
        if (estado == null) {
            estado = (String) antes.get("estado");
        }
        if (!estado.equals("VIGENTE") && !estado.equals("VENCIDO") && !estado.equals("RENOVADO")) {
            throw ApiUtil.error("El estado debe ser VIGENTE, VENCIDO o RENOVADO.");
        }

        jdbc.update("UPDATE contratos SET id_cliente = ?, fecha_inicio = ?, fecha_vencimiento = ?, duracion_anios = ?, "
                        + "monto_mensual = ?, dia_pago_estipulado = ?, cai_factura = ?, estado = ?, observaciones = ? "
                        + "WHERE id_contrato = ?",
                idCliente, ApiUtil.sql(inicio), ApiUtil.sql(fin), dur, monto, diaPago, ApiUtil.textoObligatorio(body, "cai", "CAI de factura"),
                estado, ApiUtil.textoObligatorio(body, "observaciones", "observaciones"), id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "contratos", id,
                "vence " + antes.get("fechaVencimiento") + ", estado " + antes.get("estado"),
                "vence " + fin + ", estado " + estado);
        return Map.of("mensaje", "Contrato actualizado.");
    }

    /** Renueva: el contrato actual pasa a RENOVADO y se crea uno nuevo VIGENTE que empieza cuando vencia el anterior. */
    @PostMapping("/{id}/renovar")
    @Transactional
    public Map<String, Object> renovar(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Map<String, Object> actual = obtener(id);
        if (!"VIGENTE".equals(actual.get("estado"))) {
            throw ApiUtil.error("Solo se puede renovar un contrato VIGENTE.");
        }
        LocalDate inicio = ((java.sql.Date) actual.get("fechaVencimiento")).toLocalDate();
        LocalDate fin = ApiUtil.fecha(body, "fechaVencimiento", "nueva fecha de vencimiento", true);
        if (!fin.isAfter(inicio)) {
            throw ApiUtil.error("La nueva fecha de vencimiento debe ser posterior al vencimiento actual (" + inicio + ").");
        }
        long meses = ChronoUnit.MONTHS.between(inicio, fin);
        if (meses > 24) {
            throw ApiUtil.error("La renovacion no puede exceder 2 anios.");
        }
        int dur = meses <= 12 ? 1 : 2;
        BigDecimal monto = ApiUtil.texto(body, "montoMensual") == null
                ? (BigDecimal) actual.get("montoMensual") : ApiUtil.decimal(body, "montoMensual", "monto mensual");
        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiUtil.error("El monto mensual debe ser mayor que cero.");
        }

        jdbc.update("UPDATE contratos SET estado = 'RENOVADO' WHERE id_contrato = ?", id);
        Integer nuevo = jdbc.queryForObject(
                "INSERT INTO contratos (id_cliente, fecha_inicio, fecha_vencimiento, duracion_anios, monto_mensual, "
                        + "dia_pago_estipulado, cai_factura, estado, observaciones) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'VIGENTE', ?) RETURNING id_contrato",
                Integer.class, actual.get("idCliente"), ApiUtil.sql(inicio), ApiUtil.sql(fin), dur, monto,
                actual.get("diaPago"), actual.get("cai"), "Renovacion del contrato #" + id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "contratos", id, "estado VIGENTE",
                "RENOVADO; nuevo contrato #" + nuevo + " hasta " + fin);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", nuevo);
        r.put("mensaje", "Contrato renovado. Nuevo contrato #" + nuevo + " vigente hasta " + fin + ".");
        return r;
    }
}
