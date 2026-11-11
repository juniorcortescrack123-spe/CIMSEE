package com.cimse.proveedores;

import com.cimse.config.ApiUtil;
import com.cimse.config.Validaciones;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 4 - Proveedores (PB-10). Todos los datos son obligatorios. Un proveedor con compras no se elimina,
 * por eso no existe la opcion de borrar. La "persona de enlace" es quien atiende a CIMSE en el proveedor
 * (se guarda en la columna "contacto"; en pantalla no se le llama contacto para no confundirla con un telefono).
 */
@RestController
@RequestMapping("/api/proveedores")
public class ProveedorController {

    private static final String SELECT =
            "SELECT p.id_proveedor AS \"id\", p.tipo_proveedor AS \"tipo\", p.nombre AS \"nombre\", p.rtn AS \"rtn\", "
                    + "p.telefono AS \"telefono\", p.correo AS \"correo\", p.direccion AS \"direccion\", "
                    + "p.contacto AS \"personaEnlace\", "
                    + "(SELECT COUNT(*) FROM compras_proveedores c WHERE c.id_proveedor = p.id_proveedor) AS \"compras\" "
                    + "FROM proveedores p ";

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public ProveedorController(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(required = false) String q) {
        if (q != null && !q.isBlank()) {
            String patron = "%" + q.trim() + "%";
            return jdbc.queryForList(SELECT + "WHERE p.nombre ILIKE ? OR p.rtn ILIKE ? OR p.tipo_proveedor ILIKE ? "
                    + "ORDER BY p.nombre", patron, patron, patron);
        }
        return jdbc.queryForList(SELECT + "ORDER BY p.nombre");
    }

    /** Detalle: datos del proveedor y sus compras. */
    @GetMapping("/{id}")
    public Map<String, Object> detalle(@PathVariable Integer id) {
        List<Map<String, Object>> base = jdbc.queryForList(SELECT + "WHERE p.id_proveedor = ?", id);
        if (base.isEmpty()) {
            throw ApiUtil.noEncontrado("Proveedor no encontrado.");
        }
        Map<String, Object> r = new LinkedHashMap<>(base.get(0));
        r.put("listaCompras", jdbc.queryForList(
                "SELECT id_compra AS \"id\", numero_factura AS \"factura\", categoria_insumo AS \"categoria\", "
                        + "fecha_compra AS \"fecha\", monto_total AS \"monto\", estado_pago AS \"estadoPago\" "
                        + "FROM compras_proveedores WHERE id_proveedor = ? ORDER BY fecha_compra DESC, id_compra DESC", id));
        return r;
    }

    /** Valida y normaliza (RTN 14 digitos, telefono 9999-9999, correo). Deja los valores limpios en el cuerpo. */
    private void validar(Map<String, Object> body, Integer idActual) {
        ApiUtil.textoObligatorio(body, "tipo", "tipo de proveedor");
        String nombre = ApiUtil.textoObligatorio(body, "nombre", "nombre o razon social");
        if (nombre.length() > 100) {
            throw ApiUtil.error("El nombre no puede pasar de 100 caracteres.");
        }
        String rtn = Validaciones.rtn(ApiUtil.textoObligatorio(body, "rtn", "RTN"));
        Integer repetido = idActual == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM proveedores WHERE rtn = ?", Integer.class, rtn)
                : jdbc.queryForObject("SELECT COUNT(*) FROM proveedores WHERE rtn = ? AND id_proveedor <> ?", Integer.class, rtn, idActual);
        if (repetido != null && repetido > 0) {
            throw ApiUtil.error("Ya existe un proveedor con el RTN " + rtn + ".");
        }
        body.put("rtn", rtn);
        body.put("telefono", Validaciones.telefono(ApiUtil.textoObligatorio(body, "telefono", "telefono celular"), "telefono celular"));
        body.put("correo", Validaciones.correo(ApiUtil.textoObligatorio(body, "correo", "correo")));
        ApiUtil.textoObligatorio(body, "direccion", "direccion");
        ApiUtil.textoObligatorio(body, "personaEnlace", "persona de enlace");
    }

    @PostMapping
    public Map<String, Object> registrar(@RequestBody Map<String, Object> body) {
        validar(body, null);
        Integer id = jdbc.queryForObject(
                "INSERT INTO proveedores (tipo_proveedor, nombre, rtn, telefono, direccion, correo, contacto) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id_proveedor", Integer.class,
                ApiUtil.texto(body, "tipo"), ApiUtil.texto(body, "nombre"), ApiUtil.texto(body, "rtn"),
                ApiUtil.texto(body, "telefono"), ApiUtil.texto(body, "direccion"), ApiUtil.texto(body, "correo"),
                ApiUtil.texto(body, "personaEnlace"));
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "proveedores", id, null,
                "Proveedor " + ApiUtil.texto(body, "nombre"));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensaje", "Proveedor registrado.");
        return r;
    }

    @PutMapping("/{id}")
    public Map<String, Object> editar(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> actual = jdbc.queryForList(SELECT + "WHERE p.id_proveedor = ?", id);
        if (actual.isEmpty()) {
            throw ApiUtil.noEncontrado("Proveedor no encontrado.");
        }
        validar(body, id);
        jdbc.update("UPDATE proveedores SET tipo_proveedor = ?, nombre = ?, rtn = ?, telefono = ?, direccion = ?, "
                        + "correo = ?, contacto = ? WHERE id_proveedor = ?",
                ApiUtil.texto(body, "tipo"), ApiUtil.texto(body, "nombre"), ApiUtil.texto(body, "rtn"),
                ApiUtil.texto(body, "telefono"), ApiUtil.texto(body, "direccion"), ApiUtil.texto(body, "correo"),
                ApiUtil.texto(body, "personaEnlace"), id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "proveedores", id,
                "Proveedor " + actual.get(0).get("nombre"), "Proveedor " + ApiUtil.texto(body, "nombre"));
        return Map.of("mensaje", "Proveedor actualizado.");
    }
}
