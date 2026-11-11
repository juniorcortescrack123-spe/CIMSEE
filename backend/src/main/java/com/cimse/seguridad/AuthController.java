package com.cimse.seguridad;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Autenticacion: login con token JWT, bloqueo de la cuenta despues de 3 intentos fallidos
 * (se avisa al administrador) y solicitud de recuperacion de contrasena.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Intentos fallidos seguidos permitidos antes de bloquear la cuenta. */
    static final int MAX_INTENTOS = 3;
    /** Una cuenta de administrador bloqueada se libera sola a los 15 minutos (evita quedarse sin administrador). */
    static final long MINUTOS_BLOQUEO_ADMIN = 15;

    private final CuentaService cuentas;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder encoder;
    private final AuditoriaService auditoria;
    private final JdbcTemplate jdbc;
    private final CorreoService correo;

    public AuthController(CuentaService cuentas, JwtUtil jwtUtil, PasswordEncoder encoder,
                          AuditoriaService auditoria, JdbcTemplate jdbc, CorreoService correo) {
        this.cuentas = cuentas;
        this.jwtUtil = jwtUtil;
        this.encoder = encoder;
        this.auditoria = auditoria;
        this.jdbc = jdbc;
        this.correo = correo;
    }

    private void crearSolicitudSiNoExiste(String username, String motivo) {
        Integer pendientes = jdbc.queryForObject(
                "SELECT COUNT(*) FROM solicitudes_clave WHERE nombre_usuario = ? AND estado = 'PENDIENTE' AND motivo = ?",
                Integer.class, username, motivo);
        if (pendientes == null || pendientes == 0) {
            jdbc.update("INSERT INTO solicitudes_clave (nombre_usuario, motivo) VALUES (?, ?)", username, motivo);
        }
    }

    private ResponseEntity<?> rechazo(String mensaje) {
        return ResponseEntity.status(401).body(Map.of("error", mensaje));
    }

    /** Rechazo que le indica a la pantalla si la cuenta quedo bloqueada y cuantos intentos le quedan. */
    private ResponseEntity<?> rechazo(String mensaje, boolean bloqueada, int intentosRestantes) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("error", mensaje);
        cuerpo.put("bloqueado", bloqueada);
        cuerpo.put("intentosRestantes", intentosRestantes);
        return ResponseEntity.status(401).body(cuerpo);
    }

    private boolean esAdministrador(Cuenta c) {
        return cuentas.roles(c).contains("ADMINISTRADOR");
    }

    /** Minutos que faltan para que se libere sola una cuenta de administrador bloqueada (0 si ya puede). */
    private long minutosRestantes(Cuenta c) {
        if (c.fechaBloqueo() == null) {
            return 0;
        }
        long pasados = (System.currentTimeMillis() - c.fechaBloqueo().getTime()) / 60000;
        return Math.max(0, MINUTOS_BLOQUEO_ADMIN - pasados);
    }

    /** Otro administrador activo y sin bloquear que pueda desbloquear esta cuenta. */
    private boolean hayOtroAdministrador(Cuenta c) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT u.id_usuario) FROM usuarios u JOIN usuario_roles ur ON ur.id_usuario = u.id_usuario "
                        + "JOIN roles r ON r.id_rol = ur.id_rol WHERE r.nombre = 'ADMINISTRADOR' AND u.activo = 'S' "
                        + "AND COALESCE(u.bloqueado, 'N') <> 'S' AND u.id_usuario <> ?", Integer.class, c.id());
        return n != null && n > 0;
    }

    /**
     * Una cuenta bloqueada la desbloquea el administrador. La unica excepcion: si la cuenta bloqueada es la del
     * unico administrador disponible, se libera sola a los 15 minutos (si no, el sistema quedaria sin acceso).
     */
    private boolean seLiberaSola(Cuenta c) {
        return esAdministrador(c) && !hayOtroAdministrador(c) && minutosRestantes(c) == 0;
    }

    private void desbloquear(Integer id) {
        jdbc.update("UPDATE usuarios SET bloqueado = 'N', intentos_fallidos = 0, fecha_bloqueo = NULL WHERE id_usuario = ?", id);
    }

    /** Bloquea la cuenta, deja la solicitud para el administrador y le avisa por correo. */
    private ResponseEntity<?> bloquear(Cuenta c) {
        jdbc.update("UPDATE usuarios SET bloqueado = 'S', fecha_bloqueo = CURRENT_TIMESTAMP WHERE id_usuario = ?", c.id());
        crearSolicitudSiNoExiste(c.username(), "BLOQUEO");
        auditoria.registrar(c.id(), "BLOQUEO_CUENTA", "usuarios", c.id(), null,
                "Cuenta bloqueada tras " + MAX_INTENTOS + " intentos fallidos de inicio de sesion");

        List<String> destinos = jdbc.queryForList(
                "SELECT DISTINCT e.correo FROM usuarios u JOIN empleados e ON e.id_empleado = u.id_empleado "
                        + "JOIN usuario_roles ur ON ur.id_usuario = u.id_usuario JOIN roles r ON r.id_rol = ur.id_rol "
                        + "WHERE r.nombre = 'ADMINISTRADOR' AND u.activo = 'S' AND e.correo IS NOT NULL "
                        + "AND e.correo <> '' AND u.id_usuario <> ?", String.class, c.id());
        correo.avisarBloqueo(destinos, c.username(), c.nombreCompleto());

        String aviso = "Su cuenta se bloqueo despues de " + MAX_INTENTOS + " intentos fallidos. ";
        if (esAdministrador(c) && !hayOtroAdministrador(c)) {
            aviso += "Es el unico administrador disponible: la cuenta se libera sola en " + MINUTOS_BLOQUEO_ADMIN + " minutos.";
        } else {
            aviso += "Se notifico al administrador; hasta que la desbloquee o le restablezca la contrasena no podra ingresar.";
        }
        return rechazo(aviso, true, 0);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username") == null ? null : body.get("username").trim();
        String password = body.get("password");

        Optional<Cuenta> opt = cuentas.buscar(username);
        if (opt.isPresent() && password != null) {
            Cuenta c = opt.get();
            if (!c.activo()) {
                auditoria.registrar(c.id(), "LOGIN_FALLIDO", "usuarios", c.id(), null, "Intento con usuario inactivo");
                return rechazo("Credenciales incorrectas");
            }

            // cuenta bloqueada: ni siquiera con la clave correcta
            if (c.bloqueado()) {
                if (seLiberaSola(c)) {
                    desbloquear(c.id());
                    c = cuentas.buscar(username).orElse(c);
                } else {
                    auditoria.registrar(c.id(), "LOGIN_FALLIDO", "usuarios", c.id(), null, "Intento con la cuenta bloqueada");
                    if (esAdministrador(c) && !hayOtroAdministrador(c)) {
                        return rechazo("Su cuenta de administrador esta bloqueada. Se libera sola en " + minutosRestantes(c)
                                + " minuto(s).", true, 0);
                    }
                    return rechazo("Su cuenta esta bloqueada por intentos fallidos. El administrador ya fue notificado: "
                            + "debe desbloquearla o restablecer su contrasena.", true, 0);
                }
            }

            if (encoder.matches(password, c.hash())) {
                jdbc.update("UPDATE usuarios SET intentos_fallidos = 0 WHERE id_usuario = ?", c.id());

                // la clave temporal solo sirve durante un tiempo determinado
                if (c.debeCambiarClave() && c.claveExpira() != null
                        && c.claveExpira().before(new Timestamp(System.currentTimeMillis()))) {
                    crearSolicitudSiNoExiste(c.username(), "OLVIDO");
                    auditoria.registrar(c.id(), "LOGIN_FALLIDO", "usuarios", c.id(), null, "Clave temporal vencida");
                    return rechazo("La clave temporal vencio. Se aviso al administrador para que la restablezca.");
                }
                jdbc.update("UPDATE usuarios SET ultimo_acceso = CURRENT_TIMESTAMP WHERE id_usuario = ?", c.id());
                auditoria.registrar(c.id(), "LOGIN", "usuarios", c.id(), null, "Inicio de sesion exitoso");

                Map<String, Object> r = new LinkedHashMap<>();
                r.put("mensaje", "Login exitoso");
                r.put("token", jwtUtil.generateToken(c.username(), c.rol()));
                r.put("username", c.username());
                r.put("nombre", c.nombreCompleto());
                r.put("rol", c.rol());
                r.put("roles", cuentas.roles(c));
                r.put("permisos", cuentas.permisosUsuario(c));
                r.put("debeCambiarClave", c.debeCambiarClave());
                return ResponseEntity.ok(r);
            }

            // clave incorrecta: se cuenta el intento y al tercero se bloquea
            Integer intentos = jdbc.queryForObject(
                    "UPDATE usuarios SET intentos_fallidos = COALESCE(intentos_fallidos, 0) + 1 WHERE id_usuario = ? "
                            + "RETURNING intentos_fallidos", Integer.class, c.id());
            if (intentos != null && intentos >= MAX_INTENTOS) {
                return bloquear(c);
            }
            auditoria.registrar(c.id(), "LOGIN_FALLIDO", "usuarios", c.id(), null,
                    "Contrasena incorrecta (intento " + intentos + " de " + MAX_INTENTOS + ")");
            int restantes = MAX_INTENTOS - (intentos == null ? 0 : intentos);
            return rechazo("Usuario o contrasena incorrectos. Le " + (restantes == 1 ? "queda 1 intento" : "quedan " + restantes + " intentos")
                    + " antes de que se bloquee la cuenta.", false, restantes);
        }
        // usuario inexistente o inactivo: mensaje generico
        return rechazo("Credenciales incorrectas");
    }

    /**
     * Recuperacion de contrasena: el usuario deja una solicitud; el administrador restablece la clave y el
     * sistema le envia una nueva clave temporal por correo.
     */
    @PostMapping("/recuperar")
    public ResponseEntity<?> recuperar(@RequestBody Map<String, String> body) {
        String username = body.get("username") == null ? "" : body.get("username").trim();
        Optional<Cuenta> opt = cuentas.buscar(username);
        if (opt.isPresent() && opt.get().activo()) {
            Integer pendientes = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM solicitudes_clave WHERE nombre_usuario = ? AND estado = 'PENDIENTE' AND motivo = 'OLVIDO'",
                    Integer.class, username);
            if (pendientes == null || pendientes == 0) {
                crearSolicitudSiNoExiste(username, "OLVIDO");
                auditoria.registrar(opt.get().id(), "SOLICITUD_CLAVE", "usuarios", opt.get().id(), null,
                        "El usuario solicito recuperar su contrasena");
            }
        }
        return ResponseEntity.ok(Map.of("mensaje",
                "Si el usuario existe, el administrador recibio su solicitud y le enviara una nueva clave temporal por correo."));
    }
}
