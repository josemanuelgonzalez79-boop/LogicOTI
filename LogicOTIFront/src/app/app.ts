import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';

import { Shell } from './layout/shell/shell';
import { PwaUpdateService } from './core/services/pwa-update.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [AsyncPipe, RouterOutlet, Shell],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  private readonly router = inject(Router);
  readonly pwaUpdate = inject(PwaUpdateService);

  readonly isStandaloneRoute$ = this.router.events.pipe(
    filter((event): event is NavigationEnd => event instanceof NavigationEnd),
    map((event) => this.isStandaloneUrl(event.urlAfterRedirects)),
    startWith(this.isStandaloneUrl(this.router.url)),
  );

  updateApplication(): void {
    void this.pwaUpdate.activateUpdate();
  }

  private isStandaloneUrl(url: string): boolean {
    const path = url.split(/[?#]/, 1)[0];

    return path === '/login' || path === '/camera-alert';
  }
}
