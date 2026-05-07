package com.projetAuthentification.authentification.service;

import com.projetAuthentification.authentification.entity.User;
import com.projetAuthentification.authentification.exception.AuthenticationFailedException;
import com.projetAuthentification.authentification.exception.InvalidInputException;
import com.projetAuthentification.authentification.repository.AuthNonceRepository;
import com.projetAuthentification.authentification.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangePasswordTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthNonceRepository authNonceRepository;
    @Mock private CryptoService cryptoService;

    @InjectMocks
    private AuthService authService;

    private static final String EMAIL = "alice@gmail.com";
    private static final String OLD_PASSWORD = "AncienMdp123!";
    private static final String NEW_PASSWORD = "NouveauMdp456@";

    private User userValide;

    /**
     * Étapes :
     * <ol>
     *   <li>Crée un {@link User} {@code userValide} avec l'email {@link #EMAIL}
     *       et un faux chiffré {@code "ancienChiffre=="}</li>
     *   <li>Injecte les valeurs {@code @Value} (timestampWindow, nonceTtl, tokenTtl)
     *       sur l'instance {@code authService} via {@link ReflectionTestUtils}</li>
     * </ol>
     * Variables extérieures : champs partagés {@code userValide} et {@code authService}
     * (réinitialisés à chaque test grâce à {@code @BeforeEach}).
     * Retour : {@code void}.
     */
    @BeforeEach
    void setUp() {
        userValide = new User();
        userValide.setEmail(EMAIL);
        userValide.setPasswordEncrypted("ancienChiffre==");

        ReflectionTestUtils.setField(authService, "timestampWindow", 60L);
        ReflectionTestUtils.setField(authService, "nonceTtl", 120L);
        ReflectionTestUtils.setField(authService, "tokenTtl", 15L);
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Stub : {@code userRepository.findByEmail} retourne {@code userValide}</li>
     *   <li>Stub : {@code cryptoService.decrypt} retourne {@link #OLD_PASSWORD}</li>
     *   <li>Stub : {@code cryptoService.encrypt} retourne {@code "nouveauChiffre=="}</li>
     *   <li>Appelle {@code changePassword} sous {@code assertThatNoException}</li>
     *   <li>Capture l'argument passé à {@code userRepository.save} avec un
     *       {@link ArgumentCaptor} et vérifie que le chiffré stocké est bien
     *       le nouveau ({@code "nouveauChiffre=="})</li>
     * </ol>
     * Variables extérieures : mocks {@code userRepository}, {@code cryptoService} ;
     * constantes {@link #EMAIL}, {@link #OLD_PASSWORD}, {@link #NEW_PASSWORD} ;
     * champ partagé {@code userValide}.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par les stubs sur {@code encrypt}/{@code decrypt}
     */
    @Test
    @DisplayName("Changement OK : tous les parametres corrects")
    void changePasswordOk() throws Exception {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));
        when(cryptoService.decrypt("ancienChiffre==")).thenReturn(OLD_PASSWORD);
        when(cryptoService.encrypt(NEW_PASSWORD)).thenReturn("nouveauChiffre==");

        assertThatNoException().isThrownBy(() ->
                authService.changePassword(EMAIL, OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD));

        // Vérifier le vrai changement
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User savedUser = captor.getValue();
        assertThat(savedUser.getPasswordEncrypted()).isEqualTo("nouveauChiffre==");
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Stub : {@code findByEmail} retourne {@code userValide}</li>
     *   <li>Stub : {@code decrypt} retourne {@link #OLD_PASSWORD}
     *       (le « vrai » ancien)</li>
     *   <li>Appelle {@code changePassword} avec un ancien mot de passe différent
     *       ({@code "MauvaisAncien!"}) et vérifie qu'une
     *       {@link AuthenticationFailedException} contenant
     *       {@code "incorrect"} est levée</li>
     * </ol>
     * Variables extérieures : mocks {@code userRepository}, {@code cryptoService} ;
     * constantes {@link #EMAIL}, {@link #OLD_PASSWORD}, {@link #NEW_PASSWORD} ;
     * champ partagé {@code userValide}.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par le stub sur {@code decrypt}
     */
    @Test
    @DisplayName("Changement KO : ancien mot de passe incorrect")
    void changePasswordKo_ancienMdpIncorrect() throws Exception {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));
        when(cryptoService.decrypt("ancienChiffre==")).thenReturn(OLD_PASSWORD);

        assertThatThrownBy(() ->
                authService.changePassword(EMAIL, "MauvaisAncien!", NEW_PASSWORD, NEW_PASSWORD))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("incorrect");
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Stubs : {@code findByEmail} et {@code decrypt} pour passer les
     *       deux premières vérifications</li>
     *   <li>Appelle {@code changePassword} avec une confirmation différente
     *       ({@code "AutreConfirm123@"}) et vérifie qu'une
     *       {@link InvalidInputException} contenant {@code "correspondent pas"}
     *       est levée</li>
     * </ol>
     * Variables extérieures : mocks {@code userRepository}, {@code cryptoService} ;
     * constantes {@link #EMAIL}, {@link #OLD_PASSWORD}, {@link #NEW_PASSWORD} ;
     * champ partagé {@code userValide}.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par le stub sur {@code decrypt}
     */
    @Test
    @DisplayName("Changement KO : confirmation differente du nouveau mot de passe")
    void changePasswordKo_confirmationDifferente() throws Exception {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));
        when(cryptoService.decrypt("ancienChiffre==")).thenReturn(OLD_PASSWORD);

        assertThatThrownBy(() ->
                authService.changePassword(EMAIL, OLD_PASSWORD, NEW_PASSWORD, "AutreConfirm123@"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("correspondent pas");
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Stubs : {@code findByEmail} et {@code decrypt} pour passer les
     *       vérifications préalables</li>
     *   <li>Appelle {@code changePassword} avec un nouveau mot de passe
     *       {@code "faible"} (ne respecte pas la {@code PasswordPolicy})
     *       et vérifie qu'une {@link InvalidInputException} est levée</li>
     * </ol>
     * Variables extérieures : mocks {@code userRepository}, {@code cryptoService} ;
     * constantes {@link #EMAIL}, {@link #OLD_PASSWORD} ; champ partagé {@code userValide}.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par le stub sur {@code decrypt}
     */
    @Test
    @DisplayName("Changement KO : nouveau mot de passe trop faible")
    void changePasswordKo_nouveauMdpTropFaible() throws Exception {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));
        when(cryptoService.decrypt("ancienChiffre==")).thenReturn(OLD_PASSWORD);

        assertThatThrownBy(() ->
                authService.changePassword(EMAIL, OLD_PASSWORD, "faible", "faible"))
                .isInstanceOf(InvalidInputException.class);
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Stub : {@code findByEmail} retourne {@code Optional.empty()}</li>
     *   <li>Appelle {@code changePassword} et vérifie qu'une
     *       {@link AuthenticationFailedException} est levée dès la 1ʳᵉ vérification</li>
     * </ol>
     * Variables extérieures : mock {@code userRepository} ; constantes
     * {@link #EMAIL}, {@link #OLD_PASSWORD}, {@link #NEW_PASSWORD}.
     * Retour : {@code void}.
     */
    @Test
    @DisplayName("Changement KO : utilisateur inexistant")
    void changePasswordKo_utilisateurInexistant() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.changePassword(EMAIL, OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Stub : {@code findByEmail} retourne {@code userValide}</li>
     *   <li>Stub : {@code decrypt} jette une {@link RuntimeException}</li>
     *   <li>Appelle {@code changePassword} et vérifie qu'une
     *       {@link AuthenticationFailedException} contenant {@code "Erreur interne"}
     *       est levée (l'erreur de chiffrement est convertie pour ne pas fuiter)</li>
     * </ol>
     * Variables extérieures : mocks {@code userRepository}, {@code cryptoService} ;
     * constantes {@link #EMAIL}, {@link #OLD_PASSWORD}, {@link #NEW_PASSWORD} ;
     * champ partagé {@code userValide}.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par le stub sur {@code decrypt}
     */
    @Test
    @DisplayName("Changement KO : erreur lors du dechiffrement")
    void changePasswordKo_erreurDecrypt() throws Exception {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));
        when(cryptoService.decrypt("ancienChiffre==")).thenThrow(new RuntimeException());

        assertThatThrownBy(() ->
                authService.changePassword(EMAIL, OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("Erreur interne");
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Stub : {@code findByEmail} retourne {@code userValide}</li>
     *   <li>Stub : {@code decrypt} retourne {@link #OLD_PASSWORD}</li>
     *   <li>Stub : {@code encrypt} jette une {@link RuntimeException}</li>
     *   <li>Appelle {@code changePassword} et vérifie qu'une
     *       {@link RuntimeException} contenant {@code "Erreur interne"} est remontée</li>
     * </ol>
     * Variables extérieures : mocks {@code userRepository}, {@code cryptoService} ;
     * constantes {@link #EMAIL}, {@link #OLD_PASSWORD}, {@link #NEW_PASSWORD} ;
     * champ partagé {@code userValide}.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par les stubs sur {@code encrypt}/{@code decrypt}
     */
    @Test
    @DisplayName("Changement KO : erreur lors du chiffrement")
    void changePasswordKo_erreurEncrypt() throws Exception {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(userValide));
        when(cryptoService.decrypt("ancienChiffre==")).thenReturn(OLD_PASSWORD);
        when(cryptoService.encrypt(NEW_PASSWORD)).thenThrow(new RuntimeException());

        assertThatThrownBy(() ->
                authService.changePassword(EMAIL, OLD_PASSWORD, NEW_PASSWORD, NEW_PASSWORD))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Erreur interne");
    }
}