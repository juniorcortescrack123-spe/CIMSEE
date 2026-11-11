package com.cimse.comercial;

import com.cimse.config.ApiUtil;
import com.cimse.config.Validaciones;
import com.cimse.seguridad.AuditoriaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestion de clientes (completa a ClienteController, que ya listaba y creaba).
 * Un cliente es una persona juridica (con RTN) que puede tener varias personas de enlace y varios contratos.
 * (En pantalla se dice "persona de enlace" y no "contacto", para no confundirlo con un numero de telefono.)
 * Un cliente NO se elimina: tiene historia (contratos, puestos y asignaciones).
 */
@RestController
@RequestMapping("/api/clientes")
public class ClienteGestionController {

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public ClienteGestionController(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    private void validarRtnLibre(String rtn, Integer idActual) {
        Integer n = idActual == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM clientes WHERE rtn = ?", Integer.class, rtn)
                : jdbc.queryForObject("SELECT COUNT(*) FROM clientes WHERE rtn = ? AND id_cliente <> ?",
                Integer.class, rtn, idActual);
        if (n != null && n > 0) {
            throw ApiUtil.error("Ya existe un cliente con el RTN " + rtn + ".");
        }
    }

    /**
     * Valida los datos del cliente (todos obligatorios): nombre, RTN (14 digitos), telefono celular (8 digitos),
     * correo y direccion. Deja en el cuerpo los valores ya normalizados (RTN sin guiones, telefono 9999-9999).
     */
    private void validarCliente(Map<String, Object> body, Integer idActual) {
        ApiUtil.textoObligatorio(body, "nombre", "nombre o razon social");
        String rtn = Validaciones.rtn(ApiUtil.textoObligatorio(body, "rtn", "RTN"));
        validarRtnLibre(rtn, idActual);
        body.put("rtn", rtn);
        body.put("telefono", Validaciones.telefono(ApiUtil.textoObligatorio(body, "telefono", "telefono celular"),
                "telefono celular"));
        body.put("correo", Validaciones.correo(ApiUtil.textoObligatorio(body, "correo", "correo")));
        ApiUtil.textoObligatorio(body, "direccion", "direccion");
    }

    /** Valida y normaliza una persona de enlace del cliente (todos sus datos son obligatorios). */
    private void validarContacto(Map<String, Object> c) {
        ApiUtil.textoObligatorio(c, "nombre", "nombre de la persona de enlace");
        ApiUtil.textoObligatorio(c, "cargo", "cargo de la persona de enlace");
        c.put("telefonoCelular", Validaciones.telefono(
                ApiUtil.textoObligatorio(c, "telefonoCelular", "telefono celular de la persona de enlace"),
                "telefono celular de la persona de enlace"));
        c.put("correo", Validaciones.correo(ApiUtil.textoObligatorio(c, "correo", "correo de la persona de enlace")));
    }

    private void insertarContacto(Integer idCliente, Map<String, Object> c) {
        jdbc.update("INSERT INTO cliente_contactos (id_cliente, nombre, cargo, telefono_celular, correo) VALUES (?, ?, ?, ?, ?)",
                idCliente, ApiUtil.texto(c, "nombre"), ApiUtil.texto(c, "cargo"), ApiUtil.texto(c, "telefonoCelular"),
                ApiUtil.texto(c, "correo"));
    }

    /** Registrar cliente (queda en la auditoria). Debe traer al menos una persona de enlace. */
    @PostMapping("/registrar")
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> registrar(@RequestBody Map<String, Object> body) {
        validarCliente(body, null);
        // se validan todas las personas de enlace ANTES de guardar el cliente
        List<Map<String, Object>> enlaces = new java.util.ArrayList<>();
        if (body.get("contactos") instanceof List<?>) {
            for (Object o : (List<?>) body.get("contactos")) {
                if (o instanceof Map) {
                    Map<String, Object> c = (Map<String, Object>) o;
                    validarContacto(c);
                    enlaces.add(c);
                }
            }
        }
        if (enlaces.isEmpty()) {
            throw ApiUtil.error("Registre al menos una persona de enlace del cliente.");
        }
        String nombre = ApiUtil.texto(body, "nombre");
        Integer id = jdbc.queryForObject(
                "INSERT INTO clientes (nombre, rtn, direccion, telefono, correo) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id_cliente",
                Integer.class, nombre, ApiUtil.texto(body, "rtn"), ApiUtil.texto(body, "direccion"),
                ApiUtil.texto(body, "telefono"), ApiUtil.texto(body, "correo"));
        for (Map<String, Object> c : enlaces) {
            insertarContacto(id, c);
        }
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "clientes", id, null,
                "Cliente " + nombre + " con " + enlaces.size() + " persona(s) de enlace");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensaje", "Cliente registrado.");
        return r;
    }

