# Avancement PoolUp

Plan général du projet, étape par étape. **Ce fichier est la mémoire commune de l'équipe** : on le lit après chaque `git pull` pour savoir où on en est, et on le met à jour dans le même commit que le travail qu'il décrit.

Légende : `[x]` travail réalisé (les validations restantes sont précisées dans les notes), `[~]` en cours, `[ ]` à faire. La livraison complète au sens du CLAUDE.md exige aussi la réussite des tests locaux et de la CI.
Colonne « Qui » : prénom de la personne qui a pris l'étape, pour que deux personnes ne fassent pas la même chose.

## Où on en est

- **Phases 0 et 1** : tout est terminé sauf 0.6 (Swagger)
- **Dernières étapes terminées** : 3.3 et 3.4 (`bookings`, Nazim) le 02/10/2026, `mvnw verify` local vert (68 tests)
- **En cours** : 3.7 (`admin`, Etienne). Disponible : 0.6 (Swagger)
- **Points bloquants** : aucun. Sans Java 21 installé, les tests se lancent dans un conteneur (voir « Lancer les tests sans Java 21 » plus bas)

## Phase 0 : socle technique

| #   | Étape | État | Qui | Notes |
|-----|-------|------|-----|-------|
| 0.1 | Squelette Spring Boot 3.5.16 (Maven wrapper, Lombok, package `fr.esilv.poolup`) | [x] | Nazim | Compile. Initializr ne propose plus Boot 3 : `pom.xml` écrit à la main |
| 0.2 | `application.properties` (connexion base, Flyway, `ddl-auto=validate`, UTC) | [x] | Nazim | Démarrage local validé le 02/10/2026 (`docker compose up -d` puis `.\mvnw.cmd spring-boot:run`, HTTP 401 attendu). Flyway applique les migrations au démarrage ; CI verte. `ddl-auto=validate` vérifiera les entités dès qu'il y en aura |
| 0.3 | Test d'intégration de base avec Testcontainers (le contexte démarre sur un vrai PostgreSQL) | [x] | Équipe | Configuration Testcontainers 1.x avec `postgres:16` et `@ServiceConnection`. Tests : contexte + migrations Flyway + 7 tables, villes chargées, contraintes via le script SQL existant. CI verte |
| 0.4 | CI GitHub Actions : `./mvnw verify` à chaque push | [x] | Équipe | Workflow `backend-ci.yml` sur chaque push et pull request : Java 21 Temurin, cache Maven, Docker du runner Ubuntu, `./mvnw --batch-mode --no-transfer-progress verify`, rapports de tests conservés. Wrapper rendu exécutable. Exécutions GitHub Actions réussies sur `main` |
| 0.5 | Module `common` : format d'erreur unique + `@RestControllerAdvice` (400/401/403/404/409) | [x] | Nazim | Format standard `ProblemDetail` (RFC 9457) : `status`, `title`, `detail`, `instance`, plus `errors` (champ -> message) pour un 400. Dans les services : `throw ApiException.conflict("...")` (aussi `badRequest`, `unauthorized`, `forbidden`, `notFound`). Contrainte de base violée -> 409 (filet de sécurité), erreur imprévue -> 500 sans détail. Les 401/403 levés par les filtres de sécurité seront formatés dans le `SecurityConfig` (2.1). Test `GlobalExceptionHandlerTests` (6 cas, sans Docker) |
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
| 2.1 | `auth` : inscription, connexion, JWT, BCrypt, `SecurityConfig`, compte `SUSPENDED` refusé | [x] | Nazim | `POST /api/auth/register` (201, 409 si email pris), `POST /api/auth/login` (401 identifiants faux, 403 compte suspendu), `GET /api/auth/me`. JWT HS256 via Spring OAuth2 Resource Server, valable 24 h. Entité `User`, `UserRepository`, `UserResponse` créés dans `users` (à réutiliser en 2.2). Test `AuthIntegrationTests` (9 cas) |
| 2.2 | `users` : consulter / modifier son profil et sa voiture | [x] | Nazim | `GET /api/users/me` et `PUT /api/users/me` ajoutés, validation de `firstName` / `lastName` / `carModel`, mise à jour de la voiture et tests d’intégration `UserProfileIntegrationTests` passés |
| 2.3 | `cities` : endpoint de liste des villes | [x] | Équipe | `GET /api/cities` (JWT requis) renvoie les villes Flyway triées par nom : `id`, `name`, `latitude`, `longitude`. Couches JPA / service en lecture seule / contrôleur, réponse DTO. `CityIntegrationTests` : contenu complet et tri, 401 sans jeton, 401 avec jeton invalide. **Tests validés le 02/10/2026** (Etienne) : `mvnw verify` local, 32 tests verts. Visibilité Swagger à vérifier après 0.6 |

