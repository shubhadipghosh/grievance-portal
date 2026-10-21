import { Component, input } from '@angular/core';

/** Placeholder until the grievance detail view is built. */
@Component({
  selector: 'app-grievance-detail',
  template: `<h1>Grievance {{ id() }}</h1>`,
})
export class GrievanceDetail {
  readonly id = input.required<string>();
}
