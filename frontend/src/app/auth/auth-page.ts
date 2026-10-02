import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService, TravelMode } from '../core/auth.service';

@Component({
  selector: 'app-auth-page', imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './auth-page.html'
})
export class AuthPage {
  readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  readonly registering = this.route.snapshot.data['register'] === true;
  readonly busy = signal(false);
  readonly error = signal('');
  readonly expired = this.route.snapshot.queryParamMap.get('expired') === '1';
  readonly selectedMode = signal<TravelMode>(this.auth.mode());
  readonly form = this.fb.nonNullable.group({
    firstName: ['', this.registering ? [Validators.required, Validators.maxLength(100), Validators.pattern(/\S/)] : []],
    lastName: ['', this.registering ? [Validators.required, Validators.maxLength(100), Validators.pattern(/\S/)] : []],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', this.registering ? [Validators.required, Validators.minLength(8), Validators.maxLength(72)] : [Validators.required]]
  });

  invalid(field: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[field];
    return control.touched && control.invalid;
  }

  submit(): void {
    if (this.busy()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    this.busy.set(true);
    this.error.set('');
    const value = this.form.getRawValue();
    const request = this.registering
      ? this.auth.register({ ...value, email: value.email.trim(), firstName: value.firstName.trim(), lastName: value.lastName.trim() })
      : this.auth.login(value.email.trim(), value.password);
    request.pipe(finalize(() => this.busy.set(false))).subscribe({
      next: () => {
        this.auth.setMode(this.selectedMode());
        const destination = this.route.snapshot.queryParamMap.get('returnUrl');
        // Only accept routes owned by this application, never an external return URL.
        const safeDestination = destination && /^\/(passenger|driver|admin)(?:[/?#]|$)/.test(destination);
        void this.router.navigateByUrl(safeDestination ? destination : this.auth.home());
      },
      error: (error: HttpErrorResponse) => {
        if (error.status === 0) this.error.set('Le serveur est injoignable. Réessayez dans un instant.');
        else if (error.status === 401) this.error.set('Adresse e-mail ou mot de passe incorrect.');
        else if (error.status === 403) this.error.set('Votre compte est suspendu.');
        else if (error.status === 409) this.error.set('Cette adresse e-mail est déjà utilisée.');
        else this.error.set(error.error?.detail ?? 'Impossible de terminer la demande. Réessayez.');
      }
    });
  }
}
