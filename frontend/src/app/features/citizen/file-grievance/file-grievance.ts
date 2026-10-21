import { HttpErrorResponse } from '@angular/common/http';
import {
  afterNextRender,
  Component,
  computed,
  ElementRef,
  inject,
  Injector,
  OnInit,
  signal,
  viewChild,
} from '@angular/core';
import { form, FormField, maxLength, required, submit, validate } from '@angular/forms/signals';
import { RouterLink } from '@angular/router';
import { CategorySummary, GrievanceDetails, WardSummary } from '../../../core/api/api.types';
import { GrievanceService } from '../../../core/api/grievance.service';
import { ReferenceDataService } from '../../../core/api/reference-data.service';

/** Match NewGrievance's @Size limits. Rendered as the inputs' maxlength. */
const TITLE_MAX = 200;
const DESCRIPTION_MAX = 5000;
const ADDRESS_MAX = 500;

/** Options under one parent's name; a null label means top-level options. */
interface CategoryGroup {
  label: string | null;
  options: CategorySummary[];
}

@Component({
  selector: 'app-file-grievance',
  imports: [FormField, RouterLink],
  templateUrl: './file-grievance.html',
  styleUrl: './file-grievance.scss',
})
export class FileGrievance implements OnInit {
  private readonly grievances = inject(GrievanceService);
  private readonly referenceData = inject(ReferenceDataService);
  private readonly injector = inject(Injector);
  private readonly filedHeading = viewChild<ElementRef<HTMLElement>>('filedHeading');

  protected readonly descriptionMax = DESCRIPTION_MAX;

  /** Null until loaded. */
  protected readonly categories = signal<CategorySummary[] | null>(null);
  protected readonly wards = signal<WardSummary[] | null>(null);
  protected readonly listsError = signal(false);
  protected readonly categoryGroups = computed(() => leafGroups(this.categories() ?? []));

  protected readonly grievanceForm = form(
    signal({ categoryId: '', wardId: '', title: '', description: '', addressText: '' }),
    (p) => {
      required(p.categoryId, { message: 'Choose a category.' });
      required(p.wardId, { message: 'Choose your ward.' });
      required(p.title, { message: 'Enter a short title.' });
      // The backend's @NotBlank: spaces alone are not a title.
      validate(p.title, ({ value }) => blank(value(), 'Enter a short title.'));
      maxLength(p.title, TITLE_MAX);
      required(p.description, { message: 'Describe the problem.' });
      validate(p.description, ({ value }) => blank(value(), 'Describe the problem.'));
      maxLength(p.description, DESCRIPTION_MAX);
      maxLength(p.addressText, ADDRESS_MAX);
    },
  );
  protected readonly descriptionLength = computed(
    () => this.grievanceForm.description().value().length,
  );
  protected readonly error = signal<string | null>(null);
  /** Set once filed; the page then shows the ticket number instead of the form. */
  protected readonly filed = signal<GrievanceDetails | null>(null);

  ngOnInit(): void {
    this.loadLists();
  }

  protected async loadLists(): Promise<void> {
    this.listsError.set(false);
    try {
      const [categories, wards] = await Promise.all([
        this.referenceData.categories(),
        this.referenceData.wards(),
      ]);
      this.categories.set(categories);
      this.wards.set(wards);
    } catch {
      this.listsError.set(true);
    }
  }

  protected async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    this.error.set(null);
    await submit(this.grievanceForm, async (f) => {
      const v = f().value();
      try {
        this.filed.set(
          await this.grievances.file({
            categoryId: v.categoryId,
            wardId: v.wardId,
            title: v.title.trim(),
            description: v.description.trim(),
            addressText: v.addressText.trim() || null,
            latitude: null,
            longitude: null,
          }),
        );
        afterNextRender(() => this.filedHeading()?.nativeElement.focus(), {
          injector: this.injector,
        });
      } catch (e) {
        this.error.set(fileFailure(e));
      }
      return undefined;
    });
  }
}

/** Empty is left to required(), so only one message shows. */
function blank(value: string, message: string) {
  return value !== '' && value.trim() === '' ? { kind: 'blank', message } : undefined;
}

/**
 * Only leaves can be filed against. A leaf under a listed parent goes in that
 * parent's group; the parent itself is not selectable. Order follows the
 * backend's, which is by name.
 */
function leafGroups(categories: CategorySummary[]): CategoryGroup[] {
  const parentIds = new Set(categories.map((c) => c.parentId));
  const leaves = categories.filter((c) => !parentIds.has(c.id));
  const parents = categories.filter((c) => parentIds.has(c.id));
  const groups: CategoryGroup[] = [
    { label: null, options: leaves.filter((c) => !parents.some((p) => p.id === c.parentId)) },
    ...parents.map((p) => ({ label: p.name, options: leaves.filter((c) => c.parentId === p.id) })),
  ];
  return groups.filter((g) => g.options.length > 0);
}

function fileFailure(e: unknown): string {
  if (e instanceof HttpErrorResponse) {
    switch (e.status) {
      // Status 0: no response at all. Common on patchy mobile data.
      case 0:
        return 'No connection. Check your mobile data and try again.';
      case 400:
        return 'Some details were not accepted. Check them and try again.';
      case 401:
        return 'You have been signed out. Sign in again to file your grievance.';
      // The category or ward was retired, or gained sub-categories, after the lists loaded.
      case 422:
        return 'That category or ward can no longer be chosen. Reload the page and choose again.';
    }
  }
  return 'Something went wrong. Please try again.';
}
