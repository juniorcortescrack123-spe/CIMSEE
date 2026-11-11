package com.cimse.comercial;

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Sprint 2 - Puestos de trabajo de cada cliente (eslabon CONTRATO -> PUESTO de la cadena relacional). */
@RestController
@RequestMapping("/api/puestos")
public class PuestoController {

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public PuestoController(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(required = false) Integer idCliente) {
        StringBuilder sql = new StringBuilder(
                "SELECT p.id_puesto AS \"id\", p.id_cliente AS \"idCliente\", cl.nombre AS \"cliente\", "
                        + "p.descripcion AS \"descripcion\", p.ubicacion AS \"ubicacion\", p.tipo_turno AS \"tipoTurno\", "
                        + "p.horas_contratadas AS \"horas\", p.estado AS \"estado\", "
                        + "(SELECT COUNT(*) FROM asignacion_puestos a WHERE a.id_puesto = p.id_puesto "
                        + "AND a.estado_asignacion = 'ACTIVA') AS \"guardias\" "
                        + "FROM puestos_trabajo p JOIN clientes cl ON cl.id_cliente = p.id_cliente");
        List<Object> args = new ArrayList<>();
        if (idCliente != null) {
            sql.append(" WHERE p.id_cliente = ?");
            args.add(idCliente);
        }
        sql.append(" ORDER BY cl.nombre, p.id_puesto");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    private void validar(Map<String, Object> body, Integer idCliente, String tipo) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM clientes WHERE id_cliente = ?", Integer.class, idCliente);
        if (n == null || n == 0) {
            throw ApiUtil.error("El cliente seleccionado no existe.");
        }
        if (!"12h".equals(tipo) && !"24h".equals(tipo)) {
            throw ApiUtil.error("El tipo de turno debe ser 12h o 24h.");
        }
    }

    @PostMapping
    public Map<String, Object> crear(@RequestBody Map<String, Object> body) {
        Integer idCliente = ApiUtil.entero(body, "idCliente", "cliente", true);
        String descripcion = ApiUtil.textoObligatorio(body, "descripcion", "descripcion");
        String tipo = ApiUtil.textoObligatorio(body, "tipoTurno", "tipo de turno");
        validar(body, idCliente, tipo);
        Integer horas = ApiUtil.entero(body, "horas", "horas contratadas", false);
        if (horas == null) {
            horas = "24h".equals(tipo) ? 24 : 12;
        }
        if (horas <= 0 || horas > 24) {
            throw ApiUtil.error("Las horas contratadas deben estar entre 1 y 24.");
        }
        Integer id = jdbc.queryForObject(
                "INSERT INTO puestos_trabajo (id_cliente, descripcion, ubicacion, tipo_turno, horas_contratadas, estado) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVO') RETURNING id_puesto",
                Integer.class, idCliente, descripcion, ApiUtil.textoObligatorio(body, "ubicacion", "ubicacion"), tipo, horas);
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "puestos_trabajo", id, null,
                "Puesto " + descripcion + " del cliente #" + idCliente);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensaje", "Puesto registrado.");
        return r;
    }

    @PutMapping("/{id}")
    public Map<String, String> editar(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> actual = jdbc.queryForList(
                "SELECT descripcion, estado FROM puestos_trabajo WHERE id_puesto = ?", id);
        if (actual.isEmpty()) {
            throw ApiUtil.error("Puesto no encontrado.");
        }
        Integer idCliente = ApiUtil.entero(body, "idCliente", "cliente", true);
        String descripcion = ApiUtil.textoObligatorio(body, "descripcion", "descripcion");
        String tipo = ApiUtil.textoObligatorio(body, "tipoTurno", "tipo de turno");
        validar(body, idCliente, tipo);
        Integer horas = ApiUtil.entero(body, "horas", "horas contratadas", false);
        if (horas == null) {
            horas = "24h".equals(tipo) ? 24 : 12;
        }
        if (horas <= 0 || horas > 24) {
            throw ApiUtil.error("Las horas contratadas deben estar entre 1 y 24.");
        }
        String estado = ApiUtil.texto(body, "estado");
        if (estado == null) {
            estado = (String) actual.get(0).get("estado");
        }
        if (!"ACTIVO".equals(estado) && !"INACTIVO".equals(estado)) {
            throw ApiUtil.error("El estado debe ser ACTIVO o INACTIVO.");
        }
        if ("INACTIVO".equals(estado)) {
            Integer activas = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM asignacion_puestos WHERE id_puesto = ? AND estado_asignacion = 'ACTIVA'",
                    Integer.class, id);
            if (activas != null && activas > 0) {
                throw ApiUtil.error("No se puede inactivar: el puesto tiene " + activas
                        + " guardia(s) asignado(s). Finalice primero las asignaciones.");
            }
        }
        jdbc.update("UPDATE puestos_trabajo SET id_cliente = ?, descripcion = ?, ubicacion = ?, tipo_turno = ?, "
                        + "horas_contratadas = ?, estado = ? WHERE id_puesto = ?",
                idCliente, descripcion, ApiUtil.texto(body, "ubicacion"), tipo, horas, estado, id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "puestos_trabajo", id,
                actual.get(0).get("descripcion") + " (" + actual.get(0).get("estado") + ")",
                descripcion + " (" + estado + ")");
        return Map.of("mensaje", "Puesto actualizado.");
    }
}
