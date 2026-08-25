package pe.clinica.cardenas.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public final class JwtService {
    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(String secret, long expirationMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET debe contener al menos 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(String email, String fullName, String role) {
        return generateToken(email, fullName, role, 0);
    }

    public String generateToken(String email, String fullName, String role, int sessionVersion) {
        return generateToken(email, fullName, role, sessionVersion, TenantContext.DEFAULT_CLINIC, "cardenas", false);
    }

    public String generateToken(String email, String fullName, String role, int sessionVersion,
                                String clinicId, String clinicSlug, boolean platformAdmin) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("name", fullName)
                .claim("role", role)
                .claim("sv", sessionVersion)
                .claim("clinicId", TenantContext.normalize(clinicId))
                .claim("clinicSlug", clinicSlug == null ? "cardenas" : clinicSlug)
                .claim("platformAdmin", platformAdmin)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
