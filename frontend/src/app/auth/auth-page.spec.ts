import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router, convertToParamMap } from '@angular/router';
import { AuthPage } from './auth-page';
import { AuthService } from '../core/auth.service';

describe('Login and registration', () => {
  let requests: HttpTestingController;
  function setup(register = false, returnUrl: string | null = null) {
    TestBed.configureTestingModule({ imports: [AuthPage], providers: [
      provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
      { provide: ActivatedRoute, useValue: { snapshot: { data: { register }, queryParamMap: convertToParamMap(returnUrl ? { returnUrl } : {}) } } }
    ] });
    requests = TestBed.inject(HttpTestingController);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(AuthPage); fixture.detectChanges();
    return { page: fixture.componentInstance, fixture, navigate };
  }
  beforeEach(() => { sessionStorage.clear(); localStorage.clear(); });
  afterEach(() => { requests.verify(); sessionStorage.clear(); localStorage.clear(); });
  const user = { id: 1, email: 'lea@test.fr', firstName: 'Léa', lastName: 'M', carModel: null, role: 'USER' };
  const response = { token: 'jwt', expiresAt: new Date(Date.now() + 3600000).toISOString(), user };
  it('blocks invalid submission and displays field errors', () => {
    const { page, fixture } = setup(); page.submit(); fixture.detectChanges();
    requests.expectNone('/api/auth/login');
    expect(fixture.nativeElement.textContent).toContain('Saisissez une adresse e-mail valide.');
  });
  it('logs in as driver, persists the mode and prevents double submission', () => {
    const { page, navigate } = setup();
    page.form.patchValue({ email: user.email, password: 'password123' }); page.selectedMode.set('driver');
    page.submit(); page.submit();
    const request = requests.expectOne('/api/auth/login'); expect(page.busy()).toBe(true);
    request.flush(response);
    expect(navigate).toHaveBeenCalledWith('/driver'); expect(page.busy()).toBe(false);
    expect(localStorage.getItem('poolup.mode')).toBe('driver');
  });
  it('registers with the existing backend contract and signs in immediately', () => {
    const { page, navigate } = setup(true);
    page.form.setValue({ firstName: ' Léa ', lastName: ' M ', email: user.email, password: 'password123' });
    page.submit(); const request = requests.expectOne('/api/auth/register');
    expect(request.request.body).toEqual({ firstName: 'Léa', lastName: 'M', email: user.email, password: 'password123' });
    request.flush(response); expect(TestBed.inject(AuthService).user()?.id).toBe(1);
    expect(navigate).toHaveBeenCalledWith('/passenger');
  });
  it('rejects short registration passwords', () => {
    const { page } = setup(true);
    page.form.setValue({ firstName: 'Léa', lastName: 'M', email: user.email, password: 'short' });
    page.submit(); requests.expectNone('/api/auth/register');
  });
  it.each([[401, 'incorrect'], [403, 'suspendu'], [409, 'déjà utilisée']] as const)('displays a useful message for HTTP %s', (status, message) => {
    const { page } = setup(); page.form.patchValue({ email: user.email, password: 'password123' }); page.submit();
    requests.expectOne('/api/auth/login').flush({}, { status, statusText: 'Error' });
    expect(page.error()).toContain(message); expect(page.busy()).toBe(false);
  });
  it('routes administrators to their space', () => {
    const { page, navigate } = setup(); page.form.patchValue({ email: user.email, password: 'password123' }); page.submit();
    requests.expectOne('/api/auth/login').flush({ ...response, user: { ...user, role: 'ADMIN' } });
    expect(navigate).toHaveBeenCalledWith('/admin');
  });
  it('ignores an external return URL', () => {
    const { page, navigate } = setup(false, '//external.example');
    page.form.patchValue({ email: user.email, password: 'password123' }); page.submit();
    requests.expectOne('/api/auth/login').flush(response); expect(navigate).toHaveBeenCalledWith('/passenger');
  });
});
