import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import {
  form,
  FormField,
  maxLength,
  minLength,
  required,
  submit,
  validate,
} from '@angular/forms/signals';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { tooManyAttemptsMessage } from '../../../core/auth/rate-limit';

// AuthController.PasswordChangeRequest: BCrypt reads at most 72 bytes.
const MIN_LENGTH = 12;
const MAX_LENGTH = 72;

/**
 * A staff user choosing their own password. Forced after signing in with an
 * admin-generated one (the guards send them here and nowhere else); otherwise
 * reached from the account menu.
 */
@Component({
  selector: 'app-change-password',
  imports: [FormField, RouterLink],
  templateUrl: './change-password.html',
  styleUrl: './change-password.scss',
})
export class ChangePassword {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly minLength = MIN_LENGTH;

  /** Captured on arrival: the flag clears on success, but the wording should not flip mid-screen. */
  protected readonly forced = this.auth.currentUser()?.mustChangePassword === true;

  protected readonly passwordForm = form(
    signal({ currentPassword: '', newPassword: '', confirmPassword: '' }),
    (p) => {
      required(p.currentPassword, {
        message: this.forced ? 'Enter the password you were given.' : 'Enter your current password.',
      });
      required(p.newPassword, { message: 'Enter a new password.' });
      minLength(p.newPassword, MIN_LENGTH, {
        message: `Use at least ${MIN_LENGTH} characters.`,
      });
      maxLength(p.newPassword, MAX_LENGTH, {
        message: `Use at most ${MAX_LENGTH} characters.`,
      });
      required(p.confirmPassword, { message: 'Enter the new password again.' });
      validate(p.confirmPassword, ({ value, valueOf }) =>
        value() !== '' && value() !== valueOf(p.newPassword)
          ? { kind: 'mismatch', message: 'The passwords do not match.' }
          : null,
      );
    },
  );

  protected readonly error = signal<string | null>(null);
  protected readonly changed = signal(false);

  protected readonly newLength = computed(() => this.passwordForm.newPassword().value().length);

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    this.error.set(null);
    await submit(this.passwordForm, async (f) => {
      const { currentPassword, newPassword } = f().value();
      try {
        await this.auth.changePassword({ currentPassword, newPassword });
        if (this.forced) {
          await this.router.navigateByUrl('/queue');
        } else {
          this.changed.set(true);
          f().value.set({ currentPassword: '', newPassword: '', confirmPassword: '' });
          f().reset();
        }
      } catch (e) {
        this.error.set(errorMessage(e));
      }
      return undefined;
    });
  }
}

function errorMessage(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 422 && typeof e.error?.detail === 'string') {
      // "The current password is incorrect." or "...must differ from the current one."
      return e.error.detail;
    }
    if (e.status === 429) {
      return tooManyAttemptsMessage(e);
    }
    if (e.status === 401) {
      return 'Your session has ended. Sign in again.';
    }
    if (e.status === 0) {
      return 'No connection. Check your mobile data and try again.';
    }
  }
  return 'Something went wrong. Please try again.';
}
