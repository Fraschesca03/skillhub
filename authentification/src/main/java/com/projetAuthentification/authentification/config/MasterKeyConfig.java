package com.projetAuthentification.authentification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

/**
 * Vérifie que {@code APP_MASTER_KEY} est présente au démarrage.
 *
 * <p>Si absente, l'application refuse de démarrer.
 * La méthode annotée {@code @PostConstruct} est exécutée juste après
 * que Spring ait injecté toutes les dépendances.
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 */
@Configuration
public class MasterKeyConfig {

    /** Master Key injectée depuis la propriété {@code app.master-key}. */
    @Value("${app.master-key}")
    private String masterKey;

    /**
     * Valide la présence et la longueur minimale de la Master Key au démarrage.
     *
     * @throws IllegalStateException si {@code APP_MASTER_KEY} est absente, vide
     *                               ou plus courte que 32 caractères (AES-256)
     */
    @PostConstruct
    public void validateMasterKey() {
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException(
                    "APP_MASTER_KEY est absente ou vide. " +
                            "L'application ne peut pas démarrer sans la Master Key. " +
                            "Définissez la variable d'environnement APP_MASTER_KEY."
            );
        }
        if (masterKey.length() < 32) {
            throw new IllegalStateException(
                    "APP_MASTER_KEY doit faire au moins 32 caractères pour AES-256."
            );
        }
    }
}