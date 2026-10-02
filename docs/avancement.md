# Avancement PoolUp

Plan général du projet, étape par étape. **Ce fichier est la mémoire commune de l'équipe** : on le lit après chaque `git pull` pour savoir où on en est, et on le met à jour dans le même commit que le travail qu'il décrit.

Légende : `[x]` travail réalisé (les validations restantes sont précisées dans les notes), `[~]` en cours, `[ ]` à faire. La livraison complète au sens du CLAUDE.md exige aussi la réussite des tests locaux et de la CI.
Colonne « Qui » : prénom de la personne qui a pris l'étape, pour que deux personnes ne fassent pas la même chose.

## Où on en est

- **Dernières étapes implémentées** : 0.3 et 0.4 (tests PostgreSQL 16 et CI GitHub Actions ajoutés le 02/10/2026 ; exécution à valider)
- **Prochaine étape** : 1.2 (liste des villes avec coordonnées dans `V2__cities.sql`)
- **Points bloquants** : Java 21 et Docker absents de l'environnement de travail du 02/10/2026 ; tests locaux non exécutés. Les commits des étapes 0.3/0.4 sont désormais présents sur `origin/main` ; résultat de la CI non vérifié

## Phase 0 : socle technique

| #   | Étape | État | Qui | Notes |
|-----|-------|------|-----|-------|
| 0.1 | Squelette Spring Boot 3.5.16 (Maven wrapper, Lombok, package `fr.esilv.poolup`) | [x] | Nazim | Compile. Initializr ne propose plus Boot 3 : `pom.xml` écrit à la main |
| 0.2 | `application.properties` (connexion base, Flyway, `ddl-auto=validate`, UTC) | [~] | Nazim | Démarrage local validé le 02/10/2026 avec `docker compose up -d` et `.\mvnw.cmd spring-boot:run` : PostgreSQL healthy, application démarrée, HTTP 401 attendu. Validation CI en attente de 0.4 ; aucune migration ni entité à vérifier pour l'instant |
| 0.3 | Test d'intégration de base avec Testcontainers (le contexte démarre sur un vrai PostgreSQL) | [x] | Équipe | Configuration Testcontainers 1.x avec `postgres:16` et `@ServiceConnection`. Deux tests : contexte + migrations Flyway + 7 tables, puis contraintes via le script SQL existant. `mvnw verify` bloqué localement : Java et Docker absents ; validation CI attendue |
| 0.4 | CI GitHub Actions : `./mvnw verify` à chaque push | [x] | Équipe | Workflow `backend-ci.yml` sur chaque push et pull request : Java 21 Temurin, cache Maven, Docker du runner Ubuntu, `./mvnw --batch-mode --no-transfer-progress verify`, rapports de tests conservés. Wrapper rendu exécutable. YAML et `git diff --check` validés ; première exécution GitHub Actions à confirmer |
| 0.5 | Module `common` : format d'erreur unique + `@RestControllerAdvice` (400/401/403/404/409) | [ ] | | |
| 0.6 | Swagger / springdoc | [ ] | | Dépendance à ajouter (prévue dans la stack) |

## Phase 1 : schéma de base (Flyway)

| #   | Étape | État | Qui | Notes |
|-----|-------|------|-----|-------|
| 1.1 | `V1__schema.sql` : les 7 tables et toutes les contraintes du CLAUDE.md | [~] | Équipe | Migration V1 appliquée sur PostgreSQL 16, démarrage Spring Boot et `mvnw verify` réussis. Script SQL : 25 refus attendus et cas valides vérifiés ; automatisation Testcontainers (0.3) et workflow CI (0.4) ajoutés ; résultat de la CI à confirmer |
| 1.2 | `V2__cities.sql` : liste des villes avec coordonnées | [ ] | | |
| 1.3 | Jeu de données de démo, séparé des scripts de schéma | [ ] | | Mieux après les modules auth + trips |

### Vérification du schéma (1.1)

Le script `backend/src/test/resources/db/schema_assertions.sql` vérifie les contraintes de places, prix, statuts, coordonnées, unicité des emails, réservations confirmées et notes, ainsi que le refus de se noter soi-même. Il vérifie aussi qu'une réservation annulée permet une nouvelle réservation confirmée. Les données de test sont annulées par `ROLLBACK` ; lancer sur une base de développement.

Après le démarrage du backend (qui applique Flyway), depuis la racine du dépôt, sous PowerShell :

```powershell
Get-Content -Raw -Encoding UTF8 backend/src/test/resources/db/schema_assertions.sql | docker compose exec -T db psql -U poolup -d poolup -v ON_ERROR_STOP=1
```

Cette vérification SQL est aussi exécutée automatiquement par `PoolupApplicationTests.schemaConstraintsRejectInvalidData` lors de `cd backend && ./mvnw verify`, sur un conteneur PostgreSQL 16 isolé. Java 21 et Docker doivent être disponibles ; aucun démarrage préalable de Docker Compose n'est nécessaire. Les contrôles métier entre plusieurs tables (participants, droits, trajet terminé) restent à implémenter dans les services. `reports.target_id` n'a pas de clé étrangère car la table cible dépend de `target_type` ; le service admin devra vérifier la cible.

## Phase 2 : comptes

| #   | Étape | État | Qui | Notes |
|-----|-------|------|-----|-------|
| 2.1 | `auth` : inscription, connexion, JWT, BCrypt, `SecurityConfig`, compte `SUSPENDED` refusé | [ ] | | |
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
