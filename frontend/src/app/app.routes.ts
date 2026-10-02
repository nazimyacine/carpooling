import { Routes } from '@angular/router';
import { authGuard, adminGuard } from './core/auth.guards';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./auth/auth-page').then(m => m.AuthPage) },
  { path: 'register', loadComponent: () => import('./auth/auth-page').then(m => m.AuthPage), data: { register: true } },
  { path: 'passenger', canActivate: [authGuard], loadComponent: () => import('./home/home').then(m => m.Home), data: { area: 'Passager', description: 'La recherche de trajets et vos réservations seront disponibles ici.' } },
  { path: 'driver', canActivate: [authGuard], loadComponent: () => import('./home/home').then(m => m.Home), data: { area: 'Conducteur', description: 'La publication et le suivi de vos trajets seront disponibles ici.' } },
  { path: 'admin', canActivate: [adminGuard], loadComponent: () => import('./home/home').then(m => m.Home), data: { area: 'Administration', description: 'La gestion des signalements, utilisateurs et trajets sera disponible ici.' } },
  { path: '', pathMatch: 'full', redirectTo: 'login' },
  { path: '**', redirectTo: 'login' }
];
