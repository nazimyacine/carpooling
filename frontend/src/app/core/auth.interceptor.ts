import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const api = request.url.startsWith('/api/');
  const publicAuth = /^\/api\/auth\/(login|register)$/.test(request.url);
  const token = api && !publicAuth ? auth.token() : null;
  const authenticated = token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
  return next(authenticated).pipe(catchError((error: HttpErrorResponse) => {
    if (api && !publicAuth && error.status === 401) {
      auth.clear();
      void router.navigate(['/login'], { queryParams: { expired: '1' } });
    }
    return throwError(() => error);
  }));
};
