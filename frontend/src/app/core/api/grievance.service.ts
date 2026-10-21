import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  CitizenGrievanceSummary,
  GrievanceDetails,
  GrievanceStatus,
  GrievanceSummary,
  HistoryEntry,
  NewGrievance,
  PagedModel,
} from './api.types';

@Service()
export class GrievanceService {
  private readonly http = inject(HttpClient);

  /** POST /grievances: a citizen files a grievance. */
  file(grievance: NewGrievance): Promise<GrievanceDetails> {
    return firstValueFrom(this.http.post<GrievanceDetails>('/api/v1/grievances', grievance));
  }

  /** GET /grievances/mine: the signed-in citizen's own grievances, newest first. */
  mine(page: number): Promise<PagedModel<CitizenGrievanceSummary>> {
    return firstValueFrom(
      this.http.get<PagedModel<CitizenGrievanceSummary>>('/api/v1/grievances/mine', {
        params: { page },
      }),
    );
  }

  get(id: string): Promise<GrievanceDetails> {
    return firstValueFrom(this.http.get<GrievanceDetails>(`/api/v1/grievances/${id}`));
  }

  /** 404 for a ticket that does not exist or that the caller cannot view: the two look the same. */
  byTicketNo(ticketNo: string): Promise<GrievanceDetails> {
    return firstValueFrom(
      this.http.get<GrievanceDetails>('/api/v1/grievances', { params: { ticketNo } }),
    );
  }

  /** Oldest first. Redacted by the backend for citizens. */
  history(id: string): Promise<HistoryEntry[]> {
    return firstValueFrom(this.http.get<HistoryEntry[]>(`/api/v1/grievances/${id}/history`));
  }

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
