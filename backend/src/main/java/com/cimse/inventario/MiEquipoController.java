package com.cimse.inventario;

import com.cimse.seguridad.AuditoriaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** El equipo (vehiculo, arma, etc.) que tiene asignado a su nombre cualquier persona con sesion: gerentes, supervisores, guardias... */
@RestController
@RequestMapping("/api/mi-equipo")
public class MiEquipoController {

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public MiEquipoController(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @GetMapping
    public List<Map<String, Object>> miEquipo() {
        Integer idUsuario = auditoria.idUsuarioActual();
        List<Integer> emp = idUsuario == null ? new ArrayList<>()
                : jdbc.queryForList("SELECT id_empleado FROM usuarios WHERE id_usuario = ?", Integer.class, idUsuario);
        if (emp.isEmpty() || emp.get(0) == null) {
            return new ArrayList<>();
        }
        return jdbc.queryForList(
                "SELECT e.id_asignacion AS \"idAsignacion\", i.descripcion AS \"descripcion\", i.tipo_activo AS \"tipo\", "
                        + "i.numero_serie_o_placa AS \"serieOPlaca\", i.codigo_inventario AS \"codigo\", e.fecha_entrega AS \"fechaEntrega\" "
                        + "FROM asignacion_equipos e JOIN inventario_activos i ON i.id_activo = e.id_activo "
                        + "WHERE e.id_empleado = ? AND e.fecha_devolucion IS NULL ORDER BY e.fecha_entrega DESC", emp.get(0));
    }
}
