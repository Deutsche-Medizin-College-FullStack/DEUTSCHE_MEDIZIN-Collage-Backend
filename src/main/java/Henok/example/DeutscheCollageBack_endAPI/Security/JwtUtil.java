package Henok.example.DeutscheCollageBack_endAPI.Security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    private final String secretKey;
    private final long jwtExpirationMs;

    // Constructor injection - Spring will inject the values automatically
    public JwtUtil(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.expiration}") long jwtExpirationMs) {

        this.secretKey = secretKey;
        this.jwtExpirationMs = jwtExpirationMs;

        // Optional: basic validation in development
        if (secretKey.length() < 32) {
            throw new IllegalStateException("JWT secret key is too short! Minimum 256 bits (32 characters) required.");
        }
    }

    /**
     * Generates a JWT token for the authenticated user
     * @param userDetails user details containing username and authorities
     * @return signed JWT token
     */
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        // We store only the first (primary) role – adjust if you support multiple roles
        claims.put("role", userDetails.getAuthorities().iterator().next().getAuthority());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(SignatureAlgorithm.HS256, secretKey.getBytes(StandardCharsets.UTF_8))
                .compact();
    }

    public String extractUsername(String token) {
        return Jwts.parser()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private boolean isTokenExpired(String token) {
        return Jwts.parser()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token)
                .getBody()
                .getExpiration()
                .before(new Date());
    }
}