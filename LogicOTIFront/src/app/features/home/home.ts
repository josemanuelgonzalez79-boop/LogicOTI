import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { TagModule } from 'primeng/tag';

import { SystemStatus } from '../../core/models/system-status.model';
import { SystemApiService } from '../../core/services/system-api.service';

@Component({
  selector: 'app-home',
  imports: [ButtonModule, CardModule, DatePipe, ProgressSpinnerModule, TagModule],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly api = inject(SystemApiService);

  protected readonly loading = signal(false);
  protected readonly status = signal<SystemStatus | null>(null);

  protected checkBackend(): void {
    this.loading.set(true);

    this.api
      .getStatus()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (value) => this.status.set(value),
      });
  }
}
