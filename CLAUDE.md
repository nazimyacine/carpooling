# Roulons : application de covoiturage

Projet de software engineering ESILV, équipe de 4 personnes qui travaillent en parallèle sur le même dépôt, 4 semaines.
Des conducteurs publient leurs trajets ; des passagers les trouvent sur une carte, réservent une ou plusieurs places et discutent avec le conducteur avant le départ.

**Le projet est jugé sur la solidité de l'ingénierie**, pas sur le nombre de fonctionnalités : jamais une place vendue deux fois, des données protégées, une API testée et documentée. En cas de doute, choisir la solution la plus simple et la plus robuste.

Documents de conception dans `docs/` (à lire avant de toucher à un module) :
- `docs/conception.pdf` : règles métier, architecture, modèle de données, parcours et états
- `docs/maquettes.pdf` : maquettes de tous les écrans (référence visuelle du front)

## Stack

- **Back** : Java 21, Spring Boot 3, Spring Security (JWT), Spring Data JPA, Spring WebSocket (STOMP)
- **Base** : PostgreSQL 16, schéma versionné par Flyway
- **Front** : Angular (composants standalone), Leaflet + tuiles OpenStreetMap pour la carte, client STOMP pour le chat
- **Tests** : JUnit 5, Testcontainers (vrai PostgreSQL dans un conteneur)
- **Outillage** : Docker Compose (front, back et base en une commande), GitHub Actions (build + tests à chaque push), Swagger / springdoc (documentation de l'API générée depuis le code)

## Organisation du dépôt

```
backend/     application Spring Boot
frontend/    application Angular
docs/        conception, maquettes, décisions
docker-compose.yml
```

## Architecture back

Une seule application Spring Boot découpée en modules (un package par module) :

| Module     | Rôle                                   |
|------------|----------------------------------------|
| `auth`     | inscription, connexion, émission du JWT |
| `users`    | profil, voiture                        |
| `trips`    | publier, chercher, modifier, annuler   |
| `bookings` | réserver sans surréserver, annuler     |
| `chat`     | messages par trajet (REST + WebSocket) |
| `ratings`  | notes après un trajet terminé          |
| `admin`    | signalements, sanctions                |
| `cities`   | villes et coordonnées                  |
| `common`   | gestion des erreurs, DTO partagés      |

Dans chaque module, trois couches :
- **Controller** : reçoit la requête HTTP, valide l'entrée, renvoie des DTO (jamais les entités JPA)
- **Service** : applique les règles métier, dans une transaction (`@Transactional`)
- **Repository** : Spring Data JPA

Les erreurs passent par un `@RestControllerAdvice` commun dans `common`, avec un format de réponse unique. Codes HTTP attendus : 400 (entrée invalide), 401 (non connecté), 403 (pas le droit), 404 (introuvable), 409 (conflit métier, ex. plus assez de places).

## Modèle de données (7 tables)

- `users` : email unique, password_hash, first_name, last_name, car_model, role `USER | ADMIN`, status `ACTIVE | SUSPENDED`, created_at
- `cities` : name, latitude, longitude. **Chargée par Flyway**, le conducteur choisit dans cette liste (pas de service de géocodage externe)
- `trips` : driver_id, departure_city_id, arrival_city_id, meeting_point, departure_at, seats_total, seats_available, price_per_seat, status `OPEN | FULL | COMPLETED | CANCELLED`, description, created_at
- `bookings` : trip_id, passenger_id, seats, status `CONFIRMED | CANCELLED`, created_at, cancelled_at
- `messages` : trip_id, sender_id, content, sent_at (une discussion = les messages d'un trajet)
- `ratings` : trip_id, rater_id (qui note), rated_id (qui est noté), score (1 à 5), comment (facultatif), created_at
- `reports` : reporter_id, target_type `TRIP | USER | MESSAGE`, target_id, reason, status `OPEN | RESOLVED`, created_at

Contraintes posées **en base** dans les scripts Flyway (filet de sécurité même si le code a un bug) :
- `0 <= seats_available <= seats_total`
- `seats_total` entre 1 et 8, `price_per_seat > 0`
- une seule réservation `CONFIRMED` par passager et par trajet (index unique partiel)
- `ratings` : `score` entre 1 et 5, `rater_id <> rated_id`, et une seule note par trajet pour un même couple (qui note, qui est noté) : contrainte unique sur `(trip_id, rater_id, rated_id)`

Toute évolution du schéma passe par un **nouveau** script Flyway (`V<n>__description.sql`). Ne jamais modifier un script déjà fusionné sur `main`.

## Règles métier (celles que le jury va tester)

1. **Jamais plus de réservations que de places**, même si deux passagers cliquent à la même seconde. Le décompte se fait en une seule requête qui vérifie et décrémente en même temps :
   ```sql
   UPDATE trips SET seats_available = seats_available - :n
   WHERE id = :id AND status = 'OPEN' AND seats_available >= :n
   ```
   0 ligne modifiée = plus assez de places, on répond 409. La création de la réservation est dans la même transaction. Si `seats_available` tombe à 0, le trajet passe à `FULL`.
2. **On ne réserve pas n'importe quoi** : ni son propre trajet, ni un trajet complet, annulé ou déjà parti.
3. **Le conducteur ne peut pas retirer des places déjà prises** : `seats_total` ne descend jamais sous le nombre de places réservées.
4. **Chacun ne touche qu'à ses données** : le serveur vérifie toujours le propriétaire, même si le front cache déjà le bouton.
5. **La discussion d'un trajet est privée** : seuls le conducteur et les passagers avec une réservation confirmée peuvent la lire et y écrire (vérifié aussi sur le WebSocket, pas seulement en REST).
6. **Annuler un trajet annule toutes ses réservations**, dans la même transaction ; les passagers le voient dans leur historique.
7. **On ne note que les personnes avec qui on a réellement voyagé**, et seulement une fois le trajet terminé :
   - le trajet doit être `COMPLETED` (pas `OPEN`, `FULL` ni `CANCELLED`) ;
   - un passager avec une réservation confirmée sur ce trajet peut noter le conducteur ;
   - le conducteur peut noter chacun des passagers confirmés de ce trajet ;
   - on ne se note jamais soi-même, et une seule note par personne notée et par trajet (une deuxième tentative renvoie 409) ;
   - une note est définitive : pas de modification ni de suppression par l'utilisateur.

   La note moyenne d'un utilisateur et son nombre de notes sont calculés à la lecture à partir de `ratings` (pas de colonne de moyenne stockée dans `users`, pour éviter qu'elle se désynchronise).

Cycle de vie d'un trajet : `OPEN -> FULL` (dernière place réservée), `FULL -> OPEN` (une réservation annulée), `OPEN/FULL -> CANCELLED` (le conducteur annule), `OPEN/FULL -> COMPLETED` (heure de départ passée, tâche planifiée `@Scheduled` chaque nuit).

Validation à la publication d'un trajet : date de départ dans le futur, villes de départ et d'arrivée différentes, entre 1 et 8 places, prix positif.

## Sécurité

- Deux rôles seulement : `USER` et `ADMIN`. **Passager / conducteur n'est pas un rôle** : c'est un mode d'affichage côté front, choisi à la connexion, mémorisé dans le navigateur et modifiable à tout moment depuis le bandeau.
- Authentification par JWT, mots de passe hachés (BCrypt).
- Un compte `SUSPENDED` ne peut plus se connecter.
- Les routes `/api/admin/**` sont réservées au rôle `ADMIN`.

## Front

- Écrans (voir `docs/maquettes.pdf`) : connexion / inscription ; passager : recherche + carte, détail du trajet, mes réservations (avec « Noter le conducteur » sur les trajets terminés) ; conducteur : tableau de bord, publier / modifier un trajet, noter les passagers d'un trajet terminé ; messages ; admin : signalements, utilisateurs, trajets.
- Un intercepteur HTTP ajoute le JWT à chaque appel vers l'API ; des guards protègent les routes (connecté, admin).
- Le front masque ce que l'utilisateur ne peut pas faire, mais **ne remplace jamais** les vérifications du back.

## Tests

- Tests unitaires sur les services (règles métier).
- Tests d'intégration avec Testcontainers sur un vrai PostgreSQL, en particulier le **test de concurrence** : il reste une place, deux réservations lancées en parallèle, une seule doit réussir (201), l'autre reçoit 409.
- Chaque règle métier ci-dessus a au moins un test. Pour les notes : refus sur un trajet non terminé, refus si on n'a pas voyagé ensemble, refus de se noter soi-même, refus d'une deuxième note (409).
- Un jeu de données de démo (utilisateurs, trajets, réservations, messages) est chargé pour la démo, séparé des scripts de schéma.

## Travail en équipe et Git

**Nous sommes 4 à travailler en même temps sur ce dépôt.** Le code local peut donc être en retard sur ce que les autres ont poussé.

- **Une seule branche : `main`.** Tout le monde pousse directement dessus, pas de branches de fonctionnalité ni de pull requests.
- **Toujours commencer par `git pull`**, puis **lire le code existant** des modules concernés avant d'écrire quoi que ce soit : un coéquipier a peut-être déjà créé la classe, le DTO, l'endpoint ou le script Flyway dont on a besoin. Ne jamais recréer ou dupliquer ce qui existe déjà.
- **Refaire `git pull` juste avant chaque `git push`**, et relancer les tests si le pull a ramené des changements.
- Commits petits et fréquents, pour limiter les conflits.
- En cas de conflit : ne jamais écraser le travail d'un autre (pas de `git push --force`, pas de résolution qui jette ses modifications sans vérifier). Signaler le conflit et le résoudre fichier par fichier.
- Scripts Flyway : avant d'en créer un, vérifier après un pull quel est le dernier numéro utilisé, pour que deux personnes ne créent pas le même `V<n>`.
- Commits au format Conventional Commits : `feat(bookings): ...`, `fix(trips): ...`, `test(...)`, `docs(...)`.

### Définition de terminé

Une tâche est terminée quand : le code compile, les tests passent en local puis dans la CI après le push, la règle métier concernée est testée, l'endpoint apparaît correctement dans Swagger, et le code est poussé sur `main`.

## Façon de travailler (consignes pour Claude)

- **Au début de chaque session**, faire `git pull` puis lire le code existant des modules concernés (voir « Travail en équipe et Git »). Ne rien écrire avant.
- **Avant de coder**, demander les fichiers et informations nécessaires plutôt que de coder sur des hypothèses. Rassembler le contexte d'abord, coder une seule fois.
- **Avancer par petites étapes**, avec un commit à chaque étape. Ne pas tout livrer d'un bloc.
- **Un fichier à la fois** : après chaque fichier, indiquer une commande de contrôle à lancer (compilation, test...) et attendre le retour avant de passer au suivant.
- Pour une modification, indiquer précisément le fichier et l'emplacement, et montrer le fichier ou le bloc complet dans son état final, pas des fragments à recoller.
- Ne pas ajouter de dépendance, de module ou de fonctionnalité hors périmètre sans le signaler et demander d'abord.
- Expliquer les choix en langage clair, en définissant les termes techniques.

## Commandes

```bash
docker compose up -d                  # base (et à terme front + back)
cd backend && ./mvnw spring-boot:run  # lancer le back
cd backend && ./mvnw verify           # tests (Docker doit tourner pour Testcontainers)
cd frontend && npm install && npm start
```

Swagger : http://localhost:8080/swagger-ui.html

## Hors MVP (seulement s'il reste du temps)

- GPS en direct pendant le trajet
- Tableau de bord statistique façon Power BI
- Recherche d'adresses via Nominatim

## Points ouverts (à trancher en équipe)

- **Notes : absentes des maquettes et du document de conception.** Écrans à dessiner (formulaire de note, affichage de la moyenne sur la fiche trajet et le profil) et documents à mettre à jour (7 tables au lieu de 6).
- **Notes : délai pour noter** (ex. 14 jours après le trajet, ou sans limite) ?
- **Notes : signalement d'un commentaire abusif** : ajouter `RATING` aux `target_type` de `reports` pour que l'admin puisse supprimer un commentaire ?

- **Masquer un trajet (admin)** : aucun statut prévu dans `trips` pour ça. Ajouter un champ `hidden` ou un statut dédié ?
- **Messages non lus** (tableau de bord conducteur, badge dans les discussions) : rien dans le modèle pour savoir ce qui a été lu. Ajouter une table de dernière lecture par utilisateur et par trajet, ou retirer cet affichage.
- **Note interne sur un signalement** (écran admin) : pas de colonne dans `reports`. Ajouter `admin_note` ?
- **Mot de passe oublié** (lien sur l'écran de connexion) : hors MVP ou à implémenter ?
- Versions exactes (Java, Spring Boot, Angular, PostgreSQL) et outil de build (Maven ou Gradle) à confirmer.
