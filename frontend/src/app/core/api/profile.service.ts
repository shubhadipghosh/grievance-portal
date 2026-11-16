import { HttpClient } from '@angular/common/http';
import { inject, Service, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Profile, ProfileUpdateRequest } from './api.types';

/** The signed-in user's own details, shared by the header menu and the profile page. */
@Service()
export class ProfileService {
  private readonly http = inject(HttpClient);
  private readonly current = signal<Profile | null>(null);

  readonly profile = this.current.asReadonly();

  async load(): Promise<Profile> {
    const profile = await firstValueFrom(this.http.get<Profile>('/api/v1/profile'));
    this.current.set(profile);
    return profile;
  }

  async update(request: ProfileUpdateRequest): Promise<Profile> {
    const profile = await firstValueFrom(this.http.put<Profile>('/api/v1/profile', request));
    this.current.set(profile);
    return profile;
  }

  clear(): void {
    this.current.set(null);
  }
}
