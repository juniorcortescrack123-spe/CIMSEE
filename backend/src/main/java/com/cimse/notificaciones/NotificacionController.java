package com.cimse.notificaciones;

import com.cimse.config.ApiUtil;
import com.cimse.config.Permisos;
import com.cimse.seguridad.AuditoriaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sprint 4 - Centro de notificaciones (PB-12) y alertas automaticas de vencimiento (PB-11).
 * Cada vez que se consulta, el servidor revisa contratos, permisos legales, pagos y matriculas de vehiculos y deja
 * en la tabla ALERTAS_VENCIMIENTO una alerta por cada uno que venza en 30 dias o menos (o que ya haya vencido).
 * Si el contrato se renueva o el pago se realiza, la alerta se cierra sola. Cada persona solo ve las alertas
 * de los modulos a los que tiene permiso.
 */
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private static final int DIAS_ANTICIPACION = 30;

    /** Tipo de alerta -> permiso necesario para verla, nombre y pantalla donde se atiende. */
    private static final Map<String, String[]> TIPOS = new LinkedHashMap<>();

    static {
        TIPOS.put("CONTRATO", new String[]{"CONTRATOS", "Contrato", "/comercial/alertas"});
        TIPOS.put("PERMISO", new String[]{"PERMISOS_LEGALES", "Permiso legal", "/administracion/permisos"});
        TIPOS.put("PAGO", new String[]{"PAGOS", "Pago", "/administracion/pagos"});
        TIPOS.put("MATRICULA", new String[]{"INVENTARIO", "Matricula de vehiculo", "/inventario/activos"});
    }

    private record Candidato(String tipo, int id, LocalDate fecha, String descripcion) {
        String clave() {
            return tipo + "|" + id + "|" + fecha;
        }
    }

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public NotificacionController(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    private static String prioridad(long dias) {
        if (dias < 0) {
            return "CRITICA";
        }
        if (dias <= 7) {
            return "ALTA";
        }
        return dias <= 30 ? "MEDIA" : "BAJA";
    }

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static LocalDate aFecha(Object o) {
        return ((Date) o).toLocalDate();
    }

    /** Lo que hoy esta por vencer o vencido, tomado directamente de las tablas del sistema. */
    private List<Candidato> candidatos() {
        List<Candidato> r = new ArrayList<>();
        for (Map<String, Object> f : jdbc.queryForList(
                "SELECT c.id_contrato, cl.nombre, c.fecha_vencimiento FROM contratos c JOIN clientes cl ON cl.id_cliente = c.id_cliente "
                        + "WHERE c.estado = 'VIGENTE' AND c.fecha_vencimiento <= CURRENT_DATE + " + DIAS_ANTICIPACION)) {
            r.add(new Candidato("CONTRATO", ((Number) f.get("id_contrato")).intValue(), aFecha(f.get("fecha_vencimiento")),
                    "Contrato #" + f.get("id_contrato") + " de " + f.get("nombre")));
        }
        for (Map<String, Object> f : jdbc.queryForList(
                "SELECT id_permiso, tipo, numero_permiso, fecha_vencimiento FROM permisos_administrativos "
                        + "WHERE fecha_vencimiento <= CURRENT_DATE + " + DIAS_ANTICIPACION)) {
            r.add(new Candidato("PERMISO", ((Number) f.get("id_permiso")).intValue(), aFecha(f.get("fecha_vencimiento")),
                    "Permiso " + f.get("tipo") + " " + f.get("numero_permiso")));
        }
        for (Map<String, Object> f : jdbc.queryForList(
                "SELECT id_pago_admin, categoria, concepto, monto, fecha_vencimiento FROM pagos_administrativos "
                        + "WHERE estado <> 'PAGADO' AND fecha_vencimiento <= CURRENT_DATE + " + DIAS_ANTICIPACION)) {
            r.add(new Candidato("PAGO", ((Number) f.get("id_pago_admin")).intValue(), aFecha(f.get("fecha_vencimiento")),
                    f.get("categoria") + ": " + f.get("concepto") + " (L " + f.get("monto") + ")"));
        }
        for (Map<String, Object> f : jdbc.queryForList(
                "SELECT a.id_activo, v.placa, v.marca, v.modelo, v.fecha_vencimiento_matricula FROM vehiculos v "
                        + "JOIN inventario_activos a ON a.id_activo = v.id_activo "
                        + "WHERE a.fecha_baja IS NULL AND v.fecha_vencimiento_matricula IS NOT NULL "
                        + "AND v.fecha_vencimiento_matricula <= CURRENT_DATE + " + DIAS_ANTICIPACION)) {
            r.add(new Candidato("MATRICULA", ((Number) f.get("id_activo")).intValue(), aFecha(f.get("fecha_vencimiento_matricula")),
                    "Matricula del vehiculo " + f.get("placa") + " (" + f.get("marca") + " " + f.get("modelo") + ")"));
        }
        return r;
    }

    /** Pone la tabla ALERTAS_VENCIMIENTO al dia: crea las alertas nuevas, actualiza prioridades y cierra las resueltas. */
    private synchronized void sincronizar() {
        LocalDate hoy = LocalDate.now();
        Map<String, Candidato> actuales = new HashMap<>();
        for (Candidato c : candidatos()) {
            actuales.put(c.clave(), c);
        }
        Set<String> conocidas = new HashSet<>();
        for (Map<String, Object> a : jdbc.queryForList(
                "SELECT id_alerta, tipo_entidad, id_referencia, fecha_vencimiento, estado FROM alertas_vencimiento")) {
            String clave = a.get("tipo_entidad") + "|" + ((Number) a.get("id_referencia")).intValue() + "|" + aFecha(a.get("fecha_vencimiento"));
            conocidas.add(clave);
            if (!"PENDIENTE".equals(a.get("estado"))) {
                continue;
            }
            Candidato c = actuales.get(clave);
            Integer id = ((Number) a.get("id_alerta")).intValue();
            if (c == null) {
                jdbc.update("UPDATE alertas_vencimiento SET estado = 'ATENDIDA', fecha_atencion = CURRENT_DATE, "
                        + "observaciones = 'Resuelta automaticamente (renovado, pagado o dado de baja)' WHERE id_alerta = ?", id);
            } else {
                jdbc.update("UPDATE alertas_vencimiento SET prioridad = ?, descripcion = ? WHERE id_alerta = ?",
                        prioridad(ChronoUnit.DAYS.between(hoy, c.fecha())), recortar(c.descripcion(), 200), id);
            }
        }
        for (Candidato c : actuales.values()) {
            if (!conocidas.contains(c.clave())) {
                jdbc.update("INSERT INTO alertas_vencimiento (tipo_entidad, id_referencia, descripcion, fecha_vencimiento, "
                                + "dias_anticipacion, fecha_alerta, prioridad, estado) VALUES (?, ?, ?, ?, ?, CURRENT_DATE, ?, 'PENDIENTE')",
                        c.tipo(), c.id(), recortar(c.descripcion(), 200), ApiUtil.sql(c.fecha()), DIAS_ANTICIPACION,
                        prioridad(ChronoUnit.DAYS.between(hoy, c.fecha())));
            }
        }
    }

    /** Tipos de alerta que esta persona puede ver segun sus permisos. */
    private List<String> tiposPermitidos() {
        List<String> r = new ArrayList<>();
        for (Map.Entry<String, String[]> e : TIPOS.entrySet()) {
            if (Permisos.tiene(e.getValue()[0])) {
                r.add(e.getKey());
            }
        }
        return r;
    }

    private static String marcas(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(defaultValue = "PENDIENTE") String estado,
                                            @RequestParam(required = false) String tipo) {
        sincronizar();
        List<String> permitidos = tiposPermitidos();
        if (tipo != null && !tipo.isBlank()) {
            permitidos.retainAll(List.of(tipo.trim()));
        }
        if (permitidos.isEmpty()) {
            return new ArrayList<>();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT a.id_alerta AS \"id\", a.tipo_entidad AS \"tipo\", a.id_referencia AS \"referencia\", "
                        + "a.descripcion AS \"descripcion\", a.fecha_vencimiento AS \"fechaVencimiento\", "
                        + "(a.fecha_vencimiento - CURRENT_DATE) AS \"diasRestantes\", a.prioridad AS \"prioridad\", "
                        + "a.estado AS \"estado\", a.fecha_atencion AS \"fechaAtencion\", a.observaciones AS \"observaciones\", "
                        + "u.nombre_usuario AS \"atendio\" FROM alertas_vencimiento a "
                        + "LEFT JOIN usuarios u ON u.id_usuario = a.id_usuario_atendio WHERE a.tipo_entidad IN ("
                        + marcas(permitidos.size()) + ")");
        List<Object> args = new ArrayList<>(permitidos);
        if (!"TODAS".equalsIgnoreCase(estado)) {
            sql.append(" AND a.estado = ?");
            args.add(estado.trim().toUpperCase());
        }
        sql.append(" ORDER BY CASE a.estado WHEN 'PENDIENTE' THEN 0 ELSE 1 END, a.fecha_vencimiento, a.id_alerta");
        List<Map<String, Object>> r = new ArrayList<>();
        for (Map<String, Object> f : jdbc.queryForList(sql.toString(), args.toArray())) {
            Map<String, Object> m = new LinkedHashMap<>(f);
            String[] info = TIPOS.get(String.valueOf(f.get("tipo")));
            m.put("tipoNombre", info == null ? String.valueOf(f.get("tipo")) : info[1]);
            m.put("ruta", info == null ? "/dashboard" : info[2]);
            r.add(m);
        }
        return r;
    }

    private Integer empleadoActual() {
        Integer idUsuario = auditoria.idUsuarioActual();
        List<Integer> r = idUsuario == null ? new ArrayList<>()
                : jdbc.queryForList("SELECT id_empleado FROM usuarios WHERE id_usuario = ?", Integer.class, idUsuario);
        return r.isEmpty() ? null : r.get(0);
    }

    private boolean tieneRol(String nombre) {
        Integer idUsuario = auditoria.idUsuarioActual();
        Integer n = idUsuario == null ? 0 : jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuario_roles ur JOIN roles r ON r.id_rol = ur.id_rol WHERE ur.id_usuario = ? AND r.nombre = ?",
                Integer.class, idUsuario, nombre);
        return n != null && n > 0;
    }

    private static Map<String, Object> aviso(String tipo, String titulo, Object fecha, String ruta) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tipo", tipo);
        m.put("titulo", titulo);
        m.put("fecha", fecha);
        m.put("ruta", ruta);
        return m;
    }

    /**
     * Avisos para la persona que entra (no son vencimientos): el supervisor ve las solicitudes de sus guardias esperando respuesta
     * y las incidencias abiertas que le asignaron; el guardia ve las respuestas nuevas a sus solicitudes.
     */
    private List<Map<String, Object>> avisosDePersona() {
        List<Map<String, Object>> r = new ArrayList<>();
        Integer emp = empleadoActual();
        if (emp == null) {
            return r;
        }
        if (Permisos.tiene("VISITAS")) {
            for (Map<String, Object> f : jdbc.queryForList(
                    "SELECT g.nombre_completo, s.tipo, s.fecha_solicitud FROM solicitudes_guardia s JOIN empleados g ON g.id_empleado = s.id_empleado "
                            + "WHERE s.id_supervisor = ? AND s.estado = 'PENDIENTE' ORDER BY s.fecha_solicitud", emp)) {
                r.add(aviso("SOLICITUD", f.get("nombre_completo") + " pide " + String.valueOf(f.get("tipo")).toLowerCase().replace('_', ' '),
                        f.get("fecha_solicitud"), "/operaciones/solicitudes"));
            }
            for (Map<String, Object> f : jdbc.queryForList(
                    "SELECT i.tipo, i.fecha_hora, rep.nombre_completo FROM incidencias i JOIN empleados rep ON rep.id_empleado = i.id_empleado_reporte "
                            + "WHERE i.id_supervisor_asignado = ? AND i.estado = 'ABIERTA' AND i.id_empleado_reporte <> ? ORDER BY i.fecha_hora", emp, emp)) {
                r.add(aviso("INCIDENCIA", "Incidencia abierta reportada por " + f.get("nombre_completo") + " (" + String.valueOf(f.get("tipo")).toLowerCase().replace('_', ' ') + ")",
                        f.get("fecha_hora"), "/operaciones/incidencias"));
            }
        }
        // Los gerentes ven lo que mandan los supervisores y los guardias, aunque no sea a su nombre
        if (tieneRol("GERENTE_GENERAL") || tieneRol("GERENTE_OPERACIONES")) {
            for (Map<String, Object> f : jdbc.queryForList(
                    "SELECT i.tipo, i.fecha_hora, rep.nombre_completo, COALESCE(cl.nombre, cl2.nombre) AS cliente FROM incidencias i "
                            + "JOIN empleados rep ON rep.id_empleado = i.id_empleado_reporte "
                            + "LEFT JOIN puestos_trabajo p ON p.id_puesto = i.id_puesto LEFT JOIN clientes cl ON cl.id_cliente = p.id_cliente "
                            + "LEFT JOIN clientes cl2 ON cl2.id_cliente = i.id_cliente "
                            + "WHERE i.estado = 'ABIERTA' AND (i.id_supervisor_asignado IS NULL OR i.id_supervisor_asignado <> ?) "
                            + "ORDER BY i.fecha_hora DESC LIMIT 15", emp)) {
                r.add(aviso("INCIDENCIA", "Incidencia abierta en " + f.get("cliente") + " (" + String.valueOf(f.get("tipo")).toLowerCase().replace('_', ' ')
                        + "), reportada por " + f.get("nombre_completo"), f.get("fecha_hora"), "/operaciones/incidencias"));
            }
            for (Map<String, Object> f : jdbc.queryForList(
                    "SELECT v.fecha_hora, sup.nombre_completo AS supervisor, cl.nombre AS cliente, "
                            + "(SELECT COUNT(*) FROM supervision_operativa s WHERE s.id_visita = v.id_visita AND s.guardia_presente = 'N') AS ausentes, "
                            + "(SELECT COUNT(*) FROM incidencias i WHERE i.id_visita = v.id_visita) AS incidencias "
                            + "FROM visitas_supervision v JOIN empleados sup ON sup.id_empleado = v.id_supervisor JOIN clientes cl ON cl.id_cliente = v.id_cliente "
                            + "WHERE v.fecha_hora >= CURRENT_TIMESTAMP - INTERVAL '2 days' AND v.id_supervisor <> ? "
                            + "AND ((SELECT COUNT(*) FROM supervision_operativa s WHERE s.id_visita = v.id_visita AND s.guardia_presente = 'N') > 0 "
                            + "OR (SELECT COUNT(*) FROM incidencias i WHERE i.id_visita = v.id_visita) > 0) ORDER BY v.fecha_hora DESC LIMIT 15", emp)) {
                r.add(aviso("VISITA", f.get("supervisor") + " visito " + f.get("cliente") + ": " + f.get("ausentes") + " ausente(s), "
                        + f.get("incidencias") + " incidencia(s)", f.get("fecha_hora"), "/operaciones/supervisiones"));
            }
            for (Map<String, Object> f : jdbc.queryForList(
                    "SELECT g.nombre_completo AS guardia, s.tipo, s.fecha_solicitud, sup.nombre_completo AS supervisor FROM solicitudes_guardia s "
                            + "JOIN empleados g ON g.id_empleado = s.id_empleado JOIN empleados sup ON sup.id_empleado = s.id_supervisor "
                            + "WHERE s.estado = 'PENDIENTE' AND s.id_supervisor <> ? ORDER BY s.fecha_solicitud LIMIT 15", emp)) {
                r.add(aviso("SOLICITUD", "Solicitud de " + String.valueOf(f.get("tipo")).toLowerCase().replace('_', ' ') + " de " + f.get("guardia")
                        + " esperando a su supervisor " + f.get("supervisor"), f.get("fecha_solicitud"), "/operaciones/solicitudes"));
            }
        }
        if (Permisos.tiene("PORTAL_GUARDIA")) {
            for (Map<String, Object> f : jdbc.queryForList(
                    "SELECT tipo, estado, fecha_respuesta FROM solicitudes_guardia WHERE id_empleado = ? AND estado <> 'PENDIENTE' AND visto_guardia = 'N' "
                            + "ORDER BY fecha_respuesta", emp)) {
                r.add(aviso("RESPUESTA", "Su solicitud de " + String.valueOf(f.get("tipo")).toLowerCase().replace('_', ' ') + " fue "
                        + String.valueOf(f.get("estado")).toLowerCase(), f.get("fecha_respuesta"), "/guardia"));
            }
        }
        return r;
    }

    @GetMapping("/avisos")
    public List<Map<String, Object>> avisos() {
        return avisosDePersona();
    }

    /** Conteo de alertas pendientes por prioridad (para el aviso del menu y el panel principal). */
    @GetMapping("/resumen")
    public Map<String, Object> resumen() {
        sincronizar();
        Map<String, Object> r = new LinkedHashMap<>();
        long criticas = 0;
        long altas = 0;
        long medias = 0;
        List<String> permitidos = tiposPermitidos();
        if (!permitidos.isEmpty()) {
            for (Map<String, Object> f : jdbc.queryForList(
                    "SELECT prioridad, COUNT(*) AS n FROM alertas_vencimiento WHERE estado = 'PENDIENTE' AND tipo_entidad IN ("
                            + marcas(permitidos.size()) + ") GROUP BY prioridad", permitidos.toArray())) {
                long n = ((Number) f.get("n")).longValue();
                switch (String.valueOf(f.get("prioridad"))) {
                    case "CRITICA" -> criticas = n;
                    case "ALTA" -> altas = n;
                    default -> medias += n;
                }
            }
        }
        long avisos = avisosDePersona().size();
        r.put("criticas", criticas);
        r.put("altas", altas);
        r.put("medias", medias);
        r.put("avisos", avisos);
        r.put("total", criticas + altas + medias + avisos);
        return r;
    }

    private Map<String, Object> cerrar(Integer id, Map<String, Object> body, String estadoNuevo, String verbo) {
        List<Map<String, Object>> a = jdbc.queryForList(
                "SELECT tipo_entidad, estado, descripcion FROM alertas_vencimiento WHERE id_alerta = ?", id);
        if (a.isEmpty()) {
            throw ApiUtil.noEncontrado("Alerta no encontrada.");
        }
        String[] info = TIPOS.get(String.valueOf(a.get(0).get("tipo_entidad")));
        if (info == null || !Permisos.tiene(info[0])) {
            throw ApiUtil.error("No tiene permiso para gestionar esta alerta.");
        }
        if (!"PENDIENTE".equals(a.get(0).get("estado"))) {
            throw ApiUtil.error("Esta alerta ya fue cerrada.");
        }
        String obs = ApiUtil.textoObligatorio(body, "observaciones", "observaciones");
        if (obs.length() > 300) {
            throw ApiUtil.error("Las observaciones no pueden pasar de 300 caracteres.");
        }
        Integer idUsuario = auditoria.idUsuarioActual();
        jdbc.update("UPDATE alertas_vencimiento SET estado = ?, fecha_atencion = CURRENT_DATE, id_usuario_atendio = ?, "
                + "observaciones = ? WHERE id_alerta = ?", estadoNuevo, idUsuario, obs, id);
        auditoria.registrar(idUsuario, "UPDATE", "alertas_vencimiento", id, "PENDIENTE",
                estadoNuevo + ": " + a.get(0).get("descripcion"));
        return Map.of("mensaje", "Alerta " + verbo + ".");
    }

    @PutMapping("/{id}/atender")
    public Map<String, Object> atender(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        return cerrar(id, body, "ATENDIDA", "marcada como atendida");
    }

    @PutMapping("/{id}/ignorar")
    public Map<String, Object> ignorar(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        return cerrar(id, body, "IGNORADA", "ignorada");
    }
}
