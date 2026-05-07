package com.projetAuthentification.authentification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration Spring Security de l'application.
 *
 * <p>API REST sans CSRF, autorise l'accès libre à {@code /api/auth/**},
 * {@code /api/me} et {@code /actuator/**} ; toute autre route nécessite une authentification.
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 */
@Configuration
public class SecurityConfig {

    /**
     * Définit la chaîne de filtres de sécurité HTTP.
     *
     * @param http builder fourni par Spring Security
     * @return la chaîne de filtres configurée
     * @throws Exception si la configuration de la chaîne échoue
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API REST
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/actuator/**", "/api/me").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}