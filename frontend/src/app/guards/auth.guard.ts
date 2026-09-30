import { inject } from '@angular/core';
import {
  CanActivateFn,
  Router
} from '@angular/router';

import { map } from 'rxjs';

import { AuthStateService } from '../services/auth-state.service';

export const authGuard: CanActivateFn = () => {

  const authState = inject(AuthStateService);
  const router = inject(Router);

  return authState.restoreSession().pipe(

    map(isAuthenticated => {

      if (isAuthenticated) {
        return true;
      }

      return router.createUrlTree(['/login']);
    })
  );
};