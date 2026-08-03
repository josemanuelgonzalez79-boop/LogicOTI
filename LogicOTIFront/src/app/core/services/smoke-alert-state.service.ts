import { computed, inject, Injectable, signal } from '@angular/core';
import { Subject, Subscription } from 'rxjs';

import { SensorEventHistoryItem } from '../models/history.model';
import { HistoryApiService } from './history-api.service';
import {
  RealtimeConnectionStatus,
  SmokeAlertRealtimeService,
} from './smoke-alert-realtime.service';

@Injectable({ providedIn: 'root' })
export class SmokeAlertStateService {
  private readonly historyApi = inject(HistoryApiService);
  private readonly realtime = inject(SmokeAlertRealtimeService);

  private readonly latestByDevice = new Map<string, SensorEventHistoryItem>();
  private readonly notificationSubject = new Subject<SensorEventHistoryItem>();
  private readonly activeAlertsState = signal<SensorEventHistoryItem[]>([]);
  private readonly connectionStatusState = signal<RealtimeConnectionStatus>('DISCONNECTED');
  private readonly latestEventState = signal<SensorEventHistoryItem | null>(null);

  private subscriptions = new Subscription();
  private started = false;

  readonly activeAlerts = this.activeAlertsState.asReadonly();
  readonly activeCount = computed(() => this.activeAlerts().length);
  readonly connectionStatus = this.connectionStatusState.asReadonly();
  readonly latestEvent = this.latestEventState.asReadonly();
  readonly notifications$ = this.notificationSubject.asObservable();

  start(): void {
    if (this.started) {
      return;
    }

    this.started = true;

    this.subscriptions.add(
      this.realtime.connectionStatus$.subscribe((status) => this.connectionStatusState.set(status)),
    );

    this.subscriptions.add(
      this.realtime.alerts$.subscribe((event) => this.processRealtimeEvent(event)),
    );

    this.realtime.connect();
    this.loadInitialState();
  }

  ensureConnected(): void {
    if (!this.started) {
      this.start();
      return;
    }

    this.realtime.connect();
  }

  async stop(): Promise<void> {
    if (!this.started) {
      return;
    }

    this.started = false;
    this.subscriptions.unsubscribe();
    this.subscriptions = new Subscription();
    this.latestByDevice.clear();
    this.activeAlertsState.set([]);
    this.latestEventState.set(null);

    await this.realtime.disconnect();
  }

  private loadInitialState(): void {
    this.subscriptions.add(
      this.historyApi
        .getSensorEventHistory({
          deviceType: 'SMOKE',
          limit: 200,
          offset: 0,
        })
        .subscribe({
          next: (response) => {
            response.items.forEach((event) => this.keepNewestEvent(event));
            this.updateActiveAlerts();
          },
          error: () => {
            // El WebSocket continúa funcionando aunque no cargue el histórico.
          },
        }),
    );
  }

  private processRealtimeEvent(event: SensorEventHistoryItem): void {
    if (!this.keepNewestEvent(event)) {
      return;
    }

    this.latestEventState.set(event);
    this.updateActiveAlerts();
    this.notificationSubject.next(event);
  }

  private keepNewestEvent(event: SensorEventHistoryItem): boolean {
    const currentEvent = this.latestByDevice.get(event.deviceCode);

    if (
      currentEvent &&
      new Date(currentEvent.detectedAt).getTime() > new Date(event.detectedAt).getTime()
    ) {
      return false;
    }

    if (currentEvent?.id === event.id) {
      return false;
    }

    this.latestByDevice.set(event.deviceCode, event);
    return true;
  }

  private updateActiveAlerts(): void {
    const activeAlerts = [...this.latestByDevice.values()]
      .filter((event) => event.currentState)
      .sort((a, b) => b.detectedAt.localeCompare(a.detectedAt));

    this.activeAlertsState.set(activeAlerts);
  }
}
