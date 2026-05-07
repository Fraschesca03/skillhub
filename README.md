# SkillHub — Plateforme de formations en architecture microservices

SkillHub met en relation des formateurs qui créent des formations et des apprenants qui s'y inscrivent. Pour l'examen, j'ai fait évoluer l'application vers une architecture **microservices** avec un service d'authentification en Spring Boot, communicant avec le backend Laravel via API REST.

Ce README explique comment le projet est construit, comment le lancer, et ce que j'ai modifié par rapport au projet qu'on avait.

---

## 1. Architecture du projet

SkillHub est divisé en deux services indépendants :

- **skillhub-api le dossier skillhub_CICD_backend (Laravel 12 + PHP 8.2)** — toute la logique métier : catalogue de formations, modules, inscriptions, messagerie, profils utilisateurs.
- **skillhub-auth le dossier authentification (Spring Boot 3.5 + Java 17)** — le service d'authentification : inscription, connexion HMAC-SSO, chiffrement AES-GCM des mots de passe, émission et validation de JWT.

Les deux services partagent :

- Une **base MySQL** unique (table `users` commune)
- Un **secret JWT** partagé (HS256) qui permet à Laravel de valider les tokens généré par Spring Boot après la reussite de la conexion
- Un **réseau Docker** commun (`skillhub-network`) pour la communication interne

Laravel continue de gérer les logs d'activité dans **MongoDB**. Nginx sert de reverse proxy devant Laravel.

Le front React communique avec les deux backends : il appelle Spring Boot sur le port 8080 pour tout ce qui concerne l'authentification, et Laravel sur le port 80 pour les fonctionnalités métier.

Schéma simplifié :

\*\*

```
            ┌──────────────────────────────────┐
            │   Front React (Vite, port 5173)  │
            └────────────┬──────────────┬──────┘
                         │              │
         /auth/login     │              │     /formations, /modules...
         /auth/register  │              │
                         ▼              ▼
                ┌────────────────┐   ┌────────────────┐
                │  skillhub-auth │   │  skillhub-api  │
                │  Spring Boot   │   │  Laravel 12    │
                │  port 8080     │   │  port 80       │
                └────────┬───────┘   └───────┬────────┘
                         │                   │
                         └─────────┬─────────┘
                                   │
                  ┌────────────────┴────────────────┐
                  │                                 │
                  ▼                                 ▼
            ┌──────────┐                     ┌──────────┐
            │  MySQL 8 │                     │ MongoDB 6│
            │(partagé) │                     │ (Laravel)│
            └──────────┘                     └──────────┘
```

tout ce qui est identité et à la cryptographie est géré dans Spring Boot, cet ce qui est métier dans Laravel. Chaque service a son propre Dockerfile, son propre pipeline CI/CD, sa propre image sur GHCR. C'est l'indépendance de déploiement qui justifie le choix microservices plutôt qu'un monolithe.

---

## 2. Système d'authentification SSO

### Flux complet

L'authentification suit un protocole HMAC qui garantit que le mot de passe ne quitte jamais le navigateur :

1. a l'inscription, le front envoie le mot de passe **une seule fois** à Spring Boot. Celui-ci le chiffre en **AES-GCM** avec une Master Key jamais sortie du serveur, et stocke le résultat en base.

2. a la connexion, le front ne transmet **pas** le mot de passe :

   - il génère un nonce (UUID v4) et un timestamp
   - il calcule `HMAC-SHA256(password, "email:nonce:timestamp")` encodé en **base64**
   - il envoie `{ email, nonce, timestamp, hmac }` à Spring Boot

3. Spring Boot déchiffre le mot de passe stocké, recalcule le HMAC côté serveur, compare en temps constant avec ce qu'a envoyé le client. Si c'est bon, il émet un **JWT HS256** signé avec le secret partagé `JWT_SECRET`.

4. Le front stocke ce JWT dans `localStorage` et l'utilise dans le header `Authorization: Bearer …` pour tous les appels suivants.

### Intégration avec Laravel

Pour les routes métier Laravel protégées en "mode classique" par `tymon/jwt-auth`, la validation se fait **localement** : Laravel possède le même `JWT_SECRET`, vérifie la signature HS256, et accède. Pas d'appel réseau, pas de dépendance.

