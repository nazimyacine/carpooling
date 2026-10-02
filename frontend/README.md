# PoolUp — Front Angular

Angular 21.2, composants standalone, TypeScript strict. Node.js 24 LTS et npm sont recommandés.

## Développement

Depuis la racine du dépôt, lancer la base et le backend dans un premier terminal :

```bash
docker compose up -d
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Dans un autre terminal :

```bash
cd frontend
npm ci
npm start
```

Ouvrir http://localhost:4200. Le serveur Angular relaie `/api/**` vers http://localhost:8080 via `proxy.conf.json` : aucun réglage CORS n'est nécessaire pour ce parcours local. En production, le serveur web devra également relayer `/api/` au backend et servir `index.html` pour les routes Angular (phase 5.1).

Comptes de démo : `lea@demo.poolup.fr` (passager), `karim@demo.poolup.fr` (conducteur), `admin@demo.poolup.fr` (administration), mot de passe `demo1234`.

## Validation

```bash
npm run build
npm test -- --watch=false
```

Le build est généré dans `dist/poolup/browser`. Les tests utilisent Vitest et jsdom, sans navigateur ni backend à démarrer.

## Périmètre actuel (4.1 et 4.2)

- `/login` et `/register` : formulaires validés, erreurs de l'API, choix passager/conducteur.
- `/passenger` et `/driver` : pages d'accueil protégées, à compléter aux étapes 4.3 et 4.4.
- `/admin` : page d'accueil réservée au rôle ADMIN, à compléter en 4.7.
- JWT dans `sessionStorage` (durée de la session de l'onglet) ; mode dans `localStorage`, indépendant du rôle.
- Intercepteur JWT uniquement pour les URLs relatives `/api/`, hors connexion/inscription. Réponse 401 sur une route privée : session supprimée, retour à la connexion.
- Guards : validation du compte via `/api/auth/me` à chaque navigation protégée ; rôle admin lu dans cette réponse, pas dans les données mémorisées.
- Bandeau : changement de mode et déconnexion. La préférence de mode reste mémorisée après déconnexion.

Le serveur garde la responsabilité des autorisations et des validations métier. Les espaces métier affichent une page d'attente jusqu'aux prochaines étapes.
