import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin, interval } from 'rxjs';

import { UserRole } from '../../core/models/auth.model';
import {
  AlarmMode,
  SecurityActionResponse,
  SecurityPrecheck,
  SecurityScheduleDay,
  SecuritySettings,
  SecuritySettingsUpdateRequest,
  SecurityStatus,
} from '../../core/models/security.model';
import { AuthService } from '../../core/services/auth.service';
import { RealtimeConnectionStatus } from '../../core/services/smoke-alert-realtime.service';
import { SecurityApiService } from '../../core/services/security-api.service';
import { SecurityRealtimeService } from '../../core/services/security-realtime.service';

type PendingAction = 'ARM' | 'DISARM';
type NumericSetting =
  | 'exitDelaySeconds'
  | 'lightInactivityMinutes'
  | 'minisplitInactivityMinutes'
  | 'diagnosticTimeoutSeconds'
  | 'diagnosticValidityMonths';

@Component({
  selector: 'app-security',
  standalone: true,
  imports: [DatePipe, FormsModule],
  templateUrl: './security.html',
  styleUrl: './security.scss',
})
export class Security implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  private readonly authService = inject(AuthService);
  private readonly api = inject(SecurityApiService);
  private readonly realtime = inject(SecurityRealtimeService);

  readonly loading = signal(false);
  readonly refreshingPrecheck = signal(false);
  readonly savingSettings = signal(false);
  readonly executingAction = signal(false);
  readonly errorMessage = signal('');
  readonly successMessage = signal('');
  readonly status = signal<SecurityStatus | null>(null);
  readonly precheck = signal<SecurityPrecheck | null>(null);
  readonly settings = signal<SecuritySettings | null>(null);
  readonly scheduleForm = signal<SecuritySettingsUpdateRequest | null>(null);
  readonly connectionStatus = signal<RealtimeConnectionStatus>('DISCONNECTED');
  readonly currentTime = signal(Date.now());
  readonly pendingAction = signal<PendingAction | null>(null);

  readonly currentRole: UserRole = this.authService.getSession()?.user.role ?? 'MONITORING';
  readonly canOperate = this.currentRole === 'ADMIN' || this.currentRole === 'OPERATOR';
  readonly canEditSettings = this.currentRole === 'ADMIN';

  readonly blockingIssues = computed(
    () => this.precheck()?.issues.filter((issue) => issue.blocking).length ?? 0,
  );

  readonly warningIssues = computed(
    () => this.precheck()?.issues.filter((issue) => !issue.blocking).length ?? 0,
  );

  readonly armingRemainingSeconds = computed(() => {
    this.currentTime();
    const completesAt = this.status()?.armingCompletesAt;

    return completesAt
      ? Math.max(0, Math.ceil((new Date(completesAt).getTime() - Date.now()) / 1000))
      : 0;
  });

  ngOnInit(): void {
    this.realtime.connectionStatus$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((connectionStatus) => this.connectionStatus.set(connectionStatus));

    this.realtime.status$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((status) => this.receiveStatus(status));

    interval(1000)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.currentTime.set(Date.now()));

    this.realtime.connect();
    this.loadAll();
  }

  ngOnDestroy(): void {
    void this.realtime.disconnect();
  }

  loadAll(): void {
    this.loading.set(true);
    this.clearMessages();

    forkJoin({
      status: this.api.getStatus(),
      precheck: this.api.getPrecheck(),
      settings: this.api.getSchedules(),
    })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ status, precheck, settings }) => {
          this.status.set(status);
          this.precheck.set(precheck);
          this.applySettings(settings);
        },
        error: (error: HttpErrorResponse) =>
          this.errorMessage.set(
            this.getErrorMessage(error, 'No fue posible cargar la configuración de seguridad.'),
          ),
      });
  }

  refreshPrecheck(): void {
    this.refreshingPrecheck.set(true);
    this.clearMessages();

    this.api
      .getPrecheck()
      .pipe(finalize(() => this.refreshingPrecheck.set(false)))
      .subscribe({
        next: (precheck) => this.precheck.set(precheck),
        error: (error: HttpErrorResponse) =>
          this.errorMessage.set(
            this.getErrorMessage(error, 'No fue posible revisar los sensores.'),
          ),
      });
  }

  requestAction(action: PendingAction): void {
    if (!this.canOperate || this.executingAction()) {
      return;
    }

    this.clearMessages();
    this.pendingAction.set(action);
  }

  closeActionConfirmation(): void {
    if (!this.executingAction()) {
      this.pendingAction.set(null);
    }
  }

  confirmAction(): void {
    const action = this.pendingAction();

    if (!action || !this.canOperate) {
      return;
    }

    this.executingAction.set(true);
    this.clearMessages();

    const request = action === 'ARM' ? this.api.arm() : this.api.disarm();

    request.pipe(finalize(() => this.executingAction.set(false))).subscribe({
      next: (response) => {
        this.pendingAction.set(null);
        this.applyActionResponse(response, action);
      },
      error: (error: HttpErrorResponse) =>
        this.errorMessage.set(
          this.getErrorMessage(
            error,
            action === 'ARM'
              ? 'No fue posible armar la alarma.'
              : 'No fue posible desarmar la alarma.',
          ),
        ),
    });
  }

  updateAutomaticSchedule(enabled: boolean): void {
    this.scheduleForm.update((form) =>
      form ? { ...form, automaticScheduleEnabled: enabled } : form,
    );
  }

  updateTimezone(timezone: string): void {
    this.scheduleForm.update((form) => (form ? { ...form, timezone } : form));
  }

  updateNumber(field: NumericSetting, value: number | string): void {
    const numericValue = Number(value);

    if (!Number.isFinite(numericValue)) {
      return;
    }

    this.scheduleForm.update((form) =>
      form ? { ...form, [field]: Math.trunc(numericValue) } : form,
    );
  }

  updateDayBoolean(dayOfWeek: number, field: 'enabled' | 'allDayArmed', value: boolean): void {
    this.scheduleForm.update((form) =>
      form
        ? {
            ...form,
            days: form.days.map((day) =>
              day.dayOfWeek === dayOfWeek ? { ...day, [field]: value } : day,
            ),
          }
        : form,
    );
  }

  updateDayTime(dayOfWeek: number, field: 'armTime' | 'disarmTime', value: string): void {
    this.scheduleForm.update((form) =>
      form
        ? {
            ...form,
            days: form.days.map((day) =>
              day.dayOfWeek === dayOfWeek ? { ...day, [field]: value } : day,
            ),
          }
        : form,
    );
  }

  saveSchedules(): void {
    const form = this.scheduleForm();

    if (!form || !this.canEditSettings || !this.validateSchedule(form)) {
      return;
    }

    this.savingSettings.set(true);
    this.clearMessages();

    this.api
      .updateSchedules(form)
      .pipe(finalize(() => this.savingSettings.set(false)))
      .subscribe({
        next: (settings) => {
          this.applySettings(settings);
          this.status.update((status) =>
            status
              ? {
                  ...status,
                  automaticScheduleEnabled: settings.automaticScheduleEnabled,
                  timezone: settings.timezone,
                  exitDelaySeconds: settings.exitDelaySeconds,
                  lightInactivityMinutes: settings.lightInactivityMinutes,
                  minisplitInactivityMinutes: settings.minisplitInactivityMinutes,
                }
              : status,
          );
          this.successMessage.set('La configuración de seguridad quedó guardada.');
        },
        error: (error: HttpErrorResponse) =>
          this.errorMessage.set(
            this.getErrorMessage(error, 'No fue posible guardar los horarios.'),
          ),
      });
  }

  resetScheduleForm(): void {
    const settings = this.settings();

    if (settings) {
      this.scheduleForm.set(this.toRequest(settings));
      this.clearMessages();
    }
  }

  modeLabel(mode: AlarmMode | undefined): string {
    const labels: Record<AlarmMode, string> = {
      DISARMED: 'Desarmada',
      ARMING: 'Armándose',
      ARMED: 'Armada',
      ARMED_WITH_BYPASS: 'Armada con omisiones',
      REJECTED: 'Armado rechazado',
      ALARM: 'Alarma activa',
    };

    return mode ? labels[mode] : 'Sin información';
  }

  sourceLabel(source: SecurityStatus['changeSource'] | undefined): string {
    const labels = {
      MANUAL: 'Acción manual',
      SCHEDULE: 'Horario automático',
      SYSTEM: 'Sistema',
    };

    return source ? labels[source] : 'Sin información';
  }

  dayName(dayOfWeek: number): string {
    const names = ['', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'];

    return names[dayOfWeek] ?? `Día ${dayOfWeek}`;
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

  trackDay(_: number, day: SecurityScheduleDay | { dayOfWeek: number }): number {
    return day.dayOfWeek;
  }

  private receiveStatus(status: SecurityStatus): void {
    this.status.set(status);

    if (status.mode === 'ARMED' || status.mode === 'ARMED_WITH_BYPASS') {
      this.successMessage.set(status.message);
    } else if (status.mode === 'REJECTED' || status.mode === 'ALARM') {
      this.errorMessage.set(status.message);
    }
  }

  private applyActionResponse(response: SecurityActionResponse, action: PendingAction): void {
    this.status.set(response.status);

    if (response.precheck) {
      this.precheck.set(response.precheck);
    }

    if (response.status.mode === 'REJECTED') {
      this.errorMessage.set(response.status.message);
      return;
    }

    this.successMessage.set(
      action === 'ARM' ? response.status.message : 'La alarma quedó desarmada correctamente.',
    );
  }

  private applySettings(settings: SecuritySettings): void {
    this.settings.set(settings);
    this.scheduleForm.set(this.toRequest(settings));
  }

  private toRequest(settings: SecuritySettings): SecuritySettingsUpdateRequest {
    return {
      automaticScheduleEnabled: settings.automaticScheduleEnabled,
      timezone: settings.timezone,
      exitDelaySeconds: settings.exitDelaySeconds,
      lightInactivityMinutes: settings.lightInactivityMinutes,
      minisplitInactivityMinutes: settings.minisplitInactivityMinutes,
      diagnosticTimeoutSeconds: settings.diagnosticTimeoutSeconds,
      diagnosticValidityMonths: settings.diagnosticValidityMonths,
      days: settings.days.map((day) => ({
        dayOfWeek: day.dayOfWeek,
        enabled: day.enabled,
        allDayArmed: day.allDayArmed,
        armTime: this.toTimeInput(day.armTime),
        disarmTime: this.toTimeInput(day.disarmTime),
      })),
    };
  }

  private toTimeInput(time: string): string {
    return time?.slice(0, 5) || '00:00';
  }

  private validateSchedule(form: SecuritySettingsUpdateRequest): boolean {
    if (!form.timezone.trim()) {
      this.errorMessage.set('La zona horaria es obligatoria.');
      return false;
    }

    if (form.days.length !== 7) {
      this.errorMessage.set('La configuración debe contener los siete días de la semana.');
      return false;
    }

    const invalidDay = form.days.find(
      (day) =>
        !day.armTime || !day.disarmTime || (!day.allDayArmed && day.armTime === day.disarmTime),
    );

    if (invalidDay) {
      this.errorMessage.set(
        `Revisa el horario de ${this.dayName(invalidDay.dayOfWeek)}. Las horas no pueden ser iguales.`,
      );
      return false;
    }

    return true;
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
