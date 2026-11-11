package com.cimse.sistema;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Endpoints para comprobar la conexion a la BD y ver las tablas desde la web.
 * Solo para la etapa de preparacion tecnica (se protegera con JWT despues).
 */
@RestController
@RequestMapping("/api")
public class TablasController {

    private final JdbcTemplate jdbc;

    public TablasController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Integer uno = jdbc.queryForObject("SELECT 1", Integer.class);
        String version = jdbc.queryForObject("SELECT version()", String.class);
        return Map.of("backend", "UP", "baseDeDatos", uno != null && uno == 1 ? "UP" : "DOWN", "version", version);
    }

    @GetMapping("/tablas")
    public List<String> tablas() {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name",
                String.class);
    }

    @GetMapping("/tablas/{nombre}")
    public List<Map<String, Object>> filas(@PathVariable String nombre) {
        // solo se permiten nombres que realmente existen (evita SQL injection)
        if (!tablas().contains(nombre)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tabla no encontrada: " + nombre);
        }
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT * FROM \"" + nombre + "\" LIMIT 200");
        // no exponer contrasenas ni archivos binarios en el visor
        filas.forEach(f -> f.keySet().removeIf(k -> k.contains("contrasena") || k.contains("blob")));
        return filas;
    }
}
