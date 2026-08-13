import { TestBed } from '@angular/core/testing';
import {
  SwUpdate,
  VersionEvent,
  VersionReadyEvent,
} from '@angular/service-worker';
import { Subject } from 'rxjs';

import { PwaUpdateService } from './pwa-update.service';

class SwUpdateMock {
  readonly isEnabled = true;
  readonly versionUpdates = new Subject<VersionEvent>();
  readonly checkForUpdate = vi.fn().mockResolvedValue(false);
  readonly activateUpdate = vi.fn().mockResolvedValue(true);
}

describe('PwaUpdateService', () => {
  let service: PwaUpdateService;
  let swUpdate: SwUpdateMock;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        PwaUpdateService,
        { provide: SwUpdate, useClass: SwUpdateMock },
      ],
    });

    service = TestBed.inject(PwaUpdateService);
    swUpdate = TestBed.inject(SwUpdate) as unknown as SwUpdateMock;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows the notice when a new application version is ready', () => {
    swUpdate.versionUpdates.next({
      type: 'VERSION_READY',
      currentVersion: { hash: 'version-anterior' },
      latestVersion: { hash: 'version-nueva' },
    } as VersionReadyEvent);

    expect(service.updateAvailable()).toBe(true);

    service.dismiss();

    expect(service.updateAvailable()).toBe(false);
  });

  it('checks periodically for a new version', async () => {
    await vi.advanceTimersByTimeAsync(60_000);

    expect(swUpdate.checkForUpdate).toHaveBeenCalledOnce();
  });
});
