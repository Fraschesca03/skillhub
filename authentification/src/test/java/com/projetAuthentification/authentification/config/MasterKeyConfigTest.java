package com.projetAuthentification.authentification.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class MasterKeyConfigTest {

    /**
     * Étapes :
     * <ol>
     *   <li>Construit un {@code MasterKeyConfig} sans passer par Spring</li>
     *   <li>Injecte une chaîne vide dans le champ privé {@code masterKey}
     *       via {@link ReflectionTestUtils#setField}</li>
     *   <li>Appelle {@code validateMasterKey} et vérifie qu'une
     *       {@link IllegalStateException} contenant {@code "APP_MASTER_KEY"} est levée</li>
     * </ol>
     * Variables extérieures : aucune (instance créée à la volée).
     * Retour : {@code void}.
     */
    @Test
    @DisplayName("Demarrage KO si APP_MASTER_KEY absente")
    void masterKeyAbsente_lancerException() {
        MasterKeyConfig config = new MasterKeyConfig();
        ReflectionTestUtils.setField(config, "masterKey", "");
        assertThatThrownBy(config::validateMasterKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_MASTER_KEY");
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Construit un {@code MasterKeyConfig} sans Spring</li>
     *   <li>Injecte la chaîne {@code "tropCourte"} (10 caractères) via réflexion</li>
     *   <li>Appelle {@code validateMasterKey} et vérifie l'{@link IllegalStateException}
     *       contenant {@code "32"} (longueur minimale exigée)</li>
     * </ol>
     * Variables extérieures : aucune.
     * Retour : {@code void}.
     */
    @Test
    @DisplayName("Demarrage KO si APP_MASTER_KEY trop courte")
    void masterKeyTropCourte_lancerException() {
        MasterKeyConfig config = new MasterKeyConfig();
        ReflectionTestUtils.setField(config, "masterKey", "tropCourte");
        assertThatThrownBy(config::validateMasterKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Construit un {@code MasterKeyConfig} sans Spring</li>
     *   <li>Injecte une clé de 34 caractères via réflexion</li>
     *   <li>Appelle {@code validateMasterKey} sous {@code assertThatNoException}</li>
     * </ol>
     * Variables extérieures : aucune.
     * Retour : {@code void}.
     */
    @Test
    @DisplayName("Demarrage OK si APP_MASTER_KEY valide")
    void masterKeyValide_pasException() {
        MasterKeyConfig config = new MasterKeyConfig();
        ReflectionTestUtils.setField(config, "masterKey",
                "UneCleSuperSecreteDeMinimum32Car!!");
        assertThatNoException().isThrownBy(config::validateMasterKey);
    }
}