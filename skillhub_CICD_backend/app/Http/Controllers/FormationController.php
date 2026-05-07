<?php

namespace App\Http\Controllers;

use App\Models\Formation;
use App\Models\FormationVue;
use App\Services\ActivityLogService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use OpenApi\Attributes as OA;
use Tymon\JWTAuth\Exceptions\JWTException;
use Tymon\JWTAuth\Facades\JWTAuth;

/**
 * Endpoints CRUD des formations + comptage des vues.
 *
 * @author MU202612
 */
class FormationController extends Controller
{
    private const MSG_FORMATION_INTRO = 'Formation introuvable';

    /**
     * Renvoie la liste des formations, avec filtres optionnels.
     *
     * Étapes :
     * - Charge formations + relation formateur (id, nom, email) + count des inscriptions.
     * - Applique les filtres présents dans la query string : recherche (titre OR description),
     *   categorie, niveau.
     *
     * Utilise les query params recherche, categorie, niveau (facultatifs).
     * Renvoie un tableau JSON de formations.
     */
    #[OA\Get(
        path: '/api/formations',
        tags: ['Formations'],
        summary: 'Lister les formations',
        parameters: [
            new OA\Parameter(name: 'recherche', in: 'query', description: 'Mot-clé sur titre ou description', schema: new OA\Schema(type: 'string')),
            new OA\Parameter(name: 'categorie', in: 'query', schema: new OA\Schema(type: 'string', enum: ['developpement_web', 'data', 'design', 'marketing', 'devops', 'autre'])),
            new OA\Parameter(name: 'niveau', in: 'query', schema: new OA\Schema(type: 'string', enum: ['debutant', 'intermediaire', 'avance'])),
        ],
        responses: [
            new OA\Response(response: 200, description: 'Liste des formations'),
        ]
    )]
    public function index(Request $request): JsonResponse
    {
        $query = Formation::with('formateur:id,nom,email')
            ->withCount('inscriptions');

        if ($request->filled('recherche')) {
            $motCle = $request->input('recherche');
            $query->where(function ($q) use ($motCle) {
                $q->where('titre', 'like', '%'.$motCle.'%')
                    ->orWhere('description', 'like', '%'.$motCle.'%');
            });
        }

        if ($request->filled('categorie')) {
            $query->where('categorie', $request->input('categorie'));
        }

        if ($request->filled('niveau')) {
            $query->where('niveau', $request->input('niveau'));
        }

        return response()->json($query->get());
    }

    /**
     * Affiche une formation et incrémente son compteur de vues.
     *
     * Étapes :
     * - Cherche la formation par id (avec formateur + count d'inscriptions).
     * - Si pas trouvée, renvoie 404.
     * - Tente d'identifier l'utilisateur (token optionnel — la route est publique).
     * - Appelle enregistrerVue (incrémente seulement si pas déjà vue par ce user/IP).
     * - Log la consultation via ActivityLogService.
     *
     * Utilise Formation, FormationVue, ActivityLogService et JWTAuth.
     *
     * @param  mixed  $id  identifiant de la formation
     * @return JsonResponse la formation rafraîchie ou 404
     */
    #[OA\Get(
        path: '/api/formations/{id}',
        tags: ['Formations'],
        summary: "Détail d'une formation",
        parameters: [
            new OA\Parameter(name: 'id', in: 'path', required: true, schema: new OA\Schema(type: 'integer')),
        ],
        responses: [
            new OA\Response(response: 200, description: 'Formation'),
            new OA\Response(response: 404, description: 'Formation introuvable'),
        ]
    )]
    public function show(Request $request, $id): JsonResponse
    {
        $formation = Formation::with('formateur:id,nom,email')
            ->withCount('inscriptions')
            ->find($id);

        if (! $formation) {
            return response()->json(['message' => self::MSG_FORMATION_INTRO], 404);
        }

        $utilisateurId = null;
        try {
            $user = JWTAuth::parseToken()->authenticate();
            if ($user) {
                $utilisateurId = $user->id;
            }
        } catch (JWTException $e) {
            // Utilisateur non connecte
        }

        $this->enregistrerVue($request, $formation, $utilisateurId);

        ActivityLogService::consultationFormation($formation->id, $utilisateurId);

        return response()->json($formation->fresh(['formateur:id,nom,email']));
    }

    /**
     * Crée une formation. Réservé aux formateurs.
     *
     * Étapes :
     * - Vérifie le JWT, refuse si pas formateur (403).
     * - Valide les champs via formationRules().
     * - Crée la formation avec formateur_id = user connecté, nombre_de_vues = 0.
     * - Log la création.
     *
     * @param  Request  $request  body : titre, description, categorie, niveau, prix, duree_heures
     * @return JsonResponse 201 avec { message, formation }
     */
    #[OA\Post(
        path: '/api/formations',
        tags: ['Formations'],
        summary: 'Créer une formation (formateur uniquement)',
        security: [['bearerAuth' => []]],
        requestBody: new OA\RequestBody(
            required: true,
            content: new OA\JsonContent(
                required: ['titre', 'description', 'categorie', 'niveau'],
                properties: [
                    new OA\Property(property: 'titre', type: 'string'),
                    new OA\Property(property: 'description', type: 'string'),
                    new OA\Property(property: 'categorie', type: 'string', enum: ['developpement_web', 'data', 'design', 'marketing', 'devops', 'autre']),
                    new OA\Property(property: 'niveau', type: 'string', enum: ['debutant', 'intermediaire', 'avance']),
                    new OA\Property(property: 'prix', type: 'number'),
                    new OA\Property(property: 'duree_heures', type: 'integer'),
                ]
            )
        ),
        responses: [
            new OA\Response(response: 201, description: 'Formation créée'),
            new OA\Response(response: 401, description: 'Non authentifié'),
            new OA\Response(response: 403, description: 'Pas formateur'),
            new OA\Response(response: 422, description: 'Données invalides'),
        ]
    )]
    public function store(Request $request): JsonResponse
    {
        [$user, $erreur] = $this->authentifierUtilisateur();
        if ($erreur) {
            return $erreur;
        }

        if ($user->role !== 'formateur') {
            return response()->json(['message' => 'Seul un formateur peut créer une formation'], 403);
        }

        $request->validate($this->formationRules());

        $formation = Formation::create([
            'titre' => $request->titre,
            'description' => $request->description,
            'categorie' => $request->categorie,
            'niveau' => $request->niveau,
            'prix' => $request->prix ?? 0,
            'duree_heures' => $request->duree_heures ?? 0,
            'nombre_de_vues' => 0,
            'formateur_id' => $user->id,
        ]);

        ActivityLogService::creationFormation($formation->id, $user->id);

        return response()->json([
            'message' => 'Formation créée avec succès',
            'formation' => $formation,
        ], 201);
    }

    /**
     * Met à jour une formation existante. Réservé au formateur propriétaire.
     *
     * Étapes :
     * - Vérifie le JWT.
     * - Cherche la formation, 404 sinon.
     * - Refuse si l'utilisateur n'est pas le formateur propriétaire (403).
     * - Capture les anciennes valeurs (pour le log de diff).
     * - Valide les champs et persiste.
     * - Log la modification avec ancien/nouveau.
     *
     * @param  Request  $request  body : mêmes champs que store()
     * @param  mixed  $id  id de la formation
     * @return JsonResponse { message, formation } ou erreur
     */
    #[OA\Put(
        path: '/api/formations/{id}',
        tags: ['Formations'],
        summary: 'Modifier une formation (formateur propriétaire)',
        security: [['bearerAuth' => []]],
        parameters: [
            new OA\Parameter(name: 'id', in: 'path', required: true, schema: new OA\Schema(type: 'integer')),
        ],
        requestBody: new OA\RequestBody(
            required: true,
            content: new OA\JsonContent(
                properties: [
                    new OA\Property(property: 'titre', type: 'string'),
                    new OA\Property(property: 'description', type: 'string'),
                    new OA\Property(property: 'categorie', type: 'string'),
                    new OA\Property(property: 'niveau', type: 'string'),
                    new OA\Property(property: 'prix', type: 'number'),
                    new OA\Property(property: 'duree_heures', type: 'integer'),
                ]
            )
        ),
        responses: [
            new OA\Response(response: 200, description: 'Formation modifiée'),
            new OA\Response(response: 403, description: 'Pas propriétaire'),
            new OA\Response(response: 404, description: 'Formation introuvable'),
        ]
    )]
    public function update(Request $request, $id): JsonResponse
    {
        [$user, $erreur] = $this->authentifierUtilisateur();
        if ($erreur) {
            return $erreur;
        }

        $formation = Formation::find($id);

        if (! $formation) {
            return response()->json(['message' => self::MSG_FORMATION_INTRO], 404);
        }

        if ($user->role !== 'formateur' || $formation->formateur_id !== $user->id) {
            return response()->json(['message' => 'Action non autorisée'], 403);
        }

        $oldValues = [
            'titre' => $formation->titre,
            'description' => $formation->description,
            'categorie' => $formation->categorie,
            'niveau' => $formation->niveau,
        ];

        $request->validate($this->formationRules());

        $formation->update([
            'titre' => $request->titre,
            'description' => $request->description,
            'categorie' => $request->categorie,
            'niveau' => $request->niveau,
            'prix' => $request->prix ?? $formation->prix,
            'duree_heures' => $request->duree_heures ?? $formation->duree_heures,
        ]);

        ActivityLogService::modificationFormation(
            $formation->id,
            $user->id,
            $oldValues,
            [
                'titre' => $request->titre,
                'description' => $request->description,
                'categorie' => $request->categorie,
                'niveau' => $request->niveau,
            ]
        );

        return response()->json([
            'message' => 'Formation mise à jour avec succès',
            'formation' => $formation,
        ]);
    }

    /**
     * Supprime une formation. Réservé au formateur propriétaire.
     *
     * Étapes :
     * - Vérifie le JWT.
     * - Cherche la formation, 404 sinon.
     * - Refuse si pas propriétaire (403).
     * - Log la suppression avant le delete (sinon on perd l'id).
     *
     * @param  mixed  $id
     */
    #[OA\Delete(
        path: '/api/formations/{id}',
        tags: ['Formations'],
        summary: 'Supprimer une formation',
        security: [['bearerAuth' => []]],
        parameters: [
            new OA\Parameter(name: 'id', in: 'path', required: true, schema: new OA\Schema(type: 'integer')),
        ],
        responses: [
            new OA\Response(response: 200, description: 'Formation supprimée'),
            new OA\Response(response: 403, description: 'Pas propriétaire'),
            new OA\Response(response: 404, description: 'Formation introuvable'),
        ]
    )]
    public function destroy($id): JsonResponse
    {
        [$user, $erreur] = $this->authentifierUtilisateur();
        if ($erreur) {
            return $erreur;
        }

        $formation = Formation::find($id);

        if (! $formation) {
            return response()->json(['message' => self::MSG_FORMATION_INTRO], 404);
        }

        if ($user->role !== 'formateur' || $formation->formateur_id !== $user->id) {
            return response()->json(['message' => 'Action non autorisée'], 403);
        }

        ActivityLogService::suppressionFormation($formation->id, $user->id);
        $formation->delete();

        return response()->json(['message' => 'Formation supprimée avec succès']);
    }

    // Helpers prives

    /**
     * Règles de validation partagées entre store() et update().
     *
     * @return array<string, string>
     */
    private function formationRules(): array
    {
        return [
            'titre' => 'required|string|max:255',
            'description' => 'required|string',
            'categorie' => 'required|in:developpement_web,data,design,marketing,devops,autre',
            'niveau' => 'required|in:debutant,intermediaire,avance',
            'prix' => 'nullable|numeric|min:0',
            'duree_heures' => 'nullable|integer|min:0',
        ];
    }

    /**
     * Incrémente le compteur de vues d'une formation, mais une seule fois par utilisateur ou IP.
     *
     * Comportement :
     * - Si user connecté : on regarde s'il y a déjà une ligne FormationVue
     *   pour ce couple (formation_id, utilisateur_id). Si oui, on ne fait rien.
     * - Si user anonyme : même logique mais sur (formation_id, IP).
     *
     * Cette logique évite qu'un même apprenant gonfle artificiellement les vues
     * en rechargeant la page.
     *
     * @param  Request  $request  utilisé pour récupérer l'IP
     * @param  Formation  $formation  la formation à vue
     * @param  int|null  $utilisateurId  null si non connecté
     */
    private function enregistrerVue(Request $request, Formation $formation, ?int $utilisateurId): void
    {
        if ($utilisateurId) {
            $dejaVue = FormationVue::where('formation_id', $formation->id)
                ->where('utilisateur_id', $utilisateurId)
                ->exists();

            if (! $dejaVue) {
                FormationVue::create([
                    'formation_id' => $formation->id,
                    'utilisateur_id' => $utilisateurId,
                    'ip' => $request->ip(),
                ]);
                $formation->increment('nombre_de_vues');
            }
        } else {
            $dejaVueIp = FormationVue::where('formation_id', $formation->id)
                ->whereNull('utilisateur_id')
                ->where('ip', $request->ip())
                ->exists();

            if (! $dejaVueIp) {
                FormationVue::create([
                    'formation_id' => $formation->id,
                    'utilisateur_id' => null,
                    'ip' => $request->ip(),
                ]);
                $formation->increment('nombre_de_vues');
            }
        }
    }
}
