<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use OpenApi\Attributes as OA;

/**
 * Formation proposée par un formateur. Contient des modules et reçoit des inscriptions.
 *
 * @property int $id
 * @property string $titre
 * @property string $description
 * @property string $categorie developpement_web | data | design | marketing | devops | autre
 * @property string $niveau debutant | intermediaire | avance
 * @property float $prix
 * @property int $duree_heures
 * @property int $nombre_de_vues
 * @property int $formateur_id
 *
 * @author MU202612
 */
#[OA\Schema(
    schema: 'Formation',
    type: 'object',
    title: 'Formation',
    properties: [
        new OA\Property(property: 'id', type: 'integer'),
        new OA\Property(property: 'titre', type: 'string'),
        new OA\Property(property: 'description', type: 'string'),
        new OA\Property(property: 'categorie', type: 'string', enum: ['developpement_web', 'data', 'design', 'marketing', 'devops', 'autre']),
        new OA\Property(property: 'niveau', type: 'string', enum: ['debutant', 'intermediaire', 'avance']),
        new OA\Property(property: 'prix', type: 'number'),
        new OA\Property(property: 'duree_heures', type: 'integer'),
        new OA\Property(property: 'nombre_de_vues', type: 'integer'),
        new OA\Property(property: 'formateur_id', type: 'integer'),
    ]
)]
class Formation extends Model
{
    /**
     * Champs autorisés au mass-assignment.
     *
     * @var array<int, string>
     */
    protected $fillable = [
        'titre',
        'description',
        'categorie',
        'niveau',
        'prix',
        'duree_heures',
        'nombre_de_vues',
        'formateur_id',
    ];

    /**
     * Relation : le User formateur qui a créé cette formation.
     */
    public function formateur()
    {
        return $this->belongsTo(User::class, 'formateur_id');
    }

    /**
     * Relation : modules de la formation, déjà triés par champ "ordre".
     */
    public function modules()
    {
        return $this->hasMany(Module::class, 'formation_id')->orderBy('ordre');
    }

    /**
     * Relation : inscriptions des apprenants à cette formation.
     */
    public function inscriptions()
    {
        return $this->hasMany(Inscription::class, 'formation_id');
    }

    /**
     * Relation : enregistrements de vues (un par couple user/IP) pour le compteur.
     */
    public function vues()
    {
        return $this->hasMany(FormationVue::class, 'formation_id');
    }
}
