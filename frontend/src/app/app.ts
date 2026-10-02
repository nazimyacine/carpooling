import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { AuthService, TravelMode } from './core/auth.service';

@Component({
  selector: 'app-root', imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html', styleUrl: './app.css'
})
export class App {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  switchMode(mode: TravelMode): void {
    this.auth.setMode(mode);
    void this.router.navigate([this.auth.home()]);
  }
  logout(): void {
    this.auth.clear();
    void this.router.navigate(['/login']);
  }
}
