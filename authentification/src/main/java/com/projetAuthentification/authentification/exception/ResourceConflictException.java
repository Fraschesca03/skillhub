package com.projetAuthentification.authentification.exception;

/**
 * Exception levée lorsqu'une ressource entre en conflit avec l'état actuel.
 *
 * <p>Exemple : email déjà utilisé lors d'une inscription.
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 */
public class ResourceConflictException extends RuntimeException {

    /**
     * Construit l'exception avec un message explicatif.
     *
     * @param message message décrivant le conflit
     */
    public ResourceConflictException(String message) { super(message); }
}