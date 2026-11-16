import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { UserMenu } from './core/layout/user-menu';

@Component({
  imports: [RouterOutlet, UserMenu],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {}
