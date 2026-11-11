package com.cimse.comercial;

import com.cimse.config.ApiUtil;
import com.cimse.seguridad.AuditoriaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contrato escaneado (PDF): el archivo se guarda en una carpeta fija del servidor
 * (variable CIMSE_STORAGE_DIR, un volumen de Docker) y en la base de datos solo queda la ruta.
 */
@RestController
@RequestMapping("/api/contratos")
public class ContratoDocumentoController {

    private static final long MAXIMO = 10L * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final AuditoriaService auditoria;
    private final Path raiz;

    public ContratoDocumentoController(JdbcTemplate jdbc, AuditoriaService auditoria,
                                       @Value("${cimse.storage.dir:./uploads}") String directorio) {
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.raiz = Paths.get(directorio).toAbsolutePath().normalize();
    }

    @GetMapping("/{id}/documentos")
    public List<Map<String, Object>> listar(@PathVariable Integer id) {
        return jdbc.queryForList(
                "SELECT d.id_documento AS \"id\", d.nombre_archivo AS \"nombre\", d.tamanio AS \"tamanio\", "
                        + "d.fecha_subida AS \"fecha\", u.nombre_usuario AS \"subio\" FROM contrato_documentos d "
                        + "LEFT JOIN usuarios u ON u.id_usuario = d.id_usuario_subio "
                        + "WHERE d.id_contrato = ? ORDER BY d.id_documento DESC", id);
    }

    @PostMapping("/{id}/documentos")
    public Map<String, Object> subir(@PathVariable Integer id, @RequestParam("archivo") MultipartFile archivo)
            throws IOException {
        Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM contratos WHERE id_contrato = ?", Integer.class, id);
        if (existe == null || existe == 0) {
            throw ApiUtil.noEncontrado("Contrato no encontrado.");
        }
        if (archivo == null || archivo.isEmpty()) {
            throw ApiUtil.error("Seleccione el archivo PDF del contrato escaneado.");
        }
        String original = archivo.getOriginalFilename() == null ? "contrato.pdf" : new File(archivo.getOriginalFilename()).getName();
        if (!original.toLowerCase().endsWith(".pdf")) {
            throw ApiUtil.error("Solo se permiten archivos PDF.");
        }
        if (archivo.getSize() > MAXIMO) {
            throw ApiUtil.error("El archivo no puede pasar de 10 MB.");
        }
        // verificacion basica del contenido: un PDF empieza con %PDF
        byte[] cabecera = new byte[4];
        try (java.io.InputStream in = archivo.getInputStream()) {
            if (in.read(cabecera) < 4 || cabecera[0] != '%' || cabecera[1] != 'P' || cabecera[2] != 'D' || cabecera[3] != 'F') {
                throw ApiUtil.error("El archivo no es un PDF valido.");
            }
        }
        Path carpeta = raiz.resolve("contratos").resolve(String.valueOf(id));
        Files.createDirectories(carpeta);
        Path destino = carpeta.resolve(UUID.randomUUID() + ".pdf");
        try (java.io.InputStream in = archivo.getInputStream()) {
            Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
        }
        String ruta = raiz.relativize(destino).toString().replace('\\', '/');
        Integer idDoc = jdbc.queryForObject(
                "INSERT INTO contrato_documentos (id_contrato, nombre_archivo, ruta_archivo, tamanio, id_usuario_subio) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id_documento",
                Integer.class, id, original, ruta, archivo.getSize(), auditoria.idUsuarioActual());
        auditoria.registrar(auditoria.idUsuarioActual(), "INSERT", "contrato_documentos", idDoc, null,
                "PDF " + original + " del contrato #" + id);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", idDoc);
        r.put("mensaje", "Contrato escaneado guardado.");
        return r;
    }

    @GetMapping("/documentos/{idDocumento}")
    public ResponseEntity<Resource> descargar(@PathVariable Integer idDocumento) {
        List<Map<String, Object>> d = jdbc.queryForList(
                "SELECT nombre_archivo, ruta_archivo FROM contrato_documentos WHERE id_documento = ?", idDocumento);
        if (d.isEmpty()) {
            throw ApiUtil.noEncontrado("Documento no encontrado.");
        }
        Path archivo = raiz.resolve((String) d.get(0).get("ruta_archivo")).normalize();
        if (!archivo.startsWith(raiz) || !Files.exists(archivo)) {
            throw ApiUtil.noEncontrado("El archivo no esta en el almacenamiento del servidor.");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + d.get(0).get("nombre_archivo") + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(new FileSystemResource(archivo));
    }
}
