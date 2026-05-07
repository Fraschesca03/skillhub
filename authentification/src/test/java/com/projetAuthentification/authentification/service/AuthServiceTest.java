package com.projetAuthentification.authentification.service;

import com.projetAuthentification.authentification.dto.LoginRequest;
import com.projetAuthentification.authentification.entity.AuthNonce;
import com.projetAuthentification.authentification.entity.User;
import com.projetAuthentification.authentification.exception.AuthenticationFailedException;
import com.projetAuthentification.authentification.exception.InvalidInputException;
import com.projetAuthentification.authentification.exception.ResourceConflictException;
import com.projetAuthentification.authentification.repository.AuthNonceRepository;
import com.projetAuthentification.authentification.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour AuthService — TP3
 *
 * On utilise Mockito pour simuler les dépendances (UserRepository,
 * AuthNonceRepository, CryptoService) sans avoir besoin d'une vraie base
 * de données. Chaque test est isolé et rapide.
 *
 * @ExtendWith(MockitoExtension.class) active Mockito pour cette classe
 * @Mock crée un faux objet simulé
 * @InjectMocks crée le vrai AuthService en injectant les @Mock dedans
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // ── Mocks (faux objets simulés) ──────────────────────────────────────────
    @Mock private UserRepository      userRepository;
    @Mock private AuthNonceRepository authNonceRepository;
    @Mock private CryptoService       cryptoService;
    @Mock private JwtService          jwtService;

    // Le vrai AuthService avec les mocks injectés
    @InjectMocks
    private AuthService authService;

    // ── Données communes à tous les tests ────────────────────────────────────
    private static final String EMAIL    = "alice@gmail.com";
    private static final String PASSWORD = "MonMotDePasse123!";
    private static final String NOM      = "MonNom";
    private static final String ROLE     = "apprenant";

    private User userValide;
    private long timestampValide;
    private String nonceValide;

    /**
     * Étapes :
     * <ol>
     *   <li>Crée un {@link User} {@code userValide} avec email, chiffré factice,
     *       nom et rôle issus des constantes de classe</li>
     *   <li>Capture le timestamp courant ({@code Instant.now().getEpochSecond()})
     *       comme valeur valide</li>
     *   <li>Génère un nonce UUID frais</li>
     *   <li>Injecte les valeurs {@code @Value} ({@code timestampWindow},
     *       {@code nonceTtl}, {@code tokenTtl}) sur {@code authService}
     *       via {@link ReflectionTestUtils}</li>
     * </ol>
     * Variables extérieures : champs partagés {@code userValide},
     * {@code timestampValide}, {@code nonceValide} et {@code authService}
     * (réinitialisés à chaque test).
     * Retour : {@code void}.
     */
    @BeforeEach
    void setUp() {
        // Créer un utilisateur valide pour les tests
        userValide = new User();
        userValide.setEmail(EMAIL);
        userValide.setPasswordEncrypted("motDePasseChiffre==");
        userValide.setNom(NOM);
        userValide.setRole(ROLE);

        // Timestamp actuel — valide car dans la fenêtre ±60s
        timestampValide = Instant.now().getEpochSecond();

        // Nonce unique pour chaque test
        nonceValide = UUID.randomUUID().toString();

        // Injecter les valeurs de configuration via ReflectionTestUtils
        // (remplace ce que @Value ferait normalement depuis application.properties)
        ReflectionTestUtils.setField(authService, "timestampWindow", 60L);
        ReflectionTestUtils.setField(authService, "nonceTtl",        120L);
        ReflectionTestUtils.setField(authService, "tokenTtl",        15L);
    }

    // ════════════════════════════════════════════════════════════════════════
    // TESTS DE CONNEXION
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Étapes (ARRANGE / ACT / ASSERT) :
     * <ol>
     *   <li>Stub : {@code findByEmail} retourne {@code userValide}</li>
     *   <li>Stub : {@code findByUserAndNonce} retourne {@code Optional.empty()}
     *       (nonce inconnu)</li>
     *   <li>Stub : {@code decrypt} retourne {@link #PASSWORD}</li>
     *   <li>Stub : {@code computeHmac(PASSWORD, message)} retourne
     *       {@code "signatureValide"}</li>
     *   <li>Stub : {@code compareHmacConstantTime} retourne {@code true}</li>
     *   <li>Stub : {@code jwtService.emit} retourne {@code "jwt.token.fake"}</li>
     *   <li>Appelle {@code login} et vérifie que la map retournée contient
     *       les clés {@code accessToken} (non vide) et {@code expiresAt}</li>
     * </ol>
     * Variables extérieures : tous les mocks (@Mock) ;
     * constantes {@link #EMAIL}, {@link #PASSWORD} ;
     * champs {@code userValide}, {@code nonceValide}, {@code timestampValide}.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par les stubs sur {@code decrypt}/{@code computeHmac}
     */
    @Test
    @DisplayName("Login OK : HMAC valide et tous les paramètres corrects")
    void loginOk_hmacValide() throws Exception {
        // ARRANGE (préparer)
        // Le serveur trouve l'utilisateur en base
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));
        // Le nonce n'a jamais été vu (Optional vide = pas en base)
        when(authNonceRepository.findByUserAndNonce(eq(userValide), eq(nonceValide)))
                .thenReturn(Optional.empty());
        // Le déchiffrement retourne le mot de passe en clair
        when(cryptoService.decrypt("motDePasseChiffre==")).thenReturn(PASSWORD);
        // Le calcul HMAC retourne une signature
        String message = EMAIL + ":" + nonceValide + ":" + timestampValide;
        when(cryptoService.computeHmac(PASSWORD, message)).thenReturn("signatureValide");
        // La comparaison en temps constant retourne true (signatures identiques)
        when(cryptoService.compareHmacConstantTime("signatureValide", "signatureValide"))
                .thenReturn(true);
        // Le service JWT emet un token
        when(jwtService.emit(any(), anyString(), anyString(), any()))
                .thenReturn("jwt.token.fake");

        // ACT (exécuter)
        Map<String, String> result = authService.login(
                EMAIL, nonceValide, timestampValide, "signatureValide");

        // ASSERT (vérifier)
        // Le résultat contient bien un accessToken et une date d'expiration
        assertThat(result).containsKey("accessToken");
        assertThat(result).containsKey("expiresAt");
        assertThat(result.get("accessToken")).isNotBlank();
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Stubs identiques au cas OK jusqu'à {@code computeHmac}, qui retourne
     *       {@code "signatureAttendue"}</li>
     *   <li>Stub : {@code compareHmacConstantTime("signatureAttendue", "signatureReçue")}
     *       retourne {@code false}</li>
     *   <li>Appelle {@code login} avec {@code "signatureReçue"} et vérifie
     *       qu'une {@link AuthenticationFailedException} est levée</li>
     * </ol>
     * Variables extérieures : tous les mocks ; constantes {@link #EMAIL},
     * {@link #PASSWORD} ; champs {@code userValide}, {@code nonceValide},
     * {@code timestampValide}.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par les stubs
     */
    @Test
    @DisplayName("Login KO : HMAC invalide (mauvais mot de passe)")
    void loginKo_hmacInvalide() throws Exception {
        // ARRANGE
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));
        when(authNonceRepository.findByUserAndNonce(any(), any()))
                .thenReturn(Optional.empty());
        when(cryptoService.decrypt(any())).thenReturn(PASSWORD);
        when(cryptoService.computeHmac(any(), any())).thenReturn("signatureAttendue");
        // La comparaison retourne false : signatures différentes
        when(cryptoService.compareHmacConstantTime("signatureAttendue", "signatureReçue"))
                .thenReturn(false);

        // ACT & ASSERT
        // assertThatThrownBy vérifie qu'une exception est levée
        assertThatThrownBy(() ->
                authService.login(EMAIL, nonceValide, timestampValide, "signatureReçue"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Stub : {@code findByEmail} retourne {@code userValide}</li>
     *   <li>Calcule un timestamp local {@code Instant.now() - 300} secondes</li>
     *   <li>Appelle {@code login} avec ce timestamp expiré et vérifie qu'une
     *       {@link AuthenticationFailedException} est levée</li>
     * </ol>
     * Variables extérieures : mock {@code userRepository} ;
     * constante {@link #EMAIL} ; champs {@code userValide}, {@code nonceValide}.
     * Retour : {@code void}.
     */
    @Test
    @DisplayName("Login KO : timestamp expiré (plus de 60 secondes dans le passé)")
    void loginKo_timestampExpire() {
        // ARRANGE
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));
        // Timestamp d'il y a 5 minutes = clairement hors de la fenêtre ±60s
        long timestampExpire = Instant.now().getEpochSecond() - 300;

        // ACT & ASSERT
        assertThatThrownBy(() ->
                authService.login(EMAIL, nonceValide, timestampExpire, "n'importe"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Stub : {@code findByEmail} retourne {@code userValide}</li>
     *   <li>Calcule un timestamp {@code Instant.now() + 300} secondes</li>
     *   <li>Appelle {@code login} et vérifie qu'une
     *       {@link AuthenticationFailedException} est levée</li>
     * </ol>
     * Variables extérieures : mock {@code userRepository} ;
     * constante {@link #EMAIL} ; champs {@code userValide}, {@code nonceValide}.
     * Retour : {@code void}.
     */
    @Test
    @DisplayName("Login KO : timestamp futur (plus de 60 secondes dans le futur)")
    void loginKo_timestampFutur() {
        // ARRANGE
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));
        // Timestamp dans 5 minutes = hors de la fenêtre ±60s
        long timestampFutur = Instant.now().getEpochSecond() + 300;

        // ACT & ASSERT
        assertThatThrownBy(() ->
                authService.login(EMAIL, nonceValide, timestampFutur, "n'importe"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Stub : {@code findByEmail} retourne {@code userValide}</li>
     *   <li>Crée un {@link AuthNonce} marqué {@code consumed=true}</li>
     *   <li>Stub : {@code findByUserAndNonce} retourne {@code Optional.of(nonceDeja)}</li>
     *   <li>Appelle {@code login} et vérifie qu'une
     *       {@link AuthenticationFailedException} est levée à l'étape de l'anti-rejeu</li>
     * </ol>
     * Variables extérieures : mocks {@code userRepository}, {@code authNonceRepository} ;
     * constante {@link #EMAIL} ; champs {@code userValide}, {@code nonceValide},
     * {@code timestampValide}.
     * Retour : {@code void}.
     */
    @Test
    @DisplayName("Login KO : nonce déjà utilisé (tentative de replay attack)")
    void loginKo_nonceDejaUtilise() {
        // ARRANGE
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));
        // Le nonce EST déjà en base (Optional non vide = nonce connu = consommé)
        AuthNonce nonceDeja = new AuthNonce();
        nonceDeja.setConsumed(true);
        when(authNonceRepository.findByUserAndNonce(eq(userValide), eq(nonceValide)))
                .thenReturn(Optional.of(nonceDeja));

        // ACT & ASSERT
        assertThatThrownBy(() ->
                authService.login(EMAIL, nonceValide, timestampValide, "n'importe"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Stub : {@code findByEmail} retourne {@code Optional.empty()}</li>
     *   <li>Appelle {@code login} et vérifie qu'une
     *       {@link AuthenticationFailedException} est levée dès la 1ʳᵉ étape</li>
     * </ol>
     * Variables extérieures : mock {@code userRepository} ;
     * constante {@link #EMAIL} ; champs {@code nonceValide}, {@code timestampValide}.
     * Retour : {@code void}.
     */
    @Test
    @DisplayName("Login KO : email inconnu")
    void loginKo_userInconnu() {
        // ARRANGE
        // findByEmail retourne Optional.empty() = email pas en base
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // ACT & ASSERT
        assertThatThrownBy(() ->
                authService.login(EMAIL, nonceValide, timestampValide, "n'importe"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Stubs complets pour faire passer le login (find, decrypt, computeHmac,
     *       compareHmacConstantTime, jwtService.emit)</li>
     *   <li>Appelle {@code login}</li>
     *   <li>Vérifie via {@code verify(..., times(1)).compareHmacConstantTime("sig", "sig")}
     *       que la méthode sécurisée a bien été appelée exactement une fois
     *       (et pas {@link String#equals})</li>
     * </ol>
     * Variables extérieures : tous les mocks ; constantes {@link #EMAIL},
     * {@link #PASSWORD} ; champs {@code userValide}, {@code nonceValide},
     * {@code timestampValide}.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par les stubs
     */
    @Test
    @DisplayName("Comparaison HMAC : utilise bien compareHmacConstantTime (temps constant)")
    void comparaisonTempsConstant_utiliseMethodeSecurisee() throws Exception {
        // ARRANGE
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));
        when(authNonceRepository.findByUserAndNonce(any(), any()))
                .thenReturn(Optional.empty());
        when(cryptoService.decrypt(any())).thenReturn(PASSWORD);
        when(cryptoService.computeHmac(any(), any())).thenReturn("sig");
        when(cryptoService.compareHmacConstantTime("sig", "sig")).thenReturn(true);
        when(jwtService.emit(any(), anyString(), anyString(), any()))
                .thenReturn("jwt.token.fake");

        // ACT
        authService.login(EMAIL, nonceValide, timestampValide, "sig");

        // ASSERT : on vérifie que compareHmacConstantTime a bien été appelée
        // verify() de Mockito vérifie qu'une méthode a été invoquée
        verify(cryptoService, times(1)).compareHmacConstantTime("sig", "sig");
    }

    /**
     * Étapes (deux ACT consécutifs) :
     * <ol>
     *   <li>Stubs pour faire passer le login complet</li>
     *   <li>Mock un objet {@link Claims} dont {@code get("email", String.class)}
     *       retourne {@link #EMAIL} ; stub {@code jwtService.parse} pour le retourner</li>
     *   <li>Appelle {@code login} et récupère le token de la map résultat</li>
     *   <li>Re-stub {@code findByEmail} (réutilisé par {@code getUserFromToken})</li>
     *   <li>Appelle {@code getUserFromToken(token)}</li>
     *   <li>Vérifie que l'email du {@code User} retourné correspond à {@link #EMAIL}</li>
     * </ol>
     * Variables extérieures : tous les mocks ; constantes {@link #EMAIL},
     * {@link #PASSWORD} ; champs {@code userValide}, {@code nonceValide},
     * {@code timestampValide}.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par les stubs
     */
    @Test
    @DisplayName("Token émis : accès /api/me réussi avec le token retourné")
    void tokenEmis_accesMeOk() throws Exception {
        // ARRANGE — configurer le login pour qu'il réussisse
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));
        when(authNonceRepository.findByUserAndNonce(any(), any()))
                .thenReturn(Optional.empty());
        when(cryptoService.decrypt(any())).thenReturn(PASSWORD);
        when(cryptoService.computeHmac(any(), any())).thenReturn("sig");
        when(cryptoService.compareHmacConstantTime(any(), any())).thenReturn(true);
        when(jwtService.emit(any(), anyString(), anyString(), any()))
                .thenReturn("jwt.token.fake");

        // Le parse() du JWT retourne des claims contenant l'email
        Claims claims = mock(Claims.class);
        when(claims.get("email", String.class)).thenReturn(EMAIL);
        when(jwtService.parse("jwt.token.fake")).thenReturn(claims);

        // ACT 1 : se connecter et récupérer le token
        Map<String, String> loginResult =
                authService.login(EMAIL, nonceValide, timestampValide, "sig");
        String token = loginResult.get("accessToken");

        // Préparer le mock pour getUserFromToken
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));

        // ACT 2 : utiliser le token pour accéder à /api/me
        User user = authService.getUserFromToken(token);

        // ASSERT
        assertThat(user.getEmail()).isEqualTo(EMAIL);
    }

    /**
     * Étapes : appel direct à {@code getUserFromToken} avec une chaîne factice
     * et assertion qu'une {@link AuthenticationFailedException} est levée
     * (le token n'est pas un JWT valide, {@code jwtService.parse} échoue).
     *
     * <p>Variables extérieures : aucune (pas de stub). Retour : {@code void}.
     */
    @Test
    @DisplayName("Accès /api/me : token invalide ou absent → exception")
    void accesMeSansToken_KO() {
        // ACT & ASSERT
        // Un token qui n'existe pas dans le tokenStore doit lever une exception
        assertThatThrownBy(() -> authService.getUserFromToken("tokenInexistant"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    // ════════════════════════════════════════════════════════════════════════
    // TESTS D'INSCRIPTION
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Étapes :
     * <ol>
     *   <li>Stub : {@code existsByEmail} retourne {@code false}</li>
     *   <li>Stub : {@code encrypt} retourne {@code "motDePasseChiffre=="}</li>
     *   <li>Stub : {@code save} renvoie l'argument reçu inchangé via
     *       {@code thenAnswer(i -> i.getArgument(0))}</li>
     *   <li>Appelle {@code register} et vérifie sur le {@link User} retourné
     *       que email, nom, rôle et chiffré correspondent</li>
     * </ol>
     * Variables extérieures : mocks {@code userRepository}, {@code cryptoService} ;
     * constantes {@link #EMAIL}, {@link #PASSWORD}, {@link #NOM}, {@link #ROLE}.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par le stub sur {@code encrypt}
     */
    @Test
    @DisplayName("Inscription OK : tous les champs valides")
    void registerOk() throws Exception {
        // ARRANGE
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(cryptoService.encrypt(PASSWORD)).thenReturn("motDePasseChiffre==");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // ACT
        // ACT
        User result = authService.register(EMAIL, PASSWORD, NOM, ROLE);

// ASSERT
        assertThat(result.getEmail()).isEqualTo(EMAIL);
        assertThat(result.getNom()).isEqualTo(NOM);
        assertThat(result.getRole()).isEqualTo(ROLE);
// Le mot de passe chiffré est stocké, pas le mot de passe en clair
        assertThat(result.getPasswordEncrypted()).isEqualTo("motDePasseChiffre==");
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Stub : {@code existsByEmail} retourne {@code true}</li>
     *   <li>Appelle {@code register} et vérifie qu'une
     *       {@link ResourceConflictException} est levée</li>
     * </ol>
     * Variables extérieures : mock {@code userRepository} ; constantes
     * {@link #EMAIL}, {@link #PASSWORD}, {@link #NOM}.
     * Retour : {@code void}.
     */
    @Test
    @DisplayName("Inscription KO : email déjà utilisé")
    void registerKo_emailDejaUtilise() {
        // ARRANGE
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        // ACT & ASSERT
        assertThatThrownBy(() ->
                authService.register(EMAIL, PASSWORD, NOM, "apprenant"))
                .isInstanceOf(ResourceConflictException.class);
    }

    /**
     * Étapes : appel direct à {@code register} avec un email vide ;
     * assertion qu'une {@link InvalidInputException} est levée.
     * Variables extérieures : constantes {@link #PASSWORD}, {@link #NOM}.
     * Retour : {@code void}.
     */
    @Test
    @DisplayName("Inscription KO : email vide")
    void registerKo_emailVide() {
        assertThatThrownBy(() ->
                authService.register("", PASSWORD, NOM, "apprenant"))
                .isInstanceOf(InvalidInputException.class);
    }

    /**
     * Étapes : appel direct à {@code register} avec un nom vide ;
     * assertion qu'une {@link InvalidInputException} est levée.
     * Variables extérieures : constantes {@link #EMAIL}, {@link #PASSWORD}.
     * Retour : {@code void}.
     */
    @Test
    @DisplayName("Inscription KO : nom vide")
    void registerKo_nomVide() {
        assertThatThrownBy(() ->
                authService.register(EMAIL, PASSWORD, "", "apprenant"))
                .isInstanceOf(InvalidInputException.class);
    }

    /**
     * Étapes : appel direct à {@code register} avec le rôle {@code "admin"}
     * (hors {@code apprenant}/{@code formateur}) ;
     * assertion qu'une {@link InvalidInputException} est levée.
     * Variables extérieures : constantes {@link #EMAIL}, {@link #PASSWORD}, {@link #NOM}.
     * Retour : {@code void}.
     */
    @Test
    @DisplayName("Inscription KO : role invalide (ni apprenant ni formateur)")
    void registerKo_roleInvalide() {
        assertThatThrownBy(() ->
                authService.register(EMAIL, PASSWORD, NOM, "admin"))
                .isInstanceOf(InvalidInputException.class);
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Instancie une vraie {@link CryptoService} (pas un mock)</li>
     *   <li>Injecte une {@code masterKey} de 34 caractères via
     *       {@link ReflectionTestUtils}</li>
     *   <li>Chiffre une chaîne, déchiffre, vérifie l'égalité avec l'original
     *       et l'inégalité avec le chiffré</li>
     * </ol>
     * Variables extérieures : aucune (instance locale, pas de mocks).
     * Retour : {@code void}.
     *
     * @throws Exception remontée par {@code encrypt}/{@code decrypt}
     */
    @Test
    @DisplayName("CryptoService : encrypt() puis decrypt() retourne le texte original")
    void cryptoService_encryptDecryptReversible() throws Exception {
        CryptoService realCrypto = new CryptoService();

        // CHANGEMENT TP4 : "smk" → "masterKey"
        ReflectionTestUtils.setField(realCrypto, "masterKey",
                "UneCleSuperSecreteDeMinimum32Car!!");

        String texteOriginal = "MonMotDePasse123!";
        String chiffre       = realCrypto.encrypt(texteOriginal);
        String dechiffre     = realCrypto.decrypt(chiffre);

        assertThat(dechiffre).isEqualTo(texteOriginal);
        assertThat(chiffre).isNotEqualTo(texteOriginal);
    }
    /**
     * Étapes :
     * <ol>
     *   <li>Stubs complets pour faire passer le login</li>
     *   <li>Appelle {@code login}</li>
     *   <li>Vérifie via {@code verify(authNonceRepository, times(1)).save(any(AuthNonce.class))}
     *       que le nonce a été persisté exactement une fois</li>
     * </ol>
     * Variables extérieures : tous les mocks ; constantes {@link #EMAIL},
     * {@link #PASSWORD} ; champs {@code userValide}, {@code nonceValide},
     * {@code timestampValide}.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par les stubs
     */
    @Test
    @DisplayName("Login OK : le nonce est enregistré en base")
    void loginOk_nonceSauvegarde() throws Exception {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));
        when(authNonceRepository.findByUserAndNonce(any(), any()))
                .thenReturn(Optional.empty());
        when(cryptoService.decrypt(any())).thenReturn(PASSWORD);
        when(cryptoService.computeHmac(any(), any())).thenReturn("sig");
        when(cryptoService.compareHmacConstantTime(any(), any())).thenReturn(true);
        when(jwtService.emit(any(), anyString(), anyString(), any()))
                .thenReturn("jwt.token.fake");

        authService.login(EMAIL, nonceValide, timestampValide, "sig");

        verify(authNonceRepository, times(1)).save(any(AuthNonce.class));
    }
    /**
     * Étapes :
     * <ol>
     *   <li>Crée un {@link AuthNonce} avec {@code expiresAt} dans le passé
     *       (cet objet n'est pas réinjecté dans les mocks — il sert seulement
     *       à documenter l'intention)</li>
     *   <li>Appelle {@code login} sans aucun stub configuré et vérifie qu'une
     *       {@link AuthenticationFailedException} est levée
     *       (Mockito retourne {@code Optional.empty()} par défaut sur {@code findByEmail},
     *       déclenchant l'exception « email inconnu »)</li>
     * </ol>
     * Variables extérieures : champs {@code authService}, {@code nonceValide},
     * {@code timestampValide} ; constante {@link #EMAIL}.
     * Retour : {@code void}.
     */
    @Test
    @DisplayName("Nonce expiré / supprimé ou refusé")
    void nonceExpireTest() {
        AuthNonce nonce = new AuthNonce();
        nonce.setExpiresAt(LocalDateTime.now().minusMinutes(10));
        assertThatThrownBy(() ->
                authService.login(EMAIL, nonceValide, timestampValide, "sig"))
                .isInstanceOf(AuthenticationFailedException.class);
    }
    /**
     * Étapes : appel à {@code getUserFromToken(null)} et assertion qu'une
     * {@link AuthenticationFailedException} est levée.
     * Variables extérieures : champ {@code authService}. Retour : {@code void}.
     */
    @Test
    @DisplayName("Token null : rejet")
    void tokenNull() {
        assertThatThrownBy(() -> authService.getUserFromToken(null))
                .isInstanceOf(AuthenticationFailedException.class);
    }
    /**
     * Étapes :
     * <ol>
     *   <li>Instancie une vraie {@link CryptoService}</li>
     *   <li>Injecte une {@code masterKey} non valide ({@code "badkey"})</li>
     *   <li>Appelle {@code decrypt("v1:abc:def")} et vérifie qu'une
     *       {@link Exception} est levée (Base64 invalide ou tag GCM faux)</li>
     * </ol>
     * Variables extérieures : aucune (instance locale). Retour : {@code void}.
     */
    @Test
    @DisplayName("Decrypt KO si masterKey invalide")
    void decryptFailWrongKey() {
        CryptoService crypto = new CryptoService();
        ReflectionTestUtils.setField(crypto, "masterKey", "badkey");

        assertThatThrownBy(() -> crypto.decrypt("v1:abc:def"))
                .isInstanceOf(Exception.class);
    }
    /**
     * Étapes :
     * <ol>
     *   <li>Instancie une vraie {@link CryptoService} (sans masterKey, donc
     *       le {@code NullPointerException} arrive avant le chiffrement)</li>
     *   <li>Appelle {@code encrypt(null)} et vérifie qu'une
     *       {@link Exception} est levée</li>
     * </ol>
     * Variables extérieures : aucune. Retour : {@code void}.
     */
    @Test
    @DisplayName("Encrypt KO si texte null")
    void encryptNull() {
        CryptoService crypto = new CryptoService();

        assertThatThrownBy(() -> crypto.encrypt(null))
                .isInstanceOf(Exception.class);
    }
    /**
     * Étapes :
     * <ol>
     *   <li>Stub : {@code existsByEmail} retourne {@code false}</li>
     *   <li>Stub : {@code encrypt} retourne {@code "encrypted"}</li>
     *   <li>Appelle {@code register} et vérifie que le chiffré stocké
     *       ({@code user.getPasswordEncrypted()}) ne contient pas la chaîne
     *       du mot de passe en clair</li>
     * </ol>
     * Variables extérieures : mocks {@code userRepository}, {@code cryptoService} ;
     * constantes {@link #EMAIL}, {@link #PASSWORD}, {@link #NOM}.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par le stub sur {@code encrypt}
     */
    @Test
    @DisplayName("Password jamais stocké en clair")
    void passwordNeverStoredPlain() throws Exception {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(cryptoService.encrypt(PASSWORD)).thenReturn("encrypted");

        User user = authService.register(EMAIL, PASSWORD, NOM, "apprenant");

        assertThat(user.getPasswordEncrypted()).doesNotContain(PASSWORD);
    }
    /**
     * Étapes : appel direct à {@code register} avec {@code email = null} et
     * assertion qu'une {@link InvalidInputException} est levée.
     * Variables extérieures : constantes {@link #PASSWORD}, {@link #NOM}.
     * Retour : {@code void}.
     */
    @Test
    @DisplayName("Inscription KO : email null")
    void registerKo_emailNull() {
        assertThatThrownBy(() ->
                authService.register(null, PASSWORD, NOM, "apprenant"))
                .isInstanceOf(InvalidInputException.class);
    }
    /**
     * Étapes :
     * <ol>
     *   <li>Stubs : {@code findByEmail}, {@code findByUserAndNonce} pour passer
     *       les vérifications préalables</li>
     *   <li>Stub : {@code decrypt} jette une {@link Exception("Erreur AES")}</li>
     *   <li>Appelle {@code login} et vérifie qu'une
     *       {@link AuthenticationFailedException} est levée
     *       (l'erreur AES est convertie en « Erreur interne »)</li>
     * </ol>
     * Variables extérieures : mocks ; constantes {@link #EMAIL} ;
     * champs {@code userValide}, {@code nonceValide}, {@code timestampValide}.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par le stub sur {@code decrypt}
     */
    @Test
    @DisplayName("Login KO : erreur lors du dechiffrement")
    void loginKo_erreurDechiffrement() throws Exception {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));
        when(authNonceRepository.findByUserAndNonce(any(), any()))
                .thenReturn(Optional.empty());
        when(cryptoService.decrypt(any())).thenThrow(new Exception("Erreur AES"));

        assertThatThrownBy(() ->
                authService.login(EMAIL, nonceValide, timestampValide, "sig"))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Stubs pour passer find, nonce et decrypt</li>
     *   <li>Stub : {@code computeHmac} jette une {@link Exception("Erreur HMAC")}</li>
     *   <li>Appelle {@code login} et vérifie qu'une
     *       {@link AuthenticationFailedException} est levée</li>
     * </ol>
     * Variables extérieures : mocks ; constantes {@link #EMAIL}, {@link #PASSWORD} ;
     * champs {@code userValide}, {@code nonceValide}, {@code timestampValide}.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par les stubs
     */
    @Test
    @DisplayName("Login KO : erreur lors du calcul HMAC")
    void loginKo_erreurHmac() throws Exception {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));
        when(authNonceRepository.findByUserAndNonce(any(), any()))
                .thenReturn(Optional.empty());
        when(cryptoService.decrypt(any())).thenReturn(PASSWORD);
        when(cryptoService.computeHmac(any(), any())).thenThrow(new Exception("Erreur HMAC"));

        assertThatThrownBy(() ->
                authService.login(EMAIL, nonceValide, timestampValide, "sig"))
                .isInstanceOf(AuthenticationFailedException.class);
    }
    /**
     * Étapes :
     * <ol>
     *   <li>Appelle directement {@code cleanExpiredNonces} (la méthode
     *       {@code @Scheduled} du service)</li>
     *   <li>Vérifie via {@code verify(..., times(1)).deleteByExpiresAtBefore(any())}
     *       que le repository est sollicité exactement une fois avec un
     *       {@link LocalDateTime}</li>
     * </ol>
     * Variables extérieures : mock {@code authNonceRepository}, champ {@code authService}.
     * Retour : {@code void}.
     */
    @Test
    @DisplayName("Scheduler : nettoyage des nonces expires")
    void cleanExpiredNonces_appelleRepository() {
        authService.cleanExpiredNonces();
        verify(authNonceRepository, times(1))
                .deleteByExpiresAtBefore(any(LocalDateTime.class));
    }
    /**
     * Étapes :
     * <ol>
     *   <li>Crée un {@link LoginRequest} vide</li>
     *   <li>Appelle les 4 setters avec des valeurs littérales</li>
     *   <li>Vérifie que les 4 getters retournent ces mêmes valeurs</li>
     * </ol>
     * Variables extérieures : aucune. Retour : {@code void}.
     */
    @Test
    @DisplayName("LoginRequest : getters et setters fonctionnent")
    void loginRequest_gettersSetters() {
        LoginRequest req = new LoginRequest();
        req.setEmail("alice@test.com");
        req.setNonce("uuid-123");
        req.setTimestamp(1711234567L);
        req.setHmac("hmacValue");

        assertThat(req.getEmail()).isEqualTo("alice@test.com");
        assertThat(req.getNonce()).isEqualTo("uuid-123");
        assertThat(req.getTimestamp()).isEqualTo(1711234567L);
        assertThat(req.getHmac()).isEqualTo("hmacValue");
    }
    /**
     * Étapes :
     * <ol>
     *   <li>Instancie une vraie {@link CryptoService} avec une masterKey valide</li>
     *   <li>Appelle {@code decrypt("mauvais_format")} (sans préfixe {@code v1:})
     *       et vérifie qu'une {@link IllegalArgumentException} contenant
     *       {@code "Format"} est levée</li>
     * </ol>
     * Variables extérieures : aucune. Retour : {@code void}.
     */
    @Test
    @DisplayName("Decrypt KO : format invalide")
    void decrypt_formatInvalide() {
        CryptoService crypto = new CryptoService();
        ReflectionTestUtils.setField(crypto, "masterKey",
                "UneCleSuperSecreteDeMinimum32Car!!");

        assertThatThrownBy(() -> crypto.decrypt("mauvais_format"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Format");
    }
    /**
     * Étapes :
     * <ol>
     *   <li>Instancie une vraie {@link CryptoService}</li>
     *   <li>Appelle {@code computeHmac("secret", "message")}</li>
     *   <li>Vérifie que le résultat est non null et non vide
     *       (Base64 d'une signature de 32 octets)</li>
     * </ol>
     * Variables extérieures : aucune. Retour : {@code void}.
     *
     * @throws Exception remontée par {@code computeHmac}
     */
    @Test
    @DisplayName("HMAC : calcul correct")
    void computeHmac_ok() throws Exception {
        CryptoService crypto = new CryptoService();

        String result = crypto.computeHmac("secret", "message");

        assertThat(result).isNotNull();
        assertThat(result).isNotBlank();
    }
    /**
     * Étapes : instancie un vrai {@link CryptoService}, appelle
     * {@code compareHmacConstantTime("abc", "abc")}, vérifie le retour {@code true}.
     * Variables extérieures : aucune. Retour : {@code void}.
     */
    @Test
    @DisplayName("Comparaison HMAC : true si identique")
    void compareHmac_true() {
        CryptoService crypto = new CryptoService();

        boolean result = crypto.compareHmacConstantTime("abc", "abc");

        assertThat(result).isTrue();
    }

    /**
     * Étapes : instancie un vrai {@link CryptoService}, appelle
     * {@code compareHmacConstantTime("abc", "xyz")}, vérifie le retour {@code false}.
     * Variables extérieures : aucune. Retour : {@code void}.
     */
    @Test
    @DisplayName("Comparaison HMAC : false si différent")
    void compareHmac_false() {
        CryptoService crypto = new CryptoService();

        boolean result = crypto.compareHmacConstantTime("abc", "xyz");

        assertThat(result).isFalse();
    }
    /**
     * Étapes :
     * <ol>
     *   <li>Instancie une vraie {@link CryptoService} avec une masterKey valide</li>
     *   <li>Chiffre la chaîne {@code "hello"}</li>
     *   <li>Vérifie que le résultat commence par {@code "v1:"} et contient
     *       exactement 3 segments séparés par {@code ":"}</li>
     * </ol>
     * Variables extérieures : aucune. Retour : {@code void}.
     *
     * @throws Exception remontée par {@code encrypt}
     */
    @Test
    @DisplayName("Encrypt : format v1 correct")
    void encrypt_format() throws Exception {
        CryptoService crypto = new CryptoService();
        ReflectionTestUtils.setField(crypto, "masterKey",
                "UneCleSuperSecreteDeMinimum32Car!!");

        String encrypted = crypto.encrypt("hello");

        assertThat(encrypted).startsWith("v1:");
        assertThat(encrypted.split(":")).hasSize(3);
    }
}