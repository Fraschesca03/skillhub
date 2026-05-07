package com.projetAuthentification.authentification.dto;

/**
 * DTO pour la requête de changement de mot de passe.
 *
 * <p>Reçoit le JSON :
 * <pre>{@code
 * {
 *   "email": "toto@example.com",
 *   "oldPassword": "pwd1234",
 *   "newPassword": "NewPassword123!",
 *   "confirmPassword": "NewPassword123!"
 * }
 * }</pre>
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 */
public class ChangePasswordRequest {

    private String email;
    private String oldPassword;
    private String newPassword;
    private String confirmPassword;

    /** @return l'email de l'utilisateur dont le mot de passe doit être changé */
    public String getEmail() { return email; }
    /** @param email l'email de l'utilisateur */
    public void setEmail(String email) { this.email = email; }

    /** @return l'ancien mot de passe en clair (vérifié avant changement) */
    public String getOldPassword() { return oldPassword; }
    /** @param oldPassword l'ancien mot de passe en clair */
    public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }

    /** @return le nouveau mot de passe en clair */
    public String getNewPassword() { return newPassword; }
    /** @param newPassword le nouveau mot de passe en clair */
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }

    /** @return la confirmation du nouveau mot de passe */
    public String getConfirmPassword() { return confirmPassword; }
    /** @param confirmPassword la confirmation du nouveau mot de passe */
    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}