Pour répondre à la consigne d'examen qui exigeait une **communication REST inter-services**, j'ai aussi ajouté un middleware Laravel `VerifyAuthSSO` qui fait un appel HTTP explicite vers `http://auth-service:8080/api/me` pour valider le token. La route `/api/sso/profile` utilise ce middleware et sert d'exemple. Les logs Laravel tracent chaque appel :

```
production.INFO: SSO : authentification reussie via Spring Boot {"user_id":8,"route":"api/sso/profile"}
production.WARNING: SSO : token refuse par Spring Boot {"status":401}
```

Cette double approche montre les deux cas possibles : validation locale (rapide, autonome) et token qui est faux (permet de refuser).

---

## 3. Règle métier : limite d'inscriptions

J'ai modifié le endpoint `POST /api/formations/{id}/inscription` pour y ajouter une règle métier explicite : **un apprenant ne peut suivre que 5 formations au maximum**.

L'ordre des vérifications dans le contrôleur reflète la logique métier :

1. L'utilisateur doit être authentifié (401 sinon)
2. Son rôle doit être `apprenant` (403 sinon)
3. La formation ciblée doit exister (404 sinon)
4. Il ne doit pas être déjà inscrit à cette formation (409 sinon)
5. **Il ne doit pas déjà suivre 5 formations (400 sinon)** — c'est la nouvelle règle

Le code dans `InscriptionController::store` :

```php
$maxFormations = 5;
$nombreFormationsSuivies = Inscription::where('utilisateur_id', $user->id)->count();

if ($nombreFormationsSuivies >= $maxFormations) {
    return response()->json([
        'message'             => 'Vous ne pouvez pas suivre plus de 5 formations',
        'max_formations'      => $maxFormations,
        'formations_suivies'  => $nombreFormationsSuivies,
    ], 400);
}
```

En cas de succès, la réponse contient également `formations_restantes` pour informer le front du quota encore disponible. On achoisi le code HTTP 400 plutôt que 403 ou 409 parce que la requête est techniquement valide et bien authentifiée : c'est simplement une contrainte métier qui bloque, pas un problème d'autorisation ni un conflit de données.

Deux tests fonctionnels couvrent cette règle dans `tests/Feature/ModuleEtInscriptionControllerTest.php` :

- `inscription_store_echoue_si_apprenant_a_deja_5_formations` — crée 5 inscriptions, tente la 6ème, attend 400 et vérifie qu'aucune ligne n'a été ajoutée en base
- `inscription_store_autorise_la_5eme_inscription` — vérifie que la limite est inclusive (4 + 1 = 5 passe bien, avec `formations_restantes: 0`)

---

## 4. Installation et lancement

### Prérequis

- Docker Desktop (Windows, Mac ou Linux) avec Docker Compose v2+
- Git
- Node.js 20+ et npm (uniquement si on veut lancer le front en dev)
- Environ 5 Go de RAM libre

### Configuration

Copier le template de variables d'environnement et remplir les valeurs :

```bash
cp .env.example .env
```

Ouvrir `.env` et remplir au minimum `APP_KEY`, `DB_PASSWORD`, `JWT_SECRET`, `APP_MASTER_KEY`. Le détail de chaque variable est documenté dans la section suivante.

### Lancement

Un seul `docker-compose.yml` orchestre l'ensemble :

```bash
docker compose up --build
```

Au premier lancement, le build des deux images prend 3 à 5 minutes (Composer pour Laravel, Maven pour Spring Boot). Les images buildées sont réutilisées par la suite, donc les démarrages suivants sont instantanés.

Une fois les 5 containers `Up` et healthy, vérifier que tout répond :

```bash
curl http://localhost/api/test             # → {"message":"API SkillHub OK"}
curl http://localhost:8080/actuator/health # → {"status":"UP"}
```

Si APP_KEY est vide au premier run, la générer :

```bash
docker exec skillhub-api php artisan key:generate
docker exec skillhub-api php artisan migrate --force
```

### Choix d'orchestration : ajout hors du dépôt existant

Je n'ai **pas** fusionné le code des deux microservices dans un seul dépôt Git. Chaque service garde son dépôt indépendant avec son propre pipeline CI/CD, et chacun publie son image sur GitHub Container Registry (GHCR). Le `docker-compose.yml` unifié se contente de tirer les deux images pré-publiées et de les orchestrer ensemble. Il est ajouté à la livraison sans toucher à la structure des dépôts existants.

