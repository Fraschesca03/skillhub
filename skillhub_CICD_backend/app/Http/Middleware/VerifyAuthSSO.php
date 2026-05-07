<?php

namespace App\Http\Middleware;

use App\Models\User;
use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

/**
 * Middleware SSO : délègue la vérification du JWT au microservice Spring Boot
 * d'authentification au lieu de la faire en local.
 *
 * Étapes :
 *  - Lit le header Authorization: Bearer <jwt>.
 *  - Appelle GET /api/me sur le service auth en passant le JWT.
 *  - Si le service répond 200, on charge le User Laravel correspondant
 *    et on l'injecte dans la requête pour les contrôleurs.
 *  - Sinon on renvoie 401, 503 (service injoignable) ou 502 (réponse invalide).
 *
 * Avantage : la validation JWT (signature, expiration) reste centralisée dans le
 * microservice auth ; Laravel ne connaît pas la clé. Inconvénient : un appel
 * réseau supplémentaire par requête protégée.
 *
 * @author MU202612
 */
class VerifyAuthSSO
{
    /**
     * URL du microservice Spring Boot d'authentification.
     * Configurable via .env : AUTH_SERVICE_URL
     */
    private string $authServiceUrl;

    /**
     * Lit l'URL du microservice depuis la variable d'env AUTH_SERVICE_URL,
     * avec une valeur par défaut adaptée au docker-compose interne.
     */
    public function __construct()
    {
        $this->authServiceUrl = env('AUTH_SERVICE_URL', 'http://skillhub-auth:8080');
    }

    /**
     * Pipeline du middleware (cf. docblock de la classe pour le détail des étapes).
     *
     * Effets de bord :
     * - Injecte le User Laravel dans la requête via setUserResolver et merge('sso_user').
     * - Log info en cas de succès, warning en cas de refus, error si service indispo.
     *
     * @param  Closure  $next  suite du pipeline middleware
     */
    public function handle(Request $request, Closure $next): mixed
    {
        // 1. Extraire le JWT du header Authorization
        $authHeader = $request->header('Authorization');

        if (! $authHeader || ! str_starts_with($authHeader, 'Bearer ')) {
            return response()->json([
                'message' => 'Token JWT manquant dans le header Authorization',
            ], 401);
        }

        $jwt = substr($authHeader, 7);

        // 2. Appeler le microservice Spring Boot via REST API
        //    pour valider le JWT et recuperer les infos user
        try {
            $response = Http::withHeaders([
                'Authorization' => 'Bearer '.$jwt,
                'Accept' => 'application/json',
            ])
                ->timeout(5)
                ->get($this->authServiceUrl.'/api/me');
        } catch (\Exception $e) {
            Log::error('SSO : microservice Spring Boot injoignable', [
                'error' => $e->getMessage(),
            ]);

            return response()->json([
                'message' => 'Service d\'authentification indisponible',
            ], 503);
        }

        // 3. Verifier la reponse du microservice
        if (! $response->successful()) {
            Log::warning('SSO : token refuse par Spring Boot', [
                'status' => $response->status(),
            ]);

            return response()->json([
                'message' => 'Token invalide ou expire',
            ], 401);
        }

        // 4. Extraire les donnees user renvoyees par Spring Boot
        $userData = $response->json();

        if (! isset($userData['id']) && ! isset($userData['email'])) {
            return response()->json([
                'message' => 'Reponse invalide du service d\'authentification',
            ], 502);
        }

        // 5. Charger le modele User Laravel (DB partagee)
        $userId = $userData['id'] ?? null;
        $user = $userId ? User::find($userId) : User::where('email', $userData['email'])->first();

        if (! $user) {
            return response()->json([
                'message' => 'Utilisateur non trouve dans la base metier',
            ], 404);
        }

        // 6. Injecter le user dans la requete pour les controllers
        $request->setUserResolver(fn () => $user);
        $request->merge(['sso_user' => $user]);

        // 7. Logger pour la demo
        Log::info('SSO : authentification reussie via Spring Boot', [
            'user_id' => $user->id,
            'email' => $user->email,
            'route' => $request->path(),
        ]);

        return $next($request);
    }
}
