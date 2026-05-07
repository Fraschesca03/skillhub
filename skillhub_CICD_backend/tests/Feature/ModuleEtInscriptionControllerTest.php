<?php

namespace Tests\Feature;

use App\Models\Formation;
use App\Models\Inscription;
use App\Models\Module;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;
use Tymon\JWTAuth\Facades\JWTAuth;

/**
 * Tests fonctionnels des contrôleurs Modules et Inscriptions.
 * Couvre : CRUD modules, inscription/désinscription, progression, mesFormations.
 */
class ModuleEtInscriptionControllerTest extends TestCase
{
    use RefreshDatabase;


    // Helpers


    private function creerUser(string $role): array
    {
        $user  = User::create([
            'nom'      => ucfirst($role) . ' Test',
            'email'    => $role . uniqid() . '@test.com',
            'password' => bcrypt('password123'),
            'role'     => $role,
        ]);
        $token = JWTAuth::fromUser($user);

        return ['user' => $user, 'token' => $token];
    }

    private function authHeaders(string $token): array
    {
        return ['Authorization' => 'Bearer ' . $token];
    }

    private function creerFormation(User $formateur, array $overrides = []): Formation
    {
        return Formation::create(array_merge([
            'titre'          => 'Formation Test',
            'description'    => 'Description test',
            'categorie'      => 'developpement_web',
            'niveau'         => 'debutant',
            'nombre_de_vues' => 0,
            'formateur_id'   => $formateur->id,
        ], $overrides));
    }

    private function creerModule(Formation $formation, int $ordre = 1): Module
    {
        return Module::create([
            'titre'        => 'Module ' . $ordre,
            'contenu'      => 'Contenu ' . $ordre,
            'ordre'        => $ordre,
            'formation_id' => $formation->id,
        ]);
    }

    private function inscrire(User $apprenant, Formation $formation): Inscription
    {
        return Inscription::create([
            'utilisateur_id' => $apprenant->id,
            'formation_id'   => $formation->id,
            'progression'    => 0,
        ]);
    }


    // MODULE CONTROLLER



    // GET /formations/{id}/modules (index)


    // Verifie que la liste des modules est renvoyee triee par ordre croissant.
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_index_retourne_liste_triee_par_ordre(): void
    {
        ['user' => $formateur] = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur);
        $this->creerModule($formation, 2);
        $this->creerModule($formation, 1);

        $response = $this->getJson('/api/formations/' . $formation->id . '/modules');

