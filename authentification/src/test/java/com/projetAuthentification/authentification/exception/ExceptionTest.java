package com.projetAuthentification.authentification.exception;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ExceptionTest {

    /**
     * Étapes :
     * <ol>
     *   <li>Instancie l'exception avec un message littéral</li>
     *   <li>Compare {@code getMessage()} à la chaîne fournie</li>
     * </ol>
     * Variables extérieures : aucune.
     * Retour : {@code void} — l'assertion AssertJ lève si le message diffère.
     */
    @Test
    void authenticationFailedException_message() {
        AuthenticationFailedException ex =
                new AuthenticationFailedException("Acces refuse");
        assertThat(ex.getMessage()).isEqualTo("Acces refuse");
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Instancie l'exception avec un message littéral</li>
     *   <li>Compare {@code getMessage()} à la chaîne fournie</li>
     * </ol>
     * Variables extérieures : aucune.
     * Retour : {@code void} — l'assertion AssertJ lève si le message diffère.
     */
    @Test
    void invalidInputException_message() {
        InvalidInputException ex =
                new InvalidInputException("Champ invalide");
        assertThat(ex.getMessage()).isEqualTo("Champ invalide");
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Instancie l'exception avec un message littéral</li>
     *   <li>Compare {@code getMessage()} à la chaîne fournie</li>
     * </ol>
     * Variables extérieures : aucune.
     * Retour : {@code void} — l'assertion AssertJ lève si le message diffère.
     */
    @Test
    void resourceConflictException_message() {
        ResourceConflictException ex =
                new ResourceConflictException("Email deja utilise");
        assertThat(ex.getMessage()).isEqualTo("Email deja utilise");
    }
}