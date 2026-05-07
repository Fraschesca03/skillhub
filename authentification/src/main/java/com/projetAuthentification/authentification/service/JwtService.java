package com.projetAuthentification.authentification.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Service d'émission et vérification de JWT signés HS256.
 *
 * <p>Le {@code JWT_SECRET} est partagé avec Laravel (SkillHub) pour permettre
 * à Laravel de vérifier les JWT émis par ce service d'authentification
 * sans appel réseau supplémentaire.
 *
 * <p>Format compatible {@code tymon/jwt-auth} (Laravel) :
 * <ul>
 *   <li>Claims : {@code sub} (user_id), {@code iat}, {@code nbf}, {@code exp},
 *       {@code jti}, {@code email}, {@code role}, {@code nom}, {@code iss}</li>
 *   <li>Algorithme : HS256</li>
 * </ul>
 *
 * <p>Le {@code JWT_SECRET} doit faire au minimum 32 caractères (256 bits) pour HS256.
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 */
@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.ttl-minutes:60}")
    private long ttlMinutes;

    /**
     * Construit la clé HMAC à partir du secret partagé.
     *
     * @return la clé {@link SecretKey} prête pour la signature HS256
     * @throws IllegalStateException si {@code JWT_SECRET} est absent ou plus court que 32 caractères
     */
    private SecretKey getKey() {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET doit faire au moins 32 caracteres pour HS256. " +
                            "Configure la variable d'environnement JWT_SECRET."
            );
        }
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Émet un JWT HS256 signé avec le secret partagé.
     *
     * @param userId id de l'utilisateur (sera le claim "sub")
     * @param email  email de l'utilisateur
     * @param role   role (apprenant / formateur)
     * @param nom    nom complet
     * @return le JWT encodé en string
     */
    public String emit(Long userId, String email, String role, String nom) {
        long now = System.currentTimeMillis();
        long exp = now + ttlMinutes * 60 * 1000;

        return Jwts.builder()
                .subject(String.valueOf(userId))   // sub: user ID (string pour tymon)
                .claim("email", email)
                .claim("role",  role)
                .claim("nom",   nom)
                .id(UUID.randomUUID().toString())  // jti: JWT ID
                .issuedAt(new Date(now))           // iat
                .notBefore(new Date(now))          // nbf
                .expiration(new Date(exp))         // exp
                .issuer("skillhub-auth")           // iss
                .signWith(getKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Surcharge à 3 arguments (compatibilité avec login qui n'a pas le nom sous la main).
     *
     * @param userId id de l'utilisateur (sera le claim {@code sub})
     * @param email  email de l'utilisateur
     * @param role   rôle (apprenant ou formateur)
     * @return le JWT encodé en chaîne, avec un claim {@code nom} à {@code null}
     */
    public String emit(Long userId, String email, String role) {
        return emit(userId, email, role, null);
    }

    /**
     * Parse et vérifie un JWT.
     * Lève une exception si signature invalide, expiré, ou malformé.
     *
     * @param token le JWT à vérifier
     * @return les claims extraits
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}