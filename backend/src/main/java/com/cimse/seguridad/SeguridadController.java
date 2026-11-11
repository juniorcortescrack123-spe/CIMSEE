package com.cimse.seguridad;

import com.cimse.config.Validaciones;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modulo de Seguridad (solo roles con el permiso SEGURIDAD, normalmente el ADMINISTRADOR):
 * usuarios (con uno o varios roles), roles dinamicos, permisos, solicitudes de clave y auditoria.
 * La clave inicial la genera el sistema y se envia por correo; vence si no se cambia a tiempo.
 */
@RestController
@RequestMapping("/api/seguridad")
public class SeguridadController {

    private static final String ROL_ADMIN = "ADMINISTRADOR";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final CorreoService correo;
    private final int diasVigencia;

    public SeguridadController(JdbcTemplate jdbc, PasswordEncoder encoder, AuditoriaService auditoria,
                               CorreoService correo, @Value("${cimse.clave.dias-vigencia:3}") int diasVigencia) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.correo = correo;
        this.diasVigencia = diasVigencia;
    }

    private static ResponseStatusException error(String mensaje) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
    }

    private static ResponseStatusException noEncontrado(String mensaje) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, mensaje);
    }

    private static String texto(Map<String, Object> body, String clave) {
        Object v = body.get(clave);
        return v == null ? null : v.toString().trim();
    }

    private static String sinTildes(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    // ===================================================================
    //  PERMISOS Y ROLES
    // ===================================================================

    @GetMapping("/permisos")
    public List<Map<String, Object>> permisos() {
        return jdbc.queryForList("SELECT codigo, descripcion FROM permisos ORDER BY descripcion");
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> roles() {
        List<Map<String, Object>> roles = jdbc.queryForList(
                "SELECT r.id_rol AS \"id\", r.nombre AS \"nombre\", r.descripcion AS \"descripcion\", "
                        + "(r.activo = 'S') AS \"activo\", (r.es_sistema = 'S') AS \"esSistema\", "
                        + "(SELECT COUNT(*) FROM usuario_roles ur WHERE ur.id_rol = r.id_rol) AS \"usuarios\" "
                        + "FROM roles r ORDER BY r.id_rol");
        for (Map<String, Object> r : roles) {
            r.put("permisos", jdbc.queryForList(
                    "SELECT codigo_permiso FROM rol_permisos WHERE id_rol = ? ORDER BY codigo_permiso",
                    String.class, r.get("id")));
        }
        return roles;
    }

    @PostMapping("/roles")
    @Transactional
    public Map<String, Object> crearRol(@RequestBody Map<String, Object> body) {
        String nombre = texto(body, "nombre");
        if (nombre == null || nombre.isEmpty()) {
            throw error("El nombre del rol es obligatorio.");
        }
        nombre = sinTildes(nombre).toUpperCase().replaceAll("\\s+", "_");
        if (!nombre.matches("^[A-Z0-9_]{3,30}$")) {
            throw error("El nombre del rol debe tener de 3 a 30 caracteres (letras, numeros o guion bajo).");
        }
        Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE nombre = ?", Integer.class, nombre);
        if (existe != null && existe > 0) {
            throw error("Ya existe un rol llamado " + nombre + ".");
        }
        String descripcion = texto(body, "descripcion");

        jdbc.update("INSERT INTO roles (nombre, descripcion, activo, es_sistema) VALUES (?, ?, 'S', 'N')",
                nombre, descripcion);
        Integer id = jdbc.queryForObject("SELECT id_rol FROM roles WHERE nombre = ?", Integer.class, nombre);
        List<String> permisos = guardarPermisos(id, body.get("permisos"));

        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "roles", id, null,
                "Rol " + nombre + " con permisos " + permisos);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("nombre", nombre);
        return r;
    }

    @PutMapping("/roles/{id}")
    @Transactional
    public Map<String, String> editarRol(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT nombre, descripcion, activo FROM roles WHERE id_rol = ?", id);
        if (filas.isEmpty()) {
            throw noEncontrado("Rol no encontrado.");
        }
        String nombre = (String) filas.get(0).get("nombre");
        String antes = "descripcion=" + filas.get(0).get("descripcion") + ", activo=" + filas.get(0).get("activo")
                + ", permisos=" + jdbc.queryForList(
                "SELECT codigo_permiso FROM rol_permisos WHERE id_rol = ? ORDER BY codigo_permiso", String.class, id);

        jdbc.update("UPDATE roles SET descripcion = ? WHERE id_rol = ?", texto(body, "descripcion"), id);

        if (!ROL_ADMIN.equals(nombre)) {
            if (body.get("activo") != null) {
                boolean activar = Boolean.parseBoolean(body.get("activo").toString());
                if (!activar) {
                    Integer enUso = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM usuario_roles ur JOIN usuarios u ON u.id_usuario = ur.id_usuario "
                                    + "WHERE ur.id_rol = ? AND u.activo = 'S'", Integer.class, id);
                    if (enUso != null && enUso > 0) {
                        throw error("No se puede desactivar: hay " + enUso + " usuario(s) activo(s) con este rol.");
                    }
                }
                jdbc.update("UPDATE roles SET activo = ? WHERE id_rol = ?", activar ? "S" : "N", id);
            }
            if (body.get("permisos") != null) {
                jdbc.update("DELETE FROM rol_permisos WHERE id_rol = ?", id);
                guardarPermisos(id, body.get("permisos"));
            }
        }
        String despues = "descripcion=" + texto(body, "descripcion") + ", permisos=" + jdbc.queryForList(
                "SELECT codigo_permiso FROM rol_permisos WHERE id_rol = ? ORDER BY codigo_permiso", String.class, id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "roles", id, antes, despues);
        return Map.of("mensaje", "Rol actualizado.");
    }

    @DeleteMapping("/roles/{id}")
    @Transactional
    public Map<String, String> eliminarRol(@PathVariable Integer id) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT nombre, es_sistema FROM roles WHERE id_rol = ?", id);
        if (filas.isEmpty()) {
            throw noEncontrado("Rol no encontrado.");
        }
        String nombre = (String) filas.get(0).get("nombre");
        if ("S".equals(filas.get(0).get("es_sistema"))) {
            throw error("Los roles base del sistema no se pueden eliminar.");
        }
        Integer enUso = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuario_roles WHERE id_rol = ?", Integer.class, id);
        Integer principal = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE rol = ?", Integer.class, nombre);
        if ((enUso != null && enUso > 0) || (principal != null && principal > 0)) {
            throw error("No se puede eliminar: hay usuarios con este rol. Reasignelos primero.");
        }
        jdbc.update("DELETE FROM roles WHERE id_rol = ?", id);
        auditoria.registrar(auditoria.idUsuarioActual(), "DELETE", "roles", id, "Rol " + nombre, null);
        return Map.of("mensaje", "Rol eliminado.");
    }

    /** Guarda los permisos del rol, ignorando codigos que no existan en el catalogo. */
    private List<String> guardarPermisos(Integer idRol, Object lista) {
        List<String> guardados = new ArrayList<>();
        if (lista instanceof List<?>) {
            for (Object o : (List<?>) lista) {
                String codigo = String.valueOf(o);
                Integer valido = jdbc.queryForObject("SELECT COUNT(*) FROM permisos WHERE codigo = ?", Integer.class, codigo);
                if (valido != null && valido > 0 && !guardados.contains(codigo)) {
                    jdbc.update("INSERT INTO rol_permisos (id_rol, codigo_permiso) VALUES (?, ?)", idRol, codigo);
                    guardados.add(codigo);
                }
            }
        }
        return guardados;
    }

    // ===================================================================
    //  USUARIOS
    // ===================================================================

    @GetMapping("/usuarios")
    public List<Map<String, Object>> usuarios() {
        List<Map<String, Object>> lista = jdbc.queryForList(
                "SELECT u.id_usuario AS \"id\", u.nombre_usuario AS \"usuario\", u.id_empleado AS \"idEmpleado\", "
                        + "e.nombre_completo AS \"nombre\", e.correo AS \"correo\", u.rol AS \"rol\", "
                        + "(u.activo = 'S') AS \"activo\", u.ultimo_acceso AS \"ultimoAcceso\", "
                        + "(u.debe_cambiar_clave = 'S') AS \"debeCambiarClave\", u.clave_expira AS \"claveExpira\", "
                        + "(u.bloqueado = 'S') AS \"bloqueado\", u.intentos_fallidos AS \"intentosFallidos\", "
                        + "(SELECT string_agg(r.nombre, ',' ORDER BY r.id_rol) FROM usuario_roles ur "
                        + "JOIN roles r ON r.id_rol = ur.id_rol WHERE ur.id_usuario = u.id_usuario) AS \"rolesTexto\" "
                        + "FROM usuarios u JOIN empleados e ON e.id_empleado = u.id_empleado "
                        + "ORDER BY u.id_usuario");
        for (Map<String, Object> u : lista) {
            Object txt = u.remove("rolesTexto");
            List<String> roles = txt == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(txt.toString().split(",")));
            if (roles.isEmpty()) {
                roles.add(String.valueOf(u.get("rol")));
            }
            u.put("roles", roles);
        }
        return lista;
    }

    /** Empleados activos que aun no tienen usuario, con el nombre de usuario sugerido y si tienen correo. */
    @GetMapping("/empleados-disponibles")
    public List<Map<String, Object>> empleadosDisponibles() {
        List<Map<String, Object>> lista = jdbc.queryForList(
                "SELECT e.id_empleado AS \"id\", e.identidad AS \"identidad\", e.nombre_completo AS \"nombre\", "
                        + "e.cargo AS \"cargo\", e.correo AS \"correo\" FROM empleados e "
                        + "WHERE e.activo = 'S' AND NOT EXISTS (SELECT 1 FROM usuarios u WHERE u.id_empleado = e.id_empleado) "
                        + "ORDER BY e.nombre_completo");
        for (Map<String, Object> e : lista) {
            e.put("sugerido", sugerirUsuario((String) e.get("nombre")));
        }
        return lista;
    }

    /**
     * Regla de nombre de usuario: primer nombre + "." + primer apellido, en minusculas y sin tildes.
     * (con 4 palabras el primer apellido es la 3.a; con 2 o 3 palabras es la 2.a). Si ya existe, agrega un numero.
     */
    private String sugerirUsuario(String nombreCompleto) {
        String[] p = sinTildes(nombreCompleto == null ? "" : nombreCompleto).toLowerCase().trim().split("\\s+");
        String nombre = p.length > 0 ? p[0] : "usuario";
        String apellido = p.length >= 4 ? p[2] : (p.length >= 2 ? p[1] : "");
        String base = (nombre + (apellido.isEmpty() ? "" : "." + apellido)).replaceAll("[^a-z0-9.]", "");
        if (base.length() < 3) {
            base = "usuario";
        }
        String candidato = base;
        int n = 1;
        while (existeUsuario(candidato)) {
            n++;
            candidato = base + n;
        }
        return candidato;
    }

    private boolean existeUsuario(String nombreUsuario) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE nombre_usuario = ?", Integer.class, nombreUsuario);
        return c != null && c > 0;
    }

    private Timestamp vencimiento() {
        return new Timestamp(System.currentTimeMillis() + diasVigencia * 24L * 60 * 60 * 1000);
    }

    /** Lista de roles que llega como "roles" (arreglo) o como "rol" (uno solo). */
    private List<String> rolesDelCuerpo(Map<String, Object> body) {
        List<String> roles = new ArrayList<>();
        if (body.get("roles") instanceof List<?>) {
            for (Object o : (List<?>) body.get("roles")) {
                String r = String.valueOf(o).trim();
                if (!r.isEmpty() && !roles.contains(r)) {
                    roles.add(r);
                }
            }
        } else if (texto(body, "rol") != null) {
            roles.add(texto(body, "rol"));
        }
        if (roles.isEmpty()) {
            throw error("Seleccione al menos un rol.");
        }
        for (String r : roles) {
            validarRolActivo(r);
        }
        return roles;
    }

    /** Crea el usuario: la clave la genera el sistema, se envia por correo y vence si no se cambia a tiempo. */
    @PostMapping("/usuarios")
    @Transactional
    public Map<String, Object> crearUsuario(@RequestBody Map<String, Object> body) {
        if (body.get("idEmpleado") == null) {
            throw error("Seleccione el empleado.");
        }
        int idEmpleado;
        try {
            idEmpleado = Integer.parseInt(body.get("idEmpleado").toString());
        } catch (NumberFormatException ex) {
            throw error("Empleado invalido.");
        }
        List<Map<String, Object>> emp = jdbc.queryForList(
                "SELECT nombre_completo, activo, correo FROM empleados WHERE id_empleado = ?", idEmpleado);
        if (emp.isEmpty() || !"S".equals(emp.get(0).get("activo"))) {
            throw error("El empleado no existe o esta inactivo.");
        }
        String correoEmpleado = (String) emp.get(0).get("correo");
        if (correoEmpleado == null || correoEmpleado.isBlank()) {
            throw error("El empleado no tiene correo registrado. Agreguelo en su expediente para poder enviarle la clave.");
        }
        Validaciones.correo(correoEmpleado);
        Integer yaTiene = jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE id_empleado = ?", Integer.class, idEmpleado);
        if (yaTiene != null && yaTiene > 0) {
            throw error("Ese empleado ya tiene un usuario. Cada empleado tiene su propio usuario.");
        }

        List<String> roles = rolesDelCuerpo(body);

        String usuario = texto(body, "nombreUsuario");
        if (usuario == null || usuario.isEmpty()) {
            usuario = sugerirUsuario((String) emp.get(0).get("nombre_completo"));
        }
        usuario = usuario.toLowerCase();
        if (!usuario.matches("^[a-z0-9._]{3,50}$")) {
            throw error("El usuario debe tener de 3 a 50 caracteres: letras minusculas, numeros, punto o guion bajo.");
        }
        if (existeUsuario(usuario)) {
            throw error("El nombre de usuario " + usuario + " ya existe.");
        }

        String clave = PoliticaClave.generarTemporal();
        Timestamp vence = vencimiento();
        jdbc.update("INSERT INTO usuarios (id_empleado, nombre_usuario, contrasena_hash, rol, activo, debe_cambiar_clave, clave_expira) "
                + "VALUES (?, ?, ?, ?, 'S', 'S', ?)", idEmpleado, usuario, encoder.encode(clave), roles.get(0), vence);
        Integer id = jdbc.queryForObject("SELECT id_usuario FROM usuarios WHERE nombre_usuario = ?", Integer.class, usuario);
        guardarRoles(id, roles);
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "usuarios", id, null,
                "Usuario " + usuario + " con roles " + roles + " (empleado " + idEmpleado + ")");

        boolean enviado = correo.enviarClaveTemporal(correoEmpleado, (String) emp.get(0).get("nombre_completo"),
                usuario, clave, vence, false);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("usuario", usuario);
        r.put("correo", correoEmpleado);
        r.put("correoEnviado", enviado);
        if (enviado) {
            r.put("mensaje", "Usuario creado. La clave temporal se envio a " + correoEmpleado
                    + " y vence en " + diasVigencia + " dia(s) si no la cambia.");
        } else {
            r.put("claveTemporal", clave);
            r.put("mensaje", "Usuario creado, pero no se pudo enviar el correo (servidor de correo sin configurar o con error). "
                    + "Entregue esta clave temporal en persona; vence en " + diasVigencia + " dia(s).");
        }
        return r;
    }

    private void guardarRoles(Integer idUsuario, List<String> roles) {
        jdbc.update("DELETE FROM usuario_roles WHERE id_usuario = ?", idUsuario);
        for (String rol : roles) {
            jdbc.update("INSERT INTO usuario_roles (id_usuario, id_rol) SELECT ?, id_rol FROM roles WHERE nombre = ?",
                    idUsuario, rol);
        }
    }

    private boolean tieneRol(Integer idUsuario, String rol) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM usuario_roles ur JOIN roles r ON r.id_rol = ur.id_rol "
                        + "WHERE ur.id_usuario = ? AND r.nombre = ?", Integer.class, idUsuario, rol);
        return n != null && n > 0;
    }

    /** Reemplaza los roles del usuario (un usuario puede tener varios; el primero es el principal). */
    private Map<String, String> asignarRoles(Integer id, List<String> nuevos) {
        Map<String, Object> u = usuarioPorId(id);
        String nombreUsuario = (String) u.get("nombre_usuario");
        List<String> antes = jdbc.queryForList(
                "SELECT r.nombre FROM usuario_roles ur JOIN roles r ON r.id_rol = ur.id_rol "
                        + "WHERE ur.id_usuario = ? ORDER BY r.id_rol", String.class, id);

        if (tieneRol(id, ROL_ADMIN) && !nuevos.contains(ROL_ADMIN)) {
            if (nombreUsuario.equals(auditoria.usuarioActual())) {
                throw error("No puede quitarse a si mismo el rol de administrador.");
            }
            if (administradoresActivos() <= 1) {
                throw error("Debe existir al menos un administrador activo.");
            }
        }
        guardarRoles(id, nuevos);
        jdbc.update("UPDATE usuarios SET rol = ? WHERE id_usuario = ?", nuevos.get(0), id);
        auditoria.registrar(auditoria.idUsuarioActual(), "CAMBIO_ROL", "usuarios", id,
                "roles=" + antes, "roles=" + nuevos + " (usuario " + nombreUsuario + ")");
        return Map.of("mensaje", "Roles de " + nombreUsuario + ": " + String.join(", ", nuevos) + ".");
    }

    /** Asigna uno o varios roles al usuario (por ejemplo ADMINISTRADOR y SUPERVISOR a la vez). */
    @PutMapping("/usuarios/{id}/roles")
    @Transactional
    public Map<String, String> cambiarRoles(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        return asignarRoles(id, rolesDelCuerpo(body));
    }

    /** Compatibilidad: deja al usuario con un solo rol. */
    @PutMapping("/usuarios/{id}/rol")
    @Transactional
    public Map<String, String> cambiarRol(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        return asignarRoles(id, rolesDelCuerpo(body));
    }

    /** Activar o desactivar un usuario. */
    @PutMapping("/usuarios/{id}/estado")
    @Transactional
    public Map<String, String> cambiarEstado(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Map<String, Object> u = usuarioPorId(id);
        boolean activar = Boolean.parseBoolean(String.valueOf(body.get("activo")));
        String nombreUsuario = (String) u.get("nombre_usuario");

        if (!activar) {
            if (nombreUsuario.equals(auditoria.usuarioActual())) {
                throw error("No puede desactivar su propio usuario.");
            }
            if (tieneRol(id, ROL_ADMIN) && "S".equals(u.get("activo")) && administradoresActivos() <= 1) {
                throw error("Debe existir al menos un administrador activo.");
            }
        }
        jdbc.update("UPDATE usuarios SET activo = ? WHERE id_usuario = ?", activar ? "S" : "N", id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "usuarios", id,
                "activo=" + u.get("activo"), "activo=" + (activar ? "S" : "N") + " (usuario " + nombreUsuario + ")");
        return Map.of("mensaje", "Usuario " + (activar ? "activado" : "desactivado") + ".");
    }

    /** Restablece la clave: el sistema genera una nueva, la envia por correo y vence si no se cambia a tiempo. */
    @PostMapping("/usuarios/{id}/restablecer-clave")
    @Transactional
    public Map<String, Object> restablecerClave(@PathVariable Integer id) {
        Map<String, Object> u = usuarioPorId(id);
        String nombreUsuario = (String) u.get("nombre_usuario");
        List<Map<String, Object>> emp = jdbc.queryForList(
                "SELECT e.correo, e.nombre_completo FROM usuarios u JOIN empleados e ON e.id_empleado = u.id_empleado "
                        + "WHERE u.id_usuario = ?", id);
        String correoEmpleado = emp.isEmpty() ? null : (String) emp.get(0).get("correo");
        String nombre = emp.isEmpty() ? nombreUsuario : (String) emp.get(0).get("nombre_completo");

        String clave = PoliticaClave.generarTemporal();
        Timestamp vence = vencimiento();
        jdbc.update("UPDATE usuarios SET contrasena_hash = ?, debe_cambiar_clave = 'S', clave_expira = ?, "
                + "bloqueado = 'N', intentos_fallidos = 0, fecha_bloqueo = NULL WHERE id_usuario = ?",
                encoder.encode(clave), vence, id);
        jdbc.update("UPDATE solicitudes_clave SET estado = 'ATENDIDA', fecha_atencion = CURRENT_TIMESTAMP "
                + "WHERE nombre_usuario = ? AND estado = 'PENDIENTE'", nombreUsuario);
        auditoria.registrar(auditoria.idUsuarioActual(), "RESTABLECER_CLAVE", "usuarios", id, null,
                "Clave temporal generada para " + nombreUsuario);

        boolean enviado = correo.enviarClaveTemporal(correoEmpleado, nombre, nombreUsuario, clave, vence, true);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("usuario", nombreUsuario);
        r.put("correoEnviado", enviado);
        if (enviado) {
            r.put("mensaje", "Se genero una nueva clave temporal y se envio a " + correoEmpleado + ".");
        } else {
            r.put("claveTemporal", clave);
            r.put("mensaje", "Se genero una nueva clave temporal, pero no se pudo enviar el correo. Entreguela en persona; "
                    + "vence en " + diasVigencia + " dia(s).");
        }
        return r;
    }

    /** Desbloquea una cuenta bloqueada por intentos fallidos (el usuario conserva su clave). */
    @PostMapping("/usuarios/{id}/desbloquear")
    @Transactional
    public Map<String, String> desbloquear(@PathVariable Integer id) {
        Map<String, Object> u = usuarioPorId(id);
        String nombreUsuario = (String) u.get("nombre_usuario");
        jdbc.update("UPDATE usuarios SET bloqueado = 'N', intentos_fallidos = 0, fecha_bloqueo = NULL WHERE id_usuario = ?", id);
        jdbc.update("UPDATE solicitudes_clave SET estado = 'ATENDIDA', fecha_atencion = CURRENT_TIMESTAMP "
                + "WHERE nombre_usuario = ? AND estado = 'PENDIENTE' AND motivo = 'BLOQUEO'", nombreUsuario);
        auditoria.registrar(auditoria.idUsuarioActual(), "DESBLOQUEO", "usuarios", id, "bloqueado", "desbloqueado (" + nombreUsuario + ")");
        return Map.of("mensaje", "Cuenta de " + nombreUsuario + " desbloqueada.");
    }

    @GetMapping("/solicitudes-clave")
    public List<Map<String, Object>> solicitudesClave() {
        return jdbc.queryForList(
                "SELECT s.id_solicitud AS \"id\", s.nombre_usuario AS \"usuario\", s.fecha_solicitud AS \"fecha\", "
                        + "s.motivo AS \"motivo\", u.id_usuario AS \"idUsuario\", (u.bloqueado = 'S') AS \"bloqueado\" "
                        + "FROM solicitudes_clave s "
                        + "LEFT JOIN usuarios u ON u.nombre_usuario = s.nombre_usuario "
                        + "WHERE s.estado = 'PENDIENTE' ORDER BY s.fecha_solicitud");
    }

    private Map<String, Object> usuarioPorId(Integer id) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT nombre_usuario, rol, activo FROM usuarios WHERE id_usuario = ?", id);
        if (filas.isEmpty()) {
            throw noEncontrado("Usuario no encontrado.");
        }
        return filas.get(0);
    }

    private void validarRolActivo(String rol) {
        if (rol == null || rol.isEmpty()) {
            throw error("Seleccione un rol.");
        }
        Integer ok = jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE nombre = ? AND activo = 'S'", Integer.class, rol);
        if (ok == null || ok == 0) {
            throw error("El rol " + rol + " no existe o esta inactivo.");
        }
    }

    private int administradoresActivos() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT u.id_usuario) FROM usuarios u JOIN usuario_roles ur ON ur.id_usuario = u.id_usuario "
                        + "JOIN roles r ON r.id_rol = ur.id_rol WHERE r.nombre = ? AND u.activo = 'S'",
                Integer.class, ROL_ADMIN);
        return n == null ? 0 : n;
    }

    // ===================================================================
    //  AUDITORIA
    // ===================================================================

    @GetMapping("/auditoria")
    public List<Map<String, Object>> consultarAuditoria(@RequestParam(required = false) String accion,
                                                        @RequestParam(required = false) String usuario,
                                                        @RequestParam(defaultValue = "200") int limite) {
        StringBuilder sql = new StringBuilder(
                "SELECT a.id_auditoria AS \"id\", a.fecha_hora AS \"fecha\", u.nombre_usuario AS \"usuario\", "
                        + "a.accion AS \"accion\", a.tabla_afectada AS \"tabla\", a.id_registro AS \"idRegistro\", "
                        + "a.datos_anteriores AS \"antes\", a.datos_nuevos AS \"despues\" "
                        + "FROM auditoria a LEFT JOIN usuarios u ON u.id_usuario = a.id_usuario WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (accion != null && !accion.isBlank()) {
            sql.append(" AND a.accion = ?");
            args.add(accion.trim());
        }
        if (usuario != null && !usuario.isBlank()) {
            sql.append(" AND u.nombre_usuario ILIKE ?");
            args.add("%" + usuario.trim() + "%");
        }
        sql.append(" ORDER BY a.fecha_hora DESC, a.id_auditoria DESC LIMIT ?");
        args.add(Math.max(1, Math.min(limite, 1000)));
        return jdbc.queryForList(sql.toString(), args.toArray());
    }
}
