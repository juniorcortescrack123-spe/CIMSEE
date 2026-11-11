package com.cimse.reportes;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sprint 4 - Indicadores operativos para el tablero de reportes (PB-12). Todo se calcula al momento desde la base de datos. */
@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    private static final String ASIGNADA_HOY =
            "a.estado_asignacion = 'ACTIVA' AND a.fecha_inicio <= CURRENT_DATE AND (a.fecha_fin IS NULL OR a.fecha_fin >= CURRENT_DATE)";

    private final JdbcTemplate jdbc;

    public ReporteController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private long numero(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private BigDecimal monto(String sql) {
        BigDecimal n = jdbc.queryForObject(sql, BigDecimal.class);
        return n == null ? BigDecimal.ZERO : n;
    }

    @GetMapping("/resumen")
    public Map<String, Object> resumen() {
        Map<String, Object> r = new LinkedHashMap<>();

        Map<String, Object> personal = new LinkedHashMap<>();
        personal.put("activos", numero("SELECT COUNT(*) FROM empleados WHERE activo = 'S'"));
        personal.put("porCargo", jdbc.queryForList("SELECT cargo AS \"etiqueta\", COUNT(*) AS \"valor\" FROM empleados "
                + "WHERE activo = 'S' GROUP BY cargo ORDER BY COUNT(*) DESC"));
        personal.put("guardias", numero("SELECT COUNT(*) FROM empleados WHERE activo = 'S' AND cargo = 'GUARDIA'"));
        personal.put("guardiasAsignados", numero("SELECT COUNT(DISTINCT a.id_empleado) FROM asignacion_puestos a "
                + "JOIN empleados e ON e.id_empleado = a.id_empleado WHERE e.cargo = 'GUARDIA' AND e.activo = 'S' AND " + ASIGNADA_HOY));
        r.put("personal", personal);

        Map<String, Object> comercial = new LinkedHashMap<>();
        comercial.put("clientes", numero("SELECT COUNT(*) FROM clientes"));
        comercial.put("contratosVigentes", numero("SELECT COUNT(*) FROM contratos WHERE estado = 'VIGENTE' AND fecha_vencimiento >= CURRENT_DATE"));
        comercial.put("contratosPorVencer", numero("SELECT COUNT(*) FROM contratos WHERE estado = 'VIGENTE' "
                + "AND fecha_vencimiento BETWEEN CURRENT_DATE AND CURRENT_DATE + 30"));
        comercial.put("contratosVencidos", numero("SELECT COUNT(*) FROM contratos WHERE estado = 'VIGENTE' AND fecha_vencimiento < CURRENT_DATE"));
        comercial.put("ingresoMensual", monto("SELECT COALESCE(SUM(monto_mensual), 0) FROM contratos "
                + "WHERE estado = 'VIGENTE' AND fecha_vencimiento >= CURRENT_DATE"));
        r.put("comercial", comercial);

        Map<String, Object> operaciones = new LinkedHashMap<>();
        operaciones.put("puestosActivos", numero("SELECT COUNT(*) FROM puestos_trabajo WHERE estado = 'ACTIVO'"));
        operaciones.put("puestosSinCobertura", numero("SELECT COUNT(*) FROM puestos_trabajo p WHERE p.estado = 'ACTIVO' AND NOT EXISTS "
                + "(SELECT 1 FROM asignacion_puestos a WHERE a.id_puesto = p.id_puesto AND " + ASIGNADA_HOY + ")"));
        operaciones.put("listaSinCobertura", jdbc.queryForList(
                "SELECT p.descripcion AS \"puesto\", cl.nombre AS \"cliente\", p.ubicacion AS \"ubicacion\" FROM puestos_trabajo p "
                        + "JOIN clientes cl ON cl.id_cliente = p.id_cliente WHERE p.estado = 'ACTIVO' AND NOT EXISTS "
                        + "(SELECT 1 FROM asignacion_puestos a WHERE a.id_puesto = p.id_puesto AND " + ASIGNADA_HOY + ") "
                        + "ORDER BY cl.nombre, p.descripcion LIMIT 10"));
        operaciones.put("incidenciasPorEstado", jdbc.queryForList("SELECT estado AS \"etiqueta\", COUNT(*) AS \"valor\" "
                + "FROM incidencias GROUP BY estado ORDER BY estado"));
        operaciones.put("supervisiones30", numero("SELECT COUNT(*) FROM visitas_supervision WHERE fecha_hora >= CURRENT_DATE - 30"));
        operaciones.put("guardiaAusente30", numero("SELECT COUNT(*) FROM supervision_operativa "
                + "WHERE guardia_presente = 'N' AND fecha_hora >= CURRENT_DATE - 30"));
        r.put("operaciones", operaciones);

        Map<String, Object> inventario = new LinkedHashMap<>();
        inventario.put("porTipo", jdbc.queryForList("SELECT tipo_activo AS \"etiqueta\", COUNT(*) AS \"valor\" FROM inventario_activos "
                + "WHERE fecha_baja IS NULL GROUP BY tipo_activo ORDER BY tipo_activo"));
        inventario.put("porEstado", jdbc.queryForList("SELECT estado_activo AS \"etiqueta\", COUNT(*) AS \"valor\" FROM inventario_activos "
                + "WHERE fecha_baja IS NULL GROUP BY estado_activo ORDER BY estado_activo"));
        inventario.put("valorTotal", monto("SELECT COALESCE(SUM(valor_adquisicion), 0) FROM inventario_activos WHERE fecha_baja IS NULL"));
        r.put("inventario", inventario);

        Map<String, Object> admin = new LinkedHashMap<>();
        admin.put("comprasMes", numero("SELECT COUNT(*) FROM compras_proveedores WHERE date_trunc('month', fecha_compra) = date_trunc('month', CURRENT_DATE)"));
        admin.put("montoComprasMes", monto("SELECT COALESCE(SUM(monto_total), 0) FROM compras_proveedores "
                + "WHERE date_trunc('month', fecha_compra) = date_trunc('month', CURRENT_DATE)"));
        admin.put("comprasPendientes", numero("SELECT COUNT(*) FROM compras_proveedores WHERE estado_pago = 'PENDIENTE'"));
        admin.put("montoComprasPendientes", monto("SELECT COALESCE(SUM(monto_total), 0) FROM compras_proveedores WHERE estado_pago = 'PENDIENTE'"));
        admin.put("pagosPendientes", numero("SELECT COUNT(*) FROM pagos_administrativos WHERE estado <> 'PAGADO'"));
        admin.put("montoPagosPendientes", monto("SELECT COALESCE(SUM(monto), 0) FROM pagos_administrativos WHERE estado <> 'PAGADO'"));
        admin.put("pagosVencidos", numero("SELECT COUNT(*) FROM pagos_administrativos WHERE estado <> 'PAGADO' AND fecha_vencimiento < CURRENT_DATE"));
        admin.put("permisosPorVencer", numero("SELECT COUNT(*) FROM permisos_administrativos "
                + "WHERE fecha_vencimiento BETWEEN CURRENT_DATE AND CURRENT_DATE + 30"));
        admin.put("permisosVencidos", numero("SELECT COUNT(*) FROM permisos_administrativos WHERE fecha_vencimiento < CURRENT_DATE"));
        r.put("administracion", admin);

        return r;
    }

    /** Detalle de lo que vence pronto en todos los modulos, para imprimir junto con el tablero. */
    @GetMapping("/vencimientos")
    public List<Map<String, Object>> vencimientos() {
        return jdbc.queryForList(
                "SELECT * FROM (" +
                        "SELECT 'Contrato' AS \"tipo\", 'Contrato #' || c.id_contrato || ' - ' || cl.nombre AS \"descripcion\", "
                        + "c.fecha_vencimiento AS \"fecha\" FROM contratos c JOIN clientes cl ON cl.id_cliente = c.id_cliente "
                        + "WHERE c.estado = 'VIGENTE' AND c.fecha_vencimiento <= CURRENT_DATE + 30 " +
                        "UNION ALL SELECT 'Permiso legal', tipo || ' ' || numero_permiso, fecha_vencimiento FROM permisos_administrativos "
                        + "WHERE fecha_vencimiento <= CURRENT_DATE + 30 " +
                        "UNION ALL SELECT 'Pago', categoria || ': ' || concepto, fecha_vencimiento FROM pagos_administrativos "
                        + "WHERE estado <> 'PAGADO' AND fecha_vencimiento <= CURRENT_DATE + 30" +
                        ") v ORDER BY \"fecha\"");
    }
}
