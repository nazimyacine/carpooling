import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, of, tap } from 'rxjs';

export type TravelMode = 'passenger' | 'driver';
export interface User {
  id: number; email: string; firstName: string; lastName: string;
  carModel: string | null; role: 'USER' | 'ADMIN';
}
export interface AuthResponse { token: string; expiresAt: string; user: User }
export interface RegisterRequest { email: string; password: string; firstName: string; lastName: string }

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly sessionKey = 'poolup.session';
  readonly user = signal<User | null>(null);
  readonly mode = signal<TravelMode>(localStorage.getItem('poolup.mode') === 'driver' ? 'driver' : 'passenger');
  private session: AuthResponse | null = this.restore();

  private restore(): AuthResponse | null {
    try {
      const saved: AuthResponse = JSON.parse(sessionStorage.getItem(this.sessionKey) ?? 'null');
      if (saved && typeof saved.token === 'string' && Date.parse(saved.expiresAt) > Date.now()) {
        return saved;
      }
    } catch { /* Invalid browser data is treated as a signed-out session. */ }
    sessionStorage.removeItem(this.sessionKey);
    return null;
  }

  token(): string | null {
    if (this.session && Date.parse(this.session.expiresAt) <= Date.now()) this.clear();
    return this.session?.token ?? null;
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', { email, password }).pipe(tap(response => this.save(response)));
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/register', request).pipe(tap(response => this.save(response)));
  }

  private save(response: AuthResponse): void {
    this.session = response;
    sessionStorage.setItem(this.sessionKey, JSON.stringify(response));
    this.user.set(response.user);
  }

  // Revalidate the account on protected navigation, including after a reload or suspension.
  verify(): Observable<User | null> {
    if (!this.token()) return of(null);
    return this.http.get<User>('/api/auth/me').pipe(tap(user => this.user.set(user)));
  }

  setMode(mode: TravelMode): void {
    this.mode.set(mode);
    localStorage.setItem('poolup.mode', mode);
  }

  clear(): void {
    this.session = null;
    this.user.set(null);
    sessionStorage.removeItem(this.sessionKey);
  }

  home(): string {
    return this.user()?.role === 'ADMIN' ? '/admin' : this.mode() === 'driver' ? '/driver' : '/passenger';
  }
}
