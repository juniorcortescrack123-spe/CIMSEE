package com.cimse.config;

import com.cimse.seguridad.JwtFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Seguridad de la API:
 *  - autenticacion por token JWT (sin sesiones, sin formulario de login de Spring)
 *  - contrasenas cifradas con BCrypt
 *  - /api/auth/** es publico; /api/seguridad/** solo para roles con el permiso SEGURIDAD
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Evita que Spring Boot registre el filtro dos veces: solo lo usa la cadena de seguridad
    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilterRegistro(JwtFilter filtro) {
        FilterRegistrationBean<JwtFilter> registro = new FilterRegistrationBean<>(filtro);
        registro.setEnabled(false);
        return registro;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/error/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/seguridad/**").hasAuthority("PERM_SEGURIDAD")
                        // visor generico de tablas: ya no lo usa ninguna pantalla, queda solo para el administrador
                        .requestMatchers("/api/tablas/**").hasAuthority("PERM_SEGURIDAD")
                        // Sprint 1: los expedientes de personal solo para roles con el permiso PERSONAL
                        .requestMatchers("/api/personal/**").hasAuthority("PERM_PERSONAL")
                        // Sprint 2: cada modulo exige el permiso de su rol
                        // la cadena relacional tiene su propio permiso (el personal administrativo la ve sin gestionar clientes)
                        .requestMatchers("/api/comercial/cadena", "/api/comercial/cadena/**").hasAnyAuthority("PERM_CADENA", "PERM_CLIENTES")
                        .requestMatchers("/api/clientes/**", "/api/comercial/**").hasAuthority("PERM_CLIENTES")
                        .requestMatchers("/api/contratos/**").hasAuthority("PERM_CONTRATOS")
                        .requestMatchers("/api/puestos/**").hasAnyAuthority("PERM_CLIENTES", "PERM_SUPERVISION")
                        .requestMatchers("/api/turnos/**", "/api/asignaciones/**").hasAuthority("PERM_SUPERVISION")
                        // Sprint 3: supervisiones, incidencias, inventario y equipos
                        .requestMatchers("/api/supervisiones/**", "/api/incidencias/**").hasAuthority("PERM_VISITAS")
                        .requestMatchers("/api/solicitudes-guardias/**").hasAuthority("PERM_VISITAS")
                        .requestMatchers("/api/guardia/**").hasAuthority("PERM_PORTAL_GUARDIA")
                        .requestMatchers("/api/activos/**").hasAuthority("PERM_INVENTARIO")
                        .requestMatchers("/api/equipos/**").hasAuthority("PERM_EQUIPOS")
                        // Sprint 4: proveedores y compras, pagos y permisos legales, reportes
                        .requestMatchers("/api/proveedores/**").hasAuthority("PERM_PROVEEDORES")
                        .requestMatchers("/api/compras/**").hasAuthority("PERM_COMPRAS")
                        .requestMatchers("/api/pagos/**").hasAuthority("PERM_PAGOS")
                        .requestMatchers("/api/permisos-legales/**").hasAuthority("PERM_PERMISOS_LEGALES")
                        .requestMatchers("/api/reportes/**").hasAuthority("PERM_REPORTES")
                        // /api/notificaciones: cada persona ve solo las alertas de los modulos a los que tiene permiso
                        .anyRequest().authenticated());

        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
