import { HttpClient } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  ActivityItem,
  CitizenGrievanceSummary,
  GrievanceDetails,
  GrievanceStatus,
  GrievanceSummary,
  HoldRequest,
  NewComment,
  NewGrievance,
  PagedModel,
  StaffMember,
  TicketOverview,
} from './api.types';

/** The status-change endpoints that take only an optional reason. */
export type TransitionPath =
  | 'acknowledge'
  | 'in-progress'
  | 'resolve'
  | 'reject'
  | 'close'
  | 'reopen';

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

  /** The ticket page's header and side panel. The category is named in the browser's language. */
  overview(id: string): Promise<TicketOverview> {
    return firstValueFrom(this.http.get<TicketOverview>(`/api/v1/grievances/${id}/overview`));
  }

  /** Comments and events, oldest first. Internal remarks only reach staff. */
  activity(id: string): Promise<ActivityItem[]> {
    return firstValueFrom(this.http.get<ActivityItem[]>(`/api/v1/grievances/${id}/activity`));
  }

  /** 403 for an internal remark from a citizen, or a viewer who may not comment. */
  comment(id: string, comment: NewComment): Promise<ActivityItem> {
    return firstValueFrom(
      this.http.post<ActivityItem>(`/api/v1/grievances/${id}/comments`, comment),
    );
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

  /** POST /grievances/{id}/{path}: a status change that needs no more than a reason. */
  transition(id: string, path: TransitionPath, reason: string | null): Promise<GrievanceDetails> {
    return firstValueFrom(
      this.http.post<GrievanceDetails>(`/api/v1/grievances/${id}/${path}`, { reason }),
    );
  }

  /** 422 when the assignee cannot take the work. reason: max 500. */
  assign(id: string, assigneeId: string, reason: string | null): Promise<GrievanceDetails> {
    return firstValueFrom(
      this.http.post<GrievanceDetails>(`/api/v1/grievances/${id}/assign`, { assigneeId, reason }),
    );
  }

  /** 422 when the owner details do not fit the reason type. */
  hold(id: string, hold: HoldRequest): Promise<GrievanceDetails> {
    return firstValueFrom(this.http.post<GrievanceDetails>(`/api/v1/grievances/${id}/hold`, hold));
  }

  /** Staff the grievance can be assigned to. 403 unless the caller may assign it. */
  assignees(id: string): Promise<StaffMember[]> {
    return firstValueFrom(this.http.get<StaffMember[]>(`/api/v1/grievances/${id}/assignees`));
  }
}
