<?php

namespace App\Mail;

use Illuminate\Bus\Queueable;
use Illuminate\Mail\Mailable;
use Illuminate\Queue\SerializesModels;

/**
 * Mail envoyé au destinataire au tout premier message d'une conversation.
 *
 * Pas envoyé sur les messages suivants pour ne pas spammer la boîte mail :
 * c'est le rôle de l'app web de notifier les messages suivants.
 *
 * Les propriétés publiques sont automatiquement disponibles dans la vue Blade
 * (resources/views/emails/nouveau_message.blade.php).
 *
 * @author MU202612
 */
class NouveauMessageMail extends Mailable
{
    use Queueable, SerializesModels;

    /** Nom de l'expéditeur, affiché dans le sujet et le corps du mail. */
    public string $expediteur;

    /** Nom du destinataire, utilisé pour personnaliser la formule d'appel. */
    public string $destinataire;

    /** Contenu du premier message envoyé, repris dans le mail. */
    public string $contenu;

    /** URL de la plateforme, pour le bouton "Voir le message". */
    public string $lienPlateforme;

    /**
     * Construit le mail avec les infos d'expéditeur et destinataire.
     * Récupère l'URL de la plateforme depuis config('app.url').
     *
     * @param  string  $expediteur
     * @param  string  $destinataire
     * @param  string  $contenu
     */
    public function __construct(string $expediteur, string $destinataire, string $contenu)
    {
        $this->expediteur     = $expediteur;
        $this->destinataire   = $destinataire;
        $this->contenu        = $contenu;
        $this->lienPlateforme = config('app.url');
    }

    /**
     * Définit le sujet et la vue Blade utilisée pour rendre le mail.
     *
     * @return static
     */
    public function build(): static
    {
        return $this->subject('SkillHub — Nouveau message de ' . $this->expediteur)
                    ->view('emails.nouveau_message');
    }
}
