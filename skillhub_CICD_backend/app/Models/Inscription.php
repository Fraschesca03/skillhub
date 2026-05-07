<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use OpenApi\Attributes as OA;

/**
 * Inscription d'un apprenant à une formation. Porte le pourcentage de progression.
 *
 * @property int $id
 * @property int $utilisateur_id
 * @property int $formation_id
 * @property int $progression     entre 0 et 100, recalculé à chaque module terminé
 *
 * @author MU202612
 */
#[OA\Schema(
    schema: 'Inscription',
    type: 'object',
    title: 'Inscription',
    properties: [
        new OA\Property(property: 'id', type: 'integer'),
        new OA\Property(property: 'utilisateur_id', type: 'integer'),
        new OA\Property(property: 'formation_id', type: 'integer'),
        new OA\Property(property: 'progression', type: 'integer', minimum: 0, maximum: 100),
    ]
)]
class Inscription extends Model
{
    /**
     * Champs autorisés au mass-assignment.
     *
     * @var array<int, string>
     */
    protected $fillable = [
        'utilisateur_id',
        'formation_id',
        'progression',
    ];

    /**
     * Relation : l'apprenant qui s'est inscrit.
     */
    public function utilisateur()
    {
        return $this->belongsTo(User::class, 'utilisateur_id');
    }

    /**
     * Relation : la formation suivie.
     */
    public function formation()
    {
        return $this->belongsTo(Formation::class);
    }
}
