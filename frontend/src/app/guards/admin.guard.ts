import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of, tap } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { AuthStateService } from '../services/auth-state.service';

export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const state = inject(AuthStateService);
  const router = inject(Router);
  return auth.me().pipe(
    tap(user => state.setUser(user)),
    map(user => user.role === 'ADMIN' ? true : router.createUrlTree(['/app/dashboard'])),
    catchError(() => {
      state.clearUser();
      return of(router.createUrlTree(['/login']));
    })
  );
};
