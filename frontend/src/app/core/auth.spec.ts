import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, provideRouter, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService, AuthResponse } from './auth.service';
import { authInterceptor } from './auth.interceptor';
import { authGuard, adminGuard } from './auth.guards';

const session: AuthResponse = {
  token: 'test-jwt', expiresAt: new Date(Date.now() + 3600000).toISOString(),
  user: { id: 1, email: 'lea@test.fr', firstName: 'Léa', lastName: 'M', carModel: null, role: 'USER' }
};

describe('Authentication infrastructure', () => {
  let auth: AuthService;
  let http: HttpClient;
  let requests: HttpTestingController;
  beforeEach(() => {
    sessionStorage.clear(); localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpClient); requests = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { requests.verify(); sessionStorage.clear(); localStorage.clear(); });
  function signedIn(): void {
    auth = TestBed.inject(AuthService);
    auth.login(session.user.email, 'password123').subscribe();
    const request = requests.expectOne('/api/auth/login');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(session);
  }
  it('stores the returned session and sends its JWT only to the API', () => {
    signedIn();
    expect(auth.user()?.firstName).toBe('Léa');
    expect(JSON.parse(sessionStorage.getItem('poolup.session')!).token).toBe('test-jwt');
    for (const url of ['/api/cities', 'https://external.example/api/cities', '/assets/data.json', '/api/auth/register']) {
      http.get(url).subscribe();
      const request = requests.expectOne(url);
      expect(request.request.headers.get('Authorization')).toBe(url === '/api/cities' ? 'Bearer test-jwt' : null);
      request.flush({});
    }
  });
  it('restores the token but checks the user on the server instead of trusting cached role', () => {
    sessionStorage.setItem('poolup.session', JSON.stringify({ ...session, user: { ...session.user, role: 'ADMIN' } }));
    auth = TestBed.inject(AuthService);
    expect(auth.user()).toBeNull();
    auth.verify().subscribe(user => expect(user?.role).toBe('USER'));
    requests.expectOne('/api/auth/me').flush(session.user);
  });
  it('rejects an expired stored session', () => {
    sessionStorage.setItem('poolup.session', JSON.stringify({ ...session, expiresAt: '2020-01-01' }));
    auth = TestBed.inject(AuthService);
    expect(auth.token()).toBeNull();
    expect(sessionStorage.getItem('poolup.session')).toBeNull();
  });
  it('ignores malformed browser data', () => {
    sessionStorage.setItem('poolup.session', '{');
    expect(TestBed.inject(AuthService).token()).toBeNull();
  });
  it('clears the session and redirects after an API 401', () => {
    signedIn();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    http.get('/api/cities').subscribe({ error: () => {} });
    requests.expectOne('/api/cities').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.user()).toBeNull(); expect(auth.token()).toBeNull();
    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { expired: '1' } });
  });
  it('preserves a session when a resource returns 403', () => {
    signedIn();
    http.get('/api/admin/users').subscribe({ error: () => {} });
    requests.expectOne('/api/admin/users').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(auth.token()).toBe('test-jwt');
  });
  it('redirects a visitor to login with the requested route', () => {
    const result = TestBed.runInInjectionContext(() => authGuard({} as ActivatedRouteSnapshot, { url: '/driver' } as RouterStateSnapshot)) as Observable<UrlTree>;
    result.subscribe(tree => expect(TestBed.inject(Router).serializeUrl(tree)).toBe('/login?returnUrl=%2Fdriver'));
  });
  it('allows a connected user after server validation', () => {
    signedIn();
    const result = TestBed.runInInjectionContext(() => authGuard({} as ActivatedRouteSnapshot, { url: '/passenger' } as RouterStateSnapshot)) as Observable<boolean>;
    result.subscribe(value => expect(value).toBe(true));
    requests.expectOne('/api/auth/me').flush(session.user);
  });
  it.each(['USER', 'ADMIN'] as const)('checks the admin route against the server role %s', role => {
    signedIn();
    const result = TestBed.runInInjectionContext(() => adminGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot)) as Observable<boolean | UrlTree>;
    result.subscribe(value => {
      if (role === 'ADMIN') expect(value).toBe(true);
      else expect(TestBed.inject(Router).serializeUrl(value as UrlTree)).toBe('/passenger');
    });
    requests.expectOne('/api/auth/me').flush({ ...session.user, role });
  });
});
