import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { AuthService } from './core/auth.service';

describe('Application shell', () => {
  beforeEach(() => {
    sessionStorage.clear(); localStorage.clear();
    TestBed.configureTestingModule({ imports: [App], providers: [provideHttpClient(), provideRouter([])] });
  });
  it('shows the brand without account controls when signed out', () => {
    const fixture = TestBed.createComponent(App); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('PoolUp');
    expect(fixture.nativeElement.textContent).not.toContain('Déconnexion');
  });
  it('changes mode from the header and clears the account on logout', () => {
    const fixture = TestBed.createComponent(App);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const auth = TestBed.inject(AuthService);
    auth.user.set({ id: 1, email: 'lea@test.fr', firstName: 'Léa', lastName: 'M', carModel: null, role: 'USER' });
    fixture.detectChanges();
    fixture.componentInstance.switchMode('driver');
    expect(localStorage.getItem('poolup.mode')).toBe('driver');
    fixture.componentInstance.logout();
    expect(auth.user()).toBeNull();
  });
});