    @PutMapping("/{id}")
    public Map<String, String> editar(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        List<String> actual = jdbc.queryForList("SELECT nombre FROM clientes WHERE id_cliente = ?", String.class, id);
        if (actual.isEmpty()) {
            throw ApiUtil.noEncontrado("Cliente no encontrado.");
        }
        validarCliente(body, id);
        String nombre = ApiUtil.texto(body, "nombre");
        jdbc.update("UPDATE clientes SET nombre = ?, rtn = ?, direccion = ?, telefono = ?, correo = ? WHERE id_cliente = ?",
                nombre, ApiUtil.texto(body, "rtn"), ApiUtil.texto(body, "direccion"), ApiUtil.texto(body, "telefono"),
                ApiUtil.texto(body, "correo"), id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "clientes", id,
                "Cliente " + actual.get(0), "Cliente " + nombre);
        return Map.of("mensaje", "Cliente actualizado.");
    }

    // ---------------- personas de enlace del cliente (tabla cliente_contactos) ----------------

    @GetMapping("/{id}/contactos")
    public List<Map<String, Object>> contactos(@PathVariable Integer id) {
        return jdbc.queryForList(
                "SELECT id_contacto AS \"id\", nombre AS \"nombre\", cargo AS \"cargo\", "
                        + "telefono_celular AS \"telefonoCelular\", correo AS \"correo\" "
                        + "FROM cliente_contactos WHERE id_cliente = ? ORDER BY id_contacto", id);
    }

    @PostMapping("/{id}/contactos")
    public Map<String, Object> agregarContacto(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM clientes WHERE id_cliente = ?", Integer.class, id);
        if (existe == null || existe == 0) {
            throw ApiUtil.noEncontrado("Cliente no encontrado.");
        }
        validarContacto(body);
        insertarContacto(id, body);
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "cliente_contactos", id, null,
                "Persona de enlace " + ApiUtil.texto(body, "nombre") + " del cliente #" + id);
        return Map.of("mensaje", "Persona de enlace agregada.");
    }

    @DeleteMapping("/contactos/{idContacto}")
    public Map<String, String> quitarContacto(@PathVariable Integer idContacto) {
        List<Map<String, Object>> c = jdbc.queryForList(
                "SELECT id_cliente, nombre FROM cliente_contactos WHERE id_contacto = ?", idContacto);
        if (c.isEmpty()) {
            throw ApiUtil.noEncontrado("Persona de enlace no encontrada.");
        }
        Integer restantes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cliente_contactos WHERE id_cliente = ?", Integer.class, c.get(0).get("id_cliente"));
        if (restantes != null && restantes <= 1) {
            throw ApiUtil.error("El cliente debe conservar al menos una persona de enlace. Agregue otra antes de quitar esta.");
        }
        jdbc.update("DELETE FROM cliente_contactos WHERE id_contacto = ?", idContacto);
        auditoria.registrar(auditoria.idUsuarioActual(), "DELETE", "cliente_contactos", idContacto,
                "Persona de enlace " + c.get(0).get("nombre"), null);
        return Map.of("mensaje", "Persona de enlace eliminada.");
    }

    /** Detalle del cliente: contactos, contratos, puestos y guardias asignados a cada puesto. */
    @GetMapping("/{id}/detalle")
    public Map<String, Object> detalle(@PathVariable Integer id) {
        List<Map<String, Object>> cliente = jdbc.queryForList(
                "SELECT id_cliente AS \"id\", nombre AS \"nombre\", rtn AS \"rtn\", direccion AS \"direccion\", "
                        + "telefono AS \"telefono\", correo AS \"correo\", fecha_registro AS \"fechaRegistro\" "
                        + "FROM clientes WHERE id_cliente = ?", id);
        if (cliente.isEmpty()) {
            throw ApiUtil.noEncontrado("Cliente no encontrado.");
        }
        Map<String, Object> r = new LinkedHashMap<>(cliente.get(0));
        r.put("contactos", contactos(id));
        r.put("contratos", jdbc.queryForList(
                "SELECT id_contrato AS \"id\", fecha_inicio AS \"fechaInicio\", fecha_vencimiento AS \"fechaVencimiento\", "
                        + "monto_mensual AS \"montoMensual\", estado AS \"estado\" FROM contratos "
                        + "WHERE id_cliente = ? ORDER BY fecha_inicio DESC", id));
        List<Map<String, Object>> puestos = jdbc.queryForList(
                "SELECT p.id_puesto AS \"id\", p.descripcion AS \"descripcion\", p.ubicacion AS \"ubicacion\", "
                        + "p.tipo_turno AS \"tipoTurno\", p.estado AS \"estado\" "
                        + "FROM puestos_trabajo p WHERE p.id_cliente = ? ORDER BY p.id_puesto", id);
        for (Map<String, Object> p : puestos) {
            p.put("guardias", jdbc.queryForList(
                    "SELECT e.nombre_completo AS \"nombre\", t.nombre AS \"turno\" FROM asignacion_puestos a "
                            + "JOIN empleados e ON e.id_empleado = a.id_empleado "
                            + "LEFT JOIN turnos t ON t.id_turno = a.id_turno "
                            + "WHERE a.id_puesto = ? AND a.estado_asignacion = 'ACTIVA' ORDER BY t.id_turno", p.get("id")));
        }
        r.put("puestos", puestos);
        return r;
    }
}
