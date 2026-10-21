import { HttpErrorResponse } from '@angular/common/http';

/** The message for a 429. The backend's Retry-After is whole seconds. */
export function tooManyAttemptsMessage(e: HttpErrorResponse): string {
  const seconds = Number(e.headers.get('Retry-After'));
  return seconds > 0
    ? `Too many attempts. Try again in ${formatWait(seconds)}.`
    : 'Too many attempts. Try again later.';
}

function formatWait(seconds: number): string {
  if (seconds < 60) {
    return seconds === 1 ? '1 second' : `${seconds} seconds`;
  }
  const minutes = Math.ceil(seconds / 60);
  return minutes === 1 ? '1 minute' : `${minutes} minutes`;
}
