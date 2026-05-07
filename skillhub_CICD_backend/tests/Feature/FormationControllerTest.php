<?php

namespace Tests\Feature;

use App\Models\Formation;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;
use Tymon\JWTAuth\Facades\JWTAuth;

/**
 * Tests fonctionnels du contrôleur de formations.
 * Couvre : index (avec filtres), show, store, update, destroy.
 */
class FormationControllerTest extends TestCase
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

    private function payloadFormation(array $overrides = []): array
    {
        return array_merge([
            'titre'       => 'Nouvelle Formation',
            'description' => 'Une description valide',
            'categorie'   => 'data',
            'niveau'      => 'intermediaire',
        ], $overrides);
    }


    // GET /formations (index)


    #[\PHPUnit\Framework\Attributes\Test]
    public function index_retourne_toutes_les_formations(): void
    {
        ['user' => $formateur] = $this->creerUser('formateur');
        $this->creerFormation($formateur, ['titre' => 'Formation A']);
        $this->creerFormation($formateur, ['titre' => 'Formation B']);

        $response = $this->getJson('/api/formations');

        $response->assertStatus(200);
        $this->assertCount(2, $response->json());
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function index_filtre_par_recherche(): void
    {
        ['user' => $formateur] = $this->creerUser('formateur');
        $this->creerFormation($formateur, ['titre' => 'Laravel avancé']);
        $this->creerFormation($formateur, ['titre' => 'React débutant']);

        $response = $this->getJson('/api/formations?recherche=Laravel');

        $response->assertStatus(200);
        $this->assertCount(1, $response->json());
        $this->assertEquals('Laravel avancé', $response->json()[0]['titre']);
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function index_filtre_par_categorie(): void
    {
        ['user' => $formateur] = $this->creerUser('formateur');
        $this->creerFormation($formateur, ['categorie' => 'data']);
        $this->creerFormation($formateur, ['categorie' => 'design']);

        $response = $this->getJson('/api/formations?categorie=data');

        $response->assertStatus(200);
        $this->assertCount(1, $response->json());
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function index_filtre_par_niveau(): void
    {
        ['user' => $formateur] = $this->creerUser('formateur');
        $this->creerFormation($formateur, ['niveau' => 'avance']);
        $this->creerFormation($formateur, ['niveau' => 'debutant']);

        $response = $this->getJson('/api/formations?niveau=avance');

        $response->assertStatus(200);
        $this->assertCount(1, $response->json());
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function index_retourne_tableau_vide_si_aucune_formation(): void
    {
        $response = $this->getJson('/api/formations');

        $response->assertStatus(200);
        $this->assertCount(0, $response->json());
    }


    // GET /formations/{id} (show)


    #[\PHPUnit\Framework\Attributes\Test]
    public function show_retourne_une_formation_existante(): void
    {
        ['user' => $formateur] = $this->creerUser('formateur');
        $formation             = $this->creerFormation($formateur);

        $response = $this->getJson('/api/formations/' . $formation->id);

        $response->assertStatus(200)
            ->assertJsonPath('titre', $formation->titre);
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function show_retourne_404_si_formation_inexistante(): void
    {
        $response = $this->getJson('/api/formations/99999');

        $response->assertStatus(404);
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function show_incremente_les_vues_pour_utilisateur_connecte(): void
    {
        ['user' => $formateur]                 = $this->creerUser('formateur');
        ['user' => $apprenant, 'token' => $t]  = $this->creerUser('apprenant');
        $formation = $this->creerFormation($formateur);

        $this->getJson('/api/formations/' . $formation->id, $this->authHeaders($t));

        $this->assertEquals(1, $formation->fresh()->nombre_de_vues);
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function show_ne_double_pas_les_vues_pour_le_meme_utilisateur(): void
    {
        ['user' => $formateur]                 = $this->creerUser('formateur');
        ['user' => $apprenant, 'token' => $t]  = $this->creerUser('apprenant');
        $formation = $this->creerFormation($formateur);

        $this->getJson('/api/formations/' . $formation->id, $this->authHeaders($t));
        $this->getJson('/api/formations/' . $formation->id, $this->authHeaders($t));

        $this->assertEquals(1, $formation->fresh()->nombre_de_vues);
    }


    // POST /formations (store)


    #[\PHPUnit\Framework\Attributes\Test]
    public function store_cree_formation_pour_formateur_authentifie(): void
    {
        ['token' => $token] = $this->creerUser('formateur');

        $response = $this->postJson(
            '/api/formations',
            $this->payloadFormation(),
            $this->authHeaders($token)
        );

        $response->assertStatus(201)
            ->assertJsonStructure(['message', 'formation']);

        $this->assertDatabaseHas('formations', ['titre' => 'Nouvelle Formation']);
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function store_echoue_pour_apprenant(): void
    {
        ['token' => $token] = $this->creerUser('apprenant');

        $response = $this->postJson(
            '/api/formations',
            $this->payloadFormation(),
            $this->authHeaders($token)
        );

        $response->assertStatus(403);
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function store_echoue_sans_token(): void
    {
        $response = $this->postJson('/api/formations', $this->payloadFormation());

        $response->assertStatus(401);
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function store_echoue_avec_categorie_invalide(): void
    {
        ['token' => $token] = $this->creerUser('formateur');

        $response = $this->postJson(
            '/api/formations',
            $this->payloadFormation(['categorie' => 'cuisine']),
            $this->authHeaders($token)
        );

        $response->assertStatus(422);
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function store_echoue_avec_niveau_invalide(): void
    {
        ['token' => $token] = $this->creerUser('formateur');

        $response = $this->postJson(
            '/api/formations',
            $this->payloadFormation(['niveau' => 'expert']),
            $this->authHeaders($token)
        );

        $response->assertStatus(422);
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function store_echoue_si_titre_manquant(): void
    {
        ['token' => $token] = $this->creerUser('formateur');

        $response = $this->postJson(
            '/api/formations',
            $this->payloadFormation(['titre' => '']),
            $this->authHeaders($token)
        );

        $response->assertStatus(422)
            ->assertJsonValidationErrors(['titre']);
    }


    // PUT /formations/{id} (update)


    #[\PHPUnit\Framework\Attributes\Test]
    public function update_modifie_formation_par_son_formateur(): void
    {
        ['user' => $formateur, 'token' => $token] = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur);

        $response = $this->putJson(
            '/api/formations/' . $formation->id,
            $this->payloadFormation(['titre' => 'Titre Modifié']),
            $this->authHeaders($token)
        );

        $response->assertStatus(200)
            ->assertJsonPath('formation.titre', 'Titre Modifié');
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function update_echoue_si_autre_formateur(): void
    {
        ['user' => $formateur1]              = $this->creerUser('formateur');
        ['token' => $token2]                 = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur1);

        $response = $this->putJson(
            '/api/formations/' . $formation->id,
            $this->payloadFormation(),
            $this->authHeaders($token2)
        );

        $response->assertStatus(403);
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function update_echoue_si_formation_inexistante(): void
    {
        ['token' => $token] = $this->creerUser('formateur');

        $response = $this->putJson(
            '/api/formations/99999',
            $this->payloadFormation(),
            $this->authHeaders($token)
        );

        $response->assertStatus(404);
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function update_echoue_sans_token(): void
    {
        ['user' => $formateur] = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur);

        $response = $this->putJson('/api/formations/' . $formation->id, $this->payloadFormation());

        $response->assertStatus(401);
    }


    // DELETE /formations/{id} (destroy)


    #[\PHPUnit\Framework\Attributes\Test]
    public function destroy_supprime_formation_par_son_formateur(): void
    {
        ['user' => $formateur, 'token' => $token] = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur);

        $response = $this->deleteJson(
            '/api/formations/' . $formation->id,
            [],
            $this->authHeaders($token)
        );

        $response->assertStatus(200);
        $this->assertDatabaseMissing('formations', ['id' => $formation->id]);
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function destroy_echoue_si_autre_formateur(): void
    {
        ['user' => $formateur1]  = $this->creerUser('formateur');
        ['token' => $token2]     = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur1);

        $response = $this->deleteJson(
            '/api/formations/' . $formation->id,
            [],
            $this->authHeaders($token2)
        );

        $response->assertStatus(403);
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function destroy_echoue_si_formation_inexistante(): void
    {
        ['token' => $token] = $this->creerUser('formateur');

        $response = $this->deleteJson('/api/formations/99999', [], $this->authHeaders($token));

        $response->assertStatus(404);
    }

    #[\PHPUnit\Framework\Attributes\Test]
    public function destroy_echoue_sans_token(): void
    {
        ['user' => $formateur] = $this->creerUser('formateur');
        $formation = $this->creerFormation($formateur);

        $response = $this->deleteJson('/api/formations/' . $formation->id);

        $response->assertStatus(401);
    }
}
