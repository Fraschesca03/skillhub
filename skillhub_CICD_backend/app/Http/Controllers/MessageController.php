<?php

namespace App\Http\Controllers;

use App\Mail\NouveauMessageMail;
use App\Models\Message;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Mail;
use Tymon\JWTAuth\Facades\JWTAuth;
use Tymon\JWTAuth\Exceptions\JWTException;
use OpenApi\Attributes as OA;

/**
 * Endpoints de messagerie : compteur de messages non lus, conversations,
 * historique avec un interlocuteur, envoi, et liste des interlocuteurs autorisés.
 *
 * @author MU202612
 */
class MessageController extends Controller
{
    /**
     * Renvoie le nombre de messages non lus reçus par l'utilisateur connecté.
     *
     * Pratique pour afficher un badge "X messages" en haut de l'app.
     *
     * @return JsonResponse  { non_lus: int } ou 401
     */
    #[OA\Get(
        path: '/api/messages/non-lus',
        tags: ['Messages'],
        summary: 'Nombre de messages non lus',
        security: [['bearerAuth' => []]],
        responses: [
            new OA\Response(response: 200, description: 'Compteur renvoyé'),
            new OA\Response(response: 401, description: 'Non authentifié'),
        ]
    )]
    public function nonLus(): JsonResponse
    {
        $user = $this->utilisateurConnecte();
        if (! $user) {
            return $this->reponseNonAutorise();
        }

        $count = Message::where('destinataire_id', $user->id)
            ->where('lu', false)
            ->count();

        return response()->json(['non_lus' => $count]);
    }

    /**
     * Renvoie la liste des conversations de l'utilisateur (une ligne par interlocuteur).
     *
     * Étapes :
     * - Récupère tous les messages où le user est expéditeur OU destinataire,
     *   avec leurs relations expediteur et destinataire, triés du plus récent au plus ancien.
     * - Itère sur les messages et regroupe par interlocuteur :
     *   le 1er rencontré (donc le plus récent) sert d'aperçu.
     * - Compte au passage les messages non lus de cet interlocuteur vers le user.
     *
     * Le résultat ne contient pas l'historique complet, juste un résumé par discussion.
     *
     * @return JsonResponse  { conversations: array }
     */
    #[OA\Get(
        path: '/api/messages/conversations',
        tags: ['Messages'],
        summary: 'Liste des conversations',
        security: [['bearerAuth' => []]],
        responses: [
            new OA\Response(response: 200, description: 'Conversations triées du plus récent au plus ancien'),
            new OA\Response(response: 401, description: 'Non authentifié'),
        ]
    )]
    public function conversations(): JsonResponse
    {
        $user = $this->utilisateurConnecte();
        if (! $user) {
            return $this->reponseNonAutorise();
        }

        $messages = Message::where('expediteur_id', $user->id)
            ->orWhere('destinataire_id', $user->id)
            ->with(['expediteur', 'destinataire'])
            ->orderByDesc('created_at')
            ->get();

        $conversations = [];

        foreach ($messages as $message) {
            $interlocuteur = $message->expediteur_id === $user->id
                ? $message->destinataire
                : $message->expediteur;

            $id = $interlocuteur->id;

            if (! isset($conversations[$id])) {
                $conversations[$id] = [
                    'interlocuteur_id'  => $interlocuteur->id,
                    'interlocuteur_nom' => $interlocuteur->nom,
                    'dernier_message'   => $message->contenu,
                    'date'              => $message->created_at,
                    'non_lus'           => 0,
                ];
            }

            if ($message->destinataire_id === $user->id && ! $message->lu) {
                $conversations[$id]['non_lus']++;
            }
        }

        return response()->json(['conversations' => array_values($conversations)]);
    }

    /**
     * Renvoie l'historique complet entre l'utilisateur connecté et un interlocuteur.
     *
     * Effets de bord : passe à lu=true tous les messages reçus de cet interlocuteur.
     * Sans ça, le badge "non lus" ne baisserait jamais.
     *
     * Les messages sont triés du plus ancien au plus récent (ordre de lecture).
     *
     * @param  int  $interlocuteurId
     * @return JsonResponse  { messages: array }
     */
    #[OA\Get(
        path: '/api/messages/{interlocuteurId}',
        tags: ['Messages'],
        summary: "Historique d'une conversation et marquage en lu",
        security: [['bearerAuth' => []]],
        parameters: [
            new OA\Parameter(name: 'interlocuteurId', in: 'path', required: true, schema: new OA\Schema(type: 'integer')),
        ],
        responses: [
            new OA\Response(response: 200, description: 'Messages renvoyés et marqués lus'),
            new OA\Response(response: 401, description: 'Non authentifié'),
        ]
    )]
    public function messagerie(int $interlocuteurId): JsonResponse
    {
        $user = $this->utilisateurConnecte();
        if (! $user) {
            return $this->reponseNonAutorise();
        }

        $messages = Message::where(function ($q) use ($user, $interlocuteurId) {
            $q->where('expediteur_id', $user->id)
                ->where('destinataire_id', $interlocuteurId);
        })
            ->orWhere(function ($q) use ($user, $interlocuteurId) {
                $q->where('expediteur_id', $interlocuteurId)
                    ->where('destinataire_id', $user->id);
            })
            ->with(['expediteur:id,nom', 'destinataire:id,nom'])
            ->orderBy('created_at', 'asc')
            ->get();

        Message::where('expediteur_id', $interlocuteurId)
            ->where('destinataire_id', $user->id)
            ->where('lu', false)
            ->update(['lu' => true]);

        return response()->json(['messages' => $messages]);
    }

    /**
     * Envoie un message à un autre utilisateur.
     *
     * Étapes :
     * - Vérifie le JWT.
     * - Valide destinataire_id (doit exister) et contenu (max 2000 caractères).
     * - Détermine si c'est le premier échange entre les deux (aucun message dans aucun sens).
     * - Crée le message avec lu=false.
     * - Recharge les relations expediteur/destinataire (juste id et nom) pour la réponse.
     * - Si premier message, déclenche un mail à l'autre utilisateur via NouveauMessageMail.
     *
     * @param  Request  $request  body : destinataire_id, contenu
     * @return JsonResponse  201 avec { message, data }
     */
    #[OA\Post(
        path: '/api/messages',
        tags: ['Messages'],
        summary: 'Envoyer un message',
        security: [['bearerAuth' => []]],
        requestBody: new OA\RequestBody(
            required: true,
            content: new OA\JsonContent(
                required: ['destinataire_id', 'contenu'],
                properties: [
                    new OA\Property(property: 'destinataire_id', type: 'integer'),
                    new OA\Property(property: 'contenu', type: 'string', maxLength: 2000),
                ]
            )
        ),
        responses: [
            new OA\Response(response: 201, description: 'Message créé'),
            new OA\Response(response: 401, description: 'Non authentifié'),
            new OA\Response(response: 422, description: 'Données invalides'),
        ]
    )]
    public function envoyer(Request $request): JsonResponse
    {
        $user = $this->utilisateurConnecte();
        if (! $user) {
            return $this->reponseNonAutorise();
        }

        $request->validate([
            'destinataire_id' => 'required|integer|exists:users,id',
            'contenu'         => 'required|string|max:2000',
        ]);

        $destinataireId = $request->input('destinataire_id');
        $contenu        = $request->input('contenu');

        $estPremierMessage = ! Message::where(function ($q) use ($user, $destinataireId) {
            $q->where('expediteur_id', $user->id)
                ->where('destinataire_id', $destinataireId);
        })->orWhere(function ($q) use ($user, $destinataireId) {
            $q->where('expediteur_id', $destinataireId)
                ->where('destinataire_id', $user->id);
        })->exists();

        $message = Message::create([
            'expediteur_id'   => $user->id,
            'destinataire_id' => $destinataireId,
            'contenu'         => $contenu,
            'lu'              => false,
        ]);

        $message->load('expediteur:id,nom', 'destinataire:id,nom');

        if ($estPremierMessage) {
            $destinataire = User::find($destinataireId);
            Mail::to($destinataire->email)
                ->send(new NouveauMessageMail($user->nom, $destinataire->nom, $contenu));
        }

        return response()->json(['message' => 'Message envoyé', 'data' => $message], 201);
    }

    /**
     * Renvoie les utilisateurs avec qui l'utilisateur connecté a le droit d'échanger.
     *
     * Règle métier :
     * - Si formateur : tous les apprenants inscrits à au moins une de ses formations.
     * - Si apprenant : tous les formateurs des formations auxquelles il est inscrit.
     *
     * Évite de pouvoir messager n'importe qui (anti-spam, anti-harcèlement).
     *
     * @return JsonResponse  { interlocuteurs: array }
     */
    #[OA\Get(
        path: '/api/messages/interlocuteurs',
        tags: ['Messages'],
        summary: 'Liste des utilisateurs autorisés à recevoir un message',
        security: [['bearerAuth' => []]],
        responses: [
            new OA\Response(response: 200, description: 'Liste filtrée'),
            new OA\Response(response: 401, description: 'Non authentifié'),
        ]
    )]
    public function interlocuteurs(): JsonResponse
    {
        $user = $this->utilisateurConnecte();
        if (! $user) {
            return $this->reponseNonAutorise();
        }

        if ($user->role === 'formateur') {
            $utilisateurs = User::whereHas('inscriptions', function ($q) use ($user) {
                $q->whereHas('formation', function ($q2) use ($user) {
                    $q2->where('formateur_id', $user->id);
                });
            })->select('id', 'nom', 'email', 'role')->get();
        } else {
            $utilisateurs = User::where('role', 'formateur')
                ->whereHas('formations', function ($q) use ($user) {
                    $q->whereHas('inscriptions', function ($q2) use ($user) {
                        $q2->where('utilisateur_id', $user->id);
                    });
                })->select('id', 'nom', 'email', 'role')->get();
        }

        return response()->json(['interlocuteurs' => $utilisateurs]);
    }

    // Helpers prives

    /**
     * Lit le token JWT et renvoie le user, ou null si le token est cassé.
     * Variante minimaliste de authentifierUtilisateur() pour les endpoints
     * de messagerie (qui veulent juste savoir s'il y a un user connecté).
     *
     * @return \App\Models\User|null
     */
    private function utilisateurConnecte()
    {
        try {
            return JWTAuth::parseToken()->authenticate();
        } catch (JWTException $e) {
            return null;
        }
    }

    /**
     * Réponse 401 standardisée pour la messagerie.
     */
    private function reponseNonAutorise(): JsonResponse
    {
        return response()->json(['message' => 'Non autorisé'], 401);
    }
}
