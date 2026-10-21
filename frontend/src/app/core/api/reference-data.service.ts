import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DepartmentSummary, WardSummary } from './api.types';

/** Departments and wards, limited by the backend to what the caller may see. */
@Service()
export class ReferenceDataService {
  private readonly http = inject(HttpClient);

  departments(): Promise<DepartmentSummary[]> {
    return firstValueFrom(this.http.get<DepartmentSummary[]>('/api/v1/departments'));
  }

  wards(): Promise<WardSummary[]> {
    return firstValueFrom(this.http.get<WardSummary[]>('/api/v1/wards'));
  }
}
