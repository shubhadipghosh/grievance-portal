import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Placeholder until the citizen's grievance list is built. */
@Component({
  selector: 'app-citizen-home',
  imports: [RouterLink],
  template: `<h1>My grievances</h1>
    <a routerLink="/citizen/file">File a grievance</a>`,
})
export class CitizenHome {}
