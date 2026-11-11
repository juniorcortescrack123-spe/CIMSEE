package com.cimse.operaciones;

import com.cimse.config.ApiUtil;
import com.cimse.seguridad.AuditoriaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Visitas de supervision (revision del 3 de noviembre).
 * Quien entra con el rol SUPERVISOR ya es el supervisor: no elige quien es. Ve los clientes que tiene a su cargo,
 * escoge uno y se le muestra la lista de guardias de ese cliente para "pasar asistencia": presente o ausente
 * (con o sin excusa y el motivo), una observacion y una incidencia por guardia, mas las incidencias generales de la visita
 * (vidrio roto, arma perdida...). Los administradores y gerentes pueden registrar una visita en nombre de un supervisor.
 */
@RestController
@RequestMapping("/api/supervisiones")
public class SupervisionController {

    static final List<String> MOTIVOS_FALTA = Arrays.asList("PERMISO", "INCAPACIDAD_MEDICA", "EMERGENCIA_FAMILIAR",
            "ENFERMEDAD", "SIN_AVISO", "ABANDONO_PUESTO", "OTRO");
    private static final List<String> TIPOS_FALTA = Arrays.asList("CON_EXCUSA", "SIN_EXCUSA");

    private static final String ASIGNACION_HOY =
            "a.estado_asignacion = 'ACTIVA' AND a.fecha_inicio <= CURRENT_DATE AND (a.fecha_fin IS NULL OR a.fecha_fin >= CURRENT_DATE)";

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;
    private final ComodinService comodin;

