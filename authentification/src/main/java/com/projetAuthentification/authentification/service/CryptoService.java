package com.projetAuthentification.authentification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Service de chiffrement AES-256-GCM et de calcul HMAC-SHA256.
 *
 * <p>GCM garantit qu'un même texte chiffré deux fois donne deux résultats différents
 * (grâce à l'IV aléatoire), et détecte automatiquement toute altération du chiffré.
 *
 * <p>Seules {@link #encrypt} et {@link #decrypt} ont changé entre TP3 (ECB) et TP4 (GCM).
 * {@link #computeHmac} et {@link #compareHmacConstantTime} restent identiques.
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 */
@Service
public class CryptoService {

    @Value("${app.master-key}")
    private String masterKey;

    // 12 octets = taille pour l'IV en GCM
    private static final int IV_SIZE  = 12;

    // 128 bits = taille maximale du Tag GCM (le plus sécurisé)
    private static final int TAG_SIZE = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    // Chiffrement AES-GCM
    /**
     * Chiffre un texte avec AES-256-GCM.
     *
     * <p>Différences avec ECB :
     * <ul>
     *   <li>Génère un IV aléatoire à chaque appel — même texte produit un résultat différent</li>
     *   <li>{@code cipher.init()} reçoit un {@link GCMParameterSpec} en plus (contient l'IV)</li>
     *   <li>Le résultat = IV (12 octets) + données chiffrées + Tag (16 octets)</li>
     *   <li>On stocke IV + données ensemble car on a besoin de l'IV pour déchiffrer</li>
     * </ul>
     *
     * @param plainText le texte en clair à chiffrer
     * @return une chaîne au format {@code v1:Base64(iv):Base64(ciphertext)}
     * @throws Exception si le chiffrement échoue (clé invalide, padding, etc.)
     */
    public String encrypt(String plainText) throws Exception {
        // Générer IV aléatoire
        byte[] iv = new byte[IV_SIZE];
        SECURE_RANDOM.nextBytes(iv);

        // Chiffrer
        SecretKeySpec key = buildKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE, iv));
        byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        // Format imposé : v1:Base64(iv):Base64(ciphertext)
        return "v1:" + Base64.getEncoder().encodeToString(iv) + ":" + Base64.getEncoder().encodeToString(ciphertext);
        // → "v1:k5dZya0y6qo=:xK9mP2qR7vL4nS8oT3..."
    }

    // Déchiffrement AES-GCM
    /**
     * Déchiffre un texte chiffré avec AES-256-GCM.
     *
     * <p>Opération inverse de {@link #encrypt} :
     * <ol>
     *   <li>Découper la chaîne {@code v1:iv:ciphertext}</li>
     *   <li>Décoder l'IV depuis Base64</li>
     *   <li>Décoder le ciphertext depuis Base64</li>
     *   <li>Déchiffrer avec le même IV</li>
     * </ol>
     *
     * <p><b>Protection bonus GCM :</b> si les données ont été modifiées en base,
     * Java lance {@code AEADBadTagException} automatiquement.
     * ECB n'a pas cette protection — il déchiffrerait silencieusement des données corrompues.
     *
     * @param encryptedText texte chiffré au format {@code v1:Base64(iv):Base64(ciphertext)}
     * @return le texte en clair correspondant
     * @throws IllegalArgumentException si le format n'est pas {@code v1:iv:ciphertext}
     * @throws Exception                si le déchiffrement échoue (clé invalide, tag invalide)
     */
    public String decrypt(String encryptedText) throws Exception {
        // Découper les 3 parties séparées par ":"
        // "v1:k5dZya0y6qo=:xK9mP2..." → ["v1", "k5dZya0y6qo=", "xK9mP2..."]
        String[] parts = encryptedText.split(":");
        if (parts.length != 3 || !parts[0].equals("v1")) {
            throw new IllegalArgumentException("Format de chiffrement invalide");
        }

        // Décoder IV et ciphertext depuis Base64
        byte[] iv         = Base64.getDecoder().decode(parts[1]);
        byte[] ciphertext = Base64.getDecoder().decode(parts[2]);

        // Déchiffrer
        SecretKeySpec key = buildKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_SIZE, iv));
        byte[] decrypted = cipher.doFinal(ciphertext);

        return new String(decrypted, StandardCharsets.UTF_8);
    }

    // Ces deux méthodes ne changent pas entre ECB et GCM
    /**
     * Calcule le HMAC-SHA256 d'un message avec un secret donné.
     *
     * <p>Utilisé côté serveur pour recalculer le HMAC envoyé par le client
     * et vérifier qu'il connaît le bon mot de passe.
     *
     * @param secret  clé secrète (le mot de passe en clair après déchiffrement AES)
     * @param message message à signer (typiquement {@code email:nonce:timestamp})
     * @return la signature HMAC-SHA256 encodée en Base64
     * @throws Exception si l'algorithme {@code HmacSHA256} n'est pas disponible
     */
    public String computeHmac(String secret, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        mac.init(keySpec);
        byte[] hmacBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    /**
     * Compare deux HMAC en temps constant pour résister aux <i>timing attacks</i>.
     *
     * <p>Utilise {@link MessageDigest#isEqual} qui prend toujours le même temps,
     * peu importe à quel caractère les deux signatures diffèrent.
     *
     * @param hmac1 première signature HMAC à comparer
     * @param hmac2 deuxième signature HMAC à comparer
     * @return {@code true} si les deux signatures sont identiques, {@code false} sinon
     */
    public boolean compareHmacConstantTime(String hmac1, String hmac2) {
        return MessageDigest.isEqual(
                hmac1.getBytes(StandardCharsets.UTF_8),
                hmac2.getBytes(StandardCharsets.UTF_8)
        );
    }

    // Méthode privée
    /**
     * Construit une clé AES-256 de 32 octets depuis la Master Key.
     *
     * @return la clé secrète {@code AES} prête à être utilisée par {@link Cipher}
     */
    private SecretKeySpec buildKey() {
        byte[] keyBytes = Arrays.copyOf(
                masterKey.getBytes(StandardCharsets.UTF_8), 32);
        return new SecretKeySpec(keyBytes, "AES");
    }
}