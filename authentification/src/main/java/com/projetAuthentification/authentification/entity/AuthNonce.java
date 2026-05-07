package com.projetAuthentification.authentification.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entité JPA représentant un nonce utilisé dans le protocole d'authentification HMAC.
 *
 * <p><b>C'est quoi un nonce ?</b><br>
 * Un nonce (<i>Number Used Once</i>) est une chaîne aléatoire unique générée par
 * le client pour chaque tentative de connexion. Le serveur la stocke ici
 * pour s'assurer qu'elle ne sera jamais réutilisée.
 *
 * <p><b>Pourquoi cette table existe ?</b><br>
 * Sans cette table, un attaquant qui intercepte la requête de login
 * ({@code email + nonce + timestamp + hmac}) pourrait la renvoyer identique
 * au serveur et se connecter à la place de l'utilisateur — c'est une <i>replay attack</i>.
 * En stockant chaque nonce consommé, le serveur peut détecter et rejeter
 * toute tentative de réutilisation.
 *
 * <p><b>Structure de la table {@code auth_nonce} :</b>
 * <ul>
 *   <li>{@code id}          : identifiant auto-généré</li>
 *   <li>{@code user_id}     : référence vers l'utilisateur concerné</li>
 *   <li>{@code nonce}       : la valeur UUID unique</li>
 *   <li>{@code expires_at}  : date d'expiration (now + 2 minutes)</li>
 *   <li>{@code consumed}    : {@code true} si ce nonce a déjà été utilisé</li>
 *   <li>{@code created_at}  : date de création</li>
 * </ul>
 *
 * <p><b>Contrainte unique :</b> la paire {@code (user_id, nonce)} est unique
 * en base — un même nonce ne peut apparaître qu'une seule fois par utilisateur.
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 */
@Entity
@Table(
    name = "auth_nonce",
    uniqueConstraints = {
        // Cette contrainte SQL garantit qu'un même nonce ne peut pas être enregistré deux fois pour le même utilisateur
        @UniqueConstraint(columnNames = {"user_id", "nonce"})
    }
)
public class AuthNonce {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Lien vers l'utilisateur propriétaire de ce nonce
    // @ManyToOne = plusieurs nonces peuvent appartenir à un même utilisateur
    // @JoinColumn = en base, cette colonne s'appelle "user_id"
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // La valeur du nonce — un UUID comme "a3f7c2d1-8b4e-47f6-9c1a-3e5d7f8b9012"
    @Column(nullable = false)
    private String nonce;

    // Date/heure d'expiration : now + 120 secondes
    // Après cette date, le nonce peut être supprimé par le scheduler
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // true = ce nonce a déjà été utilisé pour une connexion
    // Un nonce consommé est immédiatement rejeté s'il est réutilisé
    @Column(nullable = false)
    private boolean consumed = false;

    // Date de création — utile pour le débogage et les logs
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // ── Getters et Setters ───────────────────────────────────────────────────

    /** @return l'identifiant auto-généré */
    public Long getId() { return id; }
    /** @param id l'identifiant */
    public void setId(Long id) { this.id = id; }

    /** @return l'utilisateur propriétaire de ce nonce */
    public User getUser() { return user; }
    /** @param user l'utilisateur propriétaire */
    public void setUser(User user) { this.user = user; }

    /** @return la valeur UUID du nonce */
    public String getNonce() { return nonce; }
    /** @param nonce la valeur UUID du nonce */
    public void setNonce(String nonce) { this.nonce = nonce; }

    /** @return la date/heure d'expiration */
    public LocalDateTime getExpiresAt() { return expiresAt; }
    /** @param expiresAt la date/heure d'expiration */
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }

    /** @return {@code true} si ce nonce a déjà été utilisé */
    public boolean isConsumed() { return consumed; }
    /** @param consumed {@code true} si le nonce est consommé */
    public void setConsumed(boolean consumed) { this.consumed = consumed; }

    /** @return la date de création */
    public LocalDateTime getCreatedAt() { return createdAt; }
    /** @param createdAt la date de création */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}