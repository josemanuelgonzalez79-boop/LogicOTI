import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import {
  CommandHistoryFilters,
  CommandHistoryItem,
  CommandStatus,
  ExecutiveMonthlyReport,
  SensorDeviceType,
  SensorEventHistoryFilters,
  SensorEventHistoryItem,
  SensorEventSeverity,
  SensorEventType,
} from '../../core/models/history.model';
import { HistoryApiService } from '../../core/services/history-api.service';

type HistoryTab = 'events' | 'commands' | 'report';

interface EventFilterForm {
  areaCode: string;
  deviceCode: string;
  deviceType: '' | SensorDeviceType;
  eventType: '' | SensorEventType;
  severity: '' | SensorEventSeverity;
  from: string;
  to: string;
}

interface CommandFilterForm {
  areaCode: string;
  deviceCode: string;
  status: '' | CommandStatus;
  requestedBy: string;
  from: string;
  to: string;
}

@Component({
  selector: 'app-history',
  standalone: true,
  imports: [DatePipe, DecimalPipe, FormsModule],
  templateUrl: './history.html',
  styleUrl: './history.scss',
})
export class History implements OnInit {
  private readonly historyApi = inject(HistoryApiService);

  readonly pageSize = 20;
  readonly activeTab = signal<HistoryTab>('events');
  readonly loading = signal(false);
  readonly errorMessage = signal('');

  readonly events = signal<SensorEventHistoryItem[]>([]);
  readonly eventTotal = signal(0);
  readonly eventOffset = signal(0);

  readonly commands = signal<CommandHistoryItem[]>([]);
  readonly commandTotal = signal(0);
  readonly commandOffset = signal(0);

  readonly report = signal<ExecutiveMonthlyReport | null>(null);
  readonly reportLoading = signal(false);
  readonly reportErrorMessage = signal('');
  reportMonth = this.currentMonth();

  readonly eventPage = computed(() => Math.floor(this.eventOffset() / this.pageSize) + 1);
  readonly eventPageCount = computed(() =>
    Math.max(1, Math.ceil(this.eventTotal() / this.pageSize)),
  );
  readonly commandPage = computed(() => Math.floor(this.commandOffset() / this.pageSize) + 1);
  readonly commandPageCount = computed(() =>
    Math.max(1, Math.ceil(this.commandTotal() / this.pageSize)),
  );
  readonly reportDailyMaximum = computed(() =>
    Math.max(0, ...(this.report()?.alarmsByDay.map((item) => item.count) ?? [])),
  );
  readonly reportAreaMaximum = computed(() =>
    Math.max(0, ...(this.report()?.alarmsByArea.map((item) => item.count) ?? [])),
  );
  readonly reportSensorMaximum = computed(() =>
    Math.max(0, ...(this.report()?.alarmsBySensor.map((item) => item.count) ?? [])),
  );
  readonly reportTimeMaximum = computed(() =>
    Math.max(0, ...(this.report()?.alarmsByTimeSlot.map((item) => item.count) ?? [])),
  );

  eventFilters: EventFilterForm = this.emptyEventFilters();
  commandFilters: CommandFilterForm = this.emptyCommandFilters();

  ngOnInit(): void {
    this.loadEvents();
  }

  selectTab(tab: HistoryTab): void {
    if (this.activeTab() === tab) {
      return;
    }

    this.activeTab.set(tab);
    this.errorMessage.set('');

    if (tab === 'events' && this.events().length === 0) {
      this.loadEvents();
    }

    if (tab === 'commands' && this.commands().length === 0) {
      this.loadCommands();
    }

    if (tab === 'report' && this.report() === null) {
      this.loadReport();
    }
  }

  searchReport(): void {
    this.loadReport();
  }

  searchEvents(): void {
    this.eventOffset.set(0);
    this.loadEvents();
  }

  clearEventFilters(): void {
    this.eventFilters = this.emptyEventFilters();
    this.eventOffset.set(0);
    this.loadEvents();
  }

  previousEventPage(): void {
    if (this.eventOffset() === 0 || this.loading()) {
      return;
    }

    this.eventOffset.update((offset) => Math.max(0, offset - this.pageSize));
    this.loadEvents();
  }

  nextEventPage(): void {
    if (this.eventOffset() + this.pageSize >= this.eventTotal() || this.loading()) {
      return;
    }

    this.eventOffset.update((offset) => offset + this.pageSize);
    this.loadEvents();
  }

  searchCommands(): void {
    this.commandOffset.set(0);
    this.loadCommands();
  }

  clearCommandFilters(): void {
    this.commandFilters = this.emptyCommandFilters();
    this.commandOffset.set(0);
    this.loadCommands();
  }

  previousCommandPage(): void {
    if (this.commandOffset() === 0 || this.loading()) {
      return;
    }

    this.commandOffset.update((offset) => Math.max(0, offset - this.pageSize));
    this.loadCommands();
  }

