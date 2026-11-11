package com.cimse.personal;

import com.cimse.config.ApiUtil;
import com.cimse.config.Permisos;
import com.cimse.config.Validaciones;
import com.cimse.seguridad.Auditable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Expedientes de personal. Todos los campos del empleado son obligatorios (revision del 27 de octubre);
 * el sueldo solo lo ve y lo escribe el administrador (permiso SEGURIDAD).
 */
@RestController
@RequestMapping("/api/personal")
public class EmpleadoController {

    private static final Set<String> CARGOS = Set.of("GUARDIA", "SUPERVISOR", "GERENTE_OPERACIONES",
            "ADMINISTRATIVO", "GERENTE_GENERAL");

    private final EmpleadoRepository empleadoRepository;

    EmpleadoController(EmpleadoRepository empleadoRepository) {
        this.empleadoRepository = empleadoRepository;
    }

    @GetMapping
    public List<Empleado> listar() {
        return empleadoRepository.findAll();
    }

    /** Valida y normaliza los datos del empleado (identidad sin guiones, telefono 9999-9999, correo en minusculas). */
    private void validarDatos(Empleado e, boolean validarIdentidad) {
        if (validarIdentidad) {
            e.setIdentidad(Validaciones.identidad(e.getIdentidad()));
        }
        String nombre = e.getNombreCompleto() == null ? "" : e.getNombreCompleto().trim().replaceAll("\\s+", " ");
        if (nombre.isEmpty()) {
            throw ApiUtil.error("El nombre completo es obligatorio.");
        }
        if (!nombre.matches("^[\\p{L} .'-]{3,100}$") || nombre.split(" ").length < 2) {
            throw ApiUtil.error("Escriba el nombre completo (nombre y apellido), solo con letras.");
        }
        e.setNombreCompleto(nombre);

        if (e.getFechaNacimiento() == null) {
            throw ApiUtil.error("La fecha de nacimiento es obligatoria.");
        }
        if (e.getFechaNacimiento().isAfter(LocalDate.now().minusYears(18))) {
            throw ApiUtil.error("El empleado debe ser mayor de 18 anios.");
        }
        if (e.getDireccion() == null || e.getDireccion().trim().length() < 5) {
            throw ApiUtil.error("La direccion es obligatoria.");
        }
        e.setDireccion(e.getDireccion().trim());
        e.setTelefono(Validaciones.telefono(e.getTelefono(), "telefono"));
        e.setCorreo(Validaciones.correo(e.getCorreo()));
        if (e.getCargo() == null || !CARGOS.contains(e.getCargo())) {
            throw ApiUtil.error("Seleccione el cargo del empleado.");
        }
    }

    @Auditable(accion = "CREAR", tabla = "empleados")
    @PostMapping
    public Empleado crear(@RequestBody Empleado empleado) {
        empleado.setIdEmpleado(null);
        validarDatos(empleado, true);
        if (empleadoRepository.existsByIdentidad(empleado.getIdentidad())) {
            throw ApiUtil.error("Ya existe un empleado con la identidad " + empleado.getIdentidad() + ".");
        }
        // el sueldo solo lo escribe el administrador
        if (Permisos.tiene("SEGURIDAD")) {
            BigDecimal sueldo = empleado.getSueldoBase();
            if (sueldo == null || sueldo.compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiUtil.error("El sueldo base es obligatorio y debe ser mayor que cero.");
            }
        } else {
            empleado.setSueldoBase(null);
        }
        // empleado nuevo: contratado y activo (si no, no aparece para crearle usuario ni asignarlo a un puesto)
        empleado.setActivo("S");
        empleado.setEstadoCandidato("CONTRATADO");
        empleado.setFechaIngreso(LocalDate.now());
        empleado.setFechaRetiro(null);
        return empleadoRepository.save(empleado);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Empleado> buscar(@PathVariable Long id) {
        return empleadoRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Auditable(accion = "ACTUALIZAR", tabla = "empleados")
    @PutMapping("/{id}")
    public ResponseEntity<Empleado> actualizar(@PathVariable Long id, @RequestBody Empleado detalles) {
        return empleadoRepository.findById(id).map(empleado -> {
            validarDatos(detalles, false);
            empleado.setNombreCompleto(detalles.getNombreCompleto());
            empleado.setFechaNacimiento(detalles.getFechaNacimiento());
            empleado.setTelefono(detalles.getTelefono());
            empleado.setCorreo(detalles.getCorreo());
            empleado.setDireccion(detalles.getDireccion());
            empleado.setCargo(detalles.getCargo());
            // el sueldo solo lo ve y lo cambia el administrador: si no llega, se conserva el actual
            if (Permisos.tiene("SEGURIDAD") && detalles.getSueldoBase() != null) {
                if (detalles.getSueldoBase().compareTo(BigDecimal.ZERO) <= 0) {
                    throw ApiUtil.error("El sueldo base debe ser mayor que cero.");
                }
                empleado.setSueldoBase(detalles.getSueldoBase());
            }
            if (detalles.getEstadoCandidato() != null && !detalles.getEstadoCandidato().isBlank()) {
                empleado.setEstadoCandidato(detalles.getEstadoCandidato());
            }
            empleado.setFechaRetiro(detalles.getFechaRetiro());
            if (detalles.getActivo() != null && !detalles.getActivo().isBlank()) {
                empleado.setActivo(detalles.getActivo());
            }
            return ResponseEntity.ok(empleadoRepository.save(empleado));
        }).orElse(ResponseEntity.notFound().build());
    }

    @Auditable(accion = "ELIMINAR", tabla = "empleados")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id) {
        if (empleadoRepository.existsById(id)) {
            empleadoRepository.deleteById(id);
            return ResponseEntity.ok("Empleado eliminado correctamente.");
        }
        return ResponseEntity.notFound().build();
    }
}
