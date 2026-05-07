<?php

namespace App\Http\Controllers;

use App\Models\Formation;
use App\Models\Inscription;
use App\Models\Module;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use OpenApi\Attributes as OA;

/**
 * Endpoints des modules : liste, CRUD réservé au formateur, marquage "terminé" par l'apprenant.
 *
 * @author MU202612
 */
class ModuleController extends Controller
{
    private const MSG_MODULE_INTRO = 'Module introuvable';

    /**
     * Renvoie tous les modules d'une formation, triés par champ ordre.
     *
     * Route publique (pas besoin de token). Utile à l'apprenant qui découvre
     * le contenu avant de s'inscrire.
     *
     * @param  mixed  $formationId
     * @return JsonResponse  liste de modules
     */
    #[OA\Get(
        path: '/api/formations/{id}/modules',
        tags: ['Modules'],
        summary: "Lister les modules d'une formation",
        parameters: [
            new OA\Parameter(name: 'id', in: 'path', required: true, schema: new OA\Schema(type: 'integer')),
        ],
        responses: [
            new OA\Response(response: 200, description: 'Liste des modules'),
        ]
    )]
    public function index($formationId): JsonResponse
    {
        $modules = Module::where('formation_id', $formationId)
            ->orderBy('ordre')
            ->get();

        return response()->json($modules);
    }

    /**
     * Crée un module dans une formation. Seul le formateur propriétaire peut le faire.
     *
     * Étapes :
     * - Vérifie le JWT.
     * - Refuse si pas formateur (403).
     * - Vérifie que la formation existe (404 sinon) et qu'elle appartient au formateur (403 sinon).
     * - Valide titre / contenu / ordre.
     * - Crée le module rattaché à la formation.
     *
     * @param  Request  $request      body : titre, contenu, ordre
     * @param  mixed    $formationId  id de la formation parent
     * @return JsonResponse  201 ou erreur
     */
    #[OA\Post(
        path: '/api/formations/{id}/modules',
        tags: ['Modules'],
        summary: 'Créer un module (formateur propriétaire)',
        security: [['bearerAuth' => []]],
        parameters: [
            new OA\Parameter(name: 'id', in: 'path', required: true, schema: new OA\Schema(type: 'integer')),
        ],
        requestBody: new OA\RequestBody(
            required: true,
            content: new OA\JsonContent(
                required: ['titre', 'contenu', 'ordre'],
                properties: [
                    new OA\Property(property: 'titre', type: 'string'),
                    new OA\Property(property: 'contenu', type: 'string'),
                    new OA\Property(property: 'ordre', type: 'integer', minimum: 1),
                ]
            )
        ),
        responses: [
            new OA\Response(response: 201, description: 'Module créé'),
            new OA\Response(response: 403, description: 'Non autorisé'),
            new OA\Response(response: 404, description: 'Formation introuvable'),
        ]
    )]
    public function store(Request $request, $formationId): JsonResponse
    {
        [$user, $erreur] = $this->authentifierUtilisateur();
        if ($erreur) {
            return $erreur;
        }

        if ($user->role !== 'formateur') {
            return response()->json(['message' => 'Seul un formateur peut créer un module'], 403);
        }

        $formation = Formation::find($formationId);

        if (! $formation) {
            return response()->json(['message' => 'Formation introuvable'], 404);
        }

        if ($formation->formateur_id !== $user->id) {
            return response()->json([
                'message' => 'Vous ne pouvez pas modifier une formation qui ne vous appartient pas',
            ], 403);
        }

        $data = $request->validate($this->moduleRules());

        $module = Module::create([
            'titre'        => $data['titre'],
            'contenu'      => $data['contenu'],
            'ordre'        => $data['ordre'],
            'formation_id' => $formationId,
        ]);

        return response()->json([
            'message' => 'Module créé avec succès',
            'module'  => $module,
        ], 201);
    }

    /**
     * Met à jour un module. Réservé au formateur propriétaire de la formation.
     *
     * Étapes :
     * - Délègue la vérification (auth + rôle + propriété) à chargerModuleAutorise().
     * - Si OK, valide les champs et persiste.
     *
     * @param  Request  $request  body : titre, contenu, ordre
     * @param  mixed    $id       id du module
     * @return JsonResponse
     */
    #[OA\Put(
        path: '/api/modules/{id}',
        tags: ['Modules'],
        summary: 'Modifier un module',
        security: [['bearerAuth' => []]],
        parameters: [
            new OA\Parameter(name: 'id', in: 'path', required: true, schema: new OA\Schema(type: 'integer')),
        ],
        requestBody: new OA\RequestBody(
            required: true,
            content: new OA\JsonContent(
                properties: [
                    new OA\Property(property: 'titre', type: 'string'),
                    new OA\Property(property: 'contenu', type: 'string'),
                    new OA\Property(property: 'ordre', type: 'integer'),
                ]
            )
        ),
        responses: [
            new OA\Response(response: 200, description: 'Module modifié'),
            new OA\Response(response: 403, description: 'Non autorisé'),
            new OA\Response(response: 404, description: 'Module introuvable'),
        ]
    )]
    public function update(Request $request, $id): JsonResponse
    {
        [, $module, $erreur] = $this->chargerModuleAutorise($id);
        if ($erreur) {
            return $erreur;
        }

        $data = $request->validate($this->moduleRules());

        $module->update([
            'titre'   => $data['titre'],
            'contenu' => $data['contenu'],
            'ordre'   => $data['ordre'],
        ]);

        return response()->json([
            'message' => 'Module mis à jour avec succès',
            'module'  => $module,
        ]);
    }

    /**
     * Supprime un module. Mêmes règles d'autorisation que update().
     *
     * @param  mixed  $id
     * @return JsonResponse
     */
    #[OA\Delete(
        path: '/api/modules/{id}',
        tags: ['Modules'],
        summary: 'Supprimer un module',
        security: [['bearerAuth' => []]],
        parameters: [
            new OA\Parameter(name: 'id', in: 'path', required: true, schema: new OA\Schema(type: 'integer')),
        ],
        responses: [
            new OA\Response(response: 200, description: 'Module supprimé'),
            new OA\Response(response: 403, description: 'Non autorisé'),
            new OA\Response(response: 404, description: 'Module introuvable'),
        ]
    )]
    public function destroy($id): JsonResponse
    {
        [, $module, $erreur] = $this->chargerModuleAutorise($id);
        if ($erreur) {
            return $erreur;
        }

        $module->delete();

        return response()->json(['message' => 'Module supprimé avec succès']);
    }

    /**
     * Marque un module comme terminé par l'apprenant connecté et recalcule sa progression.
     *
     * Étapes :
     * - Vérifie le JWT.
     * - Refuse si l'utilisateur n'est pas apprenant (403).
     * - Cherche le module (404 sinon).
     * - Vérifie que l'apprenant est bien inscrit à la formation parent (403 sinon).
     * - Si déjà terminé, renvoie un message + la progression actuelle (idempotent).
     * - Sinon, ajoute la liaison module-user via la pivot avec termine=true.
     * - Recalcule progression = (modules terminés / total modules) * 100, arrondi.
     * - Met à jour inscription.progression.
     *
     * Utilise les modèles Module, Inscription et la relation user->modulesTermines().
     *
     * @param  mixed  $id  id du module
     * @return JsonResponse  { message, progression } ou erreur
     */
    #[OA\Post(
        path: '/api/modules/{id}/terminer',
        tags: ['Modules'],
        summary: 'Marquer un module comme terminé (apprenant inscrit)',
        security: [['bearerAuth' => []]],
        parameters: [
            new OA\Parameter(name: 'id', in: 'path', required: true, schema: new OA\Schema(type: 'integer')),
        ],
        responses: [
            new OA\Response(response: 200, description: 'Module terminé, progression renvoyée'),
            new OA\Response(response: 403, description: 'Pas apprenant ou pas inscrit'),
            new OA\Response(response: 404, description: 'Module introuvable'),
        ]
    )]
    public function terminer($id): JsonResponse
    {
        [$user, $erreur] = $this->authentifierUtilisateur();
        if ($erreur) {
            return $erreur;
        }

        if ($user->role !== 'apprenant') {
            return response()->json(['message' => 'Seul un apprenant peut terminer un module'], 403);
        }

        $module = Module::find($id);

        if (! $module) {
            return response()->json(['message' => self::MSG_MODULE_INTRO], 404);
        }

        $inscription = Inscription::where('utilisateur_id', $user->id)
            ->where('formation_id', $module->formation_id)
            ->first();

        if (! $inscription) {
            return response()->json([
                'message' => "Vous n'êtes pas inscrit à cette formation",
            ], 403);
        }

        $dejaTermine = $user->modulesTermines()
            ->where('modules.id', $module->id)
            ->exists();

        if ($dejaTermine) {
            return response()->json([
                'message'     => 'Ce module est déjà terminé',
                'progression' => $inscription->progression,
            ]);
        }

        $user->modulesTermines()->attach($module->id, ['termine' => true]);

        $totalModules    = Module::where('formation_id', $module->formation_id)->count();
        $modulesTermines = $user->modulesTermines()
            ->where('formation_id', $module->formation_id)
            ->count();

        $progression = $totalModules > 0
            ? round(($modulesTermines / $totalModules) * 100)
            : 0;

        $inscription->progression = $progression;
        $inscription->save();

        return response()->json([
            'message'     => 'Module terminé avec succès',
            'progression' => $inscription->progression,
        ]);
    }

    // Helpers prives

    /**
     * Centralise la chaîne d'autorisations pour update() et destroy() :
     * authentifie + vérifie rôle formateur + charge le module + vérifie qu'il appartient
     * à la formation du formateur.
     *
     * Renvoie [user, module, null] si tout va bien, sinon [null, null, JsonResponse erreur].
     *
     * @param  mixed  $id  id du module
     * @return array{0: \App\Models\User|null, 1: Module|null, 2: JsonResponse|null}
     */
    private function chargerModuleAutorise($id): array
    {
        [$user, $erreur] = $this->authentifierUtilisateur();
        if ($erreur) {
            return [null, null, $erreur];
        }

        if ($user->role !== 'formateur') {
            return [null, null, response()->json(['message' => 'Action non autorisée'], 403)];
        }

        $module = Module::find($id);
        if (! $module) {
            return [null, null, response()->json(['message' => self::MSG_MODULE_INTRO], 404)];
        }

        $formation = Formation::find($module->formation_id);
        if (! $formation || $formation->formateur_id !== $user->id) {
            return [null, null, response()->json(['message' => 'Action non autorisée'], 403)];
        }

        return [$user, $module, null];
    }

    /**
     * Règles de validation partagées entre store() et update().
     *
     * @return array<string, string>
     */
    private function moduleRules(): array
    {
        return [
            'titre'   => 'required|string|max:255',
            'contenu' => 'required|string',
            'ordre'   => 'required|integer|min:1',
        ];
    }
}
