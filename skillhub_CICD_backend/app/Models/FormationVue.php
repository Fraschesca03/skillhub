<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

/**
 * Trace une vue de formation. Sert au comptage unique :
 * une ligne par couple (formation, utilisateur) ou (formation, IP) si l'utilisateur
 * n'est pas connecté. Empêche qu'un même apprenant gonfle artificiellement
 * le compteur en rechargeant la page.
 *
 * @property int         $id
 * @property int         $formation_id
 * @property int|null    $utilisateur_id  null si visiteur anonyme
 * @property string      $ip
 *
 * @author MU202612
 */
class FormationVue extends Model
{
    /**
     * Champs autorisés au mass-assignment.
     *
     * @var array<int, string>
     */
    protected $fillable = [
        'formation_id',
        'utilisateur_id',
        'ip',
    ];

    /**
     * Relation : la formation concernée par cette vue.
     */
    public function formation()
    {
        return $this->belongsTo(Formation::class);
    }

    /**
     * Relation : l'utilisateur qui a vu (peut être null).
     */
    public function utilisateur()
    {
        return $this->belongsTo(User::class, 'utilisateur_id');
    }
}
