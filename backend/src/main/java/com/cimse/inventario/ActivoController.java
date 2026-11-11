package com.cimse.inventario;

import com.cimse.config.ApiUtil;
import com.cimse.config.Permisos;
import com.cimse.seguridad.AuditoriaService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import java.text.Normalizer;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 3 - Inventario de activos fijos (PB-08) con control unico:
 * las armas se identifican por NUMERO DE SERIE y los vehiculos por PLACA (obligatorios y no repetibles).
 */
@RestController
@RequestMapping("/api/activos")
public class ActivoController {

    private static final List<String> TIPOS_VEHICULO = Arrays.asList("AUTOMOVIL", "CAMIONETA", "MOTOCICLETA", "CAMION", "OTRO");

    private static final String SELECT =
            "SELECT i.id_activo AS \"id\", i.tipo_activo AS \"tipo\", i.descripcion AS \"descripcion\", "
                    + "i.numero_serie_o_placa AS \"serieOPlaca\", i.codigo_inventario AS \"codigo\", "
                    + "CASE WHEN i.fecha_baja IS NOT NULL THEN 'BAJA' ELSE i.estado_activo END AS \"estado\", "
                    + "i.valor_adquisicion AS \"valor\", i.ubicacion_actual AS \"ubicacion\", "
                    + "r.nombre_completo AS \"responsable\", i.fecha_registro AS \"fechaRegistro\", "
                    + "i.observaciones AS \"observaciones\", i.motivo_baja AS \"motivoBaja\" "
                    + "FROM inventario_activos i LEFT JOIN empleados r ON r.id_empleado = i.responsable_actual ";

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;

