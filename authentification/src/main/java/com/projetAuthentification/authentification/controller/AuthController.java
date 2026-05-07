package com.projetAuthentification.authentification.controller;

import com.projetAuthentification.authentification.dto.LoginRequest;
import com.projetAuthentification.authentification.entity.User;
import com.projetAuthentification.authentification.service.AuthService;
import org.springframework.web.bind.annotation.*;
import com.projetAuthentification.authentification.dto.ChangePasswordRequest;
import java.util.Map;

/**
 * Contrôleur REST exposant les endpoints d'authentification (TP3).
 *
 * <p><b>Changements vs TP2 :</b>
 * <ul>
 *   <li>{@link #login} reçoit maintenant un {@link LoginRequest} (DTO) au lieu d'une
 *       {@code Map<String,String>}, car {@code timestamp} est un {@code long} numérique</li>
 *   <li>{@link #register} : inchangé dans le principe</li>
 *   <li>{@link #me} : inchangé</li>
 * </ul>
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 * @see AuthService
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    private static final String EMAIL_KEY    = "email";
    private static final String PASSWORD_KEY = "password";

    private final AuthService authService;
    private final com.projetAuthentification.authentification.service.JwtService jwtService;

    /**
     * Construit le contrôleur avec ses dépendances injectées par Spring.
     *
     * @param authService service d'authentification (register, login, me, changePassword)
     * @param jwtService  service d'émission de JWT pour la réponse de {@link #register}
     */
    public AuthController(AuthService authService,
                          com.projetAuthentification.authentification.service.JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }
    /**
     * Endpoint {@code POST /api/auth/register} : inscrit un nouvel utilisateur.
     *
     * <p>Body JSON attendu :
     * <pre>{@code { "email":"...", "password":"...", "nom":"...", "role":"apprenant|formateur" } }</pre>
     *
     * <p>Retourne l'utilisateur créé et un JWT directement utilisable
     * (permet au front d'enchaîner register puis pages authentifiées sans re-login).
     *
     * @param body corps JSON contenant {@code email}, {@code password}, {@code nom} et {@code role}
     * @return une map contenant {@code message}, {@code user} et {@code token}
     */
    @PostMapping("/auth/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        String email    = body.get(EMAIL_KEY);
        String password = body.get(PASSWORD_KEY);
        String nom      = body.get("nom");
        String role     = body.getOrDefault("role", "apprenant");

        User user = authService.register(email, password, nom, role);
        String token = jwtService.emit(user.getId(), user.getEmail(), user.getRole(), user.getNom());

        return Map.of(
                "message", "Utilisateur cree avec succes",
                "user", Map.of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "nom", user.getNom(),
                        "role", user.getRole()
                ),
                "token", token
        );
    }

    /**
     * Endpoint {@code POST /api/auth/login} : authentification via protocole HMAC (TP3).
     *
     * <p>Body JSON attendu :
     * <pre>{@code { "email":"...", "nonce":"...", "timestamp":1711234567, "hmac":"..." } }</pre>
     *
     * <p><b>Changement TP3 :</b> on reçoit un {@link LoginRequest} (DTO) et non
     * plus une {@code Map<String,String>}, car {@code timestamp} est un {@code long}
     * numérique. Le mot de passe n'apparaît plus dans ce JSON.
     *
     * @param request DTO contenant {@code email}, {@code nonce}, {@code timestamp}, {@code hmac}
     * @return une map contenant {@code accessToken} et {@code expiresAt}
     */
    @PostMapping("/auth/login")
    public Map<String, String> login(@RequestBody LoginRequest request) {
        return authService.login(
                request.getEmail(),
                request.getNonce(),
                request.getTimestamp(),
                request.getHmac()
        );
    }

    /**
     * Endpoint {@code GET /api/me} : retourne l'email de l'utilisateur connecté.
     *
     * <p>Header attendu : {@code Authorization: <accessToken>} (avec ou sans préfixe Bearer).
     * Inchangé vs TP2.
     *
     * @param token access token JWT envoyé dans le header {@code Authorization}
     * @return une map contenant la clé {@code email}
     */
    @GetMapping("/me")
    public Map<String, String> me(@RequestHeader("Authorization") String token) {
        User user = authService.getUserFromToken(token);
        return Map.of(EMAIL_KEY, user.getEmail());
    }

    /**
     * Endpoint {@code PUT /api/auth/change-password} : change le mot de passe d'un utilisateur.
     *
     * <p>Body JSON attendu :
     * <pre>{@code
     * {
     *   "email": "toto@auth.com",
     *   "oldPassword": "pwd1234",
     *   "newPassword": "TotoNouveauMdpd123!",
     *   "confirmPassword": "TotoNouveauMdpd123!"
     * }
     * }</pre>
     *
     * @param request DTO contenant l'email, l'ancien mot de passe et le nouveau (deux fois)
     * @return une map contenant un message de succès
     */
    @PutMapping("/auth/change-password")
    public Map<String, String> changePassword(@RequestBody ChangePasswordRequest request) {

        authService.changePassword(
                request.getEmail(),
                request.getOldPassword(),
                request.getNewPassword(),
                request.getConfirmPassword()
        );

        return Map.of("message", "Mot de passe change avec succes");
    }
}