package com.cimse.seguridad;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Datos del usuario conectado y cambio de su propia contrasena. */
@RestController
@RequestMapping("/api/me")
public class PerfilController {

    private final CuentaService cuentas;
    private final AuditoriaService auditoria;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;

    public PerfilController(CuentaService cuentas, AuditoriaService auditoria, PasswordEncoder encoder,
                            JdbcTemplate jdbc) {
        this.cuentas = cuentas;
        this.auditoria = auditoria;
        this.encoder = encoder;
        this.jdbc = jdbc;
    }

    private Cuenta actual() {
        return cuentas.buscar(auditoria.usuarioActual())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sesion invalida"));
    }

    @GetMapping
    public Map<String, Object> perfil() {
        Cuenta c = actual();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("username", c.username());
        r.put("nombre", c.nombreCompleto());
        r.put("rol", c.rol());
        r.put("roles", cuentas.roles(c));
        r.put("permisos", cuentas.permisosUsuario(c));
        r.put("debeCambiarClave", c.debeCambiarClave());
        return r;
    }

    @PostMapping("/clave")
    public Map<String, String> cambiarClave(@RequestBody Map<String, String> body) {
        Cuenta c = actual();
        String actualClave = body.get("actual");
        String nueva = body.get("nueva");

        if (actualClave == null || !encoder.matches(actualClave, c.hash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contrasena actual no es correcta.");
        }
        String error = PoliticaClave.validar(nueva, c.username());
        if (error != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);
        }
        if (encoder.matches(nueva, c.hash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La nueva contrasena debe ser diferente a la actual.");
        }
        jdbc.update("UPDATE usuarios SET contrasena_hash = ?, debe_cambiar_clave = 'N', clave_expira = NULL WHERE id_usuario = ?",
                encoder.encode(nueva), c.id());
        auditoria.registrar(c.id(), "CAMBIO_CLAVE", "usuarios", c.id(), null, "El usuario cambio su contrasena");
        return Map.of("mensaje", "Contrasena actualizada correctamente.");
    }
}
