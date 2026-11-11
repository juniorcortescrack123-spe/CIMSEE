package com.cimse.comercial;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Sprint 2 - Recorrido de la cadena relacional CLIENTE -> CONTRATO -> PUESTO -> ASIGNACION <- GUARDIA. */
@RestController
@RequestMapping("/api/comercial")
public class CadenaController {

    private final JdbcTemplate jdbc;

    public CadenaController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/cadena")
    public List<Map<String, Object>> cadena() {
        return jdbc.queryForList(
                "SELECT cl.id_cliente AS \"idCliente\", cl.nombre AS \"cliente\", c.id_contrato AS \"idContrato\", "
                        + "c.estado AS \"estadoContrato\", c.fecha_vencimiento AS \"vencimiento\", "
                        + "p.id_puesto AS \"idPuesto\", p.descripcion AS \"puesto\", "
                        + "a.id_asignacion_puesto AS \"idAsignacion\", t.nombre AS \"turno\", "
                        + "e.id_empleado AS \"idGuardia\", e.nombre_completo AS \"guardia\" "
                        + "FROM clientes cl "
                        + "LEFT JOIN contratos c ON c.id_cliente = cl.id_cliente "
                        + "LEFT JOIN puestos_trabajo p ON p.id_cliente = cl.id_cliente "
                        + "LEFT JOIN asignacion_puestos a ON a.id_puesto = p.id_puesto AND a.estado_asignacion = 'ACTIVA' "
                        + "LEFT JOIN turnos t ON t.id_turno = a.id_turno "
                        + "LEFT JOIN empleados e ON e.id_empleado = a.id_empleado "
                        + "ORDER BY cl.nombre, c.id_contrato, p.id_puesto, t.id_turno");
    }
}
