<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;

/**
 * Ajoute les headers CORS aux réponses pour autoriser l'appel depuis le front Vite
 * (http://localhost:5173) et gère les requêtes preflight OPTIONS.
 *
 * Sans ce middleware, le navigateur bloque les appels cross-origin.
 *
 * @author MU202612
 */
class CorsMiddleware
{
    /**
     * Point d'entrée du middleware.
     *
     * Étapes :
     * - Si la requête est un preflight OPTIONS, on répond directement 200 avec les
     *   headers CORS et un Max-Age de 24h pour limiter les preflights successifs.
     * - Sinon on laisse le pipeline traiter la requête, puis on injecte les headers
     *   CORS sur la réponse avant de la renvoyer.
     *
     * @param  Closure  $next  suite du pipeline middleware
     * @return mixed la réponse HTTP avec headers CORS
     */
    public function handle(Request $request, Closure $next)
    {
        // Repondre directement aux requetes preflight OPTIONS
        if ($request->isMethod('OPTIONS')) {
            return response('', 200)
                ->header('Access-Control-Allow-Origin', 'http://localhost:5173')
                ->header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS')
                ->header('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Requested-With')
                ->header('Access-Control-Max-Age', '86400');
        }

        // Traiter la requete normalement puis ajouter les en-tetes CORS
        $response = $next($request);

        $response->headers->set('Access-Control-Allow-Origin', 'http://localhost:5173');
        $response->headers->set('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
        $response->headers->set('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Requested-With');

        return $response;
    }
}
