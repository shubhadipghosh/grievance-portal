import { Component, inject, OnInit, signal } from '@angular/core';
import { form, FormField, maxLength, required, submit, validate } from '@angular/forms/signals';
import { Locale, Profile as UserProfile } from '../../core/api/api.types';
import { ProfileService } from '../../core/api/profile.service';

/** Each language named in itself, so a reader of either can find their own. */
const LANGUAGES: { value: Locale; label: string }[] = [
  { value: 'en', label: 'English' },
  { value: 'bn', label: 'বাংলা (Bengali)' },
];

@Component({
  selector: 'app-profile',
  imports: [FormField],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile implements OnInit {
  private readonly profiles = inject(ProfileService);

  protected readonly languages = LANGUAGES;
  protected readonly profile = signal<UserProfile | null>(null);
  protected readonly loadError = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly saved = signal(false);

  protected readonly profileForm = form(
    signal<{ fullName: string; preferredLocale: Locale }>({ fullName: '', preferredLocale: 'en' }),
    (p) => {
      required(p.fullName, { message: 'Enter your name.' });
      // The backend's @NotBlank: spaces alone are not a name.
      validate(p.fullName, ({ value }) =>
        value().trim() === '' ? { kind: 'blank', message: 'Enter your name.' } : undefined,
      );
      maxLength(p.fullName, 150, { message: 'Use 150 characters or fewer.' });
    },
  );

  ngOnInit(): void {
    this.load();
  }

  protected async load(): Promise<void> {
    this.loadError.set(false);
    try {
      this.show(await this.profiles.load());
    } catch {
      this.loadError.set(true);
    }
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    this.error.set(null);
    this.saved.set(false);
    await submit(this.profileForm, async (f) => {
      try {
        const value = f().value();
        this.show(await this.profiles.update({ ...value, fullName: value.fullName.trim() }));
        this.saved.set(true);
      } catch {
        this.error.set('Could not save your details. Check your connection and try again.');
      }
      return undefined;
    });
  }

  private show(profile: UserProfile): void {
    this.profile.set(profile);
    this.profileForm().value.set({
      fullName: profile.fullName ?? '',
      preferredLocale: profile.preferredLocale,
    });
  }
}
