import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  Component,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  signal,
  viewChild,
} from '@angular/core';
import { form, FormField, maxLength, pattern, required, submit } from '@angular/forms/signals';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';
import { tooManyAttemptsMessage } from '../../../core/auth/rate-limit';

/** Matches the backend's otp.resend-cooldown. */
const RESEND_SECONDS = 60;

const INVALID_PHONE = 'Enter a valid 10-digit mobile number.';

@Component({
  selector: 'app-citizen-login',
  imports: [FormField],
  templateUrl: './citizen-login.html',
  styleUrl: './citizen-login.scss',
})
export class CitizenLogin {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly injector = inject(Injector);
  private readonly codeInput = viewChild<ElementRef<HTMLInputElement>>('codeInput');

  protected readonly step = signal<'phone' | 'code'>('phone');
  protected readonly phoneForm = form(signal({ phone: '' }), (p) => {
    required(p.phone, { message: 'Enter your mobile number.' });
    pattern(p.phone, /^[6-9]\d{9}$/, { message: INVALID_PHONE });
  });
  protected readonly codeForm = form(signal({ code: '' }), (p) => {
    required(p.code, { message: 'Enter the 6-digit code.' });
    pattern(p.code, /^\d{6}$/, { message: 'The code is 6 digits.' });
    // Rendered as the input's maxlength.
    maxLength(p.code, 6);
  });
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);
  protected readonly resendIn = signal(0);
  protected readonly resending = signal(false);

  private deadline = 0;
  private timer?: ReturnType<typeof setInterval>;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearInterval(this.timer));
  }

  protected async onSendCode(event: Event): Promise<void> {
    event.preventDefault();
    this.error.set(null);
    await submit(this.phoneForm, async () => {
      if (await this.send()) {
        this.step.set('code');
        afterNextRender(() => this.codeInput()?.nativeElement.focus(), {
          injector: this.injector,
        });
      }
      return undefined;
    });
  }

  protected async onResend(): Promise<void> {
    this.error.set(null);
    this.notice.set(null);
    this.resending.set(true);
    if (await this.send()) {
      this.codeForm.code().value.set('');
      this.notice.set('A new code has been sent.');
    }
    this.resending.set(false);
  }

  protected onChangeNumber(): void {
    this.error.set(null);
    this.notice.set(null);
    this.codeForm.code().value.set('');
    this.step.set('phone');
  }

  protected async onVerify(event: Event): Promise<void> {
    event.preventDefault();
    this.error.set(null);
    this.notice.set(null);
    await submit(this.codeForm, async (f) => {
      try {
        await this.auth.verifyOtp({ phone: this.e164(), code: f().value().code });
        await this.router.navigateByUrl('/citizen');
      } catch (e) {
        this.error.set(
          e instanceof HttpErrorResponse && e.status === 401
            ? // One message for every cause: never hint whether the phone is known.
              'That code did not work. Check it and try again, or request a new code.'
            : otherFailure(e),
        );
      }
      return undefined;
    });
  }

  protected e164(): string {
    return `+91${this.phoneForm.phone().value()}`;
  }

  /** Sends a code and starts the resend countdown. False if it failed; the error is shown. */
  private async send(): Promise<boolean> {
    try {
      await this.auth.sendOtp({ phone: this.e164() });
      this.startCountdown();
      return true;
    } catch (e) {
      this.error.set(sendFailure(e));
      return false;
    }
  }

  // Counts against a deadline rather than decrementing per tick: Android
  // throttles timers while the user is in the SMS app.
  private startCountdown(): void {
    clearInterval(this.timer);
    this.deadline = Date.now() + RESEND_SECONDS * 1000;
    this.resendIn.set(RESEND_SECONDS);
    this.timer = setInterval(() => {
      const left = Math.max(0, Math.ceil((this.deadline - Date.now()) / 1000));
      this.resendIn.set(left);
      if (left === 0) {
        clearInterval(this.timer);
      }
    }, 1000);
  }
}

function sendFailure(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    if (e.status === 400) {
      return INVALID_PHONE;
    }
    if (e.status === 429) {
      return tooManyAttemptsMessage(e);
    }
  }
  return otherFailure(e);
}

function otherFailure(e: unknown): string {
  // Status 0: no response at all. Common on patchy mobile data.
  return e instanceof HttpErrorResponse && e.status === 0
    ? 'No connection. Check your mobile data and try again.'
    : 'Something went wrong. Please try again.';
}
