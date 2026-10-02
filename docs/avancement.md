# Avancement PoolUp

Plan général du projet, étape par étape. **Ce fichier est la mémoire commune de l'équipe** : on le lit après chaque `git pull` pour savoir où on en est, et on le met à jour dans le même commit que le travail qu'il décrit.

Légende : `[x]` travail réalisé (les validations restantes sont précisées dans les notes), `[~]` en cours, `[ ]` à faire. La livraison complète au sens du CLAUDE.md exige aussi la réussite des tests locaux et de la CI.
Colonne « Qui » : prénom de la personne qui a pris l'étape, pour que deux personnes ne fassent pas la même chose.

## Où on en est

- **Dernières étapes terminées** : 1.2 (48 villes, `V2__cities.sql`) et 1.3 (données de démo, profil `demo`). `mvnw verify` réussi en local le 02/10/2026 : 11 tests
- **Phases 0 et 1** : 0.1 à 0.4 et 1.1 à 1.3 terminées ; CI GitHub Actions verte sur `main` (dernier run sur `577344f`, vérifié le 02/10/2026). Restent 0.5 (format d'erreur commun) et 0.6 (Swagger)
- **En cours** : 0.5 puis 2.1 (`auth`), Nazim
- **Prochaine étape** : 0.6 (libre), puis 2.2 et 2.3
- **Points bloquants** : aucun. Sans Java 21 installé, les tests se lancent dans un conteneur (voir « Lancer les tests sans Java 21 » plus bas)

## Phase 0 : socle technique

| #   | Étape | État | Qui | Notes |
|-----|-------|------|-----|-------|
| 0.1 | Squelette Spring Boot 3.5.16 (Maven wrapper, Lombok, package `fr.esilv.poolup`) | [x] | Nazim | Compile. Initializr ne propose plus Boot 3 : `pom.xml` écrit à la main |
| 0.2 | `application.properties` (connexion base, Flyway, `ddl-auto=validate`, UTC) | [x] | Nazim | Démarrage local validé le 02/10/2026 (`docker compose up -d` puis `.\mvnw.cmd spring-boot:run`, HTTP 401 attendu). Flyway applique les migrations au démarrage ; CI verte. `ddl-auto=validate` vérifiera les entités dès qu'il y en aura |
| 0.3 | Test d'intégration de base avec Testcontainers (le contexte démarre sur un vrai PostgreSQL) | [x] | Équipe | Configuration Testcontainers 1.x avec `postgres:16` et `@ServiceConnection`. Tests : contexte + migrations Flyway + 7 tables, villes chargées, contraintes via le script SQL existant. CI verte |
| 0.4 | CI GitHub Actions : `./mvnw verify` à chaque push | [x] | Équipe | Workflow `backend-ci.yml` sur chaque push et pull request : Java 21 Temurin, cache Maven, Docker du runner Ubuntu, `./mvnw --batch-mode --no-transfer-progress verify`, rapports de tests conservés. Wrapper rendu exécutable. Exécutions GitHub Actions réussies sur `main` |
| 0.5 | Module `common` : format d'erreur unique + `@RestControllerAdvice` (400/401/403/404/409) | [~] | Nazim | Commencé le 02/10/2026, avant 2.1 qui en a besoin |
| 0.6 | Swagger / springdoc | [ ] | | Dépendance à ajouter (prévue dans la stack) |

## Phase 1 : schéma de base (Flyway)

| #   | Étape | État | Qui | Notes |
|-----|-------|------|-----|-------|
| 1.1 | `V1__schema.sql` : les 7 tables et toutes les contraintes du CLAUDE.md | [x] | Baptiste | Migration V1 appliquée sur PostgreSQL 16. Script SQL : 25 refus attendus et cas valides vérifiés, rejoué automatiquement par `PoolupApplicationTests` ; CI verte |
| 1.2 | `V2__cities.sql` : liste des villes avec coordonnées | [x] | Etienne | 48 villes (grandes villes françaises + Île-de-France, dont « La Défense » comme dans les maquettes). Ajoute la contrainte `uq_cities_name` (nom unique, sinon la liste déroulante serait ambiguë). Test `citiesAreLoadedByFlyway` + contrôle d'unicité dans `schema_assertions.sql`. `mvnw verify` réussi en local |
| 1.3 | Jeu de données de démo, séparé des scripts de schéma | [x] | Etienne | `db/demo/R__demo_data.sql`, chargé seulement avec le profil `demo` (voir ci-dessous). Personnages et trajets des maquettes, dates relatives au jour du chargement. Test `DemoDataTests` : cohérence avec les règles 1, 2, 5, 6, 7, mot de passe BCrypt, rechargement sans doublon. **Si le schéma évolue (nouvelle colonne obligatoire…), mettre ce script à jour dans le même commit** |

### Vérification du schéma (1.1)

Le script `backend/src/test/resources/db/schema_assertions.sql` vérifie les contraintes de places, prix, statuts, coordonnées, unicité des emails, réservations confirmées et notes, ainsi que le refus de se noter soi-même. Il vérifie aussi qu'une réservation annulée permet une nouvelle réservation confirmée. Les données de test sont annulées par `ROLLBACK` ; lancer sur une base de développement.

Après le démarrage du backend (qui applique Flyway), depuis la racine du dépôt, sous PowerShell :

```powershell
Get-Content -Raw -Encoding UTF8 backend/src/test/resources/db/schema_assertions.sql | docker compose exec -T db psql -U poolup -d poolup -v ON_ERROR_STOP=1
```

Cette vérification SQL est aussi exécutée automatiquement par `PoolupApplicationTests.schemaConstraintsRejectInvalidData` lors de `cd backend && ./mvnw verify`, sur un conteneur PostgreSQL 16 isolé. Java 21 et Docker doivent être disponibles ; aucun démarrage préalable de Docker Compose n'est nécessaire. Les contrôles métier entre plusieurs tables (participants, droits, trajet terminé) restent à implémenter dans les services. `reports.target_id` n'a pas de clé étrangère car la table cible dépend de `target_type` ; le service admin devra vérifier la cible.

### Données de démo (1.3)

```bash
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

- Comptes : `admin@demo.poolup.fr` (ADMIN), `karim@`, `ines@`, `hugo@`, `sofia@`, `camille@` (conducteurs), `lea@`, `theo@`, `paul@`, `max@` (passagers), `nina@` (suspendue), tous en `@demo.poolup.fr`, mot de passe `demo1234`.
- Le script est une migration Flyway **répétable** (`R__`) : elle passe après les scripts de schéma et est rejouée à chaque modification du fichier. Elle supprime d'abord les anciens comptes `@demo.poolup.fr` et tout ce qui leur est lié, puis recrée les données ; le reste de la base n'est pas touché.
- Les dates sont calculées au moment du chargement : pour « rafraîchir » la démo (trajets à venir redevenus passés), modifier le script ou repartir d'une base vide (`docker compose down -v`, qui efface **toute** la base locale).
- `application.properties` contient `spring.flyway.ignore-migration-patterns=*:future,repeatable:missing` pour que l'application démarre aussi sans le profil `demo` sur une base où la démo a été chargée.

### Lancer les tests sans Java 21

Docker Desktop doit tourner. Depuis `backend/`, en Git Bash : le JDK 21 est fourni par un conteneur, Testcontainers utilise le Docker de la machine.

```bash
MSYS_NO_PATHCONV=1 docker run --rm -v //var/run/docker.sock:/var/run/docker.sock -v "$(pwd -W):/app" -v poolup-m2:/root/.m2 -w /app -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal eclipse-temurin:21-jdk sh ./mvnw -B -ntp verify
```

## Phase 2 : comptes

| #   | Étape | État | Qui | Notes |
|-----|-------|------|-----|-------|
| 2.1 | `auth` : inscription, connexion, JWT, BCrypt, `SecurityConfig`, compte `SUSPENDED` refusé | [~] | Nazim | Commencé le 02/10/2026 |
| 2.2 | `users` : consulter / modifier son profil et sa voiture | [ ] | | |
| 2.3 | `cities` : endpoint de liste des villes | [ ] | | |

## Phase 3 : modules métier (parallélisables une fois la phase 2 faite)

| #   | Étape | État | Qui | Notes |
|-----|-------|------|-----|-------|
| 3.1 | `trips` : publier (validation), chercher, modifier (règle 3), annuler (règle 6) | [ ] | | |
| 3.2 | `trips` : tâche `@Scheduled` qui passe les trajets partis à `COMPLETED` | [ ] | | |
| 3.3 | `bookings` : réserver en une requête atomique (règles 1 et 2), annuler (FULL -> OPEN) | [ ] | | |
| 3.4 | `bookings` : test de concurrence (1 place, 2 réservations parallèles : 201 + 409) | [ ] | | Test clé pour le jury |
| 3.5 | `chat` : messages REST + WebSocket STOMP, accès réservé (règle 5, aussi sur le WebSocket) | [ ] | | |
| 3.6 | `ratings` : noter après un trajet terminé (règle 7), moyenne calculée à la lecture | [ ] | | |
| 3.7 | `admin` : signalements, sanctions (suspendre un compte), routes `/api/admin/**` | [ ] | | |

## Phase 4 : front Angular

| #   | Étape | État | Qui | Notes |
|-----|-------|------|-----|-------|
| 4.1 | Squelette Angular, intercepteur JWT, guards (connecté, admin) | [ ] | | |
| 4.2 | Connexion / inscription, choix du mode passager / conducteur | [ ] | | |
| 4.3 | Passager : recherche + carte Leaflet, détail du trajet, mes réservations | [ ] | | |
| 4.4 | Conducteur : tableau de bord, publier / modifier un trajet | [ ] | | |
| 4.5 | Messages (client STOMP) | [ ] | | |
| 4.6 | Notes (formulaires passager et conducteur) | [ ] | | Écrans à dessiner (voir points ouverts du CLAUDE.md) |
| 4.7 | Admin : signalements, utilisateurs, trajets | [ ] | | |

## Phase 5 : livraison

| #   | Étape | État | Qui | Notes |
|-----|-------|------|-----|-------|
| 5.1 | Docker Compose complet (front + back + base en une commande) | [ ] | | |
| 5.2 | Données de démo chargées, scénario de démo répété | [ ] | | |
| 5.3 | Documentation à jour (`docs/`, README, points ouverts tranchés) | [ ] | | |
