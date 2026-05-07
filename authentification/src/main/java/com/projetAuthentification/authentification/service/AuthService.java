package com.projetAuthentification.authentification.service;

import com.projetAuthentification.authentification.entity.AuthNonce;
import com.projetAuthentification.authentification.entity.User;
import com.projetAuthentification.authentification.exception.AuthenticationFailedException;
import com.projetAuthentification.authentification.exception.InvalidInputException;
import com.projetAuthentification.authentification.exception.ResourceConflictException;
import com.projetAuthentification.authentification.repository.AuthNonceRepository;
import com.projetAuthentification.authentification.repository.UserRepository;
import com.projetAuthentification.authentification.validator.PasswordPolicyValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service principal d'authentification implémentant le protocole HMAC-SSO (TP3).
 *
 * <p>Ce service gère l'inscription, la connexion via signature HMAC-SHA256,
 * le changement de mot de passe et le nettoyage périodique des nonces.
 *
 * <p><b>Protocole de connexion :</b>
 * <pre>{@code
 * CLIENT                                    SERVEUR
 * nonce     = UUID.randomUUID()
 * timestamp = Instant.now().epochSecond
 * message   = email + ":" + nonce + ":" + timestamp
 * hmac      = HMAC-SHA256(password, message)
 *   POST /api/auth/login { email, nonce, timestamp, hmac }
 *                                            1. email existe ?
 *                                            2. timestamp dans +-60s ?
 *                                            3. nonce jamais vu ?
 *                                            4. dechiffrer password (AES)
 *                                            5. recalculer hmac
 *                                            6. comparer en temps constant
 *                                            7. consommer nonce
 *                                            8. emettre accessToken
 *   RETOUR { accessToken, expiresAt }
 * }</pre>
 *
 * <p><b>Changements vs TP2 :</b>
 * <ul>
 *   <li>{@link #register} : {@code BCryptPasswordEncoder} remplacé par {@link CryptoService#encrypt} (AES)</li>
 *   <li>{@link #login} : comparaison de hash remplacée par vérification HMAC</li>
 *   <li>Gestion des nonces anti-rejeu via {@link AuthNonceRepository}</li>
 *   <li>Scheduler de nettoyage des nonces expirés</li>
 * </ul>
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 * @see CryptoService
 * @see JwtService
 */
@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    // Dependances injectees par Spring
    private final UserRepository      userRepository;
    private final AuthNonceRepository authNonceRepository;
    private final CryptoService       cryptoService;
    private final JwtService          jwtService;

    // Stocke en memoire les tokens actifs : token UUID -> email
    // ConcurrentHashMap est thread-safe (plusieurs requetes simultanees)
    private final ConcurrentHashMap<String, String> tokenStore = new ConcurrentHashMap<>();

    // Parametres lus depuis application.properties
    // La syntaxe :60 signifie "valeur par defaut = 60 si la propriete n'existe pas"
    @Value("${app.auth.timestamp-window:60}")
    private long timestampWindow;

    @Value("${app.auth.nonce-ttl:120}")
    private long nonceTtl;

    @Value("${app.auth.token-ttl:15}")
    private long tokenTtl;

    /**
     * Construit le service d'authentification avec ses dépendances injectées par Spring.
     *
     * @param userRepository      repository d'accès aux utilisateurs
     * @param authNonceRepository repository d'accès aux nonces
     * @param cryptoService       service de chiffrement AES-GCM et HMAC
     * @param jwtService          service d'émission et vérification de JWT
     */
    public AuthService(UserRepository userRepository,
                       AuthNonceRepository authNonceRepository,
                       CryptoService cryptoService,
                       JwtService jwtService) {
        this.userRepository      = userRepository;
        this.authNonceRepository = authNonceRepository;
        this.cryptoService       = cryptoService;
        this.jwtService          = jwtService;
    }

    // INSCRIPTION 

    /**
     * Inscrit un nouvel utilisateur avec un rôle (apprenant ou formateur).
     *
     * <p>Le mot de passe est chiffré en AES-GCM avec la Master Key (TP4)
     * afin de permettre la vérification HMAC lors du login (TP3).
     *
     * @param email    email de l'utilisateur, non {@code null} et non vide
     * @param password mot de passe en clair, conforme à {@link PasswordPolicyValidator}
     * @param nom      nom complet de l'utilisateur, non {@code null} et non vide
     * @param role     {@code "apprenant"} ou {@code "formateur"} ; vaut {@code "apprenant"} par défaut
     * @return l'entité {@link User} persistée en base
     * @throws InvalidInputException     si l'email, le nom, le rôle ou le mot de passe est invalide
     * @throws ResourceConflictException si l'email est déjà utilisé
     */
    public User register(String email, String password, String nom, String role) {
        if (email == null || email.isBlank()) {
            logger.warn("Inscription echouee : email vide");
            throw new InvalidInputException("Email vide");
        }
        if (nom == null || nom.isBlank()) {
            throw new InvalidInputException("Nom vide");
        }
        if (role == null || role.isBlank()) {
            role = "apprenant";
        }
        if (!role.equals("apprenant") && !role.equals("formateur")) {
            throw new InvalidInputException("Role invalide (apprenant ou formateur)");
        }
        if (!PasswordPolicyValidator.isValid(password)) {
            throw new InvalidInputException(
                    "Mot de passe doit contenir 12 caracteres avec 1 maj, " +
                            "1 minuscule, 1 chiffre et 1 caractere special");
        }
        if (userRepository.existsByEmail(email)) {
            logger.warn("Inscription echouee : email deja utilise");
            throw new ResourceConflictException("Email deja utilise");
        }

        try {
            User user = new User();
            user.setEmail(email);
            user.setNom(nom);
            user.setRole(role);
            // Chiffrement AES-GCM TP4 (conservé)
            user.setPasswordEncrypted(cryptoService.encrypt(password));
            userRepository.save(user);
            logger.info("Inscription reussie");
            return user;
        } catch (Exception e) {
            logger.error("Erreur de chiffrement lors de l inscription", e);
            throw new RuntimeException("Erreur interne lors de l inscription");
        }
    }

    // CONNEXION (protocole HMAC)

    /**
     * Authentifie un utilisateur via le protocole HMAC-SSO.
     *
     * <p>Les vérifications sont effectuées dans cet ordre précis :
     * <ol>
     *   <li>L'email existe en base</li>
     *   <li>Le timestamp est dans la fenêtre {@code ±timestampWindow} secondes</li>
     *   <li>Le nonce n'a jamais été utilisé pour cet utilisateur</li>
     *   <li>Le mot de passe est déchiffré (AES-GCM)</li>
     *   <li>Le HMAC est recalculé puis comparé en temps constant</li>
     *   <li>Le nonce est consommé (persisté avec {@code consumed=true})</li>
     *   <li>Un access token JWT HS256 est émis</li>
     * </ol>
     *
     * @param email     email de l'utilisateur
     * @param nonce     UUID unique généré par le client
     * @param timestamp secondes Unix envoyées par le client
     * @param hmacRecu  signature HMAC-SHA256 envoyée par le client
     * @return une {@link Map} contenant les clés {@code "accessToken"} et {@code "expiresAt"}
     * @throws AuthenticationFailedException si l'email est inconnu, le timestamp hors fenêtre,
     *                                       le nonce déjà consommé ou la signature HMAC invalide
     */
    public Map<String, String> login(String email, String nonce, long timestamp, String hmacRecu) {

        // Etape 1 : verifier si email existe
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    logger.warn("Connexion echouee : email inconnu");
                    // On dit "Acces refuse" plutot que "Email inconnu" pour ne pas reveler si l email existe en base
                    return new AuthenticationFailedException("Acces refuse");
                });

        // Etape 2 : Timestamp dans la fenetre +-60 secondes ?
        // Instant.now().getEpochSecond() = heure actuelle du serveur en secondes Unix
        long maintenant = Instant.now().getEpochSecond();
        if (Math.abs(maintenant - timestamp) > timestampWindow) {
            logger.warn("Connexion echouee : timestamp invalide ");
            throw new AuthenticationFailedException("Acces refuse");
        }

        // Etape 3 : Nonce jamais vu ?
        // findByUserAndNonce retourne un Optional
        // ifPresent = "si on trouve ce nonce en base, executer ce bloc"
        // Si le nonce est deja en base = tentative de rejeu = refus
        authNonceRepository.findByUserAndNonce(user, nonce).ifPresent(n -> {
            logger.warn("Connexion echouee : nonce reutilise");
            throw new AuthenticationFailedException("Acces refuse");
        });

        // Etape 4 : Dechiffrer le mot de passe
        // On retrouve le mot de passe en clair grace au dechiffrement AES + SMK C'est la cle qui va servir a recalculer le HMAC
        String motDePasseClair;
        try {
            motDePasseClair = cryptoService.decrypt(user.getPasswordEncrypted());
        } catch (Exception e) {
            logger.error("Erreur de dechiffrement", e);
            throw new AuthenticationFailedException("Erreur interne");
        }

        // Etape 5 : Recalculer et comparer le HMAC
        // On reconstruit exactement le meme message que le client a signe
        String message = email + ":" + nonce + ":" + timestamp;

        String hmacAttendu;
        try {
            // computeHmac() calcule HMAC-SHA256(motDePasseClair, message)
            // Si le client a utilise le bon mot de passe, les deux HMAC seront identiques
            hmacAttendu = cryptoService.computeHmac(motDePasseClair, message);
        } catch (Exception e) {
            logger.error("Erreur de calcul HMAC", e);
            throw new AuthenticationFailedException("Erreur interne");
        }

        // Comparaison en temps constant — OBLIGATOIRE contre les timing attacks
        // compareHmacConstantTime() prend toujours le meme temps, peu importe
        // a quel caractere les deux signatures different
        if (!cryptoService.compareHmacConstantTime(hmacAttendu, hmacRecu)) {
            logger.warn("Connexion echouee : HMAC invalide");
            throw new AuthenticationFailedException("Acces refuse");
        }

        // Etape 6 : Consommer le nonce
        // On enregistre ce nonce en base avec consumed=true
        // Toute tentative de reutilisation sera bloquee a l etape 3
        AuthNonce authNonce = new AuthNonce();
        authNonce.setUser(user);
        authNonce.setNonce(nonce);
        authNonce.setExpiresAt(LocalDateTime.now().plusSeconds(nonceTtl));
        authNonce.setConsumed(true);
        authNonceRepository.save(authNonce);

        // Etape 7 : Emettre le JWT HS256 (compatible Laravel)
        // La signature utilise le JWT_SECRET partagé avec Laravel
        // Laravel peut vérifier ce token sans appeler Spring Boot
        String accessToken = jwtService.emit(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getNom()
        );
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(tokenTtl);

        logger.info("Connexion reussie");

        return Map.of(
                "accessToken", accessToken,
                "expiresAt",   expiresAt.toString()
        );
    }

    // RECUPERATION UTILISATEUR
    /**
     * Récupère l'utilisateur correspondant à un access token JWT.
     *
     * <p>Utilisé par le endpoint {@code GET /api/me}. Accepte le préfixe
     * {@code "Bearer "} optionnel devant le token.
     *
     * @param token access token reçu dans le header {@code Authorization}
     * @return l'utilisateur dont l'email est inclus dans les claims du token
     * @throws AuthenticationFailedException si le token est invalide, expiré
     *                                       ou si l'utilisateur n'existe plus en base
     */
    public User getUserFromToken(String token) {
        // Support "Bearer <token>" comme "Authorization" standard
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        String email;
        try {
            io.jsonwebtoken.Claims claims = jwtService.parse(token);
            email = claims.get("email", String.class);
            if (email == null) {
                throw new AuthenticationFailedException("Token invalide");
            }
        } catch (Exception e) {
            throw new AuthenticationFailedException("Token invalide ou expire");
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationFailedException("Utilisateur introuvable"));
    }
    // NETTOYAGE AUTOMATIQUE DES NONCES EXPIRES
    /**
     * Supprime automatiquement les nonces expirés de la base.
     *
     * <p>Annotée {@code @Scheduled(fixedDelay = 60_000)}, donc exécutée toutes les 60 secondes.
     * Sans ce nettoyage, la table {@code auth_nonce} grossirait indéfiniment ; les nonces
     * expirés ne servent plus à rien car le timestamp les rend de toute façon inutilisables.
     *
     * <p>Nécessite {@code @EnableScheduling} sur la classe d'application.
     */

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void cleanExpiredNonces() {
        authNonceRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        logger.debug("Nettoyage des nonces expires effectue");
    }
    /**
     * Change le mot de passe d'un utilisateur authentifié.
     *
     * <p>Étapes effectuées :
     * <ol>
     *   <li>Vérifier que l'utilisateur existe</li>
     *   <li>Vérifier que l'ancien mot de passe est correct</li>
     *   <li>Vérifier que {@code newPassword} et {@code confirmPassword} sont identiques</li>
     *   <li>Vérifier la force du nouveau mot de passe via {@link PasswordPolicyValidator}</li>
     *   <li>Chiffrer le nouveau mot de passe avec la Master Key</li>
     *   <li>Mettre à jour la base de données</li>
     * </ol>
     *
     * @param email           email de l'utilisateur
     * @param oldPassword     ancien mot de passe en clair
     * @param newPassword     nouveau mot de passe en clair
     * @param confirmPassword confirmation du nouveau mot de passe
     * @throws AuthenticationFailedException si l'utilisateur n'existe pas ou si l'ancien mot de passe est incorrect
     * @throws InvalidInputException         si les deux nouveaux mots de passe diffèrent ou ne respectent pas la politique
     */
    public void changePassword(String email, String oldPassword,
                               String newPassword, String confirmPassword) {

        // Étape 1 : Vérifier que l'utilisateur existe
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    logger.warn("Changement mdp echoue : utilisateur inconnu");
                    return new AuthenticationFailedException("Utilisateur introuvable");
                });

        // Étape 2 : Vérifier que l'ancien mot de passe est correct
        // On déchiffre le mot de passe stocké et on le compare
        String ancienMotDePasseClair;
        try {
            ancienMotDePasseClair = cryptoService.decrypt(user.getPasswordEncrypted());
        } catch (Exception e) {
            logger.error("Erreur dechiffrement", e);
            throw new AuthenticationFailedException("Erreur interne");
        }

        if (!ancienMotDePasseClair.equals(oldPassword)) {
            logger.warn("Changement mdp echoue : ancien mot de passe incorrect ");
            throw new AuthenticationFailedException("Ancien mot de passe incorrect");
        }

        // Étape 3 : Vérifier que newPassword et confirmPassword sont identiques
        if (!newPassword.equals(confirmPassword)) {
            throw new InvalidInputException("Les mots de passe ne correspondent pas");
        }

        // Étape 4 : Vérifier la force du nouveau mot de passe
        if (!PasswordPolicyValidator.isValid(newPassword)) {
            throw new InvalidInputException(
                    "Nouveau mot de passe trop faible : 12 caracteres min, " +
                            "1 maj, 1 min, 1 chiffre, 1 special");
        }

        // Étape 5 : Chiffrer le nouveau mot de passe avec la Master Key
        try {
            String nouveauChiffre = cryptoService.encrypt(newPassword);
            // Étape 6 : Mettre à jour la base de données
            user.setPasswordEncrypted(nouveauChiffre);
            userRepository.save(user);
            logger.info("Mot de passe change avec succes ");
        } catch (Exception e) {
            logger.error("Erreur chiffrement nouveau mdp", e);
            throw new RuntimeException("Erreur interne lors du changement de mot de passe");
        }
    }
}