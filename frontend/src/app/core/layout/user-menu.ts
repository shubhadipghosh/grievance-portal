import { Component, computed, effect, ElementRef, inject, signal, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ProfileService } from '../api/profile.service';
import { AuthService } from '../auth/auth.service';

/**
 * The account button in the header: who is signed in, a link to their
 * profile, and sign out. A disclosure (button + panel), not an ARIA menu.
 */
@Component({
  selector: 'app-user-menu',
  imports: [RouterLink],
  templateUrl: './user-menu.html',
  styleUrl: './user-menu.scss',
  host: {
    '(document:click)': 'onDocumentClick($event)',
    '(keydown.escape)': 'close(true)',
  },
})
export class UserMenu {
  private readonly auth = inject(AuthService);
  private readonly profiles = inject(ProfileService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly toggle = viewChild<ElementRef<HTMLButtonElement>>('toggle');

  protected readonly user = this.auth.currentUser;
  protected readonly profile = this.profiles.profile;
  protected readonly open = signal(false);
  protected readonly signingOut = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly displayName = computed(
    () => this.profile()?.fullName ?? (this.user()?.type === 'STAFF' ? 'Staff' : 'Citizen'),
  );
  protected readonly contact = computed(() => this.profile()?.email ?? this.profile()?.phone);
  protected readonly initials = computed(() => {
    const name = this.profile()?.fullName;
    if (!name) {
      return null;
    }
    const words = name.trim().split(/\s+/);
    return (words[0][0] + (words.length > 1 ? words[words.length - 1][0] : '')).toUpperCase();
  });

  constructor() {
    // Load the profile whenever someone signs in; forget it when they sign out.
    effect(() => {
      if (this.user()) {
        this.profiles.load().catch(() => undefined);
      } else {
        this.profiles.clear();
        this.open.set(false);
      }
    });
  }

  protected toggleOpen(): void {
    this.error.set(null);
    this.open.update((open) => !open);
  }

  protected close(restoreFocus = false): void {
    if (!this.open()) {
      return;
    }
    this.open.set(false);
    if (restoreFocus) {
      this.toggle()?.nativeElement.focus();
    }
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  protected async signOut(): Promise<void> {
    const loginUrl = this.user()?.type === 'CITIZEN' ? '/citizen/login' : '/login';
    this.signingOut.set(true);
    this.error.set(null);
    try {
      await this.auth.logout();
      await this.router.navigateByUrl(loginUrl);
    } catch {
      this.error.set('Could not sign out. Check your connection and try again.');
    } finally {
      this.signingOut.set(false);
    }
  }
}
