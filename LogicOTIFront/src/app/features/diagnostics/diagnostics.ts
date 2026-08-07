import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin, interval } from 'rxjs';

import { UserRole } from '../../core/models/auth.model';
import {
  DiagnosticStatus,
  SecurityWarning,
  SensorBypass,
  SensorDiagnostic,
  SensorDiagnosticDueItem,
  SensorDiagnosticDueResponse,
  SensorDueStatus,
  SensorType,
} from '../../core/models/sensor-diagnostic.model';
import { AuthService } from '../../core/services/auth.service';
import { SensorDiagnosticApiService } from '../../core/services/sensor-diagnostic-api.service';
import { SensorDiagnosticRealtimeService } from '../../core/services/sensor-diagnostic-realtime.service';
import { RealtimeConnectionStatus } from '../../core/services/smoke-alert-realtime.service';

type TypeFilter = 'ALL' | SensorType;
type StatusFilter = 'ALL' | SensorDueStatus;

@Component({
  selector: 'app-diagnostics',
  standalone: true,
  imports: [DatePipe, FormsModule],
  templateUrl: './diagnostics.html',
  styleUrl: './diagnostics.scss',
})
export class Diagnostics implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  private readonly authService = inject(AuthService);
  private readonly diagnosticApi = inject(SensorDiagnosticApiService);
  private readonly realtime = inject(SensorDiagnosticRealtimeService);

  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly actionId = signal<number | null>(null);
  readonly errorMessage = signal('');
  readonly successMessage = signal('');
  readonly due = signal<SensorDiagnosticDueResponse | null>(null);
  readonly diagnostics = signal<SensorDiagnostic[]>([]);
  readonly bypasses = signal<SensorBypass[]>([]);
  readonly warnings = signal<SecurityWarning[]>([]);
  readonly connectionStatus = signal<RealtimeConnectionStatus>('DISCONNECTED');
  readonly currentTime = signal(Date.now());

  readonly searchText = signal('');
  readonly areaFilter = signal('ALL');
  readonly typeFilter = signal<TypeFilter>('ALL');
  readonly statusFilter = signal<StatusFilter>('ALL');
  readonly selectedCodes = signal<Set<string>>(new Set());

  readonly bypassTarget = signal<SensorDiagnosticDueItem | null>(null);
  readonly bypassReason = signal('');
  readonly revokeTarget = signal<SensorBypass | null>(null);

  readonly currentRole: UserRole = this.authService.getSession()?.user.role ?? 'MONITORING';
  readonly canRunDiagnostics = this.currentRole === 'ADMIN' || this.currentRole === 'OPERATOR';
  readonly canManageBypasses = this.currentRole === 'ADMIN';

  readonly activeDiagnostics = computed(() =>
    this.diagnostics().filter((diagnostic) => diagnostic.status === 'RUNNING'),
  );

  readonly recentDiagnostics = computed(() =>
    this.diagnostics()
      .filter((diagnostic) => diagnostic.status !== 'RUNNING')
      .slice(0, 8),
  );

  readonly runningSensorCodes = computed(() => {
    const codes = new Set<string>();

    this.activeDiagnostics().forEach((diagnostic) =>
      diagnostic.sensors.forEach((sensor) => codes.add(sensor.deviceCode)),
    );

    return codes;
  });

  readonly activeBypassByCode = computed(() => {
    const result = new Map<string, SensorBypass>();

    this.bypasses()
      .filter((bypass) => bypass.active)
      .forEach((bypass) => result.set(bypass.sensorCode, bypass));

    return result;
  });

  readonly activeBypasses = computed(() => this.bypasses().filter((bypass) => bypass.active));

  readonly activeWarnings = computed(() => {
    const activeBypassIds = new Set(this.activeBypasses().map((bypass) => bypass.id));

    return this.warnings().filter((warning) => activeBypassIds.has(warning.bypassId));
  });

  readonly areaOptions = computed(() => {
    const areas = new Map<string, string>();

    this.due()?.sensors.forEach((sensor) => areas.set(sensor.areaCode, sensor.areaName));

    return [...areas.entries()]
      .map(([code, name]) => ({ code, name }))
      .sort((first, second) => first.name.localeCompare(second.name, 'es'));
  });

  readonly filteredSensors = computed(() => {
    const search = this.searchText().trim().toLowerCase();
    const area = this.areaFilter();
    const type = this.typeFilter();
    const status = this.statusFilter();

    return (this.due()?.sensors ?? []).filter((sensor) => {
      const matchesSearch =
        !search ||
        sensor.deviceCode.toLowerCase().includes(search) ||
        sensor.deviceName.toLowerCase().includes(search) ||
        sensor.areaCode.toLowerCase().includes(search) ||
        sensor.areaName.toLowerCase().includes(search);

      return (
        matchesSearch &&
        (area === 'ALL' || sensor.areaCode === area) &&
        (type === 'ALL' || sensor.deviceType === type) &&
        (status === 'ALL' || sensor.status === status)
      );
    });
  });

  readonly allFilteredSelected = computed(() => {
    const available = this.filteredSensors().filter(
      (sensor) => !this.runningSensorCodes().has(sensor.deviceCode),
    );

    return (
      available.length > 0 &&
      available.every((sensor) => this.selectedCodes().has(sensor.deviceCode))
    );
  });

  readonly validSensors = computed(
    () => this.due()?.sensors.filter((sensor) => sensor.status === 'VALID').length ?? 0,
  );

  ngOnInit(): void {
    this.realtime.connectionStatus$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((status) => this.connectionStatus.set(status));

    this.realtime.diagnostics$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((diagnostic) => this.receiveDiagnostic(diagnostic));

    this.realtime.warnings$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((warning) => this.receiveWarning(warning));

    interval(1000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.currentTime.set(Date.now()));

    this.realtime.ensureConnected();
    this.loadAll();
  }

  ngOnDestroy(): void {
    void this.realtime.disconnect();
  }

  loadAll(): void {
    this.loading.set(true);
    this.clearMessages();

    forkJoin({
      due: this.diagnosticApi.getDueSensors(),
      diagnostics: this.diagnosticApi.getDiagnostics(undefined, 20),
      bypasses: this.diagnosticApi.getBypasses(),
      warnings: this.diagnosticApi.getWarnings(50),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (result) => {
          this.due.set(result.due);
          this.diagnostics.set(result.diagnostics.items);
          this.bypasses.set(result.bypasses.items);
          this.warnings.set(result.warnings.items);
          this.watchActiveDiagnostics(result.diagnostics.items);
          this.removeUnavailableSelections();
        },
        error: () => {
          this.errorMessage.set('No fue posible cargar la información de diagnóstico de sensores.');
        },
      });
  }

  toggleSensor(sensorCode: string): void {
    if (this.runningSensorCodes().has(sensorCode)) {
      return;
    }

    this.selectedCodes.update((current) => {
      const updated = new Set(current);

      if (updated.has(sensorCode)) {
        updated.delete(sensorCode);
      } else {
        updated.add(sensorCode);
      }

      return updated;
    });
  }

  toggleAllFiltered(): void {
    const availableCodes = this.filteredSensors()
      .filter((sensor) => !this.runningSensorCodes().has(sensor.deviceCode))
      .map((sensor) => sensor.deviceCode);

    this.selectedCodes.update((current) => {
      const updated = new Set(current);
      const shouldSelect = !availableCodes.every((code) => updated.has(code));

      availableCodes.forEach((code) => {
        if (shouldSelect) {
          updated.add(code);
        } else {
          updated.delete(code);
        }
      });

      return updated;
    });
  }

  clearFilters(): void {
    this.searchText.set('');
    this.areaFilter.set('ALL');
    this.typeFilter.set('ALL');
    this.statusFilter.set('ALL');
  }

  startDiagnostic(): void {
    if (!this.canRunDiagnostics || this.selectedCodes().size === 0) {
      return;
    }

    this.clearMessages();
    this.saving.set(true);

    this.diagnosticApi
      .startDiagnostic([...this.selectedCodes()])
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (diagnostic) => {
          this.replaceDiagnostic(diagnostic);
          this.realtime.watchSession(diagnostic.id);
          this.selectedCodes.set(new Set());
          this.successMessage.set(
            `Diagnóstico ${diagnostic.id} iniciado. Activa y restablece cada sensor antes de que termine el tiempo.`,
          );
        },
        error: (error: HttpErrorResponse) =>
          this.errorMessage.set(
            this.getErrorMessage(error, 'No fue posible iniciar el diagnóstico.'),
          ),
      });
  }

  cancelDiagnostic(diagnostic: SensorDiagnostic): void {
    if (!this.canRunDiagnostics || diagnostic.status !== 'RUNNING') {
      return;
    }

    this.clearMessages();
    this.actionId.set(diagnostic.id);

    this.diagnosticApi
      .cancelDiagnostic(diagnostic.id)
      .pipe(finalize(() => this.actionId.set(null)))
      .subscribe({
        next: (updated) => {
          this.receiveDiagnostic(updated);
          this.successMessage.set(`El diagnóstico ${updated.id} fue cancelado.`);
        },
        error: (error: HttpErrorResponse) =>
          this.errorMessage.set(
            this.getErrorMessage(error, 'No fue posible cancelar el diagnóstico.'),
          ),
      });
  }

  openBypass(sensor: SensorDiagnosticDueItem): void {
    if (!this.canManageBypasses || sensor.deviceType !== 'MOTION') {
      return;
    }

    this.clearMessages();
    this.bypassReason.set('');
    this.bypassTarget.set(sensor);
  }

  closeBypass(): void {
    if (!this.saving()) {
      this.bypassTarget.set(null);
      this.bypassReason.set('');
    }
  }

  createBypass(): void {
    const sensor = this.bypassTarget();
    const reason = this.bypassReason().trim();

    if (!sensor || !this.canManageBypasses || reason.length === 0 || reason.length > 300) {
      this.errorMessage.set('Escribe un motivo de entre 1 y 300 caracteres.');
      return;
    }

    this.clearMessages();
    this.saving.set(true);

    this.diagnosticApi
      .createBypass(sensor.deviceCode, reason)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (bypass) => {
          this.replaceBypass(bypass);
          this.bypassTarget.set(null);
          this.bypassReason.set('');
          this.successMessage.set(
            `${bypass.sensorName} quedó omitido temporalmente para el armado de la alarma.`,
          );
        },
        error: (error: HttpErrorResponse) =>
          this.errorMessage.set(this.getErrorMessage(error, 'No fue posible omitir el sensor.')),
      });
  }

  requestRevoke(bypass: SensorBypass): void {
    if (this.canManageBypasses) {
      this.clearMessages();
      this.revokeTarget.set(bypass);
    }
  }

  closeRevoke(): void {
    if (!this.saving()) {
      this.revokeTarget.set(null);
    }
  }

  revokeBypass(): void {
    const bypass = this.revokeTarget();

    if (!bypass || !this.canManageBypasses) {
      return;
    }

    this.clearMessages();
    this.saving.set(true);

    this.diagnosticApi
      .revokeBypass(bypass.sensorCode)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (revoked) => {
          this.bypasses.update((items) =>
            items.map((item) => (item.id === revoked.id ? revoked : item)),
          );
          this.warnings.update((items) =>
            items.filter((warning) => warning.bypassId !== revoked.id),
          );
          this.revokeTarget.set(null);
          this.successMessage.set(`${revoked.sensorName} volvió a participar en el armado.`);
        },
        error: (error: HttpErrorResponse) =>
          this.errorMessage.set(
            this.getErrorMessage(error, 'No fue posible reincorporar el sensor.'),
          ),
      });
  }

  remainingSeconds(diagnostic: SensorDiagnostic): number {
    this.currentTime();

    return Math.max(0, Math.ceil((new Date(diagnostic.expiresAt).getTime() - Date.now()) / 1000));
  }

  diagnosticProgress(diagnostic: SensorDiagnostic): number {
    return Math.max(0, Math.min(100, (this.remainingSeconds(diagnostic) / 120) * 100));
  }

  sensorTypeLabel(type: SensorType): string {
    return type === 'MOTION' ? 'Movimiento' : 'Humo';
  }

  dueStatusLabel(status: SensorDueStatus): string {
    const labels: Record<SensorDueStatus, string> = {
      VALID: 'Vigente',
      DUE: 'Pendiente',
      EXPIRED: 'Vencido',
    };

    return labels[status];
  }

  diagnosticStatusLabel(status: DiagnosticStatus): string {
    const labels: Record<DiagnosticStatus, string> = {
      RUNNING: 'En proceso',
      PASSED: 'Aprobado',
      REJECTED: 'Rechazado',
      CANCELLED: 'Cancelado',
    };

    return labels[status];
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

  private receiveDiagnostic(diagnostic: SensorDiagnostic): void {
    this.replaceDiagnostic(diagnostic);

    if (diagnostic.status === 'RUNNING') {
      this.realtime.watchSession(diagnostic.id);
      return;
    }

    this.realtime.unwatchSession(diagnostic.id);
    this.diagnosticApi.getDueSensors().subscribe({
      next: (response) => this.due.set(response),
    });
  }

  private receiveWarning(warning: SecurityWarning): void {
    this.warnings.update((items) => {
      const withoutRepeated = items.filter((item) => item.id !== warning.id);
      return [warning, ...withoutRepeated].slice(0, 50);
    });
  }

  private watchActiveDiagnostics(diagnostics: SensorDiagnostic[]): void {
    diagnostics
      .filter((diagnostic) => diagnostic.status === 'RUNNING')
      .forEach((diagnostic) => this.realtime.watchSession(diagnostic.id));
  }

  private replaceDiagnostic(diagnostic: SensorDiagnostic): void {
    this.diagnostics.update((items) => {
      const withoutCurrent = items.filter((item) => item.id !== diagnostic.id);
      return [diagnostic, ...withoutCurrent]
        .sort((first, second) => second.id - first.id)
        .slice(0, 20);
    });
  }

  private replaceBypass(bypass: SensorBypass): void {
    this.bypasses.update((items) => {
      const withoutCurrent = items.filter((item) => item.id !== bypass.id);
      return [bypass, ...withoutCurrent];
    });
  }

  private removeUnavailableSelections(): void {
    const validCodes = new Set((this.due()?.sensors ?? []).map((sensor) => sensor.deviceCode));

    this.selectedCodes.update(
      (current) => new Set([...current].filter((code) => validCodes.has(code))),
    );
  }

  private clearMessages(): void {
    this.errorMessage.set('');
    this.successMessage.set('');
  }

  private getErrorMessage(error: HttpErrorResponse, fallback: string): string {
    const detail = error.error?.detail ?? error.error?.message;

    return typeof detail === 'string' && detail.trim() ? detail : fallback;
  }
}
