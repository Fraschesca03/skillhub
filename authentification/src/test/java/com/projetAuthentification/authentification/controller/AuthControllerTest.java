package com.projetAuthentification.authentification.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties")
class AuthControllerTest {

    // MockMvc simule des requêtes HTTP sans démarrer un vrai serveur
    @Autowired
    private MockMvc mockMvc;

    /**
     * Étapes :
     * <ol>
     *   <li>Construit une requête {@code POST /api/auth/register} avec un body JSON
     *       complet (nom, role, email, password) via {@code MockMvc}</li>
     *   <li>Vérifie le statut HTTP 200</li>
     *   <li>Vérifie via JSONPath que {@code $.user.email} contient l'email envoyé</li>
     *   <li>Vérifie que {@code $.token} est présent dans la réponse</li>
     * </ol>
     * Variables extérieures : {@code mockMvc} (autowired) qui frappe le contexte
     * Spring complet, et la base H2 en mémoire configurée par
     * {@code application-test.properties}.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par {@code mockMvc.perform}
     */
    @Test
    @DisplayName("Register OK : tous les champs valides")
    void registerOk() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
            {
              "nom": "Dupont",
              "role": "apprenant",
              "email": "alice.controller@test.com",
              "password": "MonMotDePasse123!"
            }
            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("alice.controller@test.com"))
                .andExpect(jsonPath("$.token").exists());
    }

    /**
     * Étapes :
     * <ol>
     *   <li>1ʳᵉ requête {@code POST /api/auth/register} avec
     *       {@code bob.double@test.com} → attendu 200</li>
     *   <li>2ᵉ requête identique → attendu 409 (Conflict),
     *       déclenché par la {@code ResourceConflictException}
     *       interceptée par {@code ControllerAdvice}</li>
     * </ol>
     * Variables extérieures : {@code mockMvc} et la base H2 en mémoire
     * (l'état persiste entre les deux appels au sein du même test).
     * Retour : {@code void}.
     *
     * @throws Exception remontée par {@code mockMvc.perform}
     */
    @Test
    @DisplayName("Register KO : email deja utilise")
    void registerKo_emailDejaUtilise() throws Exception {
        // Premier enregistrement
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
            {
              "nom": "Dupont",
              "role": "apprenant",
              "email": "bob.double@test.com",
              "password": "MonMotDePasse123!"
            }
            """))
                .andExpect(status().isOk());

        // Deuxième enregistrement avec le même email → 409
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
            {
              "nom": "Dupont",
              "role": "apprenant",
              "email": "bob.double@test.com",
              "password": "MonMotDePasse123!"
            }
            """))
                .andExpect(status().isConflict());
    }

    /**
     * Étapes :
     * <ol>
     *   <li>Envoie {@code GET /api/me} sans header {@code Authorization}</li>
     *   <li>Vérifie le statut HTTP 400 (le header obligatoire est absent,
     *       Spring rejette la requête avant d'atteindre le contrôleur)</li>
     * </ol>
     * Variables extérieures : {@code mockMvc} autowired.
     * Retour : {@code void}.
     *
     * @throws Exception remontée par {@code mockMvc.perform}
     */
    @Test
    @DisplayName("Acces /api/me sans token : KO")
    void meKo_sansToken() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isBadRequest());
    }
}