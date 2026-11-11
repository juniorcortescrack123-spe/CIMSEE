package com.cimse.administracion;

import com.cimse.config.ApiUtil;
import com.cimse.seguridad.AuditoriaService;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 4 - Pagos administrativos (RAP, IHSS, canon, alcaldia y otros) con su fecha limite (PB-11).
 * Un pago puede estar ligado a un permiso legal (por ejemplo, la renovacion del permiso de la alcaldia).
 * Un pago registrado no se elimina: se marca como PAGADO con su fecha de pago.
 */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private static final List<String> CATEGORIAS = Arrays.asList("RAP", "IHSS", "CANON", "ALCALDIA", "OTRO");

    private static final String SELECT =
            "SELECT p.id_pago_admin AS \"id\", p.concepto AS \"concepto\", p.categoria AS \"categoria\", "
                    + "p.id_permiso AS \"idPermiso\", pe.numero_permiso AS \"permiso\", "
                    + "p.fecha_vencimiento AS \"fechaVencimiento\", p.fecha_pago AS \"fechaPago\", p.monto AS \"monto\", "
                    + "p.estado AS \"estado\", p.observaciones AS \"observaciones\", "
                    + "(p.fecha_vencimiento - CURRENT_DATE) AS \"diasRestantes\" "
                    + "FROM pagos_administrativos p LEFT JOIN permisos_administrativos pe ON pe.id_permiso = p.id_permiso ";

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public PagoController(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    /** Un pago pendiente cuya fecha limite ya paso pasa a VENCIDO. */
    private void actualizarEstados() {
        jdbc.update("UPDATE pagos_administrativos SET estado = 'VENCIDO' "
                + "WHERE estado = 'PENDIENTE' AND fecha_vencimiento < CURRENT_DATE");
    }

    private List<Map<String, Object>> conSemaforo(List<Map<String, Object>> filas) {
        List<Map<String, Object>> r = new ArrayList<>();
        for (Map<String, Object> f : filas) {
            Map<String, Object> m = new LinkedHashMap<>(f);
            m.put("semaforo", "PAGADO".equals(f.get("estado")) ? "PAGADO"
                    : PermisoLegalController.semaforo(((Number) f.get("diasRestantes")).intValue()));
            r.add(m);
        }
        return r;
    }

    /** Permisos legales para relacionar un pago (asi Pagos funciona sin el permiso de Permisos legales). */
    @GetMapping("/permisos")
    public List<Map<String, Object>> permisos() {
        return jdbc.queryForList("SELECT id_permiso AS \"id\", tipo AS \"tipo\", numero_permiso AS \"numero\" "
                + "FROM permisos_administrativos ORDER BY fecha_vencimiento");
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(required = false) String estado,
                                            @RequestParam(required = false) String categoria) {
        actualizarEstados();
        StringBuilder sql = new StringBuilder(SELECT + "WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (estado != null && !estado.isBlank()) {
            sql.append(" AND p.estado = ?");
            args.add(estado.trim());
        }
        if (categoria != null && !categoria.isBlank()) {
            sql.append(" AND p.categoria = ?");
            args.add(categoria.trim());
        }
        sql.append(" ORDER BY CASE WHEN p.estado = 'PAGADO' THEN 1 ELSE 0 END, p.fecha_vencimiento");
        return conSemaforo(jdbc.queryForList(sql.toString(), args.toArray()));
    }

    private Integer validar(Map<String, Object> body) {
        String concepto = ApiUtil.textoObligatorio(body, "concepto", "concepto");
        if (concepto.length() > 100) {
            throw ApiUtil.error("El concepto no puede pasar de 100 caracteres.");
        }
        String categoria = ApiUtil.textoObligatorio(body, "categoria", "categoria");
        if (!CATEGORIAS.contains(categoria)) {
            throw ApiUtil.error("La categoria debe ser RAP, IHSS, CANON, ALCALDIA u OTRO.");
        }
        ApiUtil.fecha(body, "fechaVencimiento", "fecha limite de pago", true);
        BigDecimal monto = ApiUtil.decimal(body, "monto", "monto");
        if (monto.signum() <= 0 || monto.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw ApiUtil.error("El monto debe ser mayor que cero.");
        }
        String obs = ApiUtil.textoObligatorio(body, "observaciones", "observaciones");
        if (obs.length() > 200) {
            throw ApiUtil.error("Las observaciones no pueden pasar de 200 caracteres.");
        }
        // el permiso es opcional: solo se llena cuando el pago corresponde a un permiso legal
        Integer idPermiso = ApiUtil.entero(body, "idPermiso", "permiso", false);
        if (idPermiso != null) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM permisos_administrativos WHERE id_permiso = ?",
                    Integer.class, idPermiso);
            if (n == null || n == 0) {
                throw ApiUtil.error("El permiso seleccionado no existe.");
            }
        }
        return idPermiso;
    }

    @PostMapping
    public Map<String, Object> registrar(@RequestBody Map<String, Object> body) {
        Integer idPermiso = validar(body);
        LocalDate limite = ApiUtil.fecha(body, "fechaVencimiento", "fecha limite de pago", true);
        Integer id = jdbc.queryForObject(
                "INSERT INTO pagos_administrativos (id_permiso, concepto, categoria, fecha_vencimiento, monto, estado, observaciones) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id_pago_admin", Integer.class,
                idPermiso, ApiUtil.texto(body, "concepto"), ApiUtil.texto(body, "categoria"), ApiUtil.sql(limite),
                ApiUtil.decimal(body, "monto", "monto"), limite.isBefore(LocalDate.now()) ? "VENCIDO" : "PENDIENTE",
                ApiUtil.texto(body, "observaciones"));
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "pagos_administrativos", id, null,
                ApiUtil.texto(body, "categoria") + ": " + ApiUtil.texto(body, "concepto") + " por L "
                        + ApiUtil.decimal(body, "monto", "monto") + " (limite " + limite + ")");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensaje", "Pago registrado.");
        return r;
    }

    /** Solo se edita un pago que todavia no esta pagado. */
    @PutMapping("/{id}")
    public Map<String, Object> editar(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> actual = jdbc.queryForList(SELECT + "WHERE p.id_pago_admin = ?", id);
        if (actual.isEmpty()) {
            throw ApiUtil.noEncontrado("Pago no encontrado.");
        }
        if ("PAGADO".equals(actual.get(0).get("estado"))) {
            throw ApiUtil.error("Un pago ya realizado no se puede modificar.");
        }
        Integer idPermiso = validar(body);
        LocalDate limite = ApiUtil.fecha(body, "fechaVencimiento", "fecha limite de pago", true);
        jdbc.update("UPDATE pagos_administrativos SET id_permiso = ?, concepto = ?, categoria = ?, fecha_vencimiento = ?, "
                        + "monto = ?, estado = ?, observaciones = ? WHERE id_pago_admin = ?",
                idPermiso, ApiUtil.texto(body, "concepto"), ApiUtil.texto(body, "categoria"), ApiUtil.sql(limite),
                ApiUtil.decimal(body, "monto", "monto"), limite.isBefore(LocalDate.now()) ? "VENCIDO" : "PENDIENTE",
                ApiUtil.texto(body, "observaciones"), id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "pagos_administrativos", id,
                "monto " + actual.get(0).get("monto"), "monto " + ApiUtil.decimal(body, "monto", "monto"));
        return Map.of("mensaje", "Pago actualizado.");
    }

    /** Registra el pago realizado (fecha de pago: hoy si no se indica). */
    @PutMapping("/{id}/pagar")
    public Map<String, Object> pagar(@PathVariable Integer id, @RequestBody(required = false) Map<String, Object> body) {
        List<Map<String, Object>> actual = jdbc.queryForList(SELECT + "WHERE p.id_pago_admin = ?", id);
        if (actual.isEmpty()) {
            throw ApiUtil.noEncontrado("Pago no encontrado.");
        }
        if ("PAGADO".equals(actual.get(0).get("estado"))) {
            throw ApiUtil.error("Este pago ya fue registrado como pagado.");
        }
        LocalDate fecha = body == null ? null : ApiUtil.fecha(body, "fechaPago", "fecha de pago", false);
        if (fecha == null) {
            fecha = LocalDate.now();
        }
        if (fecha.isAfter(LocalDate.now())) {
            throw ApiUtil.error("La fecha de pago no puede ser futura.");
        }
        jdbc.update("UPDATE pagos_administrativos SET estado = 'PAGADO', fecha_pago = ? WHERE id_pago_admin = ?",
                ApiUtil.sql(fecha), id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "pagos_administrativos", id,
                "estado " + actual.get(0).get("estado"), "PAGADO el " + fecha);
        return Map.of("mensaje", "Pago registrado como pagado.");
    }
}
