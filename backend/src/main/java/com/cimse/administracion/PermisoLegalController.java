package com.cimse.administracion;

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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 4 - Permisos legales: alcaldia, armeria (armas), operacion y otros (PB-11).
 * Semaforo igual al de los contratos: VENCIDO, ROJO (7 dias o menos), AMARILLO (30 dias o menos), VERDE.
 * Un permiso no se elimina (queda su historial); cuando se renueva se actualiza su fecha de vencimiento.
 */
@RestController
@RequestMapping("/api/permisos-legales")
public class PermisoLegalController {

    private static final List<String> TIPOS = Arrays.asList("ALCALDIA", "ARMAS", "OPERACION", "OTRO");

    private static final String SELECT =
            "SELECT id_permiso AS \"id\", tipo AS \"tipo\", numero_permiso AS \"numero\", fecha_emision AS \"fechaEmision\", "
                    + "fecha_vencimiento AS \"fechaVencimiento\", estado AS \"estado\", "
                    + "(fecha_vencimiento - CURRENT_DATE) AS \"diasRestantes\" FROM permisos_administrativos ";

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public PermisoLegalController(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    static String semaforo(int dias) {
        if (dias < 0) {
            return "VENCIDO";
        }
        if (dias <= 7) {
            return "ROJO";
        }
        return dias <= 30 ? "AMARILLO" : "VERDE";
    }

    /** Pasa a VENCIDO los permisos cuya fecha ya paso. */
    private void actualizarEstados() {
        jdbc.update("UPDATE permisos_administrativos SET estado = 'VENCIDO' "
                + "WHERE estado = 'VIGENTE' AND fecha_vencimiento < CURRENT_DATE");
    }

    private List<Map<String, Object>> conSemaforo(List<Map<String, Object>> filas) {
        List<Map<String, Object>> r = new ArrayList<>();
        for (Map<String, Object> f : filas) {
            Map<String, Object> m = new LinkedHashMap<>(f);
            m.put("semaforo", semaforo(((Number) f.get("diasRestantes")).intValue()));
            r.add(m);
        }
        return r;
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(required = false) String tipo) {
        actualizarEstados();
        if (tipo != null && !tipo.isBlank()) {
            return conSemaforo(jdbc.queryForList(SELECT + "WHERE tipo = ? ORDER BY fecha_vencimiento", tipo.trim()));
        }
        return conSemaforo(jdbc.queryForList(SELECT + "ORDER BY fecha_vencimiento"));
    }

    /** Permisos que vencen dentro de los proximos N dias (30 por defecto), incluidos los ya vencidos. */
    @GetMapping("/alertas")
    public List<Map<String, Object>> alertas(@RequestParam(defaultValue = "30") int dias) {
        actualizarEstados();
        int limite = Math.max(1, Math.min(dias, 365));
        return conSemaforo(jdbc.queryForList(SELECT + "WHERE fecha_vencimiento <= CURRENT_DATE + ? "
                + "ORDER BY fecha_vencimiento", limite));
    }

    private void validar(Map<String, Object> body, Integer idActual) {
        String tipo = ApiUtil.textoObligatorio(body, "tipo", "tipo de permiso");
        if (!TIPOS.contains(tipo)) {
            throw ApiUtil.error("El tipo debe ser ALCALDIA, ARMAS, OPERACION u OTRO.");
        }
        String numero = ApiUtil.textoObligatorio(body, "numero", "numero de permiso");
        if (numero.length() > 50) {
            throw ApiUtil.error("El numero de permiso no puede pasar de 50 caracteres.");
        }
        Integer repetido = idActual == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM permisos_administrativos WHERE numero_permiso = ?", Integer.class, numero)
                : jdbc.queryForObject("SELECT COUNT(*) FROM permisos_administrativos WHERE numero_permiso = ? AND id_permiso <> ?",
                        Integer.class, numero, idActual);
        if (repetido != null && repetido > 0) {
            throw ApiUtil.error("Ya existe un permiso con el numero " + numero + ".");
        }
        LocalDate emision = ApiUtil.fecha(body, "fechaEmision", "fecha de emision", true);
        LocalDate vence = ApiUtil.fecha(body, "fechaVencimiento", "fecha de vencimiento", true);
        if (emision.isAfter(LocalDate.now())) {
            throw ApiUtil.error("La fecha de emision no puede ser futura.");
        }
        if (!vence.isAfter(emision)) {
            throw ApiUtil.error("La fecha de vencimiento debe ser posterior a la de emision.");
        }
    }

    @PostMapping
    public Map<String, Object> registrar(@RequestBody Map<String, Object> body) {
        validar(body, null);
        LocalDate vence = ApiUtil.fecha(body, "fechaVencimiento", "fecha de vencimiento", true);
        Integer id = jdbc.queryForObject(
                "INSERT INTO permisos_administrativos (tipo, numero_permiso, fecha_emision, fecha_vencimiento, estado) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id_permiso", Integer.class,
                ApiUtil.texto(body, "tipo"), ApiUtil.texto(body, "numero"),
                ApiUtil.sql(ApiUtil.fecha(body, "fechaEmision", "fecha de emision", true)), ApiUtil.sql(vence),
                vence.isBefore(LocalDate.now()) ? "VENCIDO" : "VIGENTE");
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "permisos_administrativos", id, null,
                "Permiso " + ApiUtil.texto(body, "tipo") + " " + ApiUtil.texto(body, "numero") + " vence " + vence);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensaje", "Permiso registrado.");
        return r;
    }

