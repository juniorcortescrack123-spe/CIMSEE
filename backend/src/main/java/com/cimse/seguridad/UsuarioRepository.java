package com.cimse.seguridad;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    // Esta función busca un usuario por su nombre en la BD
    Optional<Usuario> findByUsername(String username);
}