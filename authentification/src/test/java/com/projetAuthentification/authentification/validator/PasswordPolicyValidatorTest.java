package com.projetAuthentification.authentification.validator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyValidatorTest {

    /**
     * Étapes :
     * <ol>
     *   <li>Appelle {@code isValid} avec une chaîne respectant les 5 règles</li>
     *   <li>Vérifie que le retour est {@code true} via {@code assertTrue}</li>
     * </ol>
     * Variables extérieures : aucune.
     * Retour : {@code void}.
     */
    @Test
    void validPasswordShouldPass() {
        String password = "Password1234!?";
        assertTrue(PasswordPolicyValidator.isValid("Abcdef123!@#"));
    }

    /**
     * Étapes : appel direct de {@code isValid} avec une chaîne de 5 caractères
     * et assertion {@code assertFalse}.
     * Variables extérieures : aucune.
     * Retour : {@code void}.
     */
    @Test
    void motDePasseInvalide_tropCourt() {
        assertFalse(PasswordPolicyValidator.isValid("Abc1!"));
    }

    /**
     * Étapes : appel direct de {@code isValid} avec un mot de passe sans lettre
     * majuscule et assertion {@code assertFalse}.
     * Variables extérieures : aucune.
     * Retour : {@code void}.
     */
    @Test
    void motDePasseInvalide_sansMajuscule() {
        assertFalse(PasswordPolicyValidator.isValid("abcdef123!@#"));
    }

    /**
     * Étapes : appel direct de {@code isValid} avec un mot de passe sans lettre
     * minuscule et assertion {@code assertFalse}.
     * Variables extérieures : aucune.
     * Retour : {@code void}.
     */
    @Test
    void motDePasseInvalide_sansMinuscule() {
        assertFalse(PasswordPolicyValidator.isValid("ABCDEF123!@#"));
    }

    /**
     * Étapes : appel direct de {@code isValid} avec un mot de passe sans chiffre
     * et assertion {@code assertFalse}.
     * Variables extérieures : aucune.
     * Retour : {@code void}.
     */
    @Test
    void motDePasseInvalide_sansChiffre() {
        assertFalse(PasswordPolicyValidator.isValid("Abcdefgh!@#"));
    }

    /**
     * Étapes : appel direct de {@code isValid} avec un mot de passe sans
     * caractère spécial et assertion {@code assertFalse}.
     * Variables extérieures : aucune.
     * Retour : {@code void}.
     */
    @Test
    void motDePasseInvalide_sansSpecial() {
        assertFalse(PasswordPolicyValidator.isValid("Abcdefgh1234"));
    }

    /**
     * Étapes : appel direct de {@code isValid} avec {@code null} et assertion
     * {@code assertFalse} (la méthode garde une protection contre les nulls).
     * Variables extérieures : aucune.
     * Retour : {@code void}.
     */
    @Test
    void motDePasseNull() {
        assertFalse(PasswordPolicyValidator.isValid(null));
    }
}