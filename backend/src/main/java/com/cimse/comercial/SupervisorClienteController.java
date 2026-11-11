package com.cimse.comercial;

import com.cimse.config.ApiUtil;
import com.cimse.seguridad.AuditoriaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Supervisores a cargo de cada cliente (revision del 3 de noviembre). Un cliente puede tener mas de un supervisor
 * (por ejemplo una universidad con varios campus) y un supervisor varios clientes. El supervisor solo puede hacer
 * visitas a los clientes que tiene asignados.
 */
@RestController
@RequestMapping("/api/clientes/{idCliente}/supervisores")
public class SupervisorClienteController {

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public SupervisorClienteController(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    private void verificarCliente(Integer idCliente) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM clientes WHERE id_cliente = ?", Integer.class, idCliente);
        if (n == null || n == 0) {
            throw ApiUtil.noEncontrado("Cliente no encontrado.");
        }
    }

    /** Supervisores asignados y supervisores que todavia se pueden asignar. */
    @GetMapping
    public Map<String, Object> listar(@PathVariable Integer idCliente) {
        verificarCliente(idCliente);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("asignados", jdbc.queryForList(
                "SELECT e.id_empleado AS \"id\", e.nombre_completo AS \"nombre\" FROM supervisor_clientes sc "
                        + "JOIN empleados e ON e.id_empleado = sc.id_supervisor WHERE sc.id_cliente = ? ORDER BY e.nombre_completo", idCliente));
        r.put("disponibles", jdbc.queryForList(
                "SELECT e.id_empleado AS \"id\", e.nombre_completo AS \"nombre\" FROM empleados e WHERE e.activo = 'S' AND "
                        + "(e.cargo = 'SUPERVISOR' OR e.id_empleado IN (SELECT u.id_empleado FROM usuarios u "
                        + "JOIN usuario_roles ur ON ur.id_usuario = u.id_usuario JOIN roles r ON r.id_rol = ur.id_rol "
                        + "WHERE r.nombre = 'SUPERVISOR' AND u.id_empleado IS NOT NULL)) "
                        + "AND e.id_empleado NOT IN (SELECT id_supervisor FROM supervisor_clientes WHERE id_cliente = ?) "
                        + "ORDER BY e.nombre_completo", idCliente));
        return r;
    }

    @PostMapping
    public Map<String, Object> asignar(@PathVariable Integer idCliente, @RequestBody Map<String, Object> body) {
        verificarCliente(idCliente);
        Integer idSupervisor = ApiUtil.entero(body, "idSupervisor", "supervisor", true);
        Integer ok = jdbc.queryForObject(
                "SELECT COUNT(*) FROM empleados e WHERE e.id_empleado = ? AND e.activo = 'S' AND "
                        + "(e.cargo = 'SUPERVISOR' OR e.id_empleado IN (SELECT u.id_empleado FROM usuarios u "
                        + "JOIN usuario_roles ur ON ur.id_usuario = u.id_usuario JOIN roles r ON r.id_rol = ur.id_rol "
                        + "WHERE r.nombre = 'SUPERVISOR' AND u.id_empleado IS NOT NULL))", Integer.class, idSupervisor);
        if (ok == null || ok == 0) {
            throw ApiUtil.error("Seleccione un empleado activo con cargo o rol de supervisor.");
        }
        Integer ya = jdbc.queryForObject("SELECT COUNT(*) FROM supervisor_clientes WHERE id_supervisor = ? AND id_cliente = ?",
                Integer.class, idSupervisor, idCliente);
        if (ya != null && ya > 0) {
            throw ApiUtil.error("Ese supervisor ya esta a cargo de este cliente.");
        }
        jdbc.update("INSERT INTO supervisor_clientes (id_supervisor, id_cliente) VALUES (?, ?)", idSupervisor, idCliente);
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "supervisor_clientes", idCliente, null,
                "Supervisor #" + idSupervisor + " asignado al cliente #" + idCliente);
        return Map.of("mensaje", "Supervisor asignado al cliente.");
    }

    @DeleteMapping("/{idSupervisor}")
    public Map<String, Object> quitar(@PathVariable Integer idCliente, @PathVariable Integer idSupervisor) {
        int n = jdbc.update("DELETE FROM supervisor_clientes WHERE id_supervisor = ? AND id_cliente = ?", idSupervisor, idCliente);
        if (n == 0) {
            throw ApiUtil.noEncontrado("Ese supervisor no esta asignado a este cliente.");
        }
        auditoria.registrar(auditoria.idUsuarioActual(), "DELETE", "supervisor_clientes", idCliente,
                "Supervisor #" + idSupervisor, "quitado del cliente #" + idCliente);
        return Map.of("mensaje", "Supervisor quitado del cliente.");
    }
}
