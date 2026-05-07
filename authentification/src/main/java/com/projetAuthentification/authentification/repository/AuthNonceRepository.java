package com.projetAuthentification.authentification.repository;

import com.projetAuthentification.authentification.entity.AuthNonce;
import com.projetAuthentification.authentification.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Interface de persistance pour les nonces d'authentification.
 *
 * <p>Spring Data JPA génère automatiquement l'implémentation SQL à partir
 * des noms de méthodes : pas besoin d'écrire les requêtes SQL manuellement.
 *
 * <p><b>Comment Spring devine le SQL ?</b><br>
 * Spring lit le nom de la méthode et construit la requête :
 * <pre>{@code
 * findByUserAndNonce(user, nonce)
 *   --> SELECT * FROM auth_nonce WHERE user_id = ? AND nonce = ?
 *
 * deleteByExpiresAtBefore(date)
 *   --> DELETE FROM auth_nonce WHERE expires_at < ?
 * }</pre>
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 */
@Repository
public interface AuthNonceRepository extends JpaRepository<AuthNonce, Long> {

    /**
     * Cherche un nonce pour un utilisateur donné.
     * Utilisé pour vérifier si un nonce a déjà été vu (anti-rejeu).
     *
     * @param user  l'utilisateur concerné
     * @param nonce la valeur du nonce à chercher
     * @return Optional vide si le nonce n'existe pas (première utilisation)
     *         Optional avec l'entité si le nonce existe déjà (tentative de rejeu)
     */
    Optional<AuthNonce> findByUserAndNonce(User user, String nonce);

    /**
     * Supprime tous les nonces expirés.
     * Appelé par le scheduler toutes les 60 secondes pour nettoyer la base.
     *
     * @param dateTime supprime tous les nonces dont expires_at est avant cette date
     */
    void deleteByExpiresAtBefore(LocalDateTime dateTime);
}