    public ActivoController(JdbcTemplate jdbc, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(required = false) String tipo,
                                            @RequestParam(required = false) String estado,
                                            @RequestParam(required = false) String q) {
        StringBuilder sql = new StringBuilder(SELECT + "WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (tipo != null && !tipo.isBlank()) {
            sql.append(" AND i.tipo_activo = ?");
            args.add(tipo.trim());
        }
        if (estado != null && !estado.isBlank()) {
            if ("BAJA".equals(estado.trim())) {
                sql.append(" AND i.fecha_baja IS NOT NULL");
            } else {
                sql.append(" AND i.fecha_baja IS NULL AND i.estado_activo = ?");
                args.add(estado.trim());
            }
        }
        if (q != null && !q.isBlank()) {
            sql.append(" AND (i.descripcion ILIKE ? OR i.numero_serie_o_placa ILIKE ? OR i.codigo_inventario ILIKE ?)");
            String patron = "%" + q.trim() + "%";
            args.add(patron);
            args.add(patron);
            args.add(patron);
        }
        sql.append(" ORDER BY i.id_activo");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** Compras registradas, necesarias para dar de alta un activo (cada activo nace de una compra). */
    @GetMapping("/compras")
    public List<Map<String, Object>> compras() {
        return jdbc.queryForList(
                "SELECT c.id_compra AS \"id\", c.numero_factura AS \"factura\", p.nombre AS \"proveedor\", "
                        + "c.categoria_insumo AS \"categoria\", c.fecha_compra AS \"fecha\" "
                        + "FROM compras_proveedores c JOIN proveedores p ON p.id_proveedor = c.id_proveedor "
                        + "ORDER BY c.id_compra DESC");
    }

    @GetMapping("/movimientos")
    public List<Map<String, Object>> movimientos(@RequestParam(required = false) Integer idActivo,
                                                 @RequestParam(required = false) String tipo) {
        StringBuilder sql = new StringBuilder(
                "SELECT m.id_movimiento AS \"id\", m.fecha_hora AS \"fechaHora\", m.tipo_movimiento AS \"tipo\", "
                        + "i.descripcion AS \"activo\", i.numero_serie_o_placa AS \"serieOPlaca\", "
                        + "m.ubicacion_origen AS \"origen\", m.ubicacion_destino AS \"destino\", m.motivo AS \"motivo\", "
                        + "e.nombre_completo AS \"responsable\", u.nombre_usuario AS \"usuario\" "
                        + "FROM movimientos_inventario m "
                        + "LEFT JOIN inventario_activos i ON i.id_activo = m.id_activo "
                        + "LEFT JOIN empleados e ON e.id_empleado = m.id_empleado_responsable "
                        + "LEFT JOIN usuarios u ON u.id_usuario = m.id_usuario WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (idActivo != null) {
            sql.append(" AND m.id_activo = ?");
            args.add(idActivo);
        }
        if (tipo != null && !tipo.isBlank()) {
            sql.append(" AND m.tipo_movimiento = ?");
            args.add(tipo.trim());
        }
        sql.append(" ORDER BY m.fecha_hora DESC, m.id_movimiento DESC LIMIT 300");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    // ---------------------------------------------------------------- catalogos (los amplia solo el administrador)

    private void soloAdministrador() {
        if (!Permisos.tiene("SEGURIDAD")) {
            throw ApiUtil.error("Solo el administrador puede agregar o desactivar tipos de activo y ubicaciones.");
        }
    }

    /** Tipos de activo: los cuatro del sistema y los que agregue el administrador. */
    @GetMapping("/tipos")
    public List<Map<String, Object>> tipos() {
        return jdbc.queryForList(
                "SELECT t.codigo AS \"codigo\", t.nombre AS \"nombre\", (t.activo = 'S') AS \"activo\", (t.es_sistema = 'S') AS \"sistema\", "
                        + "(SELECT COUNT(*) FROM inventario_activos i WHERE i.tipo_activo = t.codigo) AS \"activos\" "
                        + "FROM tipos_activo t ORDER BY CASE WHEN t.es_sistema = 'S' THEN 0 ELSE 1 END, t.nombre");
    }

    @PostMapping("/tipos")
    public Map<String, Object> agregarTipo(@RequestBody Map<String, Object> body) {
        soloAdministrador();
        String nombre = ApiUtil.textoObligatorio(body, "nombre", "nombre del tipo de activo");
        if (nombre.length() > 60) {
            throw ApiUtil.error("El nombre del tipo no puede pasar de 60 caracteres.");
        }
        String codigo = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (codigo.isEmpty() || codigo.length() > 30) {
            throw ApiUtil.error("El nombre debe tener letras o numeros y el codigo generado no puede pasar de 30 caracteres.");
        }
        if (existe("SELECT COUNT(*) FROM tipos_activo WHERE codigo = ? OR UPPER(nombre) = UPPER(?)", codigo, nombre)) {
            throw ApiUtil.error("Ya existe un tipo de activo con ese nombre.");
        }
        jdbc.update("INSERT INTO tipos_activo (codigo, nombre, activo, es_sistema) VALUES (?, ?, 'S', 'N')", codigo, nombre);
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "tipos_activo", null, null, "Tipo de activo " + nombre + " (" + codigo + ")");
        return Map.of("mensaje", "Tipo de activo agregado: " + nombre + ".");
    }

    @PutMapping("/tipos/{codigo}/estado")
    public Map<String, Object> estadoTipo(@PathVariable String codigo, @RequestBody Map<String, Object> body) {
        soloAdministrador();
        List<Map<String, Object>> t = jdbc.queryForList("SELECT es_sistema FROM tipos_activo WHERE codigo = ?", codigo);
        if (t.isEmpty()) {
            throw ApiUtil.noEncontrado("Tipo de activo no encontrado.");
        }
        if ("S".equals(t.get(0).get("es_sistema"))) {
            throw ApiUtil.error("Los cuatro tipos del sistema no se pueden desactivar.");
        }
        boolean activar = !"false".equalsIgnoreCase(ApiUtil.texto(body, "activo"));
        jdbc.update("UPDATE tipos_activo SET activo = ? WHERE codigo = ?", activar ? "S" : "N", codigo);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "tipos_activo", null, null, codigo + (activar ? " activado" : " desactivado"));
        return Map.of("mensaje", activar ? "Tipo activado." : "Tipo desactivado: ya no aparece al registrar activos.");
    }

