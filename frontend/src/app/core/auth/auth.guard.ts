import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CurrentUser } from '../api/api.types';
import { AuthService } from './auth.service';

/** Any signed-in user. For citizen routes. */
export const authGuard: CanActivateFn = requireUser(() => true);

/** Staff only: a citizen session is redirected, not shown an empty officer view. */
export const staffGuard: CanActivateFn = requireUser((user) => user.type === 'STAFF');

function requireUser(allowed: (user: CurrentUser) => boolean): CanActivateFn {
  return async () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    await auth.restoreSession();
    const user = auth.currentUser();
    return (user !== null && allowed(user)) || router.createUrlTree(['/login']);
  };
}
