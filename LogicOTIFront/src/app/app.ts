import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import {
  NavigationEnd,
  Router,
  RouterOutlet,
} from '@angular/router';
import { filter, map, startWith } from 'rxjs';

import { Shell } from './layout/shell/shell';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    AsyncPipe,
    RouterOutlet,
    Shell,
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly router = inject(Router);

  readonly isLoginRoute$ = this.router.events.pipe(
    filter(
      (event): event is NavigationEnd =>
        event instanceof NavigationEnd,
    ),
    map((event) =>
      event.urlAfterRedirects.startsWith('/login'),
    ),
    startWith(this.router.url.startsWith('/login')),
  );
}