Cette approche est volontaire : fusionner les pipelines en un seul casserait le principe d'**indépendance de déploiement** qui est au cœur des microservices. Un bug dans le service d'authentification ne doit pas empêcher de déployer une correction sur l'API métier, et inversement. En gardant les pipelines séparés et en orchestrant via un compose commun, on obtient : simplicité de lancement côté utilisateur, indépendance côté équipe de développement.

---

## 5. Outils DevOps utilisés

### GitHub Actions — CI/CD

Le pipeline CI/CD suit exactement la séquence :

1. **Checkout** — récupération du code depuis le dépôt
2. **Install** — installation des dépendances (Composer pour Laravel, Maven pour Spring Boot)
3. **Lint** — vérification syntaxique (`php -l` sur tous les fichiers sources)
4. **Tests** — exécution des tests unitaires et fonctionnels, incluant les tests spécifiques de la règle 5 inscriptions
5. **SonarCloud** — analyse statique de la qualité du code
6. **Build Docker** — construction de l'image via le Dockerfile multi-stage
7. **Push GHCR** — publication de l'image avec deux tags : le SHA Git court pour la traçabilité immuable (`sha-abc1234`) et `latest` comme alias de la dernière version stable

Chaque push sur `main` ou `dev` déclenche le pipeline. Les pull requests déclenchent uniquement les étapes jusqu'aux tests (pas de push d'image).

### SonarCloud — Analyse qualité

SonarCloud scanne le code après chaque push et mesure :

- **Bugs** — comportements incorrects détectés statiquement
- **Code smells** — complexité cyclomatique, duplication, conventions de nommage, fonctions trop longues
- **Couverture de tests** — pourcentage de lignes exécutées par les tests (remontée via `clover.xml` pour Laravel, JaCoCo pour Spring Boot)
- **Vulnérabilités** — patterns de sécurité à risque (SQL injection, secrets en dur, etc.)
- **Dette technique** — temps estimé pour corriger tous les points soulevés

Le pipeline remonte la couverture dans SonarCloud via le paramètre `-Dsonar.php.coverage.reportPaths=build/logs/clover.xml`.

### Docker — Conteneurisation

Chaque service a son Dockerfile multi-stage :

- **Stage builder** — installe les dépendances de build (Composer, Maven, compilateurs C pour les extensions PHP)
- **Stage runtime** — image finale légère qui ne contient que l'exécutable et les libs runtime