        $response->assertStatus(200);
        $data = $response->json();
        $this->assertCount(2, $data);
        $this->assertEquals(1, $data[0]['ordre']);
        $this->assertEquals(2, $data[1]['ordre']);
    }

    // Verifie que la route renvoie un tableau vide quand la formation n a pas de module.
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_index_retourne_tableau_vide_si_aucun_module(): void
    {
        ['user' => $formateur] = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur);

        $response = $this->getJson('/api/formations/' . $formation->id . '/modules');

        $response->assertStatus(200);
        $this->assertCount(0, $response->json());
    }


    // POST /formations/{id}/modules (store)


    // Le formateur proprietaire peut ajouter un module a sa formation (HTTP 201).
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_store_cree_un_module_pour_formateur_proprietaire(): void
    {
        ['user' => $formateur, 'token' => $token] = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur);

        $response = $this->postJson(
            '/api/formations/' . $formation->id . '/modules',
            ['titre' => 'Intro PHP', 'contenu' => 'Variables et types', 'ordre' => 1],
            $this->authHeaders($token)
        );

        $response->assertStatus(201)
            ->assertJsonStructure(['message', 'module']);

        $this->assertDatabaseHas('modules', ['titre' => 'Intro PHP']);
    }

    // Un apprenant n a pas le droit de creer un module : on attend HTTP 403.
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_store_echoue_pour_apprenant(): void
    {
        ['user' => $formateur]  = $this->creerUser('formateur');
        ['token' => $token]     = $this->creerUser('apprenant');
        $formation = $this->creerFormation($formateur);

        $response = $this->postJson(
            '/api/formations/' . $formation->id . '/modules',
            ['titre' => 'Module', 'contenu' => 'Contenu', 'ordre' => 1],
            $this->authHeaders($token)
        );

        $response->assertStatus(403);
    }

    // Un formateur ne peut pas ajouter un module a la formation d un autre formateur (HTTP 403).
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_store_echoue_pour_autre_formateur(): void
    {
        ['user' => $formateur1]  = $this->creerUser('formateur');
        ['token' => $token2]     = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur1);

        $response = $this->postJson(
            '/api/formations/' . $formation->id . '/modules',
            ['titre' => 'Module', 'contenu' => 'Contenu', 'ordre' => 1],
            $this->authHeaders($token2)
        );

        $response->assertStatus(403);
    }

    // Sans token JWT, la creation de module doit etre rejetee (HTTP 401).
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_store_echoue_sans_token(): void
    {
        ['user' => $formateur] = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur);

        $response = $this->postJson(
            '/api/formations/' . $formation->id . '/modules',
            ['titre' => 'Module', 'contenu' => 'Contenu', 'ordre' => 1]
        );

        $response->assertStatus(401);
    }

    // Quand la formation cible n existe pas, on attend une 404.
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_store_echoue_si_formation_inexistante(): void
    {
        ['token' => $token] = $this->creerUser('formateur');

        $response = $this->postJson(
            '/api/formations/99999/modules',
            ['titre' => 'Module', 'contenu' => 'Contenu', 'ordre' => 1],
            $this->authHeaders($token)
        );

        $response->assertStatus(404);
    }

    // Si les champs obligatoires sont vides, la validation renvoie HTTP 422.
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_store_echoue_si_champs_manquants(): void
    {
        ['user' => $formateur, 'token' => $token] = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur);

        $response = $this->postJson(
            '/api/formations/' . $formation->id . '/modules',
            [],
            $this->authHeaders($token)
        );

        $response->assertStatus(422)
            ->assertJsonValidationErrors(['titre', 'contenu', 'ordre']);
    }


    // PUT /modules/{id} (update)


    // Le formateur proprietaire peut modifier le titre/contenu de son module.
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_update_modifie_module_par_formateur_proprietaire(): void
    {
        ['user' => $formateur, 'token' => $token] = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur);
        $module    = $this->creerModule($formation);

        $response = $this->putJson(
            '/api/modules/' . $module->id,
            ['titre' => 'Titre Modifié', 'contenu' => 'Nouveau contenu', 'ordre' => 1],
            $this->authHeaders($token)
        );

        $response->assertStatus(200)
            ->assertJsonPath('module.titre', 'Titre Modifié');
    }

    // Un formateur ne peut pas modifier le module d un autre formateur (HTTP 403).
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_update_echoue_pour_autre_formateur(): void
    {
        ['user' => $formateur1]  = $this->creerUser('formateur');
        ['token' => $token2]     = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur1);
        $module    = $this->creerModule($formation);

        $response = $this->putJson(
            '/api/modules/' . $module->id,
            ['titre' => 'Modifié', 'contenu' => 'Contenu', 'ordre' => 1],
            $this->authHeaders($token2)
        );

        $response->assertStatus(403);
    }

    // Modifier un module qui n existe pas renvoie HTTP 404.
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_update_echoue_si_module_inexistant(): void
    {
        ['token' => $token] = $this->creerUser('formateur');

        $response = $this->putJson(
            '/api/modules/99999',
            ['titre' => 'Modifié', 'contenu' => 'Contenu', 'ordre' => 1],
            $this->authHeaders($token)
        );

        $response->assertStatus(404);
    }


    // DELETE /modules/{id} (destroy)


    // Le formateur proprietaire peut supprimer son module (et il disparait en base).
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_destroy_supprime_module_par_formateur(): void
    {
        ['user' => $formateur, 'token' => $token] = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur);
        $module    = $this->creerModule($formation);

        $response = $this->deleteJson(
            '/api/modules/' . $module->id,
            [],
            $this->authHeaders($token)
        );

        $response->assertStatus(200);
        $this->assertDatabaseMissing('modules', ['id' => $module->id]);
    }

    // Un apprenant ne peut pas supprimer un module : HTTP 403.
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_destroy_echoue_pour_apprenant(): void
    {
        ['user' => $formateur]  = $this->creerUser('formateur');
        ['token' => $token]     = $this->creerUser('apprenant');
        $formation = $this->creerFormation($formateur);
        $module    = $this->creerModule($formation);

        $response = $this->deleteJson(
            '/api/modules/' . $module->id,
            [],
            $this->authHeaders($token)
        );

        $response->assertStatus(403);
    }


    // POST /modules/{id}/terminer


    // Quand un apprenant termine 1 module sur 2, sa progression passe a 50%.
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_terminer_met_a_jour_la_progression(): void
    {
        ['user' => $formateur]                     = $this->creerUser('formateur');
        ['user' => $apprenant, 'token' => $token]  = $this->creerUser('apprenant');
        $formation  = $this->creerFormation($formateur);
        $module     = $this->creerModule($formation, 1);
        $this->creerModule($formation, 2);
        $this->inscrire($apprenant, $formation);

        $response = $this->postJson(
            '/api/modules/' . $module->id . '/terminer',
            [],
            $this->authHeaders($token)
        );

        $response->assertStatus(200)
            ->assertJsonFragment(['progression' => 50]);
    }

    // Un apprenant non inscrit a la formation ne peut pas terminer un module (HTTP 403).
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_terminer_echoue_si_non_inscrit(): void
    {
        ['user' => $formateur]                     = $this->creerUser('formateur');
        ['user' => $apprenant, 'token' => $token]  = $this->creerUser('apprenant');
        $formation = $this->creerFormation($formateur);
        $module    = $this->creerModule($formation);

        $response = $this->postJson(
            '/api/modules/' . $module->id . '/terminer',
            [],
            $this->authHeaders($token)
        );

        $response->assertStatus(403);
    }

    // Terminer un module qui n existe pas renvoie HTTP 404.
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_terminer_echoue_si_module_inexistant(): void
    {
        ['token' => $token] = $this->creerUser('apprenant');

        $response = $this->postJson(
            '/api/modules/99999/terminer',
            [],
            $this->authHeaders($token)
        );

        $response->assertStatus(404);
    }

    // Terminer 2 fois le meme module renvoie un message indiquant qu il est deja termine.
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_terminer_retourne_message_si_deja_termine(): void
    {
        ['user' => $formateur]                     = $this->creerUser('formateur');
        ['user' => $apprenant, 'token' => $token]  = $this->creerUser('apprenant');
        $formation = $this->creerFormation($formateur);
        $module    = $this->creerModule($formation);
        $this->inscrire($apprenant, $formation);

        $this->postJson('/api/modules/' . $module->id . '/terminer', [], $this->authHeaders($token));
        $response = $this->postJson('/api/modules/' . $module->id . '/terminer', [], $this->authHeaders($token));

        $response->assertStatus(200)
            ->assertJsonFragment(['message' => 'Ce module est déjà terminé']);
    }

    // Un formateur ne peut pas terminer un module (action reservee aux apprenants) : HTTP 403.
    #[\PHPUnit\Framework\Attributes\Test]
    public function modules_terminer_echoue_pour_formateur(): void
    {
        ['user' => $formateur, 'token' => $token] = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur);
        $module    = $this->creerModule($formation);

        $response = $this->postJson(
            '/api/modules/' . $module->id . '/terminer',
            [],
            $this->authHeaders($token)
        );

        $response->assertStatus(403);
    }


    // INSCRIPTION CONTROLLER



    // POST /formations/{id}/inscription (store)


    // Un apprenant peut s inscrire a une formation : on verifie HTTP 201 et la ligne en base.
    #[\PHPUnit\Framework\Attributes\Test]
    public function inscription_store_inscrit_apprenant_a_formation(): void
    {
        ['user' => $formateur]                     = $this->creerUser('formateur');
        ['user' => $apprenant, 'token' => $token]  = $this->creerUser('apprenant');
        $formation = $this->creerFormation($formateur);

        $response = $this->postJson(
            '/api/formations/' . $formation->id . '/inscription',
            [],
            $this->authHeaders($token)
        );

        $response->assertStatus(201)
            ->assertJsonStructure(['message', 'inscription']);

        $this->assertDatabaseHas('inscriptions', [
            'utilisateur_id' => $apprenant->id,
            'formation_id'   => $formation->id,
        ]);
    }

    // Un formateur ne peut pas s inscrire a une formation : HTTP 403.
    #[\PHPUnit\Framework\Attributes\Test]
    public function inscription_store_echoue_pour_formateur(): void
    {
        ['user' => $formateur, 'token' => $token] = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur);

        $response = $this->postJson(
            '/api/formations/' . $formation->id . '/inscription',
            [],
            $this->authHeaders($token)
        );

        $response->assertStatus(403);
    }

    // S inscrire deux fois a la meme formation est refuse (HTTP 409, doublon).
    #[\PHPUnit\Framework\Attributes\Test]
    public function inscription_store_echoue_si_deja_inscrit(): void
    {
        ['user' => $formateur]                     = $this->creerUser('formateur');
        ['user' => $apprenant, 'token' => $token]  = $this->creerUser('apprenant');
        $formation = $this->creerFormation($formateur);
        $this->inscrire($apprenant, $formation);

        $response = $this->postJson(
            '/api/formations/' . $formation->id . '/inscription',
            [],
            $this->authHeaders($token)
        );

        $response->assertStatus(409);
    }

    // S inscrire a une formation qui n existe pas renvoie HTTP 404.
    #[\PHPUnit\Framework\Attributes\Test]
    public function inscription_store_echoue_si_formation_inexistante(): void
    {
        ['token' => $token] = $this->creerUser('apprenant');

        $response = $this->postJson('/api/formations/99999/inscription', [], $this->authHeaders($token));

        $response->assertStatus(404);
    }

    // Regle metier : un apprenant deja inscrit a 5 formations ne peut pas en suivre une 6eme.
    // On cree 5 inscriptions, on tente la 6eme, on attend HTTP 400 et aucune nouvelle ligne en base.
    #[\PHPUnit\Framework\Attributes\Test]
    public function inscription_store_echoue_si_apprenant_a_deja_5_formations(): void
    {
        ['user' => $formateur]                     = $this->creerUser('formateur');
        ['user' => $apprenant, 'token' => $token]  = $this->creerUser('apprenant');

        // Inscrit l apprenant a 5 formations differentes (la limite metier)
        for ($i = 1; $i <= 5; $i++) {
            $formation = $this->creerFormation($formateur, ['titre' => 'Formation ' . $i]);
            $this->inscrire($apprenant, $formation);
        }

        // Tentative d inscription a une 6eme formation : doit etre refusee
        $sixieme = $this->creerFormation($formateur, ['titre' => 'Formation 6']);

        $response = $this->postJson(
            '/api/formations/' . $sixieme->id . '/inscription',
            [],
            $this->authHeaders($token)
        );

        $response->assertStatus(400)
            ->assertJsonFragment([
                'message'            => 'Vous ne pouvez pas suivre plus de 5 formations',
                'max_formations'     => 5,
                'formations_suivies' => 5,
            ]);

        // Aucune inscription supplementaire ne doit avoir ete persistee
        $this->assertDatabaseMissing('inscriptions', [
            'utilisateur_id' => $apprenant->id,
            'formation_id'   => $sixieme->id,
        ]);
        $this->assertEquals(5, Inscription::where('utilisateur_id', $apprenant->id)->count());
    }

    // Cas limite : un apprenant avec 4 inscriptions peut bien faire la 5eme (HTTP 201, formations_restantes = 0).
    #[\PHPUnit\Framework\Attributes\Test]
    public function inscription_store_autorise_la_5eme_inscription(): void
    {
        ['user' => $formateur]                     = $this->creerUser('formateur');
        ['user' => $apprenant, 'token' => $token]  = $this->creerUser('apprenant');

        // L apprenant suit deja 4 formations (en dessous de la limite)
        for ($i = 1; $i <= 4; $i++) {
            $formation = $this->creerFormation($formateur, ['titre' => 'Formation ' . $i]);
            $this->inscrire($apprenant, $formation);
        }

        // La 5eme inscription doit reussir et amener le compteur a la limite
        $cinquieme = $this->creerFormation($formateur, ['titre' => 'Formation 5']);

        $response = $this->postJson(
            '/api/formations/' . $cinquieme->id . '/inscription',
            [],
            $this->authHeaders($token)
        );

        $response->assertStatus(201)
            ->assertJsonFragment([
                'message'              => 'Inscription réussie',
                'formations_restantes' => 0,
            ]);

        $this->assertEquals(5, Inscription::where('utilisateur_id', $apprenant->id)->count());
    }

    // Sans token JWT, l inscription a une formation est refusee (HTTP 401).
    #[\PHPUnit\Framework\Attributes\Test]
    public function inscription_store_echoue_sans_token(): void
    {
        ['user' => $formateur] = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur);

        $response = $this->postJson('/api/formations/' . $formation->id . '/inscription');

        $response->assertStatus(401);
    }


    // DELETE /formations/{id}/inscription (destroy)


    // Un apprenant inscrit peut se desinscrire : on verifie HTTP 200 et la suppression en base.
    #[\PHPUnit\Framework\Attributes\Test]
    public function inscription_destroy_desincrit_apprenant(): void
    {
        ['user' => $formateur]                     = $this->creerUser('formateur');
        ['user' => $apprenant, 'token' => $token]  = $this->creerUser('apprenant');
        $formation = $this->creerFormation($formateur);
        $this->inscrire($apprenant, $formation);

        $response = $this->deleteJson(
            '/api/formations/' . $formation->id . '/inscription',
            [],
            $this->authHeaders($token)
        );

        $response->assertStatus(200)
            ->assertJsonFragment(['message' => 'Désinscription réussie']);

        $this->assertDatabaseMissing('inscriptions', [
            'utilisateur_id' => $apprenant->id,
            'formation_id'   => $formation->id,
        ]);
    }

    // Tenter de se desinscrire d une formation a laquelle on n est pas inscrit : HTTP 404.
    #[\PHPUnit\Framework\Attributes\Test]
    public function inscription_destroy_echoue_si_non_inscrit(): void
    {
        ['user' => $formateur]                     = $this->creerUser('formateur');
        ['token' => $token]                        = $this->creerUser('apprenant');
        $formation = $this->creerFormation($formateur);

        $response = $this->deleteJson(
            '/api/formations/' . $formation->id . '/inscription',
            [],
            $this->authHeaders($token)
        );

        $response->assertStatus(404);
    }

    // Un formateur ne peut pas appeler la route de desinscription : HTTP 403.
    #[\PHPUnit\Framework\Attributes\Test]
    public function inscription_destroy_echoue_pour_formateur(): void
    {
        ['user' => $formateur, 'token' => $token] = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur);

        $response = $this->deleteJson(
            '/api/formations/' . $formation->id . '/inscription',
            [],
            $this->authHeaders($token)
        );

        $response->assertStatus(403);
    }


    // GET /apprenant/formations (mesFormations)


    // L apprenant connecte recoit la liste de ses inscriptions (ici 1 seule formation).
    #[\PHPUnit\Framework\Attributes\Test]
    public function mes_formations_retourne_inscriptions_apprenant(): void
    {
        ['user' => $formateur]                     = $this->creerUser('formateur');
        ['user' => $apprenant, 'token' => $token]  = $this->creerUser('apprenant');
        $formation = $this->creerFormation($formateur);
        $this->inscrire($apprenant, $formation);

        $response = $this->getJson('/api/apprenant/formations', $this->authHeaders($token));

        $response->assertStatus(200);
        $this->assertCount(1, $response->json());
    }

    // Un formateur n a pas acces a la liste "mes formations" (HTTP 403).
    #[\PHPUnit\Framework\Attributes\Test]
    public function mes_formations_echoue_pour_formateur(): void
    {
        ['token' => $token] = $this->creerUser('formateur');

        $response = $this->getJson('/api/apprenant/formations', $this->authHeaders($token));

        $response->assertStatus(403);
    }

    // Sans token, la liste "mes formations" est inaccessible (HTTP 401).
    #[\PHPUnit\Framework\Attributes\Test]
    public function mes_formations_echoue_sans_token(): void
    {
        $response = $this->getJson('/api/apprenant/formations');

        $response->assertStatus(401);
    }
}
