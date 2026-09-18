import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { EMPTY, catchError, filter, from, switchMap, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

const INITIAL_UPDATE_CHECK_MS = 60_000;
const UPDATE_CHECK_INTERVAL_MS = 5 * 60_000;

@Injectable({ providedIn: 'root' })
export class PwaUpdateService {
  private readonly swUpdate = inject(SwUpdate, { optional: true });
  private readonly destroyRef = inject(DestroyRef);

  readonly updateAvailable = signal(false);
  readonly updating = signal(false);

  constructor() {
    const swUpdate = this.swUpdate;

    if (!swUpdate?.isEnabled) {
      return;
    }

    swUpdate.versionUpdates
      .pipe(
        filter(
          (event): event is VersionReadyEvent =>
            event.type === 'VERSION_READY',
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.updateAvailable.set(true));

    timer(INITIAL_UPDATE_CHECK_MS, UPDATE_CHECK_INTERVAL_MS)
      .pipe(
        switchMap(() =>
          from(swUpdate.checkForUpdate()).pipe(
            catchError(() => EMPTY),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  dismiss(): void {
    this.updateAvailable.set(false);
  }

  async activateUpdate(): Promise<void> {
    if (this.updating()) {
      return;
    }

    this.updating.set(true);

    try {
      await this.swUpdate?.activateUpdate();
    } finally {
      window.location.reload();
    }
  }
}
