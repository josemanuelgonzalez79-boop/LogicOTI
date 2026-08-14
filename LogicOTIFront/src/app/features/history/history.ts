import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import {
  CommandHistoryFilters,
  CommandHistoryItem,
  CommandStatus,
  ExecutiveMonthlyReport,
  HistoryRetentionPolicy,
  SensorDeviceType,
  SensorEventHistoryFilters,
  SensorEventHistoryItem,
  SensorEventSeverity,
  SensorEventType,
} from '../../core/models/history.model';
import { AuthService } from '../../core/services/auth.service';
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
  private readonly authService = inject(AuthService);

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
  readonly retention = signal<HistoryRetentionPolicy | null>(null);
  readonly retentionLoading = signal(false);
  readonly retentionSaving = signal(false);
  readonly retentionRunning = signal(false);
  readonly retentionErrorMessage = signal('');
  readonly retentionSuccessMessage = signal('');
  readonly canManageRetention = this.authService.getSession()?.user.role === 'ADMIN';
  reportMonth = this.currentMonth();
  retentionEnabled = false;
  retentionMonths = 24;

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

    if (tab === 'report' && this.canManageRetention && this.retention() === null) {
      this.loadRetentionPolicy();
    }
  }

  searchReport(): void {
    this.loadReport();
  }

  exportReportToPdf(): void {
    const monthlyReport = this.report();

    if (!monthlyReport) {
      return;
    }

    const previousTitle = document.title;

    document.title = `LogicOTI_Reporte_${monthlyReport.month}`;
    document.body.classList.add('logicoti-report-print');

    try {
      window.print();
    } finally {
      document.body.classList.remove('logicoti-report-print');
      document.title = previousTitle;
    }
  }

  saveRetentionPolicy(): void {
    if (
      !Number.isInteger(this.retentionMonths) ||
      this.retentionMonths < 6 ||
      this.retentionMonths > 120
    ) {
      this.retentionErrorMessage.set('La conservación debe estar entre 6 y 120 meses.');
      return;
    }

    this.retentionSaving.set(true);
    this.retentionErrorMessage.set('');
    this.retentionSuccessMessage.set('');

    this.historyApi
      .updateRetentionPolicy(this.retentionEnabled, this.retentionMonths)
      .pipe(finalize(() => this.retentionSaving.set(false)))
      .subscribe({
        next: (response) => {
          this.applyRetentionPolicy(response);
          this.retentionSuccessMessage.set(
            response.enabled
              ? 'Política guardada. La limpieza automática queda habilitada.'
              : 'Política guardada. No se eliminarán registros automáticamente.',
          );
        },
        error: () => {
          this.retentionErrorMessage.set('No fue posible guardar la política de retención.');
        },
      });
  }

  runRetentionNow(): void {
    const policy = this.retention();

    if (!policy?.enabled || policy.candidates.total === 0 || this.retentionRunning()) {
      return;
    }

    const confirmed = window.confirm(
      `Se eliminarán permanentemente ${policy.candidates.total} registros anteriores al ` +
        `${new Date(policy.cutoffAt).toLocaleString('es-MX')}. ¿Deseas continuar?`,
    );

    if (!confirmed) {
      return;
    }

    this.retentionRunning.set(true);
    this.retentionErrorMessage.set('');
    this.retentionSuccessMessage.set('');

    this.historyApi
      .runRetention()
      .pipe(finalize(() => this.retentionRunning.set(false)))
      .subscribe({
        next: (response) => {
          this.retentionSuccessMessage.set(
            `Limpieza terminada: ${response.deleted.total} registros eliminados.`,
          );
          this.loadRetentionPolicy(false);
        },
        error: () => {
          this.retentionErrorMessage.set('No fue posible ejecutar la limpieza de históricos.');
        },
      });
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

  private loadRetentionPolicy(clearMessages = true): void {
    this.retentionLoading.set(true);

    if (clearMessages) {
      this.retentionErrorMessage.set('');
      this.retentionSuccessMessage.set('');
    }

    this.historyApi
      .getRetentionPolicy()
      .pipe(finalize(() => this.retentionLoading.set(false)))
      .subscribe({
        next: (response) => this.applyRetentionPolicy(response),
        error: () => {
          this.retentionErrorMessage.set('No fue posible consultar la política de retención.');
        },
      });
  }

  private applyRetentionPolicy(response: HistoryRetentionPolicy): void {
    this.retention.set(response);
    this.retentionEnabled = response.enabled;
    this.retentionMonths = response.retentionMonths;
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