    /** Ubicaciones (almacenes) donde se guardan los activos. */
    @GetMapping("/ubicaciones")
    public List<Map<String, Object>> ubicaciones() {
        return jdbc.queryForList(
                "SELECT u.id_ubicacion AS \"id\", u.nombre AS \"nombre\", (u.activo = 'S') AS \"activo\", "
                        + "(SELECT COUNT(*) FROM inventario_activos i WHERE i.ubicacion_actual = u.nombre AND i.fecha_baja IS NULL) AS \"activos\" "
                        + "FROM ubicaciones_inventario u ORDER BY u.nombre");
    }

    @PostMapping("/ubicaciones")
    public Map<String, Object> agregarUbicacion(@RequestBody Map<String, Object> body) {
        soloAdministrador();
        String nombre = ApiUtil.textoObligatorio(body, "nombre", "nombre de la ubicacion");
        if (nombre.length() > 100) {
            throw ApiUtil.error("El nombre de la ubicacion no puede pasar de 100 caracteres.");
        }
        if (existe("SELECT COUNT(*) FROM ubicaciones_inventario WHERE UPPER(nombre) = UPPER(?)", nombre)) {
            throw ApiUtil.error("Ya existe una ubicacion con ese nombre.");
        }
        jdbc.update("INSERT INTO ubicaciones_inventario (nombre) VALUES (?)", nombre);
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "ubicaciones_inventario", null, null, "Ubicacion " + nombre);
        return Map.of("mensaje", "Ubicacion agregada: " + nombre + ".");
    }

    @PutMapping("/ubicaciones/{id}/estado")
    public Map<String, Object> estadoUbicacion(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        soloAdministrador();
        List<Map<String, Object>> u = jdbc.queryForList("SELECT nombre FROM ubicaciones_inventario WHERE id_ubicacion = ?", id);
        if (u.isEmpty()) {
            throw ApiUtil.noEncontrado("Ubicacion no encontrada.");
        }
        boolean activar = !"false".equalsIgnoreCase(ApiUtil.texto(body, "activo"));
        if (!activar) {
            Integer en = jdbc.queryForObject("SELECT COUNT(*) FROM inventario_activos WHERE ubicacion_actual = ? AND fecha_baja IS NULL",
                    Integer.class, u.get(0).get("nombre"));
            if (en != null && en > 0) {
                throw ApiUtil.error("Hay " + en + " activo(s) en esa ubicacion; traslademelos antes de desactivarla.");
            }
        }
        jdbc.update("UPDATE ubicaciones_inventario SET activo = ? WHERE id_ubicacion = ?", activar ? "S" : "N", id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "ubicaciones_inventario", id, null,
                u.get(0).get("nombre") + (activar ? " activada" : " desactivada"));
        return Map.of("mensaje", activar ? "Ubicacion activada." : "Ubicacion desactivada.");
    }

    @GetMapping("/{id}")
    public Map<String, Object> detalle(@PathVariable Integer id) {
        List<Map<String, Object>> base = jdbc.queryForList(SELECT + "WHERE i.id_activo = ?", id);
        if (base.isEmpty()) {
            throw ApiUtil.noEncontrado("Activo no encontrado.");
        }
        Map<String, Object> r = new LinkedHashMap<>(base.get(0));
        String tipo = (String) r.get("tipo");
        List<Map<String, Object>> det = new ArrayList<>();
        if ("ARMA".equals(tipo)) {
            det = jdbc.queryForList(
                    "SELECT marca AS \"marca\", modelo AS \"modelo\", calibre AS \"calibre\", tipo_arma AS \"tipoArma\", "
                            + "numero_registro AS \"numeroRegistro\", numero_serie AS \"numeroSerie\", "
                            + "estado_arma AS \"estadoArma\" FROM armas WHERE id_activo = ?", id);
        } else if ("VEHICULO".equals(tipo)) {
            det = jdbc.queryForList(
                    "SELECT tipo_vehiculo AS \"tipoVehiculo\", marca AS \"marca\", modelo AS \"modelo\", anio AS \"anio\", "
                            + "color AS \"color\", placa AS \"placa\", vin AS \"vin\", numero_motor AS \"numeroMotor\", "
                            + "kilometraje_actual AS \"kilometraje\", fecha_vencimiento_matricula AS \"vencimientoMatricula\", "
                            + "estado_vehiculo AS \"estadoVehiculo\" FROM vehiculos WHERE id_activo = ?", id);
        } else if ("UNIFORME".equals(tipo)) {
            det = jdbc.queryForList(
                    "SELECT tipo_prenda AS \"tipoPrenda\", talla AS \"talla\", genero AS \"genero\", cantidad AS \"cantidad\" "
                            + "FROM uniformes WHERE id_activo = ?", id);
        }
        r.put("detalle", det.isEmpty() ? new LinkedHashMap<String, Object>() : det.get(0));
        r.put("movimientos", jdbc.queryForList(
                "SELECT m.fecha_hora AS \"fechaHora\", m.tipo_movimiento AS \"tipo\", m.ubicacion_origen AS \"origen\", "
                        + "m.ubicacion_destino AS \"destino\", m.motivo AS \"motivo\" FROM movimientos_inventario m "
                        + "WHERE m.id_activo = ? ORDER BY m.fecha_hora DESC, m.id_movimiento DESC LIMIT 20", id));
        r.put("asignacionActual", jdbc.queryForList(
                "SELECT a.id_asignacion AS \"id\", e.nombre_completo AS \"empleado\", a.fecha_hora_entrega AS \"fechaEntrega\" "
                        + "FROM asignacion_equipos a JOIN empleados e ON e.id_empleado = a.id_empleado "
                        + "WHERE a.id_activo = ? AND a.fecha_hora_devolucion IS NULL AND a.fecha_devolucion IS NULL", id));
        return r;
    }

    private boolean existe(String sql, Object... valores) {
        Integer n = jdbc.queryForObject(sql, Integer.class, valores);
        return n != null && n > 0;
    }

    /** Alta de un activo: arma (serie obligatoria y unica), vehiculo (placa obligatoria y unica), uniforme o equipo. */
    @PostMapping
    @Transactional
    public Map<String, Object> registrar(@RequestBody Map<String, Object> body) {
        String tipo = ApiUtil.textoObligatorio(body, "tipo", "tipo de activo");
        if (!existe("SELECT COUNT(*) FROM tipos_activo WHERE codigo = ? AND activo = 'S'", tipo)) {
            throw ApiUtil.error("Seleccione un tipo de activo de la lista (el administrador puede agregar tipos nuevos).");
        }
        Integer idCompra = ApiUtil.entero(body, "idCompra", "compra", true);
        if (!existe("SELECT COUNT(*) FROM compras_proveedores WHERE id_compra = ?", idCompra)) {
            throw ApiUtil.error("La compra seleccionada no existe.");
        }
        String descripcion = ApiUtil.textoObligatorio(body, "descripcion", "descripcion");
        if (descripcion.length() > 100) {
            throw ApiUtil.error("La descripcion no puede pasar de 100 caracteres.");
        }
        BigDecimal valor = null;
        ApiUtil.textoObligatorio(body, "valor", "valor de adquisicion");
        {
            valor = ApiUtil.decimal(body, "valor", "valor de adquisicion");
            if (valor.compareTo(BigDecimal.ZERO) < 0) {
                throw ApiUtil.error("El valor de adquisicion no puede ser negativo.");
            }
        }
        String ubicacion = ApiUtil.textoObligatorio(body, "ubicacion", "ubicacion");
        if (!existe("SELECT COUNT(*) FROM ubicaciones_inventario WHERE nombre = ? AND activo = 'S'", ubicacion)) {
            throw ApiUtil.error("Seleccione la ubicacion de la lista (el administrador puede agregar almacenes nuevos).");
        }
        LocalDate fechaAdq = ApiUtil.fecha(body, "fechaAdquisicion", "fecha de adquisicion", true);
        String codigo = ApiUtil.texto(body, "codigo");
        if (codigo != null && existe("SELECT COUNT(*) FROM inventario_activos WHERE codigo_inventario = ?", codigo)) {
            throw ApiUtil.error("Ya existe un activo con el codigo de inventario " + codigo + ".");
        }

        // Identificador unico: serie (arma) o placa (vehiculo). Obligatorio y no repetible.
        String serieOPlaca = null;
        if ("ARMA".equals(tipo)) {
            serieOPlaca = ApiUtil.textoObligatorio(body, "numeroSerie", "numero de serie del arma").toUpperCase();
            if (existe("SELECT COUNT(*) FROM inventario_activos WHERE numero_serie_o_placa = ?", serieOPlaca)
                    || existe("SELECT COUNT(*) FROM armas WHERE numero_serie = ?", serieOPlaca)) {
                throw ApiUtil.error("Ya existe un arma registrada con el numero de serie " + serieOPlaca + ".");
            }
            ApiUtil.textoObligatorio(body, "marca", "marca");
            ApiUtil.textoObligatorio(body, "tipoArma", "tipo de arma");
        } else if ("VEHICULO".equals(tipo)) {
            serieOPlaca = ApiUtil.textoObligatorio(body, "placa", "placa del vehiculo").toUpperCase();
            if (existe("SELECT COUNT(*) FROM inventario_activos WHERE numero_serie_o_placa = ?", serieOPlaca)
                    || existe("SELECT COUNT(*) FROM vehiculos WHERE placa = ?", serieOPlaca)) {
                throw ApiUtil.error("Ya existe un vehiculo registrado con la placa " + serieOPlaca + ".");
            }
            String tv = ApiUtil.textoObligatorio(body, "tipoVehiculo", "tipo de vehiculo");
            if (!TIPOS_VEHICULO.contains(tv)) {
                throw ApiUtil.error("El tipo de vehiculo debe ser AUTOMOVIL, CAMIONETA, MOTOCICLETA, CAMION u OTRO.");
            }
            ApiUtil.textoObligatorio(body, "marca", "marca");
        } else if ("UNIFORME".equals(tipo)) {
            ApiUtil.textoObligatorio(body, "tipoPrenda", "tipo de prenda");
        } else {
            serieOPlaca = ApiUtil.texto(body, "numeroSerie");
            if (serieOPlaca != null) {
                serieOPlaca = serieOPlaca.toUpperCase();
                if (existe("SELECT COUNT(*) FROM inventario_activos WHERE numero_serie_o_placa = ?", serieOPlaca)) {
                    throw ApiUtil.error("Ya existe un activo con el identificador " + serieOPlaca + ".");
                }
            }
        }

        Integer id = jdbc.queryForObject(
                "INSERT INTO inventario_activos (id_compra, tipo_activo, descripcion, numero_serie_o_placa, estado_activo, "
                        + "observaciones, codigo_inventario, valor_adquisicion, ubicacion_actual, fecha_adquisicion) "
                        + "VALUES (?, ?, ?, ?, 'DISPONIBLE', ?, ?, ?, ?, ?) RETURNING id_activo",
                Integer.class, idCompra, tipo, descripcion, serieOPlaca, ApiUtil.texto(body, "observaciones"), codigo,
                valor, ubicacion, ApiUtil.sql(fechaAdq));
        if (codigo == null) {
            jdbc.update("UPDATE inventario_activos SET codigo_inventario = ? WHERE id_activo = ?",
                    String.format("INV-%04d", id), id);
        }

        if ("ARMA".equals(tipo)) {
            jdbc.update("INSERT INTO armas (id_activo, marca, modelo, calibre, tipo_arma, numero_registro, numero_serie, "
                            + "fecha_adquisicion) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    id, ApiUtil.texto(body, "marca"), ApiUtil.texto(body, "modelo"), ApiUtil.texto(body, "calibre"),
                    ApiUtil.texto(body, "tipoArma"), ApiUtil.texto(body, "numeroRegistro"), serieOPlaca, ApiUtil.sql(fechaAdq));
        } else if ("VEHICULO".equals(tipo)) {
            Integer anio = ApiUtil.entero(body, "anio", "anio", false);
            BigDecimal km = ApiUtil.texto(body, "kilometraje") == null ? BigDecimal.ZERO
                    : ApiUtil.decimal(body, "kilometraje", "kilometraje");
            if (km.compareTo(BigDecimal.ZERO) < 0) {
                throw ApiUtil.error("El kilometraje no puede ser negativo.");
            }
            LocalDate matricula = ApiUtil.fecha(body, "vencimientoMatricula", "vencimiento de matricula", false);
            jdbc.update("INSERT INTO vehiculos (id_activo, tipo_vehiculo, marca, modelo, anio, color, placa, vin, numero_motor, "
                            + "kilometraje_actual, fecha_adquisicion, fecha_vencimiento_matricula) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, ApiUtil.texto(body, "tipoVehiculo"), ApiUtil.texto(body, "marca"), ApiUtil.texto(body, "modelo"),
                    anio, ApiUtil.texto(body, "color"), serieOPlaca, ApiUtil.texto(body, "vin"),
                    ApiUtil.texto(body, "numeroMotor"), km, ApiUtil.sql(fechaAdq), ApiUtil.sql(matricula));
        } else if ("UNIFORME".equals(tipo)) {
            BigDecimal cantidad = ApiUtil.texto(body, "cantidad") == null ? BigDecimal.ONE
                    : ApiUtil.decimal(body, "cantidad", "cantidad");
            jdbc.update("INSERT INTO uniformes (id_activo, tipo_prenda, talla, genero, cantidad) VALUES (?, ?, ?, ?, ?)",
                    id, ApiUtil.texto(body, "tipoPrenda"), ApiUtil.texto(body, "talla"), ApiUtil.texto(body, "genero"), cantidad);
        }

        jdbc.update("INSERT INTO movimientos_inventario (id_activo, tipo_movimiento, cantidad, ubicacion_destino, motivo, id_usuario) "
                        + "VALUES (?, 'ENTRADA', 1, ?, 'Registro del activo en inventario', ?)",
                id, ubicacion, auditoria.idUsuarioActual());
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "inventario_activos", id, null,
                tipo + " " + descripcion + (serieOPlaca != null ? " (" + serieOPlaca + ")" : ""));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("mensaje", "Activo registrado en el inventario.");
        return r;
    }

    @PutMapping("/{id}")
    public Map<String, String> editar(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> actual = jdbc.queryForList(
                "SELECT descripcion, ubicacion_actual FROM inventario_activos WHERE id_activo = ?", id);
        if (actual.isEmpty()) {
            throw ApiUtil.noEncontrado("Activo no encontrado.");
        }
        String descripcion = ApiUtil.textoObligatorio(body, "descripcion", "descripcion");
        if (descripcion.length() > 100) {
            throw ApiUtil.error("La descripcion no puede pasar de 100 caracteres.");
        }
        BigDecimal valor = null;
        if (ApiUtil.texto(body, "valor") != null) {
            valor = ApiUtil.decimal(body, "valor", "valor de adquisicion");
            if (valor.compareTo(BigDecimal.ZERO) < 0) {
                throw ApiUtil.error("El valor de adquisicion no puede ser negativo.");
            }
        }
        jdbc.update("UPDATE inventario_activos SET descripcion = ?, observaciones = ?, valor_adquisicion = ? WHERE id_activo = ?",
                descripcion, ApiUtil.texto(body, "observaciones"), valor, id);
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "inventario_activos", id,
                String.valueOf(actual.get(0).get("descripcion")), descripcion);
        return Map.of("mensaje", "Activo actualizado. La serie o la placa no se pueden modificar (control unico).");
    }

    /** Manda un activo a mantenimiento o lo deja disponible. No se usa mientras esta ASIGNADO. */
    @PutMapping("/{id}/estado")
    @Transactional
    public Map<String, String> cambiarEstado(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> a = jdbc.queryForList(
                "SELECT tipo_activo, estado_activo, fecha_baja FROM inventario_activos WHERE id_activo = ?", id);
        if (a.isEmpty()) {
            throw ApiUtil.noEncontrado("Activo no encontrado.");
        }
        String nuevo = ApiUtil.textoObligatorio(body, "estado", "estado");
        if (!"DISPONIBLE".equals(nuevo) && !"MANTENIMIENTO".equals(nuevo)) {
            throw ApiUtil.error("Desde aqui solo se puede cambiar a DISPONIBLE o MANTENIMIENTO. ASIGNADO se logra asignando el equipo.");
        }
        if (a.get(0).get("fecha_baja") != null) {
            throw ApiUtil.error("El activo esta dado de baja.");
        }
        if ("ASIGNADO".equals(a.get(0).get("estado_activo"))) {
            throw ApiUtil.error("El activo esta ASIGNADO. Registre primero su devolucion.");
        }
        jdbc.update("UPDATE inventario_activos SET estado_activo = ? WHERE id_activo = ?", nuevo, id);
        String tipo = (String) a.get(0).get("tipo_activo");
        if ("ARMA".equals(tipo)) {
            jdbc.update("UPDATE armas SET estado_arma = ? WHERE id_activo = ?",
                    "MANTENIMIENTO".equals(nuevo) ? "MANTENIMIENTO" : "OPERATIVA", id);
        } else if ("VEHICULO".equals(tipo)) {
            jdbc.update("UPDATE vehiculos SET estado_vehiculo = ? WHERE id_activo = ?", nuevo, id);
        }
        String motivo = ApiUtil.texto(body, "motivo");
        jdbc.update("INSERT INTO movimientos_inventario (id_activo, tipo_movimiento, cantidad, motivo, id_usuario) "
                        + "VALUES (?, 'AJUSTE', 1, ?, ?)", id,
                "Cambio de estado a " + nuevo + (motivo != null ? ": " + motivo : ""), auditoria.idUsuarioActual());
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "inventario_activos", id,
                "estado " + a.get(0).get("estado_activo"), "estado " + nuevo);
        return Map.of("mensaje", "Estado del activo: " + nuevo + ".");
    }

    /** Traslado de ubicacion (movimiento de almacen). */
    @PostMapping("/{id}/trasladar")
    @Transactional
    public Map<String, String> trasladar(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> a = jdbc.queryForList(
                "SELECT ubicacion_actual, estado_activo, fecha_baja FROM inventario_activos WHERE id_activo = ?", id);
        if (a.isEmpty()) {
            throw ApiUtil.noEncontrado("Activo no encontrado.");
        }
        if (a.get(0).get("fecha_baja") != null) {
            throw ApiUtil.error("El activo esta dado de baja.");
        }
        if ("ASIGNADO".equals(a.get(0).get("estado_activo"))) {
            throw ApiUtil.error("El activo esta ASIGNADO a un empleado; no se puede trasladar de almacen.");
        }
        String destino = ApiUtil.textoObligatorio(body, "ubicacionDestino", "ubicacion de destino");
        if (!existe("SELECT COUNT(*) FROM ubicaciones_inventario WHERE nombre = ? AND activo = 'S'", destino)) {
            throw ApiUtil.error("Seleccione el almacen de destino de la lista.");
        }
        String origen = (String) a.get(0).get("ubicacion_actual");
        if (destino.equals(origen)) {
            throw ApiUtil.error("El activo ya esta en " + destino + ".");
        }
        jdbc.update("UPDATE inventario_activos SET ubicacion_actual = ? WHERE id_activo = ?", destino, id);
        jdbc.update("INSERT INTO movimientos_inventario (id_activo, tipo_movimiento, cantidad, ubicacion_origen, ubicacion_destino, "
                        + "motivo, id_usuario) VALUES (?, 'TRANSFERENCIA', 1, ?, ?, ?, ?)",
                id, origen, destino, ApiUtil.texto(body, "motivo"), auditoria.idUsuarioActual());
        auditoria.registrar(auditoria.idUsuarioActual(), "UPDATE", "inventario_activos", id,
                "ubicacion " + origen, "ubicacion " + destino);
        return Map.of("mensaje", "Activo trasladado a " + destino + ".");
    }
}
