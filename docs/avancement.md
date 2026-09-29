# Avancement PoolUp

Plan général du projet, étape par étape. **Ce fichier est la mémoire commune de l'équipe** : on le lit après chaque `git pull` pour savoir où on en est, et on le met à jour dans le même commit que le travail qu'il décrit.

Légende : `[x]` terminé (au sens de la « Définition de terminé » du CLAUDE.md), `[~]` en cours ou partiellement vérifié, `[ ]` à faire.
Colonne « Qui » : prénom de la personne qui a pris l'étape, pour que deux personnes ne fassent pas la même chose.

## Où on en est

- **Dernière étape faite** : 0.2 (configuration de la base écrite, démarrage pas encore vérifié)
- **Prochaine étape** : vérifier le démarrage avec Docker (0.2), puis 1.1 (schéma Flyway)
- **Points bloquants** : aucun

## Phase 0 : socle technique

| #   | Étape | État | Qui | Notes |
|-----|-------|------|-----|-------|
| 0.1 | Squelette Spring Boot 3.5.16 (Maven wrapper, Lombok, package `fr.esilv.poolup`) | [x] | Nazim | Compile. Initializr ne propose plus Boot 3 : `pom.xml` écrit à la main |
| 0.2 | `application.properties` (connexion base, Flyway, `ddl-auto=validate`, UTC) | [~] | Nazim | À vérifier : `docker compose up -d` puis `.\mvnw.cmd spring-boot:run` |
| 0.3 | Test d'intégration de base avec Testcontainers (le contexte démarre sur un vrai PostgreSQL) | [ ] | | Réécrire `TestcontainersConfiguration` pour Testcontainers 1.x (Boot 3.5), image `postgres:16` |
| 0.4 | CI GitHub Actions : `./mvnw verify` à chaque push | [ ] | | |
| 0.5 | Module `common` : format d'erreur unique + `@RestControllerAdvice` (400/401/403/404/409) | [ ] | | |
| 0.6 | Swagger / springdoc | [ ] | | Dépendance à ajouter (prévue dans la stack) |

## Phase 1 : schéma de base (Flyway)

| #   | Étape | État | Qui | Notes |
|-----|-------|------|-----|-------|
| 1.1 | `V1__schema.sql` : les 7 tables et toutes les contraintes du CLAUDE.md | [ ] | | Bloque tous les modules : à faire en priorité |
| 1.2 | `V2__cities.sql` : liste des villes avec coordonnées | [ ] | | |
| 1.3 | Jeu de données de démo, séparé des scripts de schéma | [ ] | | Mieux après les modules auth + trips |

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