Bénéfices : images finales plus petites (moins de surface d'attaque), build plus rapide grâce au cache des layers, et pas d'outils de compilation embarqués en production. Spring Boot tourne avec un utilisateur non-root `spring`, conformément au principe de moindre privilège du cours (slide 231).

---

## 6. Variables d'environnement

Toutes les valeurs sensibles sont externalisées dans `.env`, jamais dans le code ni dans le `docker-compose.yml`. Le fichier `.env` est listé dans `.gitignore` et ne doit jamais être committé. Le `.env.example` fourni sert de template : il contient la liste complète des variables attendues, avec des valeurs d'exemple lorsque c'est pertinent, mais aucun secret réel.

Les variables sont regroupées par thème :

- **Application Laravel** — `APP_NAME`, `APP_ENV`, `APP_KEY`, `APP_DEBUG`, `APP_URL`
- **Base MySQL partagée** — `DB_DATABASE`, `DB_USERNAME`, `DB_PASSWORD`, `MYSQL_ROOT_PASSWORD`
- **MongoDB** — `MONGO_DATABASE`, `MONGO_ROOT_USERNAME`, `MONGO_ROOT_PASSWORD`
- **JWT partagé entre les deux services** — `JWT_SECRET` (minimum 32 caractères pour HS256), `JWT_TTL`
- **Cryptographie Spring Boot** — `APP_MASTER_KEY` (minimum 32 caractères, pour AES-GCM)
- **CORS et ports** — `APP_CORS_ORIGIN`, `NGINX_HTTP_PORT`
- **Intégration SSO** — `AUTH_SERVICE_URL` pour permettre à Laravel d'appeler Spring Boot via REST

Le fichier `.env.example` livré à la racine couvre ces variables pour les deux microservices, afin qu'un développeur qui récupère le projet puisse tout démarrer avec un simple `cp .env.example .env` suivi de l'édition des valeurs à remplir.

---

## 7. Analyse SonarCloud après la nouvelle feature

Après avoir ajouté la règle métier sur la limite des inscriptions et le middleware SSO, le pipeline a relancé SonarCloud. Voici ce que l'analyse fait ressortir et ce que je prévois de faire.

### Points positifs relevés

- Aucun bug critique ni bloquant détecté sur les modifications
- La couverture sur `InscriptionController::store` a augmenté grâce aux deux nouveaux tests fonctionnels
- Le middleware `VerifyAuthSSO` est propre et lisible, pas de code smell majeur
- Pas de nouvelle vulnérabilité de sécurité (pas de secrets en dur, pas d'injection possible)

### Points d'amélioration identifiés

- **Duplication de la constante `$maxFormations = 5`** dans le contrôleur : cette valeur devrait être externalisée dans `config/skillhub.php` sous la clé `inscription.max_par_apprenant`, pour pouvoir être ajustée sans modifier le code source. À terme, elle devrait même devenir un champ `places_max` sur le modèle Formation pour permettre des limites différentes selon les formations.

- **Complexité cyclomatique de la méthode `store()`** : la méthode enchaîne six vérifications successives (auth, rôle, existence formation, déjà inscrit, limite apprenant, limite formation à venir). SonarCloud signale que la méthode dépasse légèrement le seuil recommandé de 10. À extraire dans un service dédié `InscriptionService` ou dans des **Form Requests** Laravel pour découpler la validation de la logique de création.

- **Pas de transaction SQL autour de `Inscription::create` et du `ActivityLogService`** : en cas de forte concurrence (deux apprenants qui tentent de s'inscrire exactement en même temps à la même formation pleine), le comptage n'est pas atomique. Le risque est faible mais réel. À protéger avec `DB::transaction()` et un `lockForUpdate()` sur la formation, ou mieux avec une contrainte d'unicité en base sur `(utilisateur_id, formation_id)` qui interdit les doublons de manière inconditionnelle.

- **Couverture des cas d'erreur du middleware `VerifyAuthSSO`** : les branches 503 (Spring Boot injoignable) et 502 (réponse invalide) ne sont pas couvertes par des tests automatisés. Elles devraient l'être via un mock de `Http::fake()` pour simuler les trois cas : succès, échec 401 de Spring Boot, timeout réseau.

- **Logs SSO contiennent l'email en clair** : même si c'est pratique pour le debug, en production il faudrait logger uniquement l'`user_id` par prudence (principe de minimisation des données personnelles, RGPD).

### Plan d'action proposé

Dans l'ordre de priorité :

1. Ajouter la contrainte d'unicité en base sur `inscriptions(utilisateur_id, formation_id)` — bénéfice immédiat sur l'intégrité, aucun risque
2. Externaliser `maxFormations` dans la configuration
3. Écrire les tests manquants sur le middleware SSO (mock Http)
4. Refactorer `InscriptionController::store` en utilisant un `StoreInscriptionRequest` (Form Request) pour les validations, laissant le contrôleur ne gérer que la création
5. Retirer l'email des logs `VerifyAuthSSO`

Ces évolutions sont déjà discutées, elles ne sont pas appliquées dans cette livraison parce qu'elles dépassent le périmètre de l'examen (qui portait sur l'intégration SSO et la règle des 5 inscriptions), mais elles constituent le backlog technique prioritaire pour la version suivante.

---

## 8. Récapitulatif des livrables

- **`docker-compose.yml`** à la racine, orchestrant les deux microservices et leurs bases
- **`.env.example`** à la racine, couvrant les variables Laravel + Spring Boot
- **`app/Http/Controllers/InscriptionController.php`** — règle métier limite 5 inscriptions
- **`app/Http/Middleware/VerifyAuthSSO.php`** — middleware SSO avec appel REST vers Spring Boot
- **`tests/Feature/ModuleEtInscriptionControllerTest.php`** — tests couvrant la nouvelle règle
- **`.github/workflows/ci.yml`** — pipeline CI/CD avec étape de test explicite sur la règle des 5 inscriptions
- **`README.md`** — ce fichier
