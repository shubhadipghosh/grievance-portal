import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { GrievanceStatus, GrievanceSummary, PagedModel } from './api.types';

@Service()
export class GrievanceService {
  private readonly http = inject(HttpClient);

  /** GET /grievances: one department's grievances in one status, one page at a time. */
  queue(
    departmentId: string,
    status: GrievanceStatus,
    page: number,
  ): Promise<PagedModel<GrievanceSummary>> {
    return firstValueFrom(
      this.http.get<PagedModel<GrievanceSummary>>('/api/v1/grievances', {
        params: { departmentId, status, page },
      }),
    );
  }
}
