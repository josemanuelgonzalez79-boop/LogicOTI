import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';

import {
  AlarmActivity,
  SensorEventHistoryItem,
  SensorEventType,
} from '../../core/models/history.model';
import { AlarmApiService } from '../../core/services/alarm-api.service';
import { HistoryApiService } from '../../core/services/history-api.service';
import { SmokeAlertStateService } from '../../core/services/smoke-alert-state.service';
import { RealtimeConnectionStatus } from '../../core/services/smoke-alert-realtime.service';

type EventFilter = '' | SensorEventType;

@Component({
  selector: 'app-alarms',
  standalone: true,
  imports: [DatePipe, FormsModule],
  templateUrl: './alarms.html',
  styleUrl: './alarms.scss',
})
export class Alarms implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  private readonly alarmApi = inject(AlarmApiService);
  private readonly historyApi = inject(HistoryApiService);
  private readonly smokeAlerts = inject(SmokeAlertStateService);

  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly connectionStatus = this.smokeAlerts.connectionStatus;
  readonly events = signal<SensorEventHistoryItem[]>([]);
  readonly totalEvents = signal(0);
  readonly lastRealtimeEvent = this.smokeAlerts.latestEvent;

  readonly searchText = signal('');
  readonly eventFilter = signal<EventFilter>('');

  readonly selectedAlarm = signal<SensorEventHistoryItem | null>(null);
  readonly alarmActivity = signal<AlarmActivity | null>(null);
  readonly activityLoading = signal(false);
  readonly activitySaving = signal(false);
  readonly activityError = signal('');
  readonly activitySuccess = signal('');
  readonly draftComment = signal('');

  readonly activeAlerts = this.smokeAlerts.activeAlerts;
  readonly unacknowledgedActiveAlerts = computed(
    () => this.activeAlerts().filter((alarm) => !alarm.acknowledged).length,
  );

  readonly criticalEventsLast24Hours = computed(() => {
    const minimumDate = Date.now() - 24 * 60 * 60 * 1000;

    return this.events().filter(
      (event) =>
        event.severity === 'CRITICAL' &&
        event.eventType === 'ACTIVATED' &&
        new Date(event.detectedAt).getTime() >= minimumDate,
    ).length;
  });

  readonly clearedEvents = computed(
    () => this.events().filter((event) => event.eventType === 'CLEARED').length,
  );

  readonly filteredEvents = computed(() => {
    const search = this.searchText().trim().toLowerCase();
    const filter = this.eventFilter();

    return this.events().filter((event) => {
      const matchesType = !filter || event.eventType === filter;
      const matchesSearch =
        !search ||
        event.areaName.toLowerCase().includes(search) ||
        event.areaCode.toLowerCase().includes(search) ||
        event.deviceName.toLowerCase().includes(search) ||
        event.deviceCode.toLowerCase().includes(search) ||
        event.message.toLowerCase().includes(search);

      return matchesType && matchesSearch;
    });
  });

  ngOnInit(): void {
    this.smokeAlerts.notifications$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => this.receiveRealtimeEvent(event));

    this.smokeAlerts.attentionUpdates$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((activity) => this.receiveAttentionUpdate(activity));

    this.loadEvents();
  }

  refresh(): void {
    this.loadEvents();
    this.smokeAlerts.refreshActiveAlarms();

    if (
      this.connectionStatus() === 'DISCONNECTED' ||
      this.connectionStatus() === 'ERROR' ||
      this.connectionStatus() === 'UNAUTHORIZED'
    ) {
      this.smokeAlerts.ensureConnected();
    }
  }

  clearFilters(): void {
    this.searchText.set('');
    this.eventFilter.set('');
  }

  openAttention(alarm: SensorEventHistoryItem): void {
    if (alarm.eventType !== 'ACTIVATED') {
      return;
    }

    this.selectedAlarm.set(alarm);
    this.alarmActivity.set(null);
    this.activityError.set('');
    this.activitySuccess.set('');
    this.draftComment.set('');
    this.activityLoading.set(true);

    this.alarmApi
      .getActivity(alarm.id)
      .pipe(finalize(() => this.activityLoading.set(false)))
      .subscribe({
        next: (activity) => this.smokeAlerts.applyAlarmActivity(activity),
        error: (error: HttpErrorResponse) => {
          this.activityError.set(this.readError(error, 'No fue posible consultar la atención.'));
        },
      });
  }

  closeAttention(): void {
    if (this.activitySaving()) {
      return;
    }

    this.selectedAlarm.set(null);
    this.alarmActivity.set(null);
    this.activityError.set('');
    this.activitySuccess.set('');
    this.draftComment.set('');
  }

  saveAttention(): void {
    const alarm = this.selectedAlarm();
    const activity = this.alarmActivity();
    const comment = this.draftComment().trim();

    if (!alarm || this.activitySaving()) {
      return;
    }

    if (activity?.acknowledgement && !comment) {
      this.activityError.set('Escribe el comentario que deseas agregar a la bitácora.');
      return;
    }

    this.activitySaving.set(true);
    this.activityError.set('');
    this.activitySuccess.set('');

    const request = activity?.acknowledgement
      ? this.alarmApi.addComment(alarm.id, comment)
      : this.alarmApi.acknowledge(alarm.id, comment || null);

    request.pipe(finalize(() => this.activitySaving.set(false))).subscribe({
      next: (updatedActivity) => {
        this.draftComment.set('');
        this.activitySuccess.set(
          activity?.acknowledgement
            ? 'Comentario agregado a la bitácora.'
            : 'Alarma reconocida correctamente.',
        );
        this.smokeAlerts.applyAlarmActivity(updatedActivity);
      },
      error: (error: HttpErrorResponse) => {
        this.activityError.set(this.readError(error, 'No fue posible guardar la atención.'));
      },
    });
  }

  connectionLabel(): string {
    const labels: Record<RealtimeConnectionStatus, string> = {
      DISCONNECTED: 'Desconectado',
      CONNECTING: 'Conectando...',
      CONNECTED: 'Tiempo real conectado',
      RECONNECTING: 'Reconectando...',
      UNAUTHORIZED: 'Sesión no disponible',
      ERROR: 'Error de conexión',
    };

    return labels[this.connectionStatus()];
  }

  private loadEvents(): void {
    this.loading.set(true);
    this.errorMessage.set('');

    this.historyApi
      .getSensorEventHistory({
        deviceType: 'SMOKE',
        limit: 200,
        offset: 0,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.mergeEvents(response.items);
          this.totalEvents.set(response.total);
        },
        error: () => {
          this.errorMessage.set('No fue posible cargar las alarmas registradas.');
        },
      });
  }

  private receiveRealtimeEvent(event: SensorEventHistoryItem): void {
    const alreadyExists = this.events().some((currentEvent) => currentEvent.id === event.id);

    if (!alreadyExists) {
      this.totalEvents.update((total) => total + 1);
    }

    this.mergeEvents([event]);
  }

  private receiveAttentionUpdate(activity: AlarmActivity): void {
    this.events.update((events) =>
      events.map((event) =>
        event.id === activity.eventId
          ? {
              ...event,
              acknowledged: activity.acknowledgement !== null,
              acknowledgedBy: activity.acknowledgement?.acknowledgedBy ?? null,
              acknowledgedAt: activity.acknowledgement?.acknowledgedAt ?? null,
              commentCount: activity.commentCount,
            }
          : event,
      ),
    );

    if (this.selectedAlarm()?.id === activity.eventId) {
      this.alarmActivity.set(activity);
      this.selectedAlarm.update((alarm) =>
        alarm
          ? {
              ...alarm,
              acknowledged: activity.acknowledgement !== null,
              acknowledgedBy: activity.acknowledgement?.acknowledgedBy ?? null,
              acknowledgedAt: activity.acknowledgement?.acknowledgedAt ?? null,
              commentCount: activity.commentCount,
            }
          : null,
      );
    }
  }

  private mergeEvents(incomingEvents: SensorEventHistoryItem[]): void {
    const eventsById = new Map<number, SensorEventHistoryItem>();

    [...this.events(), ...incomingEvents].forEach((event) => eventsById.set(event.id, event));

    const orderedEvents = [...eventsById.values()].sort((a, b) =>
      b.detectedAt.localeCompare(a.detectedAt),
    );

    this.events.set(orderedEvents.slice(0, 200));
  }

  private readError(error: HttpErrorResponse, fallback: string): string {
    const detail = error.error?.detail;
    return typeof detail === 'string' && detail.trim() ? detail : fallback;
  }
}
