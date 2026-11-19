import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CurrentUser } from '../api/api.types';
import { AuthService } from './auth.service';

/** Any signed-in user. For routes both citizens and staff use. */
export const authGuard: CanActivateFn = requireUser(() => true);

/** Staff only: a citizen session is redirected, not shown an empty officer view. */
export const staffGuard: CanActivateFn = requireUser((user) => user.type === 'STAFF');

/**
 * The change-password screen: staff only, and the one route a user who must
 * change their password is let through to.
 */
export const passwordChangeGuard: CanActivateFn = requireUser(
  (user) => user.type === 'STAFF',
  '/login',
  false,
);

/** Citizens only, sent to the OTP login rather than the staff one. */
export const citizenGuard: CanActivateFn = requireUser(
  (user) => user.type === 'CITIZEN',
  '/citizen/login',
);

/**
 * @param holdForPasswordChange send a user who must change their password to
 *   that screen instead: the backend would refuse everything else anyway
 */
function requireUser(
  allowed: (user: CurrentUser) => boolean,
  loginUrl = '/login',
  holdForPasswordChange = true,
): CanActivateFn {
  return async () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    await auth.restoreSession();
    const user = auth.currentUser();
    if (user === null || !allowed(user)) {
      return router.parseUrl(loginUrl);
    }
    return !(holdForPasswordChange && user.mustChangePassword) || router.parseUrl('/change-password');
  };
}
