import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';

import { SensorEventHistoryItem, SensorEventType } from '../../core/models/history.model';
import { HistoryApiService } from '../../core/services/history-api.service';
import {
  RealtimeConnectionStatus,
  SmokeAlertRealtimeService,
} from '../../core/services/smoke-alert-realtime.service';

type EventFilter = '' | SensorEventType;

@Component({
  selector: 'app-alarms',
  standalone: true,
  imports: [DatePipe, FormsModule],
  templateUrl: './alarms.html',
  styleUrl: './alarms.scss',
})
export class Alarms implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  private readonly historyApi = inject(HistoryApiService);
  private readonly realtime = inject(SmokeAlertRealtimeService);

  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly connectionStatus = signal<RealtimeConnectionStatus>('DISCONNECTED');
  readonly events = signal<SensorEventHistoryItem[]>([]);
  readonly totalEvents = signal(0);
  readonly lastRealtimeEvent = signal<SensorEventHistoryItem | null>(null);

  readonly searchText = signal('');
  readonly eventFilter = signal<EventFilter>('');

  readonly activeAlerts = computed(() => {
    const latestByDevice = new Map<string, SensorEventHistoryItem>();

    this.events().forEach((event) => {
      if (!latestByDevice.has(event.deviceCode)) {
        latestByDevice.set(event.deviceCode, event);
      }
    });

    return [...latestByDevice.values()]
      .filter((event) => event.currentState)
      .sort((a, b) => b.detectedAt.localeCompare(a.detectedAt));
  });

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
    this.realtime.connectionStatus$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((status) => this.connectionStatus.set(status));

    this.realtime.alerts$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => this.receiveRealtimeEvent(event));

    this.loadEvents();
    this.realtime.connect();
  }

  ngOnDestroy(): void {
    void this.realtime.disconnect();
  }

  refresh(): void {
    this.loadEvents();

    if (
      this.connectionStatus() === 'DISCONNECTED' ||
      this.connectionStatus() === 'ERROR' ||
      this.connectionStatus() === 'UNAUTHORIZED'
    ) {
      this.realtime.connect();
    }
  }

  clearFilters(): void {
    this.searchText.set('');
    this.eventFilter.set('');
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

    this.lastRealtimeEvent.set(event);

    if (!alreadyExists) {
      this.totalEvents.update((total) => total + 1);
    }

    this.mergeEvents([event]);
  }

  private mergeEvents(incomingEvents: SensorEventHistoryItem[]): void {
    const eventsById = new Map<number, SensorEventHistoryItem>();

    [...this.events(), ...incomingEvents].forEach((event) => eventsById.set(event.id, event));

    const orderedEvents = [...eventsById.values()].sort((a, b) =>
      b.detectedAt.localeCompare(a.detectedAt),
    );

    this.events.set(orderedEvents.slice(0, 200));
  }
}
