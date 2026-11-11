package com.cimse.seguridad;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {
    // Esta es la "firma" secreta. En producción debería ir en el application.properties
    private static final String SECRET_KEY = System.getenv().getOrDefault("CIMSE_JWT_SECRET", "CIMSE_Arquitectura_Software_III_ClaveSecretaSuperSegura2026");
    private final Key key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private final long EXPIRATION_TIME = 1000 * 60 * 60 * 10; // 10 horas de validez

    // 1. Crear el token
    public String generateToken(String username, String rol) {
        return Jwts.builder()
                .setSubject(username)
                .claim("rol", rol)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // 2. Extraer el nombre de usuario del token
    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    // 3. Validar que el token sea correcto y no haya expirado
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String extractRol(String token) {
        return getClaims(token).get("rol", String.class); 
        // Asegúrate de que el claim donde guardas el rol se llame "rol" (así lo pusimos cuando creamos el token).
    }

    private Claims getClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}