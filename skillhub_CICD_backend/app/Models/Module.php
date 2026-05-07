<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use OpenApi\Attributes as OA;

/**
 * Module d'une formation. L'apprenant valide ses modules un par un, ce qui fait
 * progresser son inscription en pourcentage.
 *
 * @property int    $id
 * @property string $titre
 * @property string $contenu
 * @property int    $ordre         position du module dans la formation (1, 2, 3, …)
 * @property int    $formation_id
 *
 * @author MU202612
 */
#[OA\Schema(
    schema: 'Module',
    type: 'object',
    title: 'Module',
    properties: [
        new OA\Property(property: 'id', type: 'integer'),
        new OA\Property(property: 'titre', type: 'string'),
        new OA\Property(property: 'contenu', type: 'string'),
        new OA\Property(property: 'ordre', type: 'integer', minimum: 1),
        new OA\Property(property: 'formation_id', type: 'integer'),
    ]
)]
class Module extends Model
{
    /**
     * Champs autorisés au mass-assignment.
     *
     * @var array<int, string>
     */
    protected $fillable = [
        'titre',
        'contenu',
        'ordre',
        'formation_id',
    ];

    /**
     * Relation : la formation à laquelle ce module appartient.
     */
    public function formation()
    {
        return $this->belongsTo(Formation::class, 'formation_id');
    }

    /**
     * Relation N-N vers User via la table pivot module_user.
     * Le pivot a une colonne "termine" et les timestamps.
     * Sert à savoir quels utilisateurs ont validé ce module.
     */
    public function utilisateurs()
    {
        return $this->belongsToMany(User::class, 'module_user', 'module_id', 'utilisateur_id')
            ->withPivot('termine')
            ->withTimestamps();
    }
}
