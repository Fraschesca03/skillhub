package com.projetAuthentification.authentification.dto;

/**
 * DTO (Data Transfer Object) représentant le corps JSON envoyé par le client
 * lors d'une tentative de connexion (TP3).
 *
 * <p><b>Avant (TP2) le JSON ressemblait à :</b>
 * <pre>{@code
 * {
 *   "email"    : "alice@gmail.com",
 *   "password" : "MonMotDePasse123!"
 * }
 * }</pre>
 *
 * <p><b>Maintenant (TP3) le JSON ressemble à :</b>
 * <pre>{@code
 * {
 *   "email"     : "alice@gmail.com",
 *   "nonce"     : "a3f7c2d1-8b4e-47f6-9c1a-3e5d7f8b9012",
 *   "timestamp" : 1711234567,
 *   "hmac"      : "xK9mP2qR7vL4nS8oT3..."
 * }
 * }</pre>
 *
 * <p>Le mot de passe n'apparaît plus dans ce JSON. À la place on a une preuve
 * cryptographique ({@code hmac}) que l'on connaît le mot de passe, sans l'avoir envoyé.
 *
 * <p><b>Pourquoi un DTO plutôt qu'une Map ?</b><br>
 * Dans le TP2 on utilisait {@code Map<String, String>} dans le contrôleur.
 * C'est fonctionnel mais fragile : si on oublie une clé, on obtient {@code null}
 * sans message d'erreur clair. Un DTO typé est plus robuste et plus lisible.
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 */
public class LoginRequest {

    // L'email de l'utilisateur qui veut se connecter
    private String email;

    // UUID aléatoire généré par le client — utilisé une seule fois
    // Exemple : "a3f7c2d1-8b4e-47f6-9c1a-3e5d7f8b9012"
    private String nonce;

    // Secondes depuis le 1er janvier 1970 (Unix Epoch)
    // Exemple : 1711234567
    private long timestamp;

    // La signature HMAC-SHA256 encodée en Base64
    // Prouve que le client connaît le mot de passe sans l'envoyer
    // Exemple : "xK9mP2qR7vL4nS8oT3uQ6wE1rY5..."
    private String hmac;

    // Getters et Setters
    // Spring (Jackson) a besoin des getters pour sérialiser
    // et des setters pour désérialiser le JSON entrant

    /** @return l'email de l'utilisateur qui veut se connecter */
    public String getEmail() { return email; }
    /** @param email l'email de l'utilisateur */
    public void setEmail(String email) { this.email = email; }

    /** @return le nonce UUID unique généré par le client */
    public String getNonce() { return nonce; }
    /** @param nonce le nonce UUID unique généré par le client */
    public void setNonce(String nonce) { this.nonce = nonce; }

    /** @return le timestamp en secondes Unix envoyé par le client */
    public long getTimestamp() { return timestamp; }
    /** @param timestamp le timestamp en secondes Unix envoyé par le client */
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    /** @return la signature HMAC-SHA256 encodée en Base64 */
    public String getHmac() { return hmac; }
    /** @param hmac la signature HMAC-SHA256 encodée en Base64 */
    public void setHmac(String hmac) { this.hmac = hmac; }
}