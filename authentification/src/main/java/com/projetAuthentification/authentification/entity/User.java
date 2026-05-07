package com.projetAuthentification.authentification.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entité JPA représentant un utilisateur, alignée sur le modèle Laravel SkillHub.
 *
 * <p><b>Important :</b> cette entité partage la même table {@code users} que Laravel.
 * Les champs correspondent à la migration Laravel {@code create_users_table}.
 *
 * <p>Le champ {@code password} contient le mot de passe chiffré AES-GCM (réversible)
 * pour permettre le protocole HMAC de login du TP3.
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    // Colonne "password" pour être compatible avec le schéma Laravel
    // Contient le chiffré AES-GCM (TP4), pas le plain text
    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false)
    private String nom;

    // Rôle : "apprenant" ou "formateur"
    @Column(nullable = false)
    private String role = "apprenant";

    // Champs optionnels présents côté Laravel
    @Column(nullable = true)
    private String photo;

    @Column(nullable = true, columnDefinition = "TEXT")
    private String bio;

    // Protection brute-force (TP2 — conservé)
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "lock_until")
    private LocalDateTime lockUntil;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    // ── Getters et Setters ───────────────────────────────────────────────────

    /** @return l'identifiant unique auto-généré */
    public Long getId() { return id; }
    /** @param id l'identifiant unique */
    public void setId(Long id) { this.id = id; }

    /** @return l'email de l'utilisateur */
    public String getEmail() { return email; }
    /** @param email l'email de l'utilisateur */
    public void setEmail(String email) { this.email = email; }

    /**
     * Retourne le mot de passe chiffré AES-GCM (pas le plain text).
     * Utiliser {@link com.projetAuthentification.authentification.service.CryptoService#decrypt}
     * pour obtenir le plain text.
     *
     * @return le mot de passe chiffré au format {@code v1:iv:ciphertext}
     */
    public String getPassword() { return password; }
    /** @param password le mot de passe chiffré AES-GCM */
    public void setPassword(String password) { this.password = password; }

    /** @return alias de {@link #getPassword()} pour compatibilité */
    public String getPasswordEncrypted() { return password; }
    /** @param value le mot de passe chiffré AES-GCM */
    public void setPasswordEncrypted(String value) { this.password = value; }

    /** @return le nom complet */
    public String getNom() { return nom; }
    /** @param nom le nom complet */
    public void setNom(String nom) { this.nom = nom; }

    /** @return le rôle (apprenant ou formateur) */
    public String getRole() { return role; }
    /** @param role le rôle (apprenant ou formateur) */
    public void setRole(String role) { this.role = role; }

    /** @return l'URL ou nom de fichier de la photo de profil */
    public String getPhoto() { return photo; }
    /** @param photo l'URL ou nom de fichier de la photo de profil */
    public void setPhoto(String photo) { this.photo = photo; }

    /** @return la biographie de l'utilisateur */
    public String getBio() { return bio; }
    /** @param bio la biographie de l'utilisateur */
    public void setBio(String bio) { this.bio = bio; }

    /** @return le compteur de tentatives de connexion échouées */
    public int getFailedAttempts() { return failedAttempts; }
    /** @param v le compteur de tentatives échouées */
    public void setFailedAttempts(int v) { this.failedAttempts = v; }

    /** @return la date jusqu'à laquelle le compte est verrouillé */
    public LocalDateTime getLockUntil() { return lockUntil; }
    /** @param v la date de fin de verrouillage */
    public void setLockUntil(LocalDateTime v) { this.lockUntil = v; }

    /** @return la date de création du compte */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** @param v la date de création du compte */
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }

    /** @return la date de dernière modification */
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    /** @param v la date de dernière modification */
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}