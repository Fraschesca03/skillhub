package com.projetAuthentification.authentification.repository;

import com.projetAuthentification.authentification.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * Repository Spring Data JPA pour l'entité {@link User}.
 *
 * <p>Spring génère automatiquement les implémentations à partir des noms
 * de méthodes ({@code findByEmail}, {@code existsByEmail}).
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Recherche un utilisateur par son email.
     *
     * @param email email à rechercher
     * @return un {@link Optional} contenant l'utilisateur s'il existe, vide sinon
     */
    Optional<User> findByEmail(String email);

    /**
     * Vérifie si un utilisateur avec cet email existe déjà.
     *
     * @param email email à vérifier
     * @return {@code true} si l'email existe en base, {@code false} sinon
     */
    boolean existsByEmail(String email);
}