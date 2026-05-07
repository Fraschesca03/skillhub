<?php

namespace App\OpenApi;

use OpenApi\Attributes as OA;

/**
 * Conteneur des annotations globales de la doc OpenAPI : titre, version,
 * serveur, schéma de sécurité Bearer JWT et tags.
 *
 * Cette classe ne contient pas de logique : elle existe uniquement pour porter
 * les annotations qui doivent être déclarées une seule fois dans le projet.
 *
 * @author MU202612
 */
#[OA\Info(
    title: 'SkillHub API',
    version: '1.0.0',
    description: 'API REST de la plateforme SkillHub : authentification, formations, modules, inscriptions et messagerie.',
    contact: new OA\Contact(name: 'MU202612')
)]
#[OA\Server(url: 'http://localhost:8000', description: 'Serveur local')]
#[OA\SecurityScheme(
    securityScheme: 'bearerAuth',
    type: 'http',
    scheme: 'bearer',
    bearerFormat: 'JWT',
    description: "Token JWT renvoyé par /api/login. Préfixer avec 'Bearer '."
)]
#[OA\Tag(name: 'Auth', description: 'Inscription, connexion, profil')]
#[OA\Tag(name: 'Formations', description: 'CRUD des formations')]
#[OA\Tag(name: 'Modules', description: "Modules d'une formation")]
#[OA\Tag(name: 'Inscriptions', description: "Inscription d'un apprenant à une formation")]
#[OA\Tag(name: 'Messages', description: 'Messagerie entre utilisateurs')]
class Documentation
{
}
