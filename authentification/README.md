# Projet Authentification TP2

## Description
Cette application est la version améliorée du TP1, toujours en Java/Spring Boot avec un backend REST et une base de données MySQL.
**Attention :** Cette implémentation est volontairement dangereuse et ne doit **jamais** être utilisée en production. Les mots de passe sont stockés en clair et les mécanismes de sécurité sont minimalistes.

---
Objectifs TP2 :

Améliorer l’authentification avec mot de passe strict, hash adaptatif (Bcrypt) et verrouillage anti-brute force.

Première démarche qualité outillée avec SonarCloud.

L’authentification reste encore vulnérable au rejeu : la protection complète sera réalisée dans TP3/TP4.

Fonctionnalités héritées du TP1 :

Inscription et connexion (/api/auth/register, /api/auth/login)

Accès à /api/me via token

Vérification de mot de passe côté client et serveur


## Prérequis

Java 17

Maven

MySQL

Postman ou tout autre client REST

Node.js + npm (pour les clients JS si utilisés)

SonarCloud pour analyse de qualité

## 1 - Lancer MySQL et configurer application.properties
1.1 Démarrer MySQL

Windows : net start mysql ou via XAMPP/WAMP

Linux : sudo service mysql start

Mac : brew services start mysql

1.2 Créer la base de données
CREATE DATABASE tp2_auth;
1.3 Configurer src/main/resources/application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/tp2_auth?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=rootpassword
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

# Logging dans un fichier
logging.file.name=logs/tp2.log
logging.level.root=INFO
2 - Lancer l’API (Spring Boot)

Aller dans le dossier du projet :

cd C:\Documents\authentification

Compiler et lancer avec Maven :

mvn clean install
mvn spring-boot:run

L’API sera disponible sur :

http://localhost:8080
3 - Lancer le client Java / Postman
Inscription

POST /api/auth/register
Body JSON :

{
  "email": "test@example.com",
  "password": "Pwd1234!",
  "passwordConfirm": "Pwd1234!"
}
Connexion

POST /api/auth/login
Body JSON :

{
  "email": "test@example.com",
  "password": "Pwd1234!"
}
Accéder à /api/me

GET /api/me
Header :

Authorization: <token>
4 - Compte de test
Email	Mot de passe
test@example.com
	Pwd1234!
5 - Politique de mot de passe TP2

Minimum 12 caractères

1 majuscule, 1 minuscule, 1 chiffre, 1 caractère spécial

Double saisie côté client (password et passwordConfirm)

Indicateur de force :

Rouge : non conforme

Orange : conforme mais faible

Vert : conforme et bon niveau

6 - Sécurité côté serveur

Hash adaptatif Bcrypt pour le stockage du mot de passe (password_hash)

Anti-brute force : 5 échecs → blocage 2 minutes

Code HTTP 423 (Locked) ou 429 (Too Many Requests) pour verrouillage

Route /api/me protégée, accessible uniquement si authentifié

Les erreurs côté login ne divulguent pas si l’email ou le mot de passe est incorrect

7 - Analyse de sécurité TP2

Stockage sécurisé des mots de passe

Les mots de passe ne sont plus en clair, mais hashés avec Bcrypt.

Anti-brute force

Blocage après 5 tentatives ratées.

Token toujours vulnérable au rejeu

Les tokens peuvent être capturés et rejoués (TP3 corrigera).

Validation côté client et serveur

Politique de mot de passe stricte et confirmation double saisie.

Non-divulgation d’erreurs

Même message pour email inconnu ou mot de passe incorrect pour éviter l’extraction d’information.

8 - Qualité logicielle

Analyse obligatoire avec SonarCloud

Couverture minimale : 60 %

Exceptions personnalisées et @ControllerAdvice enrichies

Javadoc obligatoire pour toutes classes security, validator, services, et exceptions

Bugs majeurs et vulnérabilités corrigés

Code smells prioritaires : exception handling, validation, duplication, complexité inutile

9 - Arborescence du projet
authentification/
├─ src/
│  ├─ main/java/com/projetAuthentification/authentification/
│  │  ├─ controller/AuthController.java
│  │  ├─ service/AuthService.java
│  │  ├─ entity/User.java
│  │  ├─ exception/*.java
│  │  ├─ repository/UserRepository.java
│  │  └─ AuthentificationApplication.java
│  └─ resources/application.properties
├─ logs/
├─ pom.xml
└─ README.md
10 - Git et versionnement

Tag initial TP2 : v2.0-start

Le code doit être analysé et commit régulièrement avec messages clairs

Les prochaines versions TP3 et TP4 corrigeront les vulnérabilités restantes et ajouteront la protection anti-rejeu