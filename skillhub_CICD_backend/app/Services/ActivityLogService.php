<?php

namespace App\Services;

use App\Models\ActivityLog;
use Illuminate\Support\Facades\Log;
use Throwable;

/**
 * Couche fine au-dessus du modèle ActivityLog pour écrire les événements
 * dans MongoDB. Évite que les contrôleurs aient à connaître le format des logs
 * ou les noms d'event.
 *
 * Toutes les méthodes sont statiques : on les appelle directement
 * sans instancier le service.
 *
 * Les écritures sont best-effort : si MongoDB est indisponible (panne, env de
 * test sans Mongo, etc.), on log un warning et on continue. L'idée est
 * qu'un échec d'analytics ne doit jamais casser une requête utilisateur.
 *
 * @author MU202612
 */
class ActivityLogService
{
    /**
     * Tente une écriture dans la collection activity_logs sans jamais lever d'exception.
     * En cas d'échec, on log un warning et on swallow l'erreur.
     *
     * @param  array<string, mixed>  $payload
     */
    private static function safeCreate(array $payload): void
    {
        try {
            ActivityLog::create($payload);
        } catch (Throwable $e) {
            Log::warning('ActivityLog skipped (Mongo indispo)', [
                'event' => $payload['event'] ?? null,
                'error' => $e->getMessage(),
            ]);
        }
    }

    /**
     * Enregistre une consultation de formation (event "course_view").
     *
     * Le user_id peut être null si la formation a été consultée par un visiteur
     * non connecté.
     */
    public static function consultationFormation(int $formationId, ?int $userId): void
    {
        self::safeCreate([
            'event' => 'course_view',
            'user_id' => $userId,
            'formation_id' => $formationId,
            'timestamp' => now()->toISOString(),
        ]);
    }

    /**
     * Enregistre une inscription d'apprenant à une formation (event "course_enrollment").
     */
    public static function inscriptionFormation(int $formationId, int $userId): void
    {
        self::safeCreate([
            'event' => 'course_enrollment',
            'user_id' => $userId,
            'formation_id' => $formationId,
            'timestamp' => now()->toISOString(),
        ]);
    }

    /**
     * Enregistre la création d'une formation par un formateur (event "course_creation").
     *
     * @param  int  $userId  l'auteur (le formateur)
     */
    public static function creationFormation(int $formationId, int $userId): void
    {
        self::safeCreate([
            'event' => 'course_creation',
            'user_id' => $userId,
            'formation_id' => $formationId,
            'timestamp' => now()->toISOString(),
        ]);
    }

    /**
     * Enregistre une modification de formation (event "course_update").
     *
     * Stocke les valeurs avant et après pour pouvoir reconstruire un diff.
     * Pratique pour audit / dashboard admin.
     *
     * @param  array<string, mixed>  $oldValues
     * @param  array<string, mixed>  $newValues
     */
    public static function modificationFormation(int $formationId, int $userId, array $oldValues, array $newValues): void
    {
        self::safeCreate([
            'event' => 'course_update',
            'user_id' => $userId,
            'formation_id' => $formationId,
            'old_values' => $oldValues,
            'new_values' => $newValues,
            'timestamp' => now()->toISOString(),
        ]);
    }

    /**
     * Enregistre la suppression d'une formation (event "course_deletion").
     * À appeler avant le delete réel pour ne pas perdre l'id.
     */
    public static function suppressionFormation(int $formationId, int $userId): void
    {
        self::safeCreate([
            'event' => 'course_deletion',
            'user_id' => $userId,
            'formation_id' => $formationId,
            'timestamp' => now()->toISOString(),
        ]);
    }
}
