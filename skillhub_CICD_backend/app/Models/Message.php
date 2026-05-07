<?php

namespace App\Models;

use MongoDB\Laravel\Eloquent\Model;
use OpenApi\Attributes as OA;

/**
 * Message de la messagerie. Stocké dans MongoDB (collection "messages") pour
 * absorber le volume sans gonfler la base MySQL.
 *
 * Les ids expediteur_id et destinataire_id référencent des lignes de la table
 * MySQL users — il n'y a pas de contrainte de clé étrangère côté MongoDB,
 * c'est l'application qui garantit la cohérence.
 *
 * @property string $_id id Mongo (ObjectId)
 * @property int $expediteur_id
 * @property int $destinataire_id
 * @property string $contenu
 * @property bool $lu passe à true quand le destinataire ouvre la conversation
 *
 * @author MU202612
 */
#[OA\Schema(
    schema: 'Message',
    type: 'object',
    title: 'Message',
    properties: [
        new OA\Property(property: '_id', type: 'string'),
        new OA\Property(property: 'expediteur_id', type: 'integer'),
        new OA\Property(property: 'destinataire_id', type: 'integer'),
        new OA\Property(property: 'contenu', type: 'string', maxLength: 2000),
        new OA\Property(property: 'lu', type: 'boolean'),
    ]
)]
class Message extends Model
{
    /**
     * Nom de la connexion DB définie dans config/database.php.
     */
    protected $connection = 'mongodb';

    /**
     * Nom de la collection MongoDB.
     */
    protected $collection = 'messages';

    /**
     * Champs autorisés au mass-assignment.
     *
     * @var array<int, string>
     */
    protected $fillable = [
        'expediteur_id',
        'destinataire_id',
        'contenu',
        'lu',
    ];

    /**
     * Casts pour forcer les types à la lecture/écriture (MongoDB est typé mou).
     *
     * @var array<string, string>
     */
    protected $casts = [
        'lu' => 'boolean',
        'expediteur_id' => 'integer',
        'destinataire_id' => 'integer',
    ];

    /**
     * Relation : l'expéditeur (côté MySQL, modèle User).
     */
    public function expediteur()
    {
        return $this->belongsTo(User::class, 'expediteur_id');
    }

    /**
     * Relation : le destinataire (côté MySQL, modèle User).
     */
    public function destinataire()
    {
        return $this->belongsTo(User::class, 'destinataire_id');
    }
}
