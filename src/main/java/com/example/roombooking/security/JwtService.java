package com.example.roombooking.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(JwtProperties properties) {
        byte[] secret = Decoders.BASE64.decode(properties.secret());
        // Keys.hmacShaKeyFor() would pick HS384/HS512 for longer secrets; pin HS256 regardless of key length.
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 256 bits (32 bytes) for HS256");
        }
        this.key = new SecretKeySpec(secret, "HmacSHA256");
        this.expirationMs = properties.expirationMs();
    }

    public String generateToken(UserPrincipal principal) {
        Date now = new Date();
        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("role", principal.getRole().name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Returns the username if the token is well-formed, correctly signed and not expired. */
    public Optional<String> extractValidUsername(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            if (!Jwts.SIG.HS256.getId().equals(jws.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            return Optional.ofNullable(jws.getPayload().getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
