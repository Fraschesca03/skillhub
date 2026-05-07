# Projet Authentification TP1

## Description
Cette application est un **TP d’authentification Java/Spring Boot** avec un backend REST et une base de données MySQL.  
Elle permet de :
- S’inscrire et se connecter (`/api/auth/register`, `/api/auth/login`)
- Accéder à une route protégée `/api/me` via un **token simple**.

**Attention :** Cette implémentation est volontairement dangereuse et ne doit **jamais** être utilisée en production. Les mots de passe sont stockés en clair et les mécanismes de sécurité sont minimalistes.

---

## Prérequis
- Java 17
- Maven
- MySQL
- Postman ou tout autre client REST

---

1 -Lancer MySQL et configurer `application.properties`

### 1.1 Démarrer MySQL
- Windows : `net start mysql` ou via XAMPP/WAMP
- Linux : `sudo service mysql start`
- Mac : `brew services start mysql`

### 1.2 Créer la base de données
```sql
CREATE DATABASE tp1_auth;
1.3 Configurer src/main/resources/application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/tp1_auth?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=rootpassword
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

# Logging dans un fichier
logging.file.name=logs/tp1.log
logging.level.root=INFO

2 -Lancer l’API (Spring Boot)

Aller dans le dossier du projet :

cd C:\Documents\authentification

Compiler et lancer avec Maven :

mvn clean install
mvn spring-boot:run

L’API sera disponible sur :

http://localhost:8080
3 - Lancer le client Java / Postman

POST /api/auth/register → créer un utilisateur

POST /api/auth/login → récupérer un token

GET /api/me → accéder aux infos de l’utilisateur via token

4 - Compte de test
Email	Mot de passe
test@example.com
	pwd1234
4.1 Inscription

POST /api/auth/register

{
  "email": "test@example.com",
  "password": "pwd1234"
}
4.2 Connexion

POST /api/auth/login

{
  "email": "test@example.com",
  "password": "pwd1234"
}
4.3 Récupérer token pour /api/me
{
  "token": "votre-token-reçu-du-login"
}
4.4 Accéder à /api/me

GET /api/me avec header :

Authorization: <token>
5 - Analyse de sécurité TP1

Stockage des mots de passe en clair

Les mots de passe sont enregistrés directement dans la base de données sans hachage ni salage.

Risque : fuite facile si la base est compromise.

Token d’authentification faible

Les tokens générés sont aléatoires mais non expirés ni sécurisés.

Risque : un token volé permet un accès illimité.

Pas de validation côté client / HTTPS

Les communications ne sont pas chiffrées.

Risque : interception des mots de passe et tokens.

Pas de limitation d’authentification

Pas de mécanisme pour bloquer des tentatives répétées.

Risque : attaques par force brute.

Endpoints exposés sans rôle ou permissions

Tous les utilisateurs peuvent appeler /api/me s’ils ont un token.

Risque : aucune distinction des rôles ou accès restreints.

Cette analyse montre que ce TP est uniquement pour l’apprentissage, et ne doit jamais être utilisé en production.

6 - Arborescence
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