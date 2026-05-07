<?php

use App\Http\Controllers\AuthController;
use App\Http\Controllers\FormationController;
use App\Http\Controllers\InscriptionController;
use App\Http\Controllers\MessageController;
use App\Http\Controllers\ModuleController;
use Illuminate\Support\Facades\Route;

//  Test API
Route::get('/test', function () {
    return response()->json(['message' => 'API SkillHub OK']);
});

//  Authentification
Route::post('/register', [AuthController::class, 'register']);
Route::post('/login',    [AuthController::class, 'login']);
Route::get('/profile',   [AuthController::class, 'profile']);
Route::post('/logout',   [AuthController::class, 'logout']);

// Route upload photo profil — ajoutee car methode existante sans route
Route::post('/profil/photo', [AuthController::class, 'uploadPhoto']);

//  Formations
// Routes protegees par le middleware SSO (appel REST au microservice Spring Boot) ---
Route::middleware('auth.sso')->group(function () {
    // Route specialement pour la demo SSO
    Route::get('/sso/profile', function (\Illuminate\Http\Request $request) {
        $user = $request->sso_user;
        return response()->json([
            'message'          => 'Authentification SSO reussie via microservice Spring Boot',
            'user'             => $user,
            'middleware'       => 'auth.sso',
            'auth_service_url' => env('AUTH_SERVICE_URL', 'http://skillhub-auth:8080'),
        ]);
    });

    // Protege egalement la liste des formations
    Route::get('/formations-protected', [\App\Http\Controllers\FormationController::class, 'index']);
});


Route::get('/formations',        [FormationController::class, 'index']);
Route::get('/formations/{id}',   [FormationController::class, 'show']);
Route::post('/formations',       [FormationController::class, 'store']);
Route::put('/formations/{id}',   [FormationController::class, 'update']);
Route::delete('/formations/{id}', [FormationController::class, 'destroy']);


//  Modules
Route::get('/formations/{id}/modules',  [ModuleController::class, 'index']);
Route::post('/formations/{id}/modules', [ModuleController::class, 'store']);
Route::put('/modules/{id}',             [ModuleController::class, 'update']);
Route::delete('/modules/{id}',          [ModuleController::class, 'destroy']);
Route::post('/modules/{id}/terminer',   [ModuleController::class, 'terminer']);

//  Inscriptions
Route::post('/formations/{id}/inscription',   [InscriptionController::class, 'store']);
Route::delete('/formations/{id}/inscription', [InscriptionController::class, 'destroy']);
Route::get('/apprenant/formations',           [InscriptionController::class, 'mesFormations']);

//  Messagerie
Route::get('/messages/non-lus',                       [MessageController::class, 'nonLus']);
Route::get('/messages/conversations',                 [MessageController::class, 'conversations']);
Route::get('/messages/conversation/{interlocuteurId}', [MessageController::class, 'messagerie']);
Route::post('/messages/envoyer',                      [MessageController::class, 'envoyer']);
Route::get('/messages/interlocuteurs',                [MessageController::class, 'interlocuteurs']);

//  Preflight CORS
Route::options('/{any}', function () {
    return response('', 200);
})->where('any', '.*');
