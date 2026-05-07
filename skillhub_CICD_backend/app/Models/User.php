<?php

namespace App\Models;

use Illuminate\Foundation\Auth\User as Authenticatable;
use Illuminate\Notifications\Notifiable;
use Tymon\JWTAuth\Contracts\JWTSubject;
use OpenApi\Attributes as OA;

/**
 * Utilisateur de la plateforme. Peut être apprenant ou formateur.
 *
 * Implémente JWTSubject pour que tymon/jwt-auth sache générer un token à partir d'un User.
 *
 * @property int         $id
 * @property string      $nom
 * @property string      $email
 * @property string      $password        hashé par le cast Eloquent
 * @property string      $role            "apprenant" ou "formateur"
 * @property string|null $photo_profil    chemin relatif vers /images/profils
 *
 * @author MU202612
 */
#[OA\Schema(
    schema: 'User',
    type: 'object',
    title: 'User',
    properties: [
        new OA\Property(property: 'id', type: 'integer', example: 1),
        new OA\Property(property: 'nom', type: 'string', example: 'Alice Dupont'),
        new OA\Property(property: 'email', type: 'string', format: 'email'),
        new OA\Property(property: 'role', type: 'string', enum: ['apprenant', 'formateur']),
        new OA\Property(property: 'photo_profil', type: 'string', nullable: true),
    ]
)]
class User extends Authenticatable implements JWTSubject
{
    use Notifiable;

    /**
     * Champs autorisés au mass-assignment via create()/update().
     *
     * @var array<int, string>
     */
    protected $fillable = [
        'nom',
        'email',
        'password',
        'role',
        'photo_profil',
    ];

    /**
     * Champs jamais sérialisés en JSON (ne doivent pas fuir vers le client).
     *
     * @var array<int, string>
     */
    protected $hidden = [
        'password',
        'remember_token',
    ];

    /**
     * Casts Eloquent. Le cast "hashed" hashe automatiquement le password
     * dès qu'on l'assigne, donc pas besoin d'appeler bcrypt() à la main.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'password' => 'hashed',
        ];
    }

    /**
     * Identifiant utilisé par tymon/jwt-auth comme claim "sub" du JWT.
     * Renvoie la clé primaire du modèle (généralement id).
     *
     * @return mixed
     */
    public function getJWTIdentifier()
    {
        return $this->getKey();
    }

    /**
     * Claims supplémentaires à insérer dans le JWT. Ici aucun, le payload reste minimal.
     *
     * @return array<string, mixed>
     */
    public function getJWTCustomClaims(): array
    {
        return [];
    }

    /**
     * Relation : formations dont cet utilisateur est le formateur (côté "1" de 1-N).
     * Utile depuis le dashboard formateur.
     */
    public function formations()
    {
        return $this->hasMany(Formation::class, 'formateur_id');
    }

    /**
     * Relation : inscriptions de l'utilisateur (apprenant) aux formations.
     */
    public function inscriptions()
    {
        return $this->hasMany(Inscription::class, 'utilisateur_id');
    }

    /**
     * Relation N-N vers Module avec table pivot module_user.
     * Le pivot a une colonne "termine" et les timestamps.
     * Sert à savoir quels modules l'apprenant a déjà validés.
     */
    public function modulesTermines()
    {
        return $this->belongsToMany(Module::class, 'module_user', 'utilisateur_id', 'module_id')
            ->withPivot('termine')
            ->withTimestamps();
    }

    /**
     * Relation : messages envoyés par l'utilisateur.
     */
    public function messagesEnvoyes()
    {
        return $this->hasMany(Message::class, 'expediteur_id');
    }

    /**
     * Relation : messages reçus par l'utilisateur.
     */
    public function messagesRecus()
    {
        return $this->hasMany(Message::class, 'destinataire_id');
    }
}
