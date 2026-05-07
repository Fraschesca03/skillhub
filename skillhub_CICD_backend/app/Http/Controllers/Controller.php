<?php

namespace App\Http\Controllers;

use App\Models\User;
use Illuminate\Http\JsonResponse;
use Tymon\JWTAuth\Exceptions\JWTException;
use Tymon\JWTAuth\Facades\JWTAuth;

/**
 * Classe parente des contrôleurs. Centralise le helper d'auth JWT.
 *
 * @author MU202612
 */
abstract class Controller
{
    protected const MSG_TOKEN_INVALIDE = 'Token invalide ou absent';

    protected const MSG_USER_NON_TROUVE = 'Utilisateur non trouvé';

    /**
     * Lit le token JWT du header Authorization et renvoie l'utilisateur correspondant.
     *
     * - Si le token est absent ou expiré : renvoie [null, JsonResponse 401].
     * - Si le token est valide mais que l'utilisateur n'existe plus : [null, JsonResponse 404].
     * - Si tout va bien : [User, null].
     *
     * Utilisé partout où on a besoin de connaître l'appelant avant d'agir.
     *
     * @return array{0: User|null, 1: JsonResponse|null} couple [user, erreur]
     */
    protected function authentifierUtilisateur(): array
    {
        try {
            $user = JWTAuth::parseToken()->authenticate();

            if (! $user) {
                return [null, response()->json(['message' => self::MSG_USER_NON_TROUVE], 404)];
            }

            return [$user, null];
        } catch (JWTException $e) {
            return [null, response()->json(['message' => self::MSG_TOKEN_INVALIDE], 401)];
        }
    }
}
