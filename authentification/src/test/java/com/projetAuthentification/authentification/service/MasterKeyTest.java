package com.projetAuthentification.authentification.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests obligatoires TP4 sur la Master Key et le chiffrement.
 *
 * @SpringBootTest charge le contexte Spring complet
 * @TestPropertySource utilise application-test.properties
 * qui injecte une Master Key fictive et une base H2 en mémoire
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class MasterKeyTest {

    @Autowired
    private CryptoService cryptoService;

    /**
     * Étapes :
     * <ol>
     *   <li>Chiffre une chaîne littérale via {@code cryptoService.encrypt}</li>
     *   <li>Déchiffre le résultat via {@code cryptoService.decrypt}</li>
     *   <li>Vérifie l'égalité avec le texte initial</li>
     * </ol>
     * Variables extérieures : {@code cryptoService} (autowired du contexte Spring,
     * configuré par {@code application-test.properties}).
     * Retour : {@code void}.
     *
     * @throws Exception remontée par {@code encrypt}/{@code decrypt}
     */
    @Test
    @DisplayName("Encrypt puis decrypt retourne le texte original")
    void encryptDecryptOk() throws Exception {
        String original = "MonMotDePasse123!";
        String chiffre  = cryptoService.encrypt(original);
        String retour   = cryptoService.decrypt(chiffre);
        assertThat(retour).isEqualTo(original);
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Chiffre une chaîne littérale</li>
     *   <li>Compare le résultat à la chaîne d'origine avec {@code isNotEqualTo}</li>
     * </ol>
     * Variables extérieures : {@code cryptoService} autowired.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par {@code encrypt}
     */
    @Test
    @DisplayName("Le chiffre est different du clair")
    void chiffreEstDifferentDuClair() throws Exception {
        String original = "MonMotDePasse123!";
        String chiffre  = cryptoService.encrypt(original);
        assertThat(chiffre).isNotEqualTo(original);
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Chiffre une chaîne littérale</li>
     *   <li>Vérifie que le résultat commence par {@code "v1:"}</li>
     *   <li>Vérifie qu'un split sur {@code ":"} produit exactement 3 segments</li>
     * </ol>
     * Variables extérieures : {@code cryptoService} autowired.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par {@code encrypt}
     */
    @Test
    @DisplayName("Format de stockage correct : v1:iv:ciphertext")
    void formatChiffreEstCorrect() throws Exception {
        String chiffre = cryptoService.encrypt("test");
        assertThat(chiffre).startsWith("v1:");
        assertThat(chiffre.split(":")).hasSize(3);
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Chiffre une chaîne littérale</li>
     *   <li>Découpe le résultat sur {@code ":"} et altère le 3ᵉ segment
     *       (ciphertext) en y concaténant {@code "XXXX"}</li>
     *   <li>Vérifie que {@code decrypt} sur la chaîne altérée lève une exception
     *       (typiquement {@code AEADBadTagException} via le tag GCM)</li>
     * </ol>
     * Variables extérieures : {@code cryptoService} autowired.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par {@code encrypt}
     */
    @Test
    @DisplayName("Dechiffrement KO si ciphertext modifie")
    void dechiffrementKoSiModifie() throws Exception {
        String chiffre = cryptoService.encrypt("MonMotDePasse123!");
        String[] parts   = chiffre.split(":");
        String   modifie = parts[0] + ":" + parts[1] + ":" + parts[2] + "XXXX";
        assertThatThrownBy(() -> cryptoService.decrypt(modifie))
                .isInstanceOf(Exception.class);
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Chiffre deux fois la même chaîne littérale</li>
     *   <li>Vérifie que les deux résultats diffèrent (IV aléatoire à chaque appel)</li>
     * </ol>
     * Variables extérieures : {@code cryptoService} autowired.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par {@code encrypt}
     */
    @Test
    @DisplayName("Deux chiffrements du meme texte donnent des resultats differents")
    void deuxChiffrementsDifferents() throws Exception {
        String texte    = "MonMotDePasse123!";
        String chiffre1 = cryptoService.encrypt(texte);
        String chiffre2 = cryptoService.encrypt(texte);
        assertThat(chiffre1).isNotEqualTo(chiffre2);
    }
}