    @PutMapping("/{id}")
    public Map<String, Object> editar(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> actual = jdbc.queryForList(SELECT + "WHERE id_permiso = ?", id);
        if (actual.isEmpty()) {
            throw ApiUtil.noEncontrado("Permiso no encontrado.");
        }
        validar(body, id);
        LocalDate vence = ApiUtil.fecha(body, "fechaVencimiento", "fecha de vencimiento", true);
        jdbc.update("UPDATE permisos_administrativos SET tipo = ?, numero_permiso = ?, fecha_emision = ?, "
                        + "fecha_vencimiento = ?, estado = ? WHERE id_permiso = ?",
                ApiUtil.texto(body, "tipo"), ApiUtil.texto(body, "numero"),
                ApiUtil.sql(ApiUtil.fecha(body, "fechaEmision", "fecha de emision", true)), ApiUtil.sql(vence),
                vence.isBefore(LocalDate.now()) ? "VENCIDO" : "VIGENTE", id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "permisos_administrativos", id,
                "vence " + actual.get(0).get("fechaVencimiento"), "vence " + vence);
        return Map.of("mensaje", "Permiso actualizado.");
    }

    /** Renueva el permiso con la nueva fecha de vencimiento (debe ser posterior a la actual). */
    @PostMapping("/{id}/renovar")
    public Map<String, Object> renovar(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> actual = jdbc.queryForList(SELECT + "WHERE id_permiso = ?", id);
        if (actual.isEmpty()) {
            throw ApiUtil.noEncontrado("Permiso no encontrado.");
        }
        LocalDate nueva = ApiUtil.fecha(body, "fechaVencimiento", "nueva fecha de vencimiento", true);
        LocalDate anterior = ((java.sql.Date) actual.get(0).get("fechaVencimiento")).toLocalDate();
        if (!nueva.isAfter(anterior) || !nueva.isAfter(LocalDate.now())) {
            throw ApiUtil.error("La nueva fecha debe ser posterior al vencimiento actual (" + anterior + ") y a hoy.");
        }
        jdbc.update("UPDATE permisos_administrativos SET fecha_emision = CURRENT_DATE, fecha_vencimiento = ?, "
                + "estado = 'VIGENTE' WHERE id_permiso = ?", ApiUtil.sql(nueva), id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "permisos_administrativos", id,
                "vencia " + anterior, "renovado hasta " + nueva);
        return Map.of("mensaje", "Permiso renovado hasta " + nueva + ".");
    }
}
