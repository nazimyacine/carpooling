# Roulons : application de covoiturage

Projet de software engineering, ESILV. Équipe de 4 personnes, 4 semaines.

Des conducteurs publient leurs trajets. Des passagers les trouvent sur une carte, réservent une ou plusieurs places et discutent avec le conducteur avant le départ. Après le trajet, chacun peut noter les personnes avec qui il a voyagé.

Le projet met l'accent sur la solidité de l'ingénierie plutôt que sur le nombre de fonctionnalités : jamais une place vendue deux fois, des données protégées, une API testée et documentée.

## Fonctionnalités

Un seul compte pour tout le monde : à la connexion, on choisit le mode **passager** ou **conducteur**, et on peut en changer à tout moment depuis le bandeau. Un espace **administrateur** est réservé aux modérateurs.

**Passager**
- Chercher un trajet par ville de départ, ville d'arrivée, date et nombre de places
- Voir les trajets sur une carte
- Réserver une ou plusieurs places, annuler, consulter son historique
- Discuter avec le conducteur

**Conducteur**
- Publier un trajet : villes, point de rendez-vous, horaire, nombre de places, prix
- Modifier ou annuler ses trajets (annuler un trajet annule toutes ses réservations)
- Voir qui a réservé et combien de places restent
- Discuter avec ses passagers

**Notation**
- Une fois le trajet terminé, le passager peut noter le conducteur, et le conducteur chacun de ses passagers (note de 1 à 5, commentaire facultatif)
- On ne note que les personnes avec qui on a réellement voyagé, une seule fois par trajet

**Administrateur**
- Traiter les signalements des utilisateurs
- Masquer un trajet, supprimer un message, suspendre un compte

## Garanties

- **Pas de surréservation** : même si deux passagers réservent la dernière place à la même seconde, un seul l'obtient. Ce scénario est rejoué par un test automatisé.
- **Chacun ne touche qu'à ses données** : le serveur vérifie toujours le propriétaire, indépendamment de ce qu'affiche l'interface.
- **Discussions privées** : seuls le conducteur et les passagers confirmés d'un trajet peuvent lire et écrire dans sa discussion.
- **Données cohérentes** : des contraintes en base de données bloquent les incohérences même en cas de bug dans le code.

## Stack technique

| Partie | Technologies |
|---|---|
| Back | Java 21, Spring Boot 3, Spring Security (JWT), Spring Data JPA, WebSocket (STOMP) |
| Base de données | PostgreSQL 16, migrations Flyway |
| Front | Angular, Leaflet + OpenStreetMap pour la carte |
| Tests | JUnit 5, Testcontainers (PostgreSQL réel) |
| Outillage | Docker Compose, GitHub Actions, Swagger (springdoc) |

## Organisation du dépôt

```
backend/             application Spring Boot (API REST + WebSocket)
frontend/            application Angular
docs/                documents de conception et maquettes
docker-compose.yml   lancement de l'ensemble en une commande
```

## Lancer le projet

> **À compléter** : le code n'existe pas encore. Cette section sera remplie au fur et à mesure.

### Prérequis

_À compléter_ (versions de Java, Node.js, Docker)

### Tout lancer avec Docker Compose

_À compléter_

### Lancer le back seul

_À compléter_

### Lancer le front seul

_À compléter_

### Lancer les tests

_À compléter_ (Docker doit tourner pour les tests d'intégration)

### Documentation de l'API

_À compléter_ : Swagger sera accessible sur http://localhost:8080/swagger-ui.html une fois le back lancé.

### Comptes de démo

_À compléter_

## Documentation

Le dossier [`docs/`](docs/) contient :
- [`conception.pdf`](docs/conception.pdf) : règles métier, architecture, modèle de données, parcours et cycle de vie d'un trajet
- [`maquettes.pdf`](docs/maquettes.pdf) : maquettes de tous les écrans

## Équipe

| Nom | Rôle |
|---|---|
| MARSEILLE Baptiste | _À compléter_ |
| PADELO Etienne | _À compléter_ |
| MOUAKHAR Hela | _À compléter_ |
| BOUGADOUM Nazim | _À compléter_ |
