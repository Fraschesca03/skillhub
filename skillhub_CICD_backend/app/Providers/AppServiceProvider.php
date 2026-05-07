<?php

namespace App\Providers;

use Illuminate\Support\Facades\Schema;
use Illuminate\Support\ServiceProvider;

/**
 * Service provider de bootstrap de l'application.
 *
 * @author MU202612
 */
class AppServiceProvider extends ServiceProvider
{
    /**
     * Enregistrement des bindings dans le container. Rien à faire ici pour l'instant.
     */
    public function register(): void
    {
        //
    }

    /**
     * Code exécuté au démarrage, après que tous les providers sont enregistrés.
     *
     * Limite la longueur par défaut des colonnes string à 191 caractères pour
     * supporter les anciennes versions de MySQL/MariaDB qui plafonnent les index
     * utf8mb4 à 191 caractères.
     */
    public function boot(): void
    {
        Schema::defaultStringLength(191);
    }
}
