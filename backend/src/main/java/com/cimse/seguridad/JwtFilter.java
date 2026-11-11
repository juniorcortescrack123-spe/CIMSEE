package com.cimse.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Guardian de la API: lee el token JWT, confirma en la BD que el usuario sigue activo y le asigna los
 * permisos de TODOS sus roles (PERM_xxx). Si el administrador desactiva a un usuario o le cambia los roles,
 * el efecto es inmediato.
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CuentaService cuentas;

    public JwtFilter(JwtUtil jwtUtil, CuentaService cuentas) {
        this.jwtUtil = jwtUtil;
        this.cuentas = cuentas;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String jwt = header.substring(7);
            if (jwtUtil.validateToken(jwt)) {
                Optional<Cuenta> cuenta = cuentas.buscar(jwtUtil.extractUsername(jwt));
                if (cuenta.isPresent() && cuenta.get().activo() && !cuenta.get().bloqueado()) {
                    List<GrantedAuthority> autoridades = new ArrayList<>();
                    for (String rol : cuentas.roles(cuenta.get())) {
                        autoridades.add(new SimpleGrantedAuthority(rol));
                        autoridades.add(new SimpleGrantedAuthority("ROLE_" + rol));
                    }
                    for (String p : cuentas.permisosUsuario(cuenta.get())) {
                        autoridades.add(new SimpleGrantedAuthority("PERM_" + p));
                    }
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(cuenta.get().username(), null, autoridades);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(request, response);
    }
}
