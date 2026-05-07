package com.projetAuthentification.authentification.validator;

import java.util.regex.Pattern;

/**
 * Vérifie qu'un mot de passe respecte la politique de sécurité définie.
 *
 * <p>Politique (TP2) :
 * <ul>
 *   <li>Minimum 12 caractères</li>
 *   <li>Au moins 1 majuscule</li>
 *   <li>Au moins 1 minuscule</li>
 *   <li>Au moins 1 chiffre</li>
 *   <li>Au moins 1 caractère spécial</li>
 * </ul>
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 */
public class PasswordPolicyValidator {

    // Regex correspondant à la politique
    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^\\w\\s]).{12,}$");

    /**
     * Vérifie si le mot de passe respecte la politique.
     *
     * @param password mot de passe à vérifier
     * @return true si conforme, false sinon
     */
    public static boolean isValid(String password) {
        if (password == null) return false;
        return PASSWORD_PATTERN.matcher(password).matches();
    }
}