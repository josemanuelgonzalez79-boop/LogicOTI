import { computed, inject, Injectable, signal } from '@angular/core';
import { Subject, Subscription } from 'rxjs';

import { AlarmActivity, SensorEventHistoryItem } from '../models/history.model';
import { AlarmApiService } from './alarm-api.service';
import {
  RealtimeConnectionStatus,
  SmokeAlertRealtimeService,
} from './smoke-alert-realtime.service';

@Injectable({ providedIn: 'root' })
export class SmokeAlertStateService {
  private readonly alarmApi = inject(AlarmApiService);
  private readonly realtime = inject(SmokeAlertRealtimeService);

  private readonly latestByDevice = new Map<string, SensorEventHistoryItem>();
  private readonly notificationSubject = new Subject<SensorEventHistoryItem>();
  private readonly attentionSubject = new Subject<AlarmActivity>();
  private readonly activeAlertsState = signal<SensorEventHistoryItem[]>([]);
  private readonly connectionStatusState = signal<RealtimeConnectionStatus>('DISCONNECTED');
  private readonly latestEventState = signal<SensorEventHistoryItem | null>(null);

  private subscriptions = new Subscription();
  private started = false;
  private realtimeRevision = 0;

  readonly activeAlerts = this.activeAlertsState.asReadonly();
  readonly activeCount = computed(() => this.activeAlerts().length);
  readonly connectionStatus = this.connectionStatusState.asReadonly();
  readonly latestEvent = this.latestEventState.asReadonly();
  readonly notifications$ = this.notificationSubject.asObservable();
  readonly attentionUpdates$ = this.attentionSubject.asObservable();

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

    this.subscriptions.add(
      this.realtime.attention$.subscribe((activity) => this.applyAlarmActivity(activity)),
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

  refreshActiveAlarms(): void {
    if (!this.started) {
      this.start();
      return;
    }

    this.loadInitialState();
  }

  applyAlarmActivity(activity: AlarmActivity): void {
    let updated = false;

    this.latestByDevice.forEach((event, deviceCode) => {
      if (event.id !== activity.eventId) {
        return;
      }

      this.latestByDevice.set(deviceCode, {
        ...event,
        acknowledged: activity.acknowledgement !== null,
        acknowledgedBy: activity.acknowledgement?.acknowledgedBy ?? null,
        acknowledgedAt: activity.acknowledgement?.acknowledgedAt ?? null,
        commentCount: activity.commentCount,
      });
      updated = true;
    });

    if (updated) {
      this.updateActiveAlerts();
    }

    this.attentionSubject.next(activity);
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
    this.realtimeRevision = 0;

    await this.realtime.disconnect();
  }

  private loadInitialState(): void {
    const revisionAtRequest = this.realtimeRevision;

    this.subscriptions.add(
      this.alarmApi.getActiveAlarms().subscribe({
        next: (response) => {
          if (revisionAtRequest === this.realtimeRevision) {
            this.latestByDevice.clear();
          }

          response.items.forEach((event) => this.keepNewestEvent(event));
          this.updateActiveAlerts();
        },
        error: () => {
          // El WebSocket continúa funcionando aunque no cargue las alarmas iniciales.
        },
      }),
    );
  }

  private processRealtimeEvent(event: SensorEventHistoryItem): void {
    if (!this.keepNewestEvent(event)) {
      return;
    }

    this.realtimeRevision += 1;
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
