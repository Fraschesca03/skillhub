<?php

namespace App\Models;

use MongoDB\Laravel\Eloquent\Model as MongoModel;

/**
 * Trace des événements métier importants : création/modification/suppression de
 * formations, inscriptions, consultations… Stocké dans MongoDB.
 *
 * Pour les événements de modification, on garde old_values et new_values
 * afin de pouvoir reconstruire un diff côté admin.
 *
 * @property string $_id
 * @property string $event ex: "formation.modification", "formation.consultation"
 * @property int|null $user_id null si action anonyme
 * @property int|null $formation_id
 * @property int|null $module_id
 * @property array|null $old_values
 * @property array|null $new_values
 * @property string $timestamp
 *
 * @author MU202612
 */
class ActivityLog extends MongoModel
{
    /**
     * Nom de la connexion DB.
     */
    protected $connection = 'mongodb';

    /**
     * Nom de la collection MongoDB.
     */
    protected $collection = 'activity_logs';

    /**
     * Champs autorisés au mass-assignment.
     *
     * @var array<int, string>
     */
    protected $fillable = [
        'event',
        'user_id',
        'formation_id',
        'module_id',
        'old_values',
        'new_values',
        'timestamp',
    ];
}
