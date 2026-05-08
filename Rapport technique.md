# Rapport technique SkillHub

Auteur : MU202612
Bloc 03 — Industrialisation et qualité logicielle

## Pourquoi ce document existe

Quand un nouveau dev arrive dans l'équipe, on n'a pas envie qu'il passe deux semaines à comprendre où sont les choses. Ce document est fait pour ça : il se lit en une matinée, et au bout de deux ou trois jours on doit pouvoir pousser sa première PR sans avoir besoin de demander à quelqu'un toutes les heures.

On y parle de comment le projet est construit, pourquoi on a fait ces choix, comment on travaille au jour le jour (les branches, les PR, le pipeline CI), et où regarder dans le code quand on veut comprendre quelque chose en détail. Le document sert aussi de support pour les TDs du bloc 03 (qualité logicielle, documentation technique, plan d'amélioration continue). Les sections concernées sont signalées au passage.

## 1. C'est quoi SkillHub

SkillHub est une plateforme web qui met en relation des formateurs et des apprenants. Un formateur publie une formation découpée en modules. Un apprenant s'y inscrit, valide ses modules au fur et à mesure, et peut discuter avec son formateur via une messagerie intégrée.

Le projet a été construit en plusieurs étapes. Pendant les blocs 01 et 02, c'était un monolithe Laravel plus React avec une seule base MySQL. Pendant le bloc 03, on a découpé l'application en deux services séparés (un pour l'authentification, un pour le métier), ajouté MongoDB pour la messagerie, et mis en place tout le pipeline d'intégration continue avec analyse de qualité. Ce rapport décrit l'état du projet à la fin du bloc 03.

## 2. Architecture en quelques mots

L'application repose sur quatre briques. Un front React qui tourne en local sur le port 5173, un service d'authentification écrit en Spring Boot (Java 17, port 8080), un backend métier en Laravel 12 (PHP 8.2, port 8000), et deux bases de données : MySQL pour les données structurées (users, formations, modules, inscriptions) et MongoDB pour ce qui grossit beaucoup et n'a pas de schéma figé (les messages et les logs d'activité).

Le front parle directement aux deux backends. Pour s'inscrire ou se connecter, il appelle Spring Boot. Pour tout le reste — récupérer le catalogue de formations, s'inscrire à une formation, envoyer un message — il appelle Laravel. Les deux services partagent la même base MySQL, mais une règle stricte est respectée : aucun service n'écrit jamais dans la base de l'autre. Tout passe par REST. C'est ce qui permet de modifier un service sans casser l'autre.

### Pourquoi avoir séparé l'auth en Java

L'authentification aurait pu rester dans Laravel, ça nous aurait fait un service de moins à gérer. On l'a sortie pour trois raisons. La première, c'est que c'est le seul endroit du projet où on manipule des secrets cryptographiques (la clé maître AES, le secret JWT). Concentrer ce code au même endroit permet de l'auditer plus facilement. La deuxième, c'est qu'on ouvre la porte à d'autres services qui réutiliseraient le même mécanisme de connexion (un service de paiement, un service d'analytics) sans avoir à réimplémenter la vérification des tokens. Et la troisième, c'est que Java avec Spring Boot offre un écosystème de tests et d'analyse statique très solide pour ce type de code sensible (JUnit, JaCoCo, Checkstyle).

### Pourquoi Laravel pour le métier

Laravel a été retenu pour la productivité. Les routes, la validation des entrées, l'ORM Eloquent, les migrations, les tests, tout est livré prêt à l'emploi. Pour des règles métier qui changent souvent, c'est ce qu'on a trouvé de plus rapide. Et PHP est plus accessible qu'un langage compilé pour des profils juniors.

### Pourquoi deux bases de données

MySQL fait très bien son travail pour les données qui ont un schéma stable et des contraintes d'intégrité. Un user a un email unique, une inscription lie un apprenant à une formation, une formation appartient à un formateur. Les jointures et les clés étrangères sont gérées nativement et c'est exactement ce qu'on veut.

MongoDB est utilisé pour les messages et les logs d'activité parce que ces collections grossissent vite, que leur structure peut bouger, et qu'on n'a pas besoin de les joindre avec le reste du système. Si on les avait mises dans MySQL, la table principale aurait gonflé sans raison et les sauvegardes auraient été plus lourdes.

### Pourquoi JWT en HS256

JWT permet une authentification sans état : le serveur n'a pas à interroger une base à chaque requête, il vérifie juste la signature. On a choisi HS256 (HMAC partagé) plutôt que RS256 (clés asymétriques) parce qu'on contrôle les deux services qui se parlent et qu'il n'y a pas de tiers à qui distribuer une clé publique. Le secret est partagé via une variable d'environnement, et c'est suffisant.

## 3. Démarrer le projet en local — objectif trois jours

### Premier jour : installer et faire tourner

Côté machine, il faut Java 17, PHP 8.2 avec les extensions `mongodb`, `pdo_mysql`, `mbstring` et `gd`, Composer, Node 20 ou plus récent, MySQL 8 (ce qui est livré avec WAMP fait l'affaire), MongoDB 6 (en service Windows ou via un container Docker), et Git. Pour vérifier qu'une dépendance est bien là, les commandes habituelles : `java --version`, `php --version`, `composer --version`, `node --version`.

Une fois l'environnement prêt, on clone le dépôt et on prépare chaque service. Pour Spring Boot, on télécharge les dépendances Maven avec `./mvnw dependency:go-offline` dans le dossier `authentification`. Pour Laravel, dans `skillhub_CICD_backend`, on lance `composer install`, on copie `.env.example` vers `.env`, puis `php artisan key:generate`. Pour le front, dans `skillhub-front`, c'est `npm install` suivi d'un `cp .env.example .env.local`.

Il reste à créer la base. Le projet utilise le nom `skillhub_mu` (on a choisi ce suffixe pour ne pas entrer en conflit avec d'autres bases existantes sur les machines de dev). En SQL :

```sql
CREATE DATABASE skillhub_mu CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Vérifier dans `skillhub_CICD_backend/.env` que `DB_DATABASE=skillhub_mu`, et dans `authentification/src/main/resources/application.properties` que la JDBC URL pointe sur la même base. Ensuite on lance les migrations Laravel avec `php artisan migrate`. Spring Boot crée ses tables tout seul au démarrage grâce à `ddl-auto=update`.

À ce stade, lancer les trois services dans trois terminaux séparés. Le front avec `npm run dev`, Spring Boot avec `./mvnw spring-boot:run`, et Laravel avec `php -d opcache.enable_cli=0 artisan serve --port=8000`. Le flag OPcache est utile sous Windows pour éviter une erreur de "file mapping" qu'on rencontre souvent ici. Si tout fonctionne, on doit pouvoir ouvrir `http://localhost:5173`, créer un compte, et voir la page d'accueil charger la liste des formations sans erreur.

### Deuxième jour : suivre un flux de bout en bout

Une fois que ça tourne, le meilleur moyen de comprendre le code c'est de prendre un parcours utilisateur et de le suivre depuis le clic dans le front jusqu'à l'écriture en base.

Prenons l'inscription. Le formulaire React appelle la fonction `register` dans `skillhub-front/src/services/authService.js`. Cette fonction envoie une requête `POST /api/auth/register` à Spring Boot sur le port 8080. Côté Java, c'est la méthode `register` du `AuthController` qui reçoit la requête, vérifie les données, et appelle `AuthService.register` pour créer l'utilisateur en base. Le mot de passe est chiffré en AES-GCM par `CryptoService` avant d'être stocké. Une fois l'utilisateur créé, `JwtService.emit` génère un JWT que le contrôleur renvoie au front. Le front stocke ce JWT dans `localStorage`.

À partir de là, toutes les requêtes vers Laravel passent par `axiosConfig.js` qui ajoute automatiquement le header `Authorization: Bearer <token>`. Côté Laravel, le package `tymon/jwt-auth` valide la signature avec le même `JWT_SECRET` que celui utilisé par Spring Boot, ce qui prouve que le token vient bien d'une connexion légitime.

Refaire le même exercice avec "se connecter" (qui utilise le protocole HMAC, expliqué plus loin dans la section sécurité) et avec "s'inscrire à une formation" (qui touche `InscriptionController` et la table `inscriptions`). Au bout de deux jours, on a une bonne idée du squelette.

### Troisième jour : pousser quelque chose

Choisir un petit ticket. Une faute dans un message d'erreur, un champ optionnel à ajouter quelque part, un log qui manque. Pas la peine de s'attaquer à la cryptographie ou au pipeline CI tout de suite. Suivre la procédure décrite plus bas (créer une branche `feature/...`, écrire le code, écrire le test, ouvrir une PR vers `dev`). Au moment où la PR est verte sur le pipeline et qu'un autre dev a relu le diff, on est officiellement opérationnel.

## 4. Où trouver la documentation du code

Il y a trois sources, une par technologie utilisée.

Pour le service auth en Java, on utilise Javadoc. Toutes les classes publiques portent un bloc Javadoc avec les balises classiques (`@author`, `@version`, `@since`, et pour les méthodes les `@param`, `@return`, `@throws`). La doc se génère avec `./mvnw javadoc:javadoc` dans le dossier `authentification`, et le résultat HTML se trouve dans `target/reports/apidocs/index.html`. On l'ouvre dans n'importe quel navigateur. Avant de modifier une classe Java sensible, ouvrir d'abord son Javadoc : la plupart des règles métier y sont expliquées en haut.

Pour le backend Laravel, on utilise Swagger via le package `darkaonline/l5-swagger`. Les annotations OpenAPI 3 sont posées en attributs PHP 8 directement au-dessus des méthodes des contrôleurs (`AuthController`, `FormationController`, `InscriptionController`, `MessageController`, `ModuleController`). La doc se régénère avec `php artisan l5-swagger:generate`, et on la consulte en ouvrant `http://localhost:8000/api/documentation` une fois Laravel lancé. C'est une UI interactive : on peut tester n'importe quel endpoint depuis la page, à condition d'avoir un JWT à coller dans le bouton "Authorize" en haut à droite. Un fichier statique `storage/api-docs/api-docs.json` est aussi commit dans le dépôt, c'est la dernière version générée.

Pour le PHPDoc, c'est plus simple : on lit les commentaires directement dans le code. Les méthodes des contrôleurs et services Laravel ont des blocs PHPDoc avec types, paramètres, retours et exceptions. PHPStorm ou VSCode avec l'extension Intelephense les utilisent pour l'auto-complétion. On n'a pas généré de site statique pour ça, ce serait redondant avec Swagger pour les endpoints, et pour les services internes les commentaires en place suffisent.

Pour les routes elles-mêmes, le fichier `skillhub_CICD_backend/routes/api.php` liste toutes les URLs métier. Pour Spring Boot, les routes sont définies par les annotations `@PostMapping` et `@GetMapping` dans `AuthController.java`.

## 5. Qualité logicielle et conformité aux normes (TD1)

On respecte plusieurs référentiels, certains parce que la communauté les a adoptés, d'autres parce qu'ils sont obligatoires. Pour le style de code PHP, c'est PSR-12, vérifié automatiquement par Laravel Pint dans le pipeline CI (`vendor/bin/pint --test`). Pour le code Java, c'est le Google Java Style, vérifié par Checkstyle (`mvn checkstyle:check`). Pour la sécurité applicative, on s'aligne sur l'OWASP Top 10 : validation des entrées, mots de passe chiffrés, JWT signés, secrets hors du code, pas de log sensible. Pour la qualité produit au sens large, on s'appuie sur la grille ISO/IEC 25010 (fiabilité, sécurité, maintenabilité, portabilité), c'est SonarCloud qui mesure les sous-caractéristiques. Pour les données personnelles, on suit le RGPD : aucun mot de passe en clair, et on minimise ce qu'on log. Et pour la lisibilité du Git log, on utilise les Conventional Commits (`feat:`, `fix:`, `docs:`, `ci:`).

Quand on a fait le bilan de conformité, on a vu que le style de code (PHP comme Java) est aligné, le pipeline ne laisse rien passer. La gestion des secrets est saine : `.env` dans `.gitignore`, `.env.example` documenté, secrets GitHub pour la CI. La sécurité applicative est en place : mots de passe AES-GCM, JWT HS256, validation des entrées sur toutes les routes, CORS restrictif.

On a quand même des écarts. Le premier concerne la couverture de tests : le service auth et le backend Laravel sont bien couverts (60 tests JUnit, 158 tests PHPUnit), mais le front React est très peu testé. On vise 70 % de couverture sur le front d'ici trois mois en intégrant Cypress pour les parcours critiques. Le deuxième écart, c'est le quality gate Sonar qui est volontairement en mode non bloquant le temps que la couverture monte ; on le rendra bloquant quand on aura passé le seuil. Le troisième, c'est que le middleware `VerifyAuthSSO` log encore l'email en clair dans les warnings ; c'est pratique en dev, mais on doit le retirer en prod par minimisation RGPD.

À côté de ça, deux actions correctives sont prévues à court terme. Activer Dependabot pour avoir des alertes automatiques sur les dépendances vulnérables (cette semaine). Et compléter les tests automatisés du middleware SSO sur les cas d'erreur (timeout, 401, 502) qu'on n'a pas couverts pour l'instant.

## 6. Documentation technique du projet (TD2)

Cette section est organisée comme un guide de maintenance : ce qu'il faut savoir pour reprendre le projet sans repartir de zéro.

Le monorepo `EC09_MU202612` contient deux dossiers principaux. Le dossier `authentification/` contient le service Spring Boot avec son code source dans `src/main/java`, ses tests dans `src/test/java`, et son `pom.xml`. Le dossier `skillhub_CICD_backend/` contient le backend Laravel avec ses contrôleurs dans `app/Http/Controllers`, ses middleware dans `app/Http/Middleware`, ses modèles Eloquent dans `app/Models`, ses routes dans `routes/api.php`, ses tests fonctionnels dans `tests/Feature`, ses tests unitaires dans `tests/Unit`, et ses migrations SQL dans `database/migrations`.

À la racine, on trouve le pipeline CI (`.github/workflows/ci.yml`), le `docker-compose.yml` (utilisable mais pas nécessaire), le `README.md` pour le démarrage rapide, et ce rapport. Le front React est dans un dépôt séparé, à `c:\wamp64\www\skillhub-final\skillhub-front\`.

Les choix techniques principaux ont déjà été expliqués dans la section architecture. Pour résumer brièvement le pourquoi de chacun : Spring Boot pour l'auth parce que la crypto est centralisée et l'écosystème de tests est mature. Laravel 12 pour le métier parce que la productivité est élevée et les tests fonctionnels faciles à écrire. React et Vite pour le front parce que Vite démarre vite et fait du hot reload bien. MySQL parce qu'il est déjà installé sur les machines de dev (WAMP) et que les besoins du projet ne justifient pas PostgreSQL. MongoDB parce que les messages ont un format flexible. JWT HS256 parce qu'on contrôle les deux côtés du système. AES-GCM 256 et HMAC-SHA256 parce que ce sont les algos recommandés par l'OWASP. GitHub Actions pour la CI parce que c'est intégré au dépôt. SonarCloud pour la qualité parce qu'il n'y a rien à maintenir. Docker existe (un Dockerfile par service plus un `docker-compose.yml`) mais on ne l'utilise plus en local. On a constaté que travailler en local direct est plus rapide à itérer et nous épargne plusieurs problèmes spécifiques à Windows.

Pour les procédures de maintenance qu'on rencontre souvent, voici comment elles se passent.

Quand on veut ajouter une colonne SQL, on crée une nouvelle migration avec `php artisan make:migration add_telephone_to_users_table`, on édite le fichier généré dans `database/migrations`, puis on lance `php artisan migrate`. Une règle absolue : ne jamais modifier une migration qui a déjà été déployée en production. On en crée une nouvelle.

Quand on veut ajouter un endpoint Laravel, on déclare la route dans `routes/api.php`, on écrit la méthode dans le contrôleur correspondant, on ajoute l'annotation OpenAPI au-dessus de la méthode (sinon Swagger ne sait pas quoi documenter), on régénère la doc avec `php artisan l5-swagger:generate`, et on écrit au moins un test fonctionnel qui exerce l'endpoint dans `tests/Feature`.

Quand on veut ajouter un endpoint Spring Boot, on ajoute la méthode dans `AuthController` avec l'annotation `@PostMapping` ou `@GetMapping` adaptée, on écrit le bloc Javadoc juste au-dessus (le doclint strict du build casse si la doc est manquante ou incomplète), et on ajoute un test JUnit dans `src/test/java`.

Pour mettre à jour une dépendance, c'est `composer update <package>` côté PHP suivi de la suite de tests, ou la mise à jour de la version dans `pom.xml` côté Java suivi de `mvn clean verify`. Avant de monter une version majeure (Spring Boot, Laravel, PHP, Java), on lit les notes de migration de l'éditeur en entier.

Pour faire évoluer un schéma MongoDB, comme il n'y a pas de migrations versionnées, on respecte une règle de rétrocompatibilité : si on renomme un champ, on lit les deux noms pendant au moins une release pour ne pas casser les anciens documents.

## 7. Comment on travaille au quotidien

La branche `main` est la branche stable, c'est ce qui tourne en production. La branche `dev` reçoit les fonctionnalités terminées. Chaque ticket part d'une branche `feature/<nom-court>`. Le workflow type, c'est `git checkout dev && git pull && git checkout -b feature/limite-inscription`, on code, on commit régulièrement, on pousse, et on ouvre une PR vers `dev`. Une fois la PR mergée dans `dev`, elle remontera vers `main` lors de la prochaine release.

Les commits suivent les Conventional Commits : `type: description`. Les types qu'on rencontre le plus souvent sont `feat:` pour une nouvelle fonctionnalité, `fix:` pour une correction de bug, `docs:` pour la documentation, `ci:` pour le pipeline, `refactor:` pour une réorganisation sans changement de comportement, `test:` pour ajouter ou corriger des tests. Un exemple typique : `feat(auth): ajouter le changement de mot de passe`.

Une PR doit avoir un titre clair (impératif présent : "Ajouter X" plutôt que "Ajout de X"), une description qui explique le quoi et le pourquoi, et la liste des points qui restent à faire si le travail n'est pas complètement terminé. Le pipeline CI doit être vert avant le merge, et au moins un autre développeur doit relire le diff. Les commentaires bloquants se traitent avant le merge ; les suggestions non bloquantes peuvent partir dans une PR de suivi pour ne pas tout retarder.

Le pipeline tourne à chaque push sur `main` ou `dev` et à chaque PR vers ces branches. Il contient deux jobs en parallèle, un pour Spring Boot et un pour Laravel. Chaque job suit la même séquence. D'abord l'install des dépendances (`mvn dependency:go-offline` ou `composer install`), avec mise en cache. Ensuite le lint (Checkstyle pour Java, `php -l` plus Laravel Pint pour PHP). Ensuite les tests (JUnit avec couverture JaCoCo pour Java, PHPUnit avec couverture PCOV pour PHP). Et enfin l'analyse SonarCloud, qui remonte les rapports de couverture (`target/site/jacoco/jacoco.xml` et `build/logs/clover.xml`) pour que le quality gate ait des chiffres à comparer.

Avant de pousser une PR, on prend l'habitude de lancer la même chose en local : `./mvnw clean verify` côté Java, `php artisan test` et `vendor/bin/pint --test` côté PHP. Si tout passe en local, ça passera en CI sauf cas de variables d'environnement spécifiques.

## 8. Sécurité

Les mots de passe sont chiffrés en AES-256 GCM côté Java. La clé maître est lue depuis la variable `APP_MASTER_KEY` (32 caractères minimum) et n'est jamais loguée ni copiée ailleurs. Le format stocké en base est `v1:Base64(iv):Base64(ciphertext)`, avec un préfixe de version pour pouvoir migrer vers une autre version d'algo si besoin un jour. Le mode GCM ajoute un tag d'authentification, ce qui veut dire que si quelqu'un altère le contenu en base, le déchiffrement échouera explicitement.

La connexion utilise un protocole HMAC qui garantit que le mot de passe ne quitte jamais le navigateur. Le client génère un nonce (un UUID) et un timestamp, calcule `HMAC-SHA256(password, "email:nonce:timestamp")` encodé en Base64, et envoie au serveur uniquement `{ email, nonce, timestamp, hmac }`. Le serveur recalcule le HMAC de son côté avec le mot de passe déchiffré, et compare en temps constant pour éviter les attaques temporelles. Si le timestamp est trop ancien (plus de 60 secondes), la requête est refusée. Si le nonce a déjà servi (il est gardé en mémoire pendant 120 secondes), pareil. C'est ce qui empêche les attaques par rejeu.

Les JWT sont signés HS256 avec un secret de 32 caractères minimum, partagé entre Spring Boot et Laravel via la variable `JWT_SECRET`. La durée de vie par défaut est de 60 minutes. Le claim `iss=skillhub-auth` permet à Laravel de vérifier que le token a bien été émis par notre service auth et pas par autre chose. Si Laravel reçoit un JWT signé avec un autre secret, la vérification échoue tout simplement.

Côté CORS, Laravel autorise uniquement `http://localhost:5173` (le front Vite local) et le domaine de prod, configuré dans `config/cors.php`. Spring Boot a la même restriction via l'annotation `@CrossOrigin(origins = "http://localhost:5173")` posée sur le contrôleur d'auth.

Toutes les routes valident leur payload. Côté Laravel avec `request()->validate(['email' => 'required|email', ...])`, côté Spring Boot avec `@Valid` et les contraintes Bean Validation sur les DTO. Quand la validation échoue, on renvoie un code 4xx avec un message clair, sans exposer de détails internes qui pourraient aider un attaquant.

Tous les secrets vivent dans les variables d'environnement (fichier `.env` en local, secrets GitHub Actions en CI). Aucune valeur sensible n'est commitée. On vérifie ça à chaque PR, et le `.gitignore` est strict.

## 9. Plan d'amélioration continue de la qualité (TD3)

L'idée de cette partie n'est pas de promettre de tout faire d'un coup, mais de poser une feuille de route mesurable sur les prochains mois.

La cible à six mois pour la couverture de tests est 80 % sur le code nouveau et 65 % sur l'ensemble. Pour y arriver, on s'engage sur deux règles. La première : pour chaque bug corrigé, on écrit le test de régression qui l'attrape. C'est ce qui empêche le bug de revenir trois mois plus tard. La deuxième : aucune PR ne peut faire baisser la couverture globale du projet. SonarCloud compare et bloque, une fois qu'on aura activé le quality gate bloquant. À plus long terme, on veut couvrir les trois parcours critiques (login, inscription à une formation, envoi de message) par des tests end-to-end. Côté front, l'idée est d'introduire Cypress, qui simule un vrai navigateur. Le front est aujourd'hui le maillon le moins testé du système et c'est un risque.

Pour l'instant, le quality gate est en mode `continue-on-error` (non bloquant) parce qu'on est en train de monter la couverture et qu'on ne voulait pas paralyser les PR pendant cette phase. Quand on aura passé les seuils — couverture nouveau code supérieure à 70 %, zéro nouveau bug critique, zéro nouvelle vulnérabilité, code dupliqué inférieur à 3 % sur le nouveau code — on rendra le quality gate bloquant. Ce sera une bascule franche, planifiée avec l'équipe.

Sur la sécurité, plusieurs actions sont au planning. Activer Dependabot pour avoir des alertes automatiques sur les vulnérabilités des dépendances (à faire dans la semaine, c'est une activation en deux clics dans GitHub). Mettre en place une revue de sécurité trimestrielle qui inclut la rotation des secrets, l'audit des permissions des tokens GitHub, l'audit des dépendances, et la relecture des middlewares d'authentification. Retirer l'email du log warning dans `VerifyAuthSSO` à la prochaine PR. Et dans un horizon plus lointain, proposer un 2FA aux comptes formateurs.

Côté observabilité, on log déjà les événements importants dans MongoDB via `ActivityLogService` (login réussi ou échoué, inscription, modification de formation). L'étape suivante c'est de centraliser les logs et les erreurs dans un outil unique (Loki ou ELK) et de monter des dashboards Grafana avec les métriques qu'il faut surveiller : taux d'erreur 5xx par endpoint, latence p95, nombre de logins en échec par minute. Sans cette observabilité, on ne sait pas si un déploiement vient d'aggraver la situation, et on découvre les problèmes via les utilisateurs.

Pour la documentation vivante, la règle qu'on s'impose est que toute modification d'API entraîne une mise à jour de l'annotation OpenAPI correspondante, et toute modification d'une méthode publique entraîne une mise à jour de son PHPDoc ou Javadoc. Le pipeline CI régénère la doc à chaque build. À chaque release, on prévoit de publier le HTML statique de la doc Swagger sur GitHub Pages pour qu'elle soit accessible aux équipes externes.

L'onboarding fait aussi partie de ce plan qualité. Quand un nouveau développeur rejoint l'équipe, ce rapport est le premier document qu'il lit. À la fin de sa première semaine, on lui demande explicitement de signaler ce qui était flou, faux ou manquant, et on corrige le document. C'est cette boucle qui empêche la documentation de pourrir : ce sont les yeux neufs qui repèrent ce que les anciens ont arrêté de voir.

On veut aussi faire vivre trois niveaux d'environnements : le local sur la machine du dev (mise à jour manuelle), le staging dans le cloud (mis à jour automatiquement à chaque push sur `dev`), la prod (mise à jour manuellement après validation sur staging). Cette progression filtre les régressions avant qu'elles atteignent les utilisateurs.

Enfin, sur la revue de code : toute PR est relue par au moins un autre développeur. La revue ne se contente pas de vérifier que le code "marche". Elle regarde si le code fait bien ce que le titre annonce, s'il y a des tests, si la doc est à jour, si aucun secret n'est en dur, si aucun log ne contient de donnée sensible. Les commentaires bloquants se traitent avant le merge, les suggestions non bloquantes partent en PR de suivi. Une revue ne doit pas s'éterniser : si elle prend plus de deux jours, on en discute en stand-up.

## 10. Gestion des erreurs

Côté Spring Boot, on a un `@ControllerAdvice` global qui capture les exceptions métier (`AuthenticationFailedException`, `InvalidInputException`, `ResourceConflictException`) et les transforme en réponses JSON propres avec le bon code HTTP, un message clair, et un timestamp. Le client reçoit toujours du JSON, jamais une stack trace.

Côté Laravel, le handler global d'exceptions remplit le même rôle. Les exceptions personnalisées renvoient 401, 409 ou 422 selon le cas, avec un message localisé.

Pour les services qui ne sont pas critiques pour la requête utilisateur (comme `ActivityLogService` qui écrit dans MongoDB), on applique un pattern best-effort : la méthode est entourée d'un try/catch, et en cas d'erreur on log un warning sans faire planter la requête. Une indisponibilité de Mongo ne doit jamais empêcher un utilisateur de se connecter ou de modifier sa formation. C'est une règle qu'on a apprise en pratique : les services secondaires ne doivent pas tirer les services principaux dans leur chute.

## 11. Avec ou sans Docker

Le projet a un `docker-compose.yml` à la racine, et chaque service a son `Dockerfile`. C'est utile pour vérifier que les images sont à jour avant un push, ou pour lancer un environnement complet sans rien installer. Mais en pratique au quotidien on ne l'utilise plus. On a constaté que travailler en local direct (WAMP plus Spring Boot via Maven plus Vite) est plus rapide à itérer, plus simple à déboguer, et nous épargne plusieurs problèmes spécifiques à Windows (port qui glisse, file mapping cassé, lenteur du volume monté).

Pour lancer en local direct, trois terminaux. Un pour Spring Boot avec `./mvnw spring-boot:run` dans `authentification`. Un pour Laravel avec `php -d opcache.enable_cli=0 artisan serve --port=8000` dans `skillhub_CICD_backend`. Un pour le front avec `npm run dev` dans `skillhub-front`.

Pour lancer avec Docker, c'est `docker compose up --build` à la racine. Tous les services tournent dans des containers, MySQL et MongoDB compris. Le setup prend plus de RAM mais a l'avantage d'être strictement identique entre les machines.

## 12. Problèmes fréquents

On a rencontré quelques pièges récurrents qui méritent d'être notés.

Sous Windows, `php artisan serve` plante parfois avec "Unable to create file mapping". C'est un conflit entre OPcache (et parfois WinCache) et le serveur de dev de PHP. Le contournement, c'est `php -d opcache.enable_cli=0 -d wincache.ocenabled=0 artisan serve --port=8000`. Pour la solution propre, désactiver OPcache pour le CLI dans le `php.ini`.

Si Laravel renvoie une erreur SQL "Table 'cache' n'existe pas", c'est que les migrations n'ont pas tourné sur la bonne base. Vérifier `DB_DATABASE` dans `.env` et lancer `php artisan migrate`.

Si SonarCloud reste sur "We are analyzing your project" indéfiniment, c'est probablement que `mvn sonar:sonar` n'a pas envoyé l'analyse au bon projet. Le plugin Maven ne lit pas `sonar-project.properties`, il lit `pom.xml`. Vérifier que `<sonar.projectKey>` est bien renseigné dans `authentification/pom.xml`.

Si le front affiche des erreurs CORS, vérifier que `http://localhost:5173` est bien autorisé dans `config/cors.php` côté Laravel et dans l'annotation `@CrossOrigin` côté Spring Boot.

Si Laravel rejette les JWT émis par Spring Boot, le `JWT_SECRET` n'est pas identique entre les deux. Comparer la valeur dans `skillhub_CICD_backend/.env` et dans `authentification/src/main/resources/application.properties`. Le moindre caractère différent fait échouer la vérification de signature.

Si les tests Mongo échouent, c'est souvent que `mongod` ne tourne pas. La commande `netstat -ano | findstr :27017` doit montrer un processus en LISTENING. Si non, lancer `net start MongoDB` dans un terminal admin.

## 13. Pour aller plus loin

Ce document n'est pas figé. Quand quelque chose paraît flou en le lisant, le réflexe est d'ouvrir une issue ou d'envoyer une PR pour le corriger. La meilleure documentation est celle que les développeurs corrigent au fur et à mesure qu'ils s'en servent.

Trois ressources complètent ce rapport. Le Javadoc généré du service auth pour le détail des classes Java (`./mvnw javadoc:javadoc` puis `target/reports/apidocs/index.html`). L'UI Swagger pour explorer et tester les endpoints Laravel (`http://localhost:8000/api/documentation` une fois le serveur lancé). Et le `README.md` à la racine pour le démarrage rapide et le récap des livrables.