### Utiliser l'authentification (2.1)

- Le front envoie le jeton reçu au login dans l'en-tête `Authorization: Bearer <token>`. Toutes les routes demandent un jeton sauf `POST /api/auth/register`, `POST /api/auth/login` et Swagger ; `/api/admin/**` demande le rôle `ADMIN`.
- Dans un contrôleur, l'utilisateur connecté : `@AuthenticationPrincipal Jwt jwt`, puis `Long.valueOf(jwt.getSubject())` (le sujet du jeton est l'id de l'utilisateur). Le jeton contient aussi `email` et `role`.
- Erreurs métier dans un service : `throw ApiException.conflict("...")` (ou `badRequest`, `unauthorized`, `forbidden`, `notFound`), format commun de 0.5.
- Tests d'intégration hors du package racine : `@Import(TestcontainersConfiguration.class)` (classe rendue `public`) ; exemple complet dans `AuthIntegrationTests` (vrais jetons, MockMvc).
- Clé de signature : `poolup.jwt.secret` (variable `JWT_SECRET`, au moins 32 caractères ; la valeur par défaut ne sert qu'en développement). Durée : `JWT_EXPIRATION` (24 h par défaut).
- Limite connue : un jeton déjà émis reste valable jusqu'à son expiration, même si le compte est suspendu entre-temps (à traiter si besoin en 3.7). CORS pour le front Angular à configurer en 4.1.
- Comptes de démo (profil `demo`) : connexion avec `prenom@demo.poolup.fr` / `demo1234`.

## Phase 3 : modules métier (parallélisables une fois la phase 2 faite)

| #   | Étape | État | Qui | Notes |
|-----|-------|------|-----|-------|
| 3.1 | `trips` : publier (validation), chercher, modifier (règle 3), annuler (règle 6) | [x] | Etienne | `POST /api/trips` (201), `GET /api/trips?departureCityId=&arrivalCityId=&date=AAAA-MM-JJ&seats=` (filtres facultatifs, jour en heure de Paris, trajets OPEN/FULL pas encore partis, triés par heure de départ ; FULL inclus sauf si `seats` est donné), `GET /api/trips/{id}`, `GET /api/trips/mine` (tableau de bord conducteur, tous statuts), `PUT /api/trips/{id}` (même corps que la publication), `POST /api/trips/{id}/cancel`. Réponse `TripResponse` : villes avec coordonnées, `seatsBooked`, conducteur réduit à prénom + initiale du nom + voiture (pas d'email). 400 : date passée, villes identiques ou inconnues, places hors 1..8, prix <= 0 ; 403 : pas le conducteur ; 404 ; 409 : places déjà réservées (règle 3), trajet annulé, terminé ou déjà parti. Modifier/annuler verrouille la ligne du trajet (`SELECT ... FOR UPDATE`) : une réservation simultanée attend, aucun décompte écrasé. Annuler passe les réservations `CONFIRMED` en `CANCELLED` (+ `cancelled_at`) dans la même transaction (requête dans `TripRepository`, en attendant le module `bookings`). `TripIntegrationTests` : 19 tests verts en local |
| 3.2 | `trips` : tâche `@Scheduled` qui passe les trajets partis à `COMPLETED` | [x] | Etienne | `TripCompletionJob`, chaque nuit à 3 h heure de Paris (`poolup.trips.completion-cron`, surchargeable par `TRIPS_COMPLETION_CRON`) : une seule requête `UPDATE` passe les trajets OPEN/FULL dont l'heure de départ est passée à `COMPLETED` (ouvre les notes, règle 7). Planification activée par `common/SchedulingConfig`. Entre le départ et le passage de la tâche, un trajet ne peut déjà plus être modifié ni annulé (heure vérifiée) et n'apparaît plus dans la recherche ; **3.3 doit aussi vérifier `departure_at > now()` dans la requête de réservation**. `TripCompletionJobTests` : OPEN/FULL partis -> COMPLETED, annulés et à venir intacts, tâche rejouable, cron enregistré. 3 tests verts en local |
| 3.3 | `bookings` : réserver en une requête atomique (règles 1 et 2), annuler (FULL -> OPEN) | [x] | Nazim | `POST /api/trips/{tripId}/bookings` `{"seats": n}` (201), `GET /api/bookings/mine` (historique passager, réservations annulées comprises, avec le trajet et son statut : règle 6 visible), `POST /api/bookings/{id}/cancel`. Réservation : une seule requête `UPDATE trips ... WHERE status = 'OPEN' AND seats_available >= n AND departure_at > now() AND driver_id <> passager` (0 ligne = 409), passage à `FULL` dans la même requête, puis création de la réservation dans la même transaction. 403 : son propre trajet ; 409 : complet, pas assez de places, annulé, terminé, déjà parti, déjà réservé (aussi garanti par l'index unique, l'échec annule le décompte) ; 400 : places hors 1..8 ; 404. Annulation par le passager seulement (403 sinon), avant le départ, une seule fois (409) : places rendues, `FULL -> OPEN`. Verrouille le trajet avant la réservation, dans le même ordre que l'annulation du trajet par le conducteur (pas d'interblocage). `BookingIntegrationTests` : 11 tests |
| 3.4 | `bookings` : test de concurrence (1 place, 2 réservations parallèles : 201 + 409) | [x] | Nazim | Test clé pour le jury. `BookingConcurrencyTests` : vrai serveur HTTP, requêtes lancées au même instant depuis plusieurs threads, état final vérifié en base. (1) 1 place, 2 passagers : 201 + 409, trajet `FULL`, rejoué 10 fois ; (2) 3 places, 10 passagers : exactement 3 × 201 et 7 × 409 ; (3) double clic du même passager : 1 réservation, places décomptées une seule fois (rejoué 10 fois). Contre-vérifié : avec une version « lire puis écrire » du décompte, le test échoue (deux 201) |
| 3.5 | `chat` : messages REST + WebSocket STOMP, accès réservé (règle 5, aussi sur le WebSocket) | [x] | Baptiste | `GET/POST /api/trips/{tripId}/messages` (JWT requis), accès autorisé au conducteur et aux passagers confirmés ; WebSocket STOMP `/ws` + `/app/chat/{tripId}` avec diffusion sur `/topic/trips/{tripId}`. Tests d’intégration `ChatIntegrationTests` validés (8 tests passés au total sur le périmètre chat/ratings) |
| 3.6 | `ratings` : noter après un trajet terminé (règle 7), moyenne calculée à la lecture | [x] | Baptiste | `POST /api/trips/{tripId}/ratings` + `GET /api/trips/{tripId}/ratings` + `GET /api/users/{userId}/ratings`, validation métier : trajet terminé, personne ayant voyagé avec le noté, pas de self-rate, note unique par trajet/rater/rated, moyenne calculée au moment de la lecture. Intégration validée par `RatingIntegrationTests` |
| 3.7 | `admin` : signalements, sanctions (suspendre un compte), routes `/api/admin/**` | [~] | Etienne | |

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
