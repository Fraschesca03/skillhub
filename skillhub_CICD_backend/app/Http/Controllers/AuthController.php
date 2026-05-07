<?php

namespace App\Http\Controllers;

use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Tymon\JWTAuth\Facades\JWTAuth;
use Tymon\JWTAuth\Exceptions\JWTException;
use OpenApi\Attributes as OA;

/**
 * Endpoints d'inscription, de connexion, de profil et de logout.
 *
 * @author MU202612
 */
class AuthController extends Controller
{
    /**
     * Crée un nouveau compte et renvoie un JWT directement utilisable.
     *
     * Étapes :
     * - Valide les champs requis (nom, email, password, role).
     * - Crée le User en base (le password est hashé par le cast Eloquent).
     * - Émet un JWT via JWTAuth::fromUser pour que le front enchaîne sans re-login.
     *
     * Utilise le modèle User et la facade JWTAuth.
     * Renvoie un JSON { message, user, token } avec status 201.
     *
     * @param  Request  $request  body : nom, email, password, role
     * @return JsonResponse
     */
    #[OA\Post(
        path: '/api/register',
        tags: ['Auth'],
        summary: "Inscrire un utilisateur",
        requestBody: new OA\RequestBody(
            required: true,
            content: new OA\JsonContent(
                required: ['nom', 'email', 'password', 'role'],
                properties: [
                    new OA\Property(property: 'nom', type: 'string', example: 'Alice Dupont'),
                    new OA\Property(property: 'email', type: 'string', format: 'email', example: 'alice@gmail.com'),
                    new OA\Property(property: 'password', type: 'string', format: 'password', example: 'MotDePasse123!'),
                    new OA\Property(property: 'role', type: 'string', enum: ['apprenant', 'formateur'], example: 'apprenant'),
                ]
            )
        ),
        responses: [
            new OA\Response(response: 201, description: 'Utilisateur créé'),
            new OA\Response(response: 422, description: 'Champ invalide ou email déjà utilisé'),
        ]
    )]
    public function register(Request $request): JsonResponse
    {
        $request->validate([
            'nom'      => 'required|string|max:255',
            'email'    => 'required|email|unique:users,email',
            'password' => 'required|string|min:6',
            'role'     => 'required|in:apprenant,formateur',
        ]);

        $user  = User::create([
            'nom'      => $request->input('nom'),
            'email'    => $request->input('email'),
            'password' => $request->input('password'),
            'role'     => $request->input('role'),
        ]);
        $token = JWTAuth::fromUser($user);

        return response()->json([
            'message' => 'Utilisateur créé avec succès',
            'user'    => $user,
            'token'   => $token,
        ], 201);
    }

    /**
     * Vérifie email + password et renvoie un JWT en cas de succès.
     *
     * Étapes :
     * - Valide email et password.
     * - Tente JWTAuth::attempt avec ces credentials.
     * - Si l'attempt rate, renvoie 401 sans détail (on ne dit pas ce qui a foiré).
     *
     * Utilise la facade JWTAuth et auth()->user() pour récupérer le profil.
     * Renvoie { message, token, user } ou 401 en cas d'échec.
     *
     * @param  Request  $request  body : email, password
     * @return JsonResponse
     */
    #[OA\Post(
        path: '/api/login',
        tags: ['Auth'],
        summary: 'Se connecter et obtenir un JWT',
        requestBody: new OA\RequestBody(
            required: true,
            content: new OA\JsonContent(
                required: ['email', 'password'],
                properties: [
                    new OA\Property(property: 'email', type: 'string', format: 'email'),
                    new OA\Property(property: 'password', type: 'string', format: 'password'),
                ]
            )
        ),
        responses: [
            new OA\Response(response: 200, description: 'Connexion réussie'),
            new OA\Response(response: 401, description: 'Email ou mot de passe incorrect'),
        ]
    )]
    public function login(Request $request): JsonResponse
    {
        $request->validate([
            'email'    => 'required|email',
            'password' => 'required|string',
        ]);

        $credentials = [
            'email'    => $request->input('email'),
            'password' => $request->input('password'),
        ];

        $token = JWTAuth::attempt($credentials);

        if (! $token) {
            return response()->json(['message' => 'Email ou mot de passe incorrect'], 401);
        }

        return response()->json([
            'message' => 'Connexion réussie',
            'token'   => $token,
            'user'    => auth()->user(),
        ]);
    }

    /**
     * Renvoie le profil de l'utilisateur identifié par le token.
     *
     * Étapes :
     * - Lit le token via authentifierUtilisateur (helper du parent).
     * - Si le token est invalide, renvoie l'erreur déjà préparée.
     * - Sinon renvoie { user }.
     *
     * @return JsonResponse
     */
    #[OA\Get(
        path: '/api/profile',
        tags: ['Auth'],
        summary: "Profil de l'utilisateur connecté",
        security: [['bearerAuth' => []]],
        responses: [
            new OA\Response(response: 200, description: 'Profil renvoyé'),
            new OA\Response(response: 401, description: 'Token invalide ou absent'),
        ]
    )]
    public function profile(): JsonResponse
    {
        [$user, $erreur] = $this->authentifierUtilisateur();
        if ($erreur) {
            return $erreur;
        }

        return response()->json(['user' => $user]);
    }

    /**
     * Invalide le token JWT côté serveur.
     *
     * Le token est ajouté à la blacklist, donc même si le client le garde
     * il ne pourra plus l'utiliser.
     *
     * @return JsonResponse 200 si OK, 401 si le token était déjà invalide
     */
    #[OA\Post(
        path: '/api/logout',
        tags: ['Auth'],
        summary: 'Invalider le token courant',
        security: [['bearerAuth' => []]],
        responses: [
            new OA\Response(response: 200, description: 'Déconnexion réussie'),
            new OA\Response(response: 401, description: 'Token invalide'),
        ]
    )]
    public function logout(): JsonResponse
    {
        try {
            JWTAuth::parseToken()->invalidate();

            return response()->json(['message' => 'Déconnexion réussie']);

        } catch (JWTException $e) {
            return response()->json(['message' => self::MSG_TOKEN_INVALIDE], 401);
        }
    }

    /**
     * Remplace la photo de profil de l'utilisateur connecté.
     *
     * Étapes :
     * - Vérifie le JWT.
     * - Valide le fichier (image, max 2 Mo, formats jpeg/png/jpg/gif).
     * - Supprime l'ancienne photo si elle existe sur disque.
     * - Déplace le nouveau fichier vers public/images/profils avec un nom unique
     *   (profil_{userId}_{timestamp}.{ext}).
     * - Met à jour user.photo_profil et persiste.
     *
     * Utilise public_path() pour le stockage et $request->file('photo').
     * Renvoie { message, photo_profil, user }.
     *
     * @param  Request  $request  doit contenir un fichier 'photo'
     * @return JsonResponse
     */
    #[OA\Post(
        path: '/api/profil/photo',
        tags: ['Auth'],
        summary: 'Uploader une photo de profil',
        security: [['bearerAuth' => []]],
        requestBody: new OA\RequestBody(
            required: true,
            content: new OA\MediaType(
                mediaType: 'multipart/form-data',
                schema: new OA\Schema(
                    required: ['photo'],
                    properties: [
                        new OA\Property(property: 'photo', type: 'string', format: 'binary'),
                    ]
                )
            )
        ),
        responses: [
            new OA\Response(response: 200, description: 'Photo mise à jour'),
            new OA\Response(response: 401, description: 'Non authentifié'),
            new OA\Response(response: 422, description: 'Fichier invalide'),
        ]
    )]
    public function uploadPhoto(Request $request): JsonResponse
    {
        [$user, $erreur] = $this->authentifierUtilisateur();
        if ($erreur) {
            return $erreur;
        }

        $request->validate([
            'photo' => 'required|image|mimes:jpeg,png,jpg,gif|max:2048',
        ]);

        // Suppression de l ancienne photo si elle existe
        if ($user->photo_profil && file_exists(public_path($user->photo_profil))) {
            unlink(public_path($user->photo_profil));
        }

        // Sauvegarde de la nouvelle photo
        $fichier    = $request->file('photo');
        $nomFichier = 'profil_' . $user->id . '_' . time() . '.' . $fichier->getClientOriginalExtension();
        $fichier->move(public_path('images/profils'), $nomFichier);

        $user->photo_profil = '/images/profils/' . $nomFichier;
        $user->save();

        return response()->json([
            'message'      => 'Photo mise à jour avec succès',
            'photo_profil' => $user->photo_profil,
            'user'         => $user,
        ]);
    }
}
