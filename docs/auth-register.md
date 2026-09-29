# S1-02 — Inscription utilisateur

Branche : `feature/auth-register`.

## API

`POST http://localhost:8081/api/auth/register`

Content-Type : `application/json`

```json
{
  "firstName": "Utilisateur",
  "lastName": "Démo",
  "email": "demo@example.com",
  "password": "Exemple-Test-123!"
}
```

Réponse 201 : `id`, `firstName`, `lastName`, `email`, `role` et `enabled`. Aucun mot de passe ni hash dans la réponse.

- Prénom et nom obligatoires, 100 caractères maximum chacun, espaces extérieurs retirés.
- E-mail obligatoire, valide, 150 caractères maximum, normalisé en minuscules et unique.
- Mot de passe obligatoire, 8 caractères minimum, 72 octets UTF-8 maximum (limite BCrypt). Les espaces du mot de passe sont conservés.
- Rôle attribué exclusivement par le serveur : `USER`. `ADMIN` est réservé à une future gestion des rôles ; l'inscription publique ne le donne jamais.
- 400 : JSON ou données invalides. Les erreurs de validation sont dans `errors`.
- 409 : adresse e-mail déjà utilisée.
- CORS autorise `http://localhost:4200`.

## Organisation

`AuthController` → `RegistrationService` → `UserRepository` → table `users`.
`User` est une entité JPA ; `Role` est une enum stockée comme texte.
Une contrainte unique protège l'e-mail, y compris lors d'inscriptions simultanées.
Le mot de passe est haché avec BCrypt (coût 12), via Spring Security Crypto.

## Lancement local

Recharger Maven dans IntelliJ et redémarrer `SimulateurBackendApplication` avec `DB_PASSWORD` déjà défini dans sa configuration d'exécution. La configuration actuelle `ddl-auto=update` crée la table au démarrage sur PostgreSQL. Les migrations versionnées restent à mettre en place avant un déploiement.

## Tests

Depuis `backend/simulateur-backend` :

```powershell
.\mvnw.cmd test
```

Les tests utilisent H2 en mémoire en mode PostgreSQL, uniquement dans `src/test/resources/application.properties`. Ils n'utilisent ni le mot de passe ni les données PostgreSQL locales. Ils vérifient l'API, la persistance, le hash, les doublons, la validation, le rôle imposé et CORS. Une vérification sur PostgreSQL réel reste nécessaire avant intégration dans la branche stable.

## Portée

Cette étape implémente l'API d'inscription. Elle ne crée pas de session ou de JWT. Le formulaire Angular, la connexion, la déconnexion, la vérification d'e-mail et la gestion des rôles viendront dans les étapes suivantes.

Référence : https://docs.spring.io/spring-security/reference/7.0/features/authentication/password-storage.html

La table users remplace le mapping précédent app_users. Aucune donnée existante n’est supprimée ou migrée automatiquement. created_at est renseigné par @PrePersist avec LocalDateTime ; enabled vaut true lors de l’inscription.
