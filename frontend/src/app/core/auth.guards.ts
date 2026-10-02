import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const login = router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
  return auth.verify().pipe(map(user => user ? true : login), catchError(() => of(login)));
};

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.verify().pipe(
    map(user => !user ? router.createUrlTree(['/login']) : user.role === 'ADMIN' ? true : router.createUrlTree([auth.home()])),
    catchError(() => of(router.createUrlTree(['/login'])))
  );
};
