package com.cimse.proveedores;

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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 4 - Compras a proveedores con su detalle de insumos (PB-10).
 * Cada compra pertenece a un proveedor y tiene al menos una linea de detalle; el total lo calcula el servidor.
 * Las compras de PAPELERIA y MATERIALES suben solas las existencias del catalogo de insumos (inventario_materiales).
 * Las armas, vehiculos y uniformes se registran como activos desde Inventario > Activos fijos, usando esta compra.
 * Una compra es un documento contable: no se edita ni se borra; solo se marca como pagada.
 */
@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private static final List<String> CATEGORIAS = Arrays.asList("UNIFORMES", "ARMAS", "VEHICULOS", "PAPELERIA", "MATERIALES");
    private static final List<String> CON_EXISTENCIAS = Arrays.asList("PAPELERIA", "MATERIALES");

    private static final String SELECT =
            "SELECT c.id_compra AS \"id\", c.id_proveedor AS \"idProveedor\", p.nombre AS \"proveedor\", "
                    + "c.categoria_insumo AS \"categoria\", c.numero_factura AS \"factura\", c.monto_total AS \"monto\", "
                    + "c.fecha_compra AS \"fecha\", c.estado_pago AS \"estadoPago\", c.observaciones AS \"observaciones\", "
                    + "e.nombre_completo AS \"autorizo\", (CURRENT_DATE - c.fecha_compra) AS \"dias\" "
                    + "FROM compras_proveedores c JOIN proveedores p ON p.id_proveedor = c.id_proveedor "
                    + "LEFT JOIN empleados e ON e.id_empleado = c.id_empleado_autoriza ";

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public CompraController(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(required = false) Integer idProveedor,
                                            @RequestParam(required = false) String estadoPago) {
        StringBuilder sql = new StringBuilder(SELECT + "WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (idProveedor != null) {
            sql.append(" AND c.id_proveedor = ?");
            args.add(idProveedor);
        }
        if (estadoPago != null && !estadoPago.isBlank()) {
            sql.append(" AND c.estado_pago = ?");
            args.add(estadoPago.trim());
        }
        sql.append(" ORDER BY c.fecha_compra DESC, c.id_compra DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** Proveedores para elegir en el formulario de compra (asi Compras funciona sin el permiso de Proveedores). */
    @GetMapping("/proveedores")
    public List<Map<String, Object>> proveedores() {
        return jdbc.queryForList("SELECT id_proveedor AS \"id\", nombre AS \"nombre\", tipo_proveedor AS \"tipo\" FROM proveedores ORDER BY nombre");
    }

    /** Existencias del catalogo de insumos: suma de lo comprado de cada material. */
    @GetMapping("/existencias")
    public List<Map<String, Object>> existencias() {
        return jdbc.queryForList(
                "SELECT descripcion AS \"descripcion\", COALESCE(MAX(unidad_medida), 'UNIDAD') AS \"unidad\", "
                        + "SUM(cantidad_adquirida) AS \"adquirido\", SUM(stock_actual) AS \"existencia\", "
                        + "MAX(fecha_registro) AS \"ultimaCompra\" "
                        + "FROM inventario_materiales GROUP BY descripcion ORDER BY descripcion");
    }

    @GetMapping("/{id}")
    public Map<String, Object> detalle(@PathVariable Integer id) {
        List<Map<String, Object>> base = jdbc.queryForList(SELECT + "WHERE c.id_compra = ?", id);
        if (base.isEmpty()) {
            throw ApiUtil.noEncontrado("Compra no encontrada.");
        }
        Map<String, Object> r = new LinkedHashMap<>(base.get(0));
        r.put("detalle", jdbc.queryForList(
                "SELECT descripcion AS \"descripcion\", cantidad AS \"cantidad\", unidad_medida AS \"unidad\", "
                        + "precio_unitario AS \"precio\", subtotal AS \"subtotal\" FROM detalle_compras "
                        + "WHERE id_compra = ? ORDER BY id_detalle", id));
        return r;
    }

    /** Registra la compra con su detalle en una sola transaccion. */
    @PostMapping
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> registrar(@RequestBody Map<String, Object> body) {
        Integer idProveedor = ApiUtil.entero(body, "idProveedor", "proveedor", true);
        Integer prov = jdbc.queryForObject("SELECT COUNT(*) FROM proveedores WHERE id_proveedor = ?", Integer.class, idProveedor);
        if (prov == null || prov == 0) {
            throw ApiUtil.error("El proveedor seleccionado no existe.");
        }
        String categoria = ApiUtil.textoObligatorio(body, "categoria", "categoria del insumo");
        if (!CATEGORIAS.contains(categoria)) {
            throw ApiUtil.error("La categoria debe ser UNIFORMES, ARMAS, VEHICULOS, PAPELERIA o MATERIALES.");
        }
        String factura = ApiUtil.textoObligatorio(body, "factura", "numero de factura");
        if (factura.length() > 50) {
            throw ApiUtil.error("El numero de factura no puede pasar de 50 caracteres.");
        }
        Integer repetida = jdbc.queryForObject(
                "SELECT COUNT(*) FROM compras_proveedores WHERE id_proveedor = ? AND numero_factura = ?",
                Integer.class, idProveedor, factura);
        if (repetida != null && repetida > 0) {
            throw ApiUtil.error("Ese proveedor ya tiene registrada la factura " + factura + ".");
        }
        LocalDate fecha = ApiUtil.fecha(body, "fecha", "fecha de compra", true);
        if (fecha.isAfter(LocalDate.now())) {
            throw ApiUtil.error("La fecha de compra no puede ser futura.");
        }
        String observaciones = ApiUtil.textoObligatorio(body, "observaciones", "observaciones");
        if (observaciones.length() > 200) {
            throw ApiUtil.error("Las observaciones no pueden pasar de 200 caracteres.");
        }

        if (!(body.get("detalle") instanceof List<?>) || ((List<?>) body.get("detalle")).isEmpty()) {
            throw ApiUtil.error("Agregue al menos un insumo al detalle de la compra.");
        }
        List<Map<String, Object>> lineas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int n = 0;
        for (Object o : (List<?>) body.get("detalle")) {
            n++;
            if (!(o instanceof Map)) {
                throw ApiUtil.error("Linea " + n + " del detalle invalida.");
            }
            Map<String, Object> l = (Map<String, Object>) o;
            String desc = ApiUtil.textoObligatorio(l, "descripcion", "descripcion del insumo (linea " + n + ")");
            if (desc.length() > 100) {
                throw ApiUtil.error("La descripcion del insumo (linea " + n + ") no puede pasar de 100 caracteres.");
            }
            BigDecimal cantidad = ApiUtil.decimal(l, "cantidad", "cantidad (linea " + n + ")");
            BigDecimal precio = ApiUtil.decimal(l, "precio", "precio unitario (linea " + n + ")");
            if (cantidad.signum() <= 0) {
                throw ApiUtil.error("La cantidad de la linea " + n + " debe ser mayor que cero.");
            }
            if (precio.signum() < 0) {
                throw ApiUtil.error("El precio de la linea " + n + " no puede ser negativo.");
            }
            ApiUtil.textoObligatorio(l, "unidad", "unidad de medida (linea " + n + ")");
            BigDecimal subtotal = cantidad.multiply(precio).setScale(2, RoundingMode.HALF_UP);
            l.put("_subtotal", subtotal);
            total = total.add(subtotal);
            lineas.add(l);
        }
        if (total.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw ApiUtil.error("El total de la compra es demasiado grande.");
        }

        // quien autoriza: el empleado del usuario que registra
        Integer idUsuario = auditoria.idUsuarioActual();
        List<Integer> emp = idUsuario == null ? new ArrayList<>()
                : jdbc.queryForList("SELECT id_empleado FROM usuarios WHERE id_usuario = ?", Integer.class, idUsuario);
        Integer idEmpleado = emp.isEmpty() ? null : emp.get(0);

        Integer id = jdbc.queryForObject(
                "INSERT INTO compras_proveedores (id_proveedor, id_empleado_autoriza, categoria_insumo, numero_factura, "
                        + "monto_total, fecha_compra, estado_pago, observaciones) VALUES (?, ?, ?, ?, ?, ?, 'PENDIENTE', ?) "
                        + "RETURNING id_compra", Integer.class,
                idProveedor, idEmpleado, categoria, factura, total, ApiUtil.sql(fecha), observaciones);

        for (Map<String, Object> l : lineas) {
            BigDecimal cantidad = ApiUtil.decimal(l, "cantidad", "cantidad");
            BigDecimal precio = ApiUtil.decimal(l, "precio", "precio");
            String desc = ApiUtil.texto(l, "descripcion");
            String unidad = ApiUtil.texto(l, "unidad").toUpperCase();
            jdbc.update("INSERT INTO detalle_compras (id_compra, descripcion, cantidad, unidad_medida, precio_unitario, subtotal) "
                    + "VALUES (?, ?, ?, ?, ?, ?)", id, desc, cantidad, unidad, precio, l.get("_subtotal"));
            if (CON_EXISTENCIAS.contains(categoria)) {
                // actualizacion automatica de existencias del catalogo de insumos
                jdbc.update("INSERT INTO inventario_materiales (id_compra, descripcion, cantidad_adquirida, unidad_medida, stock_actual) "
                        + "VALUES (?, ?, ?, ?, ?)", id, desc, cantidad, unidad, cantidad);
            }
        }
        auditoria.registrar(idUsuario, "INSERT", "compras_proveedores", id, null,
                "Compra " + factura + " por L " + total + " (" + lineas.size() + " insumo(s), " + categoria + ")");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("monto", total);
        r.put("mensaje", "Compra registrada por L " + total + "."
                + (CON_EXISTENCIAS.contains(categoria) ? " Las existencias del catalogo de insumos ya se actualizaron." : ""));
        return r;
    }

    /** Marca la compra como pagada (no se puede deshacer). */
    @PutMapping("/{id}/pagar")
    public Map<String, Object> pagar(@PathVariable Integer id) {
        List<Map<String, Object>> c = jdbc.queryForList(
                "SELECT estado_pago, numero_factura FROM compras_proveedores WHERE id_compra = ?", id);
        if (c.isEmpty()) {
            throw ApiUtil.noEncontrado("Compra no encontrada.");
        }
        if ("PAGADO".equals(c.get(0).get("estado_pago"))) {
            throw ApiUtil.error("La compra ya esta pagada.");
        }
        jdbc.update("UPDATE compras_proveedores SET estado_pago = 'PAGADO' WHERE id_compra = ?", id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "compras_proveedores", id,
                "estado PENDIENTE", "estado PAGADO (factura " + c.get(0).get("numero_factura") + ")");
        return Map.of("mensaje", "Compra marcada como pagada.");
    }
}
