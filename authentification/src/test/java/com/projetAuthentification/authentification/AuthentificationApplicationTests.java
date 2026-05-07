package com.projetAuthentification.authentification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Test de démarrage du contexte Spring.
 * Vérifie que l'application démarre correctement.
 *
 * @TestPropertySource injecte application-test.properties
 * qui fournit APP_MASTER_KEY fictive + base H2 en mémoire.
 * Sans ça, Spring cherche APP_MASTER_KEY dans l'environnement
 * et plante si elle n'est pas définie.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class AuthentificationApplicationTests {

	/**
	 * Étapes : aucune instruction explicite dans le corps — tout repose sur
	 * l'annotation {@code @SpringBootTest} qui charge le contexte Spring complet.
	 *
	 * <p>Variables extérieures : aucune ; les propriétés viennent du fichier
	 * {@code application-test.properties} déclaré sur la classe.
	 *
	 * <p>Retour : {@code void}. Réussit si tous les beans
	 * ({@code MasterKeyConfig}, {@code CryptoService}, {@code AuthService}, …)
	 * s'initialisent sans exception ; échoue dès qu'un bean rate son démarrage.
	 */
	@Test
	void contextLoads() {
	}
}
