<?php

namespace App\Http\Controllers;

use App\Models\Formation;
use App\Models\Inscription;
use App\Services\ActivityLogService;
use Illuminate\Http\JsonResponse;
use OpenApi\Attributes as OA;

/**
 * Endpoints des inscriptions : s'inscrire, se désinscrire, voir ses formations.
 *
 * @author MU202612
 */
class InscriptionController extends Controller
{
    /**
     * Inscrit l'apprenant connecté à une formation.
     *
     * Étapes :
     * - Vérifie le JWT.
     * - Refuse si pas apprenant (403).
     * - 404 si la formation n'existe pas.
     * - 409 si l'apprenant est déjà inscrit à cette formation.
     * - 400 si l'apprenant a déjà 5 formations en cours (limite métier).
     * - Sinon crée l'inscription avec progression à 0 et log l'événement.
     *
     * Renvoie aussi le nombre de formations restantes que l'apprenant peut
     * encore suivre, pratique pour afficher un compteur côté front.
     *
     * @param  mixed  $formationId
     * @return JsonResponse  201 ou erreur
     */
    #[OA\Post(
        path: '/api/formations/{id}/inscription',
        tags: ['Inscriptions'],
        summary: "S'inscrire à une formation (apprenant uniquement, max 5)",
        security: [['bearerAuth' => []]],
        parameters: [
            new OA\Parameter(name: 'id', in: 'path', required: true, schema: new OA\Schema(type: 'integer')),
        ],
        responses: [
            new OA\Response(response: 201, description: 'Inscription réussie'),
            new OA\Response(response: 400, description: 'Limite de 5 formations atteinte'),
            new OA\Response(response: 403, description: 'Pas apprenant'),
            new OA\Response(response: 404, description: 'Formation introuvable'),
            new OA\Response(response: 409, description: 'Déjà inscrit'),
        ]
    )]
    public function store($formationId): JsonResponse
    {
        [$user, $erreur] = $this->authentifierUtilisateur();
        if ($erreur) {
            return $erreur;
        }

        if ($user->role !== 'apprenant') {
            return response()->json([
                'message' => "Seul un apprenant peut s'inscrire à une formation",
            ], 403);
        }

        $formation = Formation::find($formationId);

        if (! $formation) {
            return response()->json(['message' => 'Formation introuvable'], 404);
        }

        // Verifier que l'apprenant n'est pas deja inscrit
        $dejaInscrit = Inscription::where('utilisateur_id', $user->id)
            ->where('formation_id', $formation->id)
            ->first();

        if ($dejaInscrit) {
            return response()->json([
                'message' => 'Vous êtes déjà inscrit à cette formation',
            ], 409);
        }

        // REGLE METIER : un apprenant ne peut suivre que 5 formations au maximum
        $maxFormations = 5;
        $nombreFormationsSuivies = Inscription::where('utilisateur_id', $user->id)->count();

        if ($nombreFormationsSuivies >= $maxFormations) {
            return response()->json([
                'message'             => 'Vous ne pouvez pas suivre plus de 5 formations',
                'max_formations'      => $maxFormations,
                'formations_suivies'  => $nombreFormationsSuivies,
            ], 400);
        }

        $inscription = Inscription::create([
            'utilisateur_id' => $user->id,
            'formation_id'   => $formation->id,
            'progression'    => 0,
        ]);

        ActivityLogService::inscriptionFormation($formation->id, $user->id);

        return response()->json([
            'message'             => 'Inscription réussie',
            'inscription'         => $inscription,
            'formations_restantes' => $maxFormations - ($nombreFormationsSuivies + 1),
        ], 201);
    }

    /**
     * Désinscrit l'apprenant connecté d'une formation.
     *
     * Étapes :
     * - Vérifie le JWT.
     * - Refuse si pas apprenant (403).
     * - Cherche l'inscription pour le couple (user, formation).
     * - 404 si pas trouvée, sinon supprime.
     *
     * @param  mixed  $formationId
     * @return JsonResponse
     */
    #[OA\Delete(
        path: '/api/formations/{id}/inscription',
        tags: ['Inscriptions'],
        summary: "Se désinscrire d'une formation",
        security: [['bearerAuth' => []]],
        parameters: [
            new OA\Parameter(name: 'id', in: 'path', required: true, schema: new OA\Schema(type: 'integer')),
        ],
        responses: [
            new OA\Response(response: 200, description: 'Désinscription réussie'),
            new OA\Response(response: 403, description: 'Pas apprenant'),
            new OA\Response(response: 404, description: 'Inscription introuvable'),
        ]
    )]
    public function destroy($formationId): JsonResponse
    {
        [$user, $erreur] = $this->authentifierUtilisateur();
        if ($erreur) {
            return $erreur;
        }

        if ($user->role !== 'apprenant') {
            return response()->json(['message' => 'Seul un apprenant peut se désinscrire'], 403);
        }

        $inscription = Inscription::where('utilisateur_id', $user->id)
            ->where('formation_id', $formationId)
            ->first();

        if (! $inscription) {
            return response()->json(['message' => 'Inscription introuvable'], 404);
        }

        $inscription->delete();

        return response()->json(['message' => 'Désinscription réussie']);
    }

    /**
     * Renvoie toutes les formations suivies par l'apprenant connecté,
     * avec la formation et son formateur (id, nom, email) chargés.
     *
     * Sert de tableau de bord apprenant.
     *
     * @return JsonResponse  liste d'inscriptions enrichies
     */
    #[OA\Get(
        path: '/api/apprenant/formations',
        tags: ['Inscriptions'],
        summary: 'Mes formations en cours',
        security: [['bearerAuth' => []]],
        responses: [
            new OA\Response(response: 200, description: "Liste des inscriptions de l'apprenant"),
            new OA\Response(response: 403, description: 'Pas apprenant'),
        ]
    )]
    public function mesFormations(): JsonResponse
    {
        [$user, $erreur] = $this->authentifierUtilisateur();
        if ($erreur) {
            return $erreur;
        }

        if ($user->role !== 'apprenant') {
            return response()->json([
                'message' => 'Seul un apprenant peut voir ses formations',
            ], 403);
        }

        $inscriptions = Inscription::with('formation.formateur:id,nom,email')
            ->where('utilisateur_id', $user->id)
            ->get();

        return response()->json($inscriptions);
    }
}
