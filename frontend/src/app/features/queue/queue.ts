import { DatePipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  DepartmentSummary,
  GrievanceStatus,
  GrievanceSummary,
  PagedModel,
} from '../../core/api/api.types';
import { GrievanceService } from '../../core/api/grievance.service';
import { STATUS_LABELS } from '../../core/api/labels';
import { ReferenceDataService } from '../../core/api/reference-data.service';

// Statuses whose SLA clock is running. On hold, slaDueAt is stale until the
// hold ends and extends it; resolved, closed and rejected have stopped.
const CLOCK_RUNNING = new Set<GrievanceStatus>([
  'SUBMITTED',
  'ACKNOWLEDGED',
  'ASSIGNED',
  'IN_PROGRESS',
  'REOPENED',
]);

@Component({
  selector: 'app-queue',
  imports: [DatePipe, RouterLink],
  templateUrl: './queue.html',
  styleUrl: './queue.scss',
})
export class Queue implements OnInit {
  private readonly grievanceService = inject(GrievanceService);
  private readonly referenceData = inject(ReferenceDataService);
  private readonly router = inject(Router);

  protected readonly statuses = Object.keys(STATUS_LABELS) as GrievanceStatus[];
  protected readonly statusLabels = STATUS_LABELS;

  /** Null until loaded. */
  protected readonly departments = signal<DepartmentSummary[] | null>(null);
  protected readonly departmentsError = signal(false);
  protected readonly departmentId = signal<string | null>(null);
  protected readonly status = signal<GrievanceStatus>('SUBMITTED');

  protected readonly grievances = signal<GrievanceSummary[]>([]);
  protected readonly page = signal<PagedModel<GrievanceSummary>['page'] | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  /** When the shown page arrived; overdue is judged against this. */
  private readonly loadedAt = signal(0);
  /** Lets a slow earlier response be ignored once a later request has started. */
  private requestId = 0;

  ngOnInit(): void {
    this.loadDepartments();
  }

  protected async loadDepartments(): Promise<void> {
    this.departmentsError.set(false);
    try {
      const departments = await this.referenceData.departments();
      this.departments.set(departments);
      // A field officer usually has exactly one; don't make them pick it.
      if (departments.length === 1) {
        this.selectDepartment(departments[0].id);
      }
    } catch {
      this.departmentsError.set(true);
    }
  }

  protected selectDepartment(id: string): void {
    this.departmentId.set(id || null);
    this.load(0);
  }

  protected selectStatus(status: string): void {
    this.status.set(status as GrievanceStatus);
    this.load(0);
  }

  protected async load(pageNumber: number): Promise<void> {
    const departmentId = this.departmentId();
    if (!departmentId) {
      return;
    }
    const id = ++this.requestId;
    this.loading.set(true);
    this.error.set(null);
    try {
      const result = await this.grievanceService.queue(departmentId, this.status(), pageNumber);
      if (id === this.requestId) {
        this.grievances.set(result.content);
        this.page.set(result.page);
        this.loadedAt.set(Date.now());
      }
    } catch {
      if (id === this.requestId) {
        this.grievances.set([]);
        this.page.set(null);
        this.error.set('Could not load the queue. Check your connection and try again.');
      }
    } finally {
      if (id === this.requestId) {
        this.loading.set(false);
      }
    }
  }

  protected isOverdue(g: GrievanceSummary): boolean {
    return CLOCK_RUNNING.has(g.status) && Date.parse(g.slaDueAt) < this.loadedAt();
  }

  /** Breached, but already resolved: needs explaining, not action. See STATE_MACHINE.md. */
  protected isResolvedLate(g: GrievanceSummary): boolean {
    return g.resolvedAt !== null && Date.parse(g.resolvedAt) > Date.parse(g.slaDueAt);
  }

  protected open(g: GrievanceSummary): void {
    this.router.navigate(['/grievances', g.id]);
  }
}
