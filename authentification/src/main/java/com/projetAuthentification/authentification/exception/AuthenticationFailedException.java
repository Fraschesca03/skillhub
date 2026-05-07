package com.projetAuthentification.authentification.exception;

/**
 * Exception levée lorsque l'authentification échoue.
 *
 * <p>Exemples : email inconnu, mot de passe incorrect, signature HMAC invalide,
 * timestamp hors fenêtre, nonce déjà consommé, token JWT expiré.
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 */
public class AuthenticationFailedException extends RuntimeException {

    /**
     * Construit l'exception avec un message explicatif.
     *
     * @param message message décrivant la cause de l'échec d'authentification
     */
    public AuthenticationFailedException(String message) {
        super(message);
    }
}