import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CurrentUser } from '../api/api.types';
import { AuthService } from './auth.service';

/** Any signed-in user. For routes both citizens and staff use. */
export const authGuard: CanActivateFn = requireUser(() => true);

/** Staff only: a citizen session is redirected, not shown an empty officer view. */
export const staffGuard: CanActivateFn = requireUser((user) => user.type === 'STAFF');

/** Citizens only, sent to the OTP login rather than the staff one. */
export const citizenGuard: CanActivateFn = requireUser(
  (user) => user.type === 'CITIZEN',
  '/citizen/login',
);

function requireUser(
  allowed: (user: CurrentUser) => boolean,
  loginUrl = '/login',
): CanActivateFn {
  return async () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    await auth.restoreSession();
    const user = auth.currentUser();
    return (user !== null && allowed(user)) || router.parseUrl(loginUrl);
  };
}
