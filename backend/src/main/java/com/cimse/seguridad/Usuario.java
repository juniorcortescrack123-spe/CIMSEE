package com.cimse.seguridad;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "usuarios")
public class Usuario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long id;

    // 👇 IMPORTANTE: Mapeamos la llave foránea al empleado
    @Column(name = "id_empleado") 
    private Integer idEmpleado;

    @Column(name = "nombre_usuario", unique = true, nullable = false)
    private String username;

    @Column(name = "contrasena_hash", nullable = false)
    private String password;

    @Column(name = "rol")
    private String rol;

    // 👇 NUEVO CAMPO: Para saber si está activo o inactivo ('S' o 'N')
    @Column(name = "activo", length = 1)
    private String activo = "S";

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    public LocalDateTime getUltimoAcceso() { return ultimoAcceso; }
    public void setUltimoAcceso(LocalDateTime ultimoAcceso) { this.ultimoAcceso = ultimoAcceso; }

    // --- Métodos Getters y Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getIdEmpleado() { return idEmpleado; }
    public void setIdEmpleado(Integer idEmpleado) { this.idEmpleado = idEmpleado; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }

    // Getter y Setter para el campo activo
    public String getActivo() { return activo; }
    public void setActivo(String activo) { this.activo = activo; }
}