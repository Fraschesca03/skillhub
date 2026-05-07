package com.projetAuthentification.authentification.exception;

/**
 * Exception levée lorsqu'une entrée utilisateur est invalide.
 *
 * <p>Exemples : email vide, mot de passe trop court, rôle inconnu,
 * deux mots de passe ne correspondant pas lors d'un changement.
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 */
public class InvalidInputException extends RuntimeException {

    /**
     * Construit l'exception avec un message explicatif.
     *
     * @param message message décrivant la cause du rejet de l'entrée
     */
    public InvalidInputException(String message) { super(message); }
}