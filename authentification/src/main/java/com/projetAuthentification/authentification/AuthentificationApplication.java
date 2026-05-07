package com.projetAuthentification.authentification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe principale de l'application Spring Boot d'authentification.
 *
 * <p><b>Changement TP3 :</b> ajout de {@code @EnableScheduling}.
 *
 * <p>Sans cette annotation, le {@code @Scheduled(fixedDelay=60_000)}
 * dans {@code AuthService} ne s'exécuterait jamais — Spring ignorerait silencieusement
 * les méthodes annotées {@code @Scheduled}.
 *
 * <p>{@code @EnableScheduling} dit à Spring : « active le moteur de tâches
 * planifiées, cherche tous les {@code @Scheduled} dans l'application et exécute-les ».
 *
 * @author MU202612
 * @version 1.0
 * @since 1.0
 */
@SpringBootApplication
@EnableScheduling
public class AuthentificationApplication {

	/**
	 * Point d'entrée de l'application.
	 *
	 * @param args arguments de ligne de commande passés à Spring Boot
	 */
	public static void main(String[] args) {
		SpringApplication.run(AuthentificationApplication.class, args);
	}
}