    public SupervisionController(JdbcTemplate jdbc, AuditoriaService auditoria, ComodinService comodin) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.comodin = comodin;
    }

    // ---------------------------------------------------------------- quien es el supervisor

    private boolean tieneRolSupervisor(Integer idUsuario) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuario_roles ur JOIN roles r ON r.id_rol = ur.id_rol "
                        + "WHERE ur.id_usuario = ? AND r.nombre = 'SUPERVISOR'", Integer.class, idUsuario);
        return n != null && n > 0;
    }

    private Integer empleadoDelUsuario(Integer idUsuario) {
        List<Integer> r = jdbc.queryForList("SELECT id_empleado FROM usuarios WHERE id_usuario = ?", Integer.class, idUsuario);
        return r.isEmpty() ? null : r.get(0);
    }

    private List<Map<String, Object>> listaSupervisores() {
        return jdbc.queryForList(
                "SELECT e.id_empleado AS \"id\", e.nombre_completo AS \"nombre\" FROM empleados e WHERE e.activo = 'S' AND "
                        + "(e.cargo = 'SUPERVISOR' OR e.id_empleado IN (SELECT u.id_empleado FROM usuarios u "
                        + "JOIN usuario_roles ur ON ur.id_usuario = u.id_usuario JOIN roles r ON r.id_rol = ur.id_rol "
                        + "WHERE r.nombre = 'SUPERVISOR' AND u.id_empleado IS NOT NULL)) ORDER BY e.nombre_completo");
    }

    /** Si la persona tiene el rol SUPERVISOR, el supervisor es ella misma; si no, debe indicar uno de la lista. */
    private Integer resolverSupervisor(Integer idSolicitado) {
        Integer idUsuario = auditoria.idUsuarioActual();
        if (idUsuario != null && tieneRolSupervisor(idUsuario)) {
            Integer propio = empleadoDelUsuario(idUsuario);
            if (propio == null) {
                throw ApiUtil.error("Su usuario no esta ligado a un empleado; pida al administrador que lo corrija.");
            }
            return propio;
        }
        if (idSolicitado == null) {
            throw ApiUtil.error("Seleccione el supervisor.");
        }
        for (Map<String, Object> s : listaSupervisores()) {
            if (idSolicitado.equals(((Number) s.get("id")).intValue())) {
                return idSolicitado;
            }
        }
        throw ApiUtil.error("El supervisor debe ser un empleado activo con cargo o rol de supervisor.");
    }

    /** Quien entra: si es supervisor se le presenta directamente; si no, la lista para elegir. */
    @GetMapping("/contexto")
    public Map<String, Object> contexto() {
        Integer idUsuario = auditoria.idUsuarioActual();
        Map<String, Object> r = new LinkedHashMap<>();
        boolean esSupervisor = idUsuario != null && tieneRolSupervisor(idUsuario);
        r.put("esSupervisor", esSupervisor);
        if (esSupervisor) {
            Integer propio = empleadoDelUsuario(idUsuario);
            r.put("idSupervisor", propio);
            List<String> n = propio == null ? new ArrayList<>()
                    : jdbc.queryForList("SELECT nombre_completo FROM empleados WHERE id_empleado = ?", String.class, propio);
            r.put("nombre", n.isEmpty() ? null : n.get(0));
        } else {
            r.put("supervisores", listaSupervisores());
        }
        return r;
    }

    // ---------------------------------------------------------------- clientes y guardias a supervisar

    @GetMapping("/clientes")
    public List<Map<String, Object>> clientes(@RequestParam(required = false) Integer idSupervisor) {
        Integer sup = resolverSupervisor(idSupervisor);
        return jdbc.queryForList(
                "SELECT c.id_cliente AS \"id\", c.nombre AS \"nombre\", c.direccion AS \"direccion\", "
                        + "(SELECT COUNT(*) FROM asignacion_puestos a JOIN puestos_trabajo p ON p.id_puesto = a.id_puesto "
                        + "WHERE p.id_cliente = c.id_cliente AND p.estado = 'ACTIVO' AND " + ASIGNACION_HOY + ") AS \"guardias\" "
                        + "FROM supervisor_clientes sc JOIN clientes c ON c.id_cliente = sc.id_cliente "
                        + "WHERE sc.id_supervisor = ? ORDER BY c.nombre", sup);
    }

    private void verificarCliente(Integer idSupervisor, Integer idCliente) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM supervisor_clientes WHERE id_supervisor = ? AND id_cliente = ?",
                Integer.class, idSupervisor, idCliente);
        if (n == null || n == 0) {
            throw ApiUtil.error("Ese cliente no esta a cargo de este supervisor. Pida al administrador que se lo asigne "
                    + "(Gestion de clientes > Detalle > Supervisores a cargo).");
        }
    }

    private List<Map<String, Object>> guardiasDelCliente(Integer idCliente) {
        return jdbc.queryForList(
                "SELECT a.id_asignacion_puesto AS \"idAsignacionPuesto\", a.id_empleado AS \"idGuardia\", "
                        + "g.nombre_completo AS \"guardia\", p.id_puesto AS \"idPuesto\", p.descripcion AS \"puesto\", "
                        + "p.ubicacion AS \"ubicacion\", t.nombre AS \"turno\" FROM asignacion_puestos a "
                        + "JOIN puestos_trabajo p ON p.id_puesto = a.id_puesto JOIN empleados g ON g.id_empleado = a.id_empleado "
                        + "LEFT JOIN turnos t ON t.id_turno = a.id_turno "
                        + "WHERE p.id_cliente = ? AND p.estado = 'ACTIVO' AND " + ASIGNACION_HOY
                        + " ORDER BY p.descripcion, g.nombre_completo", idCliente);
    }

    /** La lista de guardias del cliente, lista para pasar asistencia. */
    @GetMapping("/clientes/{idCliente}/guardias")
    public List<Map<String, Object>> guardias(@PathVariable Integer idCliente,
                                              @RequestParam(required = false) Integer idSupervisor) {
        verificarCliente(resolverSupervisor(idSupervisor), idCliente);
        return guardiasDelCliente(idCliente);
    }

    /** Guardias que pueden cubrir una falta como comodin. */
    @GetMapping("/comodines")
    public List<Map<String, Object>> comodines() {
        return jdbc.queryForList("SELECT id_empleado AS \"id\", nombre_completo AS \"nombre\" FROM empleados "
                + "WHERE activo = 'S' AND cargo = 'GUARDIA' ORDER BY nombre_completo");
    }

    // ---------------------------------------------------------------- registrar la visita

    private Integer crearIncidencia(Integer idReporta, Integer idPuesto, Integer idSupervisor, String tipo, String descripcion,
                                    String nivel, Integer idCliente, Integer idVisita, Integer idAfectado) {
        Integer id = jdbc.queryForObject(
                "INSERT INTO incidencias (id_empleado_reporte, id_puesto, id_supervisor_asignado, tipo, descripcion, nivel, "
                        + "id_cliente, id_visita, id_empleado_afectado) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_incidencia",
                Integer.class, idReporta, idPuesto, idSupervisor, tipo, descripcion, nivel, idCliente, idVisita, idAfectado);
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "incidencias", id, null,
                tipo + " (" + nivel + ") generada por la visita #" + idVisita);
        return id;
    }

    @PostMapping("/visitas")
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> registrarVisita(@RequestBody Map<String, Object> body) {
        Integer idSupervisor = resolverSupervisor(ApiUtil.entero(body, "idSupervisor", "supervisor", false));
        Integer idCliente = ApiUtil.entero(body, "idCliente", "cliente", true);
        verificarCliente(idSupervisor, idCliente);
        String observaciones = ApiUtil.textoObligatorio(body, "observaciones", "observaciones generales de la visita");
        if (observaciones.length() > 500) {
            throw ApiUtil.error("Las observaciones generales no pueden pasar de 500 caracteres.");
        }

        // ---- 1. toda la lista de guardias del cliente debe venir marcada (como pasar asistencia)
        List<Map<String, Object>> esperados = guardiasDelCliente(idCliente);
        if (esperados.isEmpty()) {
            throw ApiUtil.error("Este cliente no tiene guardias asignados hoy; no hay a quien supervisar.");
        }
        if (!(body.get("guardias") instanceof List<?>)) {
            throw ApiUtil.error("Marque a los guardias del cliente como presentes o ausentes.");
        }
        Map<Integer, Map<String, Object>> porAsignacion = new LinkedHashMap<>();
        for (Map<String, Object> e : esperados) {
            porAsignacion.put(((Number) e.get("idAsignacionPuesto")).intValue(), e);
        }
        Map<Integer, Map<String, Object>> filas = new LinkedHashMap<>();
        for (Object o : (List<?>) body.get("guardias")) {
            if (!(o instanceof Map)) {
                throw ApiUtil.error("Fila de guardia invalida.");
            }
            Map<String, Object> f = (Map<String, Object>) o;
            Integer idAsig = ApiUtil.entero(f, "idAsignacionPuesto", "asignacion del guardia", true);
            if (!porAsignacion.containsKey(idAsig)) {
                throw ApiUtil.error("Hay un guardia que no pertenece a este cliente o ya no esta asignado.");
            }
            if (filas.put(idAsig, f) != null) {
                throw ApiUtil.error("Un guardia aparece dos veces en la lista.");
            }
        }
        List<String> faltan = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Object>> e : porAsignacion.entrySet()) {
            if (!filas.containsKey(e.getKey())) {
                faltan.add((String) e.getValue().get("guardia"));
            }
        }
        if (!faltan.isEmpty()) {
            throw ApiUtil.error("Falta marcar a: " + String.join(", ", faltan) + ".");
        }

        // ---- 2. validar cada fila antes de guardar nada
        Set<Integer> comodinesUsados = new HashSet<>();
        for (Map.Entry<Integer, Map<String, Object>> e : filas.entrySet()) {
            Map<String, Object> f = e.getValue();
            String nombre = (String) porAsignacion.get(e.getKey()).get("guardia");
            if (!(f.get("presente") instanceof Boolean)) {
                throw ApiUtil.error("Marque presente o ausente para " + nombre + ".");
            }
            boolean presente = (Boolean) f.get("presente");
            if (!presente) {
                String tipoFalta = ApiUtil.textoObligatorio(f, "tipoFalta", "tipo de falta de " + nombre + " (con o sin excusa)");
                if (!TIPOS_FALTA.contains(tipoFalta)) {
                    throw ApiUtil.error("El tipo de falta de " + nombre + " debe ser CON_EXCUSA o SIN_EXCUSA.");
                }
                String motivo = ApiUtil.textoObligatorio(f, "motivoFalta", "motivo de la falta de " + nombre);
                if (!MOTIVOS_FALTA.contains(motivo)) {
                    throw ApiUtil.error("El motivo de la falta de " + nombre + " no es valido.");
                }
                Integer reemplazo = ApiUtil.entero(f, "idEmpleadoReemplazo", "guardia comodin", false);
                if (reemplazo != null && !comodinesUsados.add(reemplazo)) {
                    throw ApiUtil.error("El mismo guardia comodin no puede cubrir dos faltas en la misma visita.");
                }
            } else if (ApiUtil.entero(f, "idEmpleadoReemplazo", "guardia comodin", false) != null) {
                throw ApiUtil.error("Solo se solicita guardia comodin si el guardia falto (" + nombre + " esta presente).");
            }
            String obs = ApiUtil.texto(f, "observaciones");
            if (obs != null && obs.length() > 200) {
                throw ApiUtil.error("La observacion de " + nombre + " no puede pasar de 200 caracteres.");
            }
            String inc = ApiUtil.texto(f, "incidencia");
            if (inc != null) {
                if (inc.length() > 500) {
                    throw ApiUtil.error("La incidencia de " + nombre + " no puede pasar de 500 caracteres.");
                }
                String tipoInc = ApiUtil.texto(f, "tipoIncidencia");
                if (tipoInc != null && !IncidenciaController.TIPOS.contains(tipoInc)) {
                    throw ApiUtil.error("El tipo de incidencia de " + nombre + " no es valido.");
                }
            }
        }
        List<Map<String, Object>> generales = new ArrayList<>();
        if (body.get("incidencias") instanceof List<?>) {
            int n = 0;
            for (Object o : (List<?>) body.get("incidencias")) {
                n++;
                if (!(o instanceof Map)) {
                    throw ApiUtil.error("Incidencia general " + n + " invalida.");
                }
                Map<String, Object> g = (Map<String, Object>) o;
                String tipo = ApiUtil.textoObligatorio(g, "tipo", "tipo de la incidencia general " + n);
                if (!IncidenciaController.TIPOS.contains(tipo)) {
                    throw ApiUtil.error("El tipo de la incidencia general " + n + " no es valido.");
                }
                String d = ApiUtil.textoObligatorio(g, "descripcion", "descripcion de la incidencia general " + n);
                if (d.length() > 500) {
                    throw ApiUtil.error("La incidencia general " + n + " no puede pasar de 500 caracteres.");
                }
                generales.add(g);
            }
        }

        // ---- 3. guardar la visita y todo lo que genera
        Integer idVisita = jdbc.queryForObject(
                "INSERT INTO visitas_supervision (id_supervisor, id_cliente, observaciones) VALUES (?, ?, ?) RETURNING id_visita",
                Integer.class, idSupervisor, idCliente, observaciones);
        int presentes = 0;
        int ausentes = 0;
        int incidencias = 0;
        int comodines = 0;
        for (Map.Entry<Integer, Map<String, Object>> e : filas.entrySet()) {
            Integer idAsig = e.getKey();
            Map<String, Object> f = e.getValue();
            Map<String, Object> info = porAsignacion.get(idAsig);
            Integer idGuardia = ((Number) info.get("idGuardia")).intValue();
            Integer idPuesto = ((Number) info.get("idPuesto")).intValue();
            String nombre = (String) info.get("guardia");
            boolean presente = (Boolean) f.get("presente");
            String tipoFalta = presente ? null : ApiUtil.texto(f, "tipoFalta");
            String motivo = presente ? null : ApiUtil.texto(f, "motivoFalta");
            boolean firma = !"false".equalsIgnoreCase(ApiUtil.texto(f, "firmaLibro"));
            Integer reemplazo = presente ? null : ApiUtil.entero(f, "idEmpleadoReemplazo", "guardia comodin", false);
            String inc = ApiUtil.texto(f, "incidencia");

            // asistencia del dia: PRESENTE, JUSTIFICADO (falta con excusa) o AUSENTE (sin excusa)
            String estadoAsistencia = presente ? "PRESENTE" : ("CON_EXCUSA".equals(tipoFalta) ? "JUSTIFICADO" : "AUSENTE");
            List<Integer> asistencias = jdbc.queryForList(
                    "SELECT id_asistencia FROM asistencia_diaria WHERE id_asignacion_puesto = ? AND fecha = CURRENT_DATE",
                    Integer.class, idAsig);
            Integer idAsistencia;
            if (asistencias.isEmpty()) {
                idAsistencia = jdbc.queryForObject(
                        "INSERT INTO asistencia_diaria (id_empleado, id_asignacion_puesto, fecha, id_turno, estado_asistencia) "
                                + "VALUES (?, ?, CURRENT_DATE, (SELECT id_turno FROM asignacion_puestos WHERE id_asignacion_puesto = ?), ?) "
                                + "RETURNING id_asistencia", Integer.class, idGuardia, idAsig, idAsig, estadoAsistencia);
            } else {
                idAsistencia = asistencias.get(0);
                jdbc.update("UPDATE asistencia_diaria SET estado_asistencia = ? WHERE id_asistencia = ?", estadoAsistencia, idAsistencia);
            }

            Integer idSup = jdbc.queryForObject(
                    "INSERT INTO supervision_operativa (id_empleado, id_asistencia, guardia_presente, incidencia_detectada, firma_libro, "
                            + "observaciones, id_empleado_reemplazo, id_visita, tipo_falta, motivo_falta) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_supervision", Integer.class,
                    idSupervisor, idAsistencia, presente ? "S" : "N", inc, firma ? "S" : "N", ApiUtil.texto(f, "observaciones"),
                    reemplazo, idVisita, tipoFalta, motivo);
            auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "supervision_operativa", idSup, null,
                    "Visita #" + idVisita + ": " + nombre + (presente ? " presente" : " AUSENTE (" + tipoFalta + ", " + motivo + ")"));

            if (presente) {
                presentes++;
            } else {
                ausentes++;
                Integer idInc = crearIncidencia(idSupervisor, idPuesto, idSupervisor, "AUSENCIA",
                        "El guardia " + nombre + " no se encontraba en su puesto (" + ("CON_EXCUSA".equals(tipoFalta) ? "con excusa" : "sin excusa")
                                + ", motivo: " + motivo + "). Visita #" + idVisita + ".", "GUARDIA", idCliente, idVisita, idGuardia);
                incidencias++;
                if (reemplazo != null) {
                    comodin.asignarReemplazo(idAsig, reemplazo, "Visita #" + idVisita);
                    jdbc.update("UPDATE incidencias SET estado = 'EN_PROCESO' WHERE id_incidencia = ?", idInc);
                    comodines++;
                }
            }
            if (inc != null) {
                String tipoInc = ApiUtil.texto(f, "tipoIncidencia");
                crearIncidencia(idSupervisor, idPuesto, idSupervisor, tipoInc == null ? "OTRO" : tipoInc, inc, "GUARDIA",
                        idCliente, idVisita, idGuardia);
                incidencias++;
            }
        }
        for (Map<String, Object> g : generales) {
            crearIncidencia(idSupervisor, null, idSupervisor, ApiUtil.texto(g, "tipo"), ApiUtil.texto(g, "descripcion"),
                    "ESTABLECIMIENTO", idCliente, idVisita, null);
            incidencias++;
        }
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "visitas_supervision", idVisita, null,
                "Visita al cliente #" + idCliente + ": " + presentes + " presente(s), " + ausentes + " ausente(s), "
                        + incidencias + " incidencia(s)");

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", idVisita);
        r.put("mensaje", "Visita registrada: " + presentes + " presente(s), " + ausentes + " ausente(s), " + incidencias
                + " incidencia(s)" + (comodines > 0 ? ", " + comodines + " guardia(s) comodin asignado(s) para hoy." : "."));
        return r;
    }

    // ---------------------------------------------------------------- historial

    private LocalDate parse(String s) {
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            throw ApiUtil.error("Fecha invalida (use AAAA-MM-DD).");
        }
    }

    /** Quien tiene el rol SUPERVISOR solo ve sus propias visitas; administradores y gerentes ven todas. */
    private Integer soloPropias() {
        Integer idUsuario = auditoria.idUsuarioActual();
        return idUsuario != null && tieneRolSupervisor(idUsuario) ? empleadoDelUsuario(idUsuario) : null;
    }

    @GetMapping("/visitas")
    public List<Map<String, Object>> visitas(@RequestParam(required = false) String desde,
                                             @RequestParam(required = false) String hasta) {
        StringBuilder sql = new StringBuilder(
                "SELECT v.id_visita AS \"id\", v.fecha_hora AS \"fechaHora\", sup.nombre_completo AS \"supervisor\", "
                        + "cl.nombre AS \"cliente\", "
                        + "(SELECT COUNT(*) FROM supervision_operativa s WHERE s.id_visita = v.id_visita AND s.guardia_presente = 'S') AS \"presentes\", "
                        + "(SELECT COUNT(*) FROM supervision_operativa s WHERE s.id_visita = v.id_visita AND s.guardia_presente = 'N') AS \"ausentes\", "
                        + "(SELECT COUNT(*) FROM incidencias i WHERE i.id_visita = v.id_visita) AS \"incidencias\" "
                        + "FROM visitas_supervision v JOIN empleados sup ON sup.id_empleado = v.id_supervisor "
                        + "JOIN clientes cl ON cl.id_cliente = v.id_cliente WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        Integer propio = soloPropias();
        if (propio != null) {
            sql.append(" AND v.id_supervisor = ?");
            args.add(propio);
        }
        if (desde != null && !desde.isBlank()) {
            sql.append(" AND v.fecha_hora::date >= ?");
            args.add(ApiUtil.sql(parse(desde)));
        }
        if (hasta != null && !hasta.isBlank()) {
            sql.append(" AND v.fecha_hora::date <= ?");
            args.add(ApiUtil.sql(parse(hasta)));
        }
        sql.append(" ORDER BY v.fecha_hora DESC, v.id_visita DESC LIMIT 300");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @GetMapping("/visitas/{id}")
    public Map<String, Object> detalleVisita(@PathVariable Integer id) {
        List<Map<String, Object>> cab = jdbc.queryForList(
                "SELECT v.id_visita AS \"id\", v.id_supervisor AS \"idSupervisor\", v.fecha_hora AS \"fechaHora\", "
                        + "sup.nombre_completo AS \"supervisor\", cl.nombre AS \"cliente\", v.observaciones AS \"observaciones\" "
                        + "FROM visitas_supervision v JOIN empleados sup ON sup.id_empleado = v.id_supervisor "
                        + "JOIN clientes cl ON cl.id_cliente = v.id_cliente WHERE v.id_visita = ?", id);
        if (cab.isEmpty()) {
            throw ApiUtil.noEncontrado("Visita no encontrada.");
        }
        Integer propio = soloPropias();
        if (propio != null && !propio.equals(((Number) cab.get(0).get("idSupervisor")).intValue())) {
            throw ApiUtil.error("Solo puede consultar sus propias visitas.");
        }
        Map<String, Object> r = new LinkedHashMap<>(cab.get(0));
        r.put("guardias", jdbc.queryForList(
                "SELECT g.nombre_completo AS \"guardia\", p.descripcion AS \"puesto\", t.nombre AS \"turno\", "
                        + "(s.guardia_presente = 'S') AS \"presente\", s.tipo_falta AS \"tipoFalta\", s.motivo_falta AS \"motivoFalta\", "
                        + "(s.firma_libro = 'S') AS \"firmaLibro\", s.observaciones AS \"observaciones\", "
                        + "s.incidencia_detectada AS \"incidencia\", rep.nombre_completo AS \"comodin\" "
                        + "FROM supervision_operativa s JOIN asistencia_diaria a ON a.id_asistencia = s.id_asistencia "
                        + "JOIN empleados g ON g.id_empleado = a.id_empleado "
                        + "JOIN asignacion_puestos ap ON ap.id_asignacion_puesto = a.id_asignacion_puesto "
                        + "JOIN puestos_trabajo p ON p.id_puesto = ap.id_puesto LEFT JOIN turnos t ON t.id_turno = a.id_turno "
                        + "LEFT JOIN empleados rep ON rep.id_empleado = s.id_empleado_reemplazo "
                        + "WHERE s.id_visita = ? ORDER BY p.descripcion, g.nombre_completo", id));
        r.put("listaIncidencias", jdbc.queryForList(
                "SELECT i.id_incidencia AS \"id\", i.tipo AS \"tipo\", i.nivel AS \"nivel\", i.descripcion AS \"descripcion\", "
                        + "i.estado AS \"estado\", g.nombre_completo AS \"guardia\" FROM incidencias i "
                        + "LEFT JOIN empleados g ON g.id_empleado = i.id_empleado_afectado WHERE i.id_visita = ? ORDER BY i.id_incidencia", id));
        return r;
    }
}