  nextCommandPage(): void {
    if (this.commandOffset() + this.pageSize >= this.commandTotal() || this.loading()) {
      return;
    }

    this.commandOffset.update((offset) => offset + this.pageSize);
    this.loadCommands();
  }

  eventTypeLabel(eventType: SensorEventType): string {
    return eventType === 'ACTIVATED' ? 'Activado' : 'Restablecido';
  }

  deviceTypeLabel(deviceType: SensorDeviceType): string {
    return deviceType === 'SMOKE' ? 'Humo' : 'Movimiento';
  }

  severityLabel(severity: SensorEventSeverity): string {
    const labels: Record<SensorEventSeverity, string> = {
      INFO: 'Información',
      WARNING: 'Advertencia',
      CRITICAL: 'Crítico',
    };

    return labels[severity];
  }

  commandStatusLabel(status: CommandStatus): string {
    const labels: Record<CommandStatus, string> = {
      PENDING: 'Pendiente',
      CONFIRMED: 'Confirmado',
      NOT_CONFIRMED: 'Sin confirmar',
      FAILED: 'Fallido',
      REJECTED: 'Rechazado',
    };

    return labels[status];
  }

  booleanLabel(value: boolean | null): string {
    if (value === null) {
      return 'Sin dato';
    }

    return value ? 'Encendido' : 'Apagado';
  }

  reportBarWidth(value: number, maximum: number): number {
    if (value === 0 || maximum === 0) {
      return 0;
    }

    return Math.max(5, (value / maximum) * 100);
  }

  reportMonthLabel(month: string): string {
    const [year, monthNumber] = month.split('-').map(Number);

    return new Intl.DateTimeFormat('es-MX', {
      month: 'long',
      year: 'numeric',
    }).format(new Date(year, monthNumber - 1, 1));
  }

  private loadEvents(): void {
    const filters: SensorEventHistoryFilters = {
      areaCode: this.clean(this.eventFilters.areaCode),
      deviceCode: this.clean(this.eventFilters.deviceCode),
      deviceType: this.eventFilters.deviceType || undefined,
      eventType: this.eventFilters.eventType || undefined,
      severity: this.eventFilters.severity || undefined,
      from: this.toIsoInstant(this.eventFilters.from),
      to: this.toIsoInstant(this.eventFilters.to),
      limit: this.pageSize,
      offset: this.eventOffset(),
    };

    this.loading.set(true);
    this.errorMessage.set('');

    this.historyApi
      .getSensorEventHistory(filters)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.events.set(response.items);
          this.eventTotal.set(response.total);
        },
        error: () => {
          this.events.set([]);
          this.eventTotal.set(0);
          this.errorMessage.set('No fue posible consultar los eventos de sensores.');
        },
      });
  }

  private loadCommands(): void {
    const filters: CommandHistoryFilters = {
      areaCode: this.clean(this.commandFilters.areaCode),
      deviceCode: this.clean(this.commandFilters.deviceCode),
      status: this.commandFilters.status || undefined,
      requestedBy: this.clean(this.commandFilters.requestedBy),
      from: this.toIsoInstant(this.commandFilters.from),
      to: this.toIsoInstant(this.commandFilters.to),
      limit: this.pageSize,
      offset: this.commandOffset(),
    };

    this.loading.set(true);
    this.errorMessage.set('');

    this.historyApi
      .getCommandHistory(filters)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.commands.set(response.items);
          this.commandTotal.set(response.total);
        },
        error: () => {
          this.commands.set([]);
          this.commandTotal.set(0);
          this.errorMessage.set('No fue posible consultar el histórico de comandos.');
        },
      });
  }

  private loadReport(): void {
    if (!/^\d{4}-\d{2}$/.test(this.reportMonth)) {
      this.reportErrorMessage.set('Selecciona un mes válido.');
      return;
    }

    this.reportLoading.set(true);
    this.reportErrorMessage.set('');

    this.historyApi
      .getExecutiveMonthlyReport(this.reportMonth)
      .pipe(finalize(() => this.reportLoading.set(false)))
      .subscribe({
        next: (response) => this.report.set(response),
        error: () => {
          this.report.set(null);
          this.reportErrorMessage.set('No fue posible generar el reporte ejecutivo mensual.');
        },
      });
  }

  private clean(value: string): string | undefined {
    const cleaned = value.trim().toUpperCase();
    return cleaned || undefined;
  }

  private toIsoInstant(value: string): string | undefined {
    if (!value) {
      return undefined;
    }

    return new Date(value).toISOString();
  }

  private emptyEventFilters(): EventFilterForm {
    return {
      areaCode: '',
      deviceCode: '',
      deviceType: '',
      eventType: '',
      severity: '',
      from: '',
      to: '',
    };
  }

  private emptyCommandFilters(): CommandFilterForm {
    return {
      areaCode: '',
      deviceCode: '',
      status: '',
      requestedBy: '',
      from: '',
      to: '',
    };
  }

  private currentMonth(): string {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');

    return `${year}-${month}`;
  }
}
