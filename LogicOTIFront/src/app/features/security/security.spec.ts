import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, of, Subject } from 'rxjs';

import {
  SecurityActionResponse,
  SecurityPrecheck,
  SecuritySettings,
  SecurityStatus,
} from '../../core/models/security.model';
import { AuthService } from '../../core/services/auth.service';
import { RealtimeConnectionStatus } from '../../core/services/smoke-alert-realtime.service';
import { SecurityApiService } from '../../core/services/security-api.service';
import { SecurityRealtimeService } from '../../core/services/security-realtime.service';
import { Security } from './security';

const days = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo'].map(
  (dayName, index) => ({
    dayOfWeek: index + 1,
    dayName,
    enabled: true,
    allDayArmed: index > 4,
    armTime: '18:00:00',
    disarmTime: '08:00:00',
  }),
);

const status: SecurityStatus = {
  mode: 'DISARMED',
  armed: false,
  alarmActive: false,
  message: 'La alarma se encuentra desarmada.',
  changedBy: 'SYSTEM',
  changeSource: 'SYSTEM',
  changedAt: '2026-08-07T17:00:00Z',
  armingCompletesAt: null,
  automaticScheduleEnabled: true,
  timezone: 'America/Mazatlan',
  exitDelaySeconds: 60,
  lightInactivityMinutes: 10,
  minisplitInactivityMinutes: 30,
  timestamp: '2026-08-07T17:00:00Z',
};

const precheck: SecurityPrecheck = {
  ready: true,
  plcEnabled: true,
  plcConnected: true,
  totalMotionSensors: 28,
  readableMotionSensors: 28,
  issues: [],
  timestamp: '2026-08-07T17:00:00Z',
};

const settings: SecuritySettings = {
  automaticScheduleEnabled: true,
  timezone: 'America/Mazatlan',
  exitDelaySeconds: 60,
  lightInactivityMinutes: 10,
  minisplitInactivityMinutes: 30,
  diagnosticTimeoutSeconds: 120,
  diagnosticValidityMonths: 4,
  days,
  updatedAt: '2026-08-07T17:00:00Z',
  updatedBy: 'SYSTEM',
};

class SecurityApiServiceMock {
  readonly getStatus = vi.fn(() => of(status));
  readonly getPrecheck = vi.fn(() => of(precheck));
  readonly getSchedules = vi.fn(() => of(settings));
  readonly arm = vi.fn(() =>
    of<SecurityActionResponse>({
      status: { ...status, mode: 'ARMING', message: 'La alarma se está armando.' },
      precheck,
    }),
  );
  readonly disarm = vi.fn(() => of<SecurityActionResponse>({ status, precheck: null }));
  readonly updateSchedules = vi.fn(() => of(settings));
}

class SecurityRealtimeServiceMock {
  private readonly statusSubject = new Subject<SecurityStatus>();
  private readonly connectionSubject = new BehaviorSubject<RealtimeConnectionStatus>('CONNECTED');

  readonly status$ = this.statusSubject.asObservable();
  readonly connectionStatus$ = this.connectionSubject.asObservable();
  readonly connect = vi.fn();
  readonly disconnect = vi.fn(async () => undefined);

  emit(value: SecurityStatus): void {
    this.statusSubject.next(value);
  }
}

class AuthServiceMock {
  readonly getSession = vi.fn(() => ({
    user: {
      id: 2,
      username: 'admin',
      fullName: 'Administrador',
      role: 'ADMIN' as const,
    },
  }));
}

describe('Security', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Security],
      providers: [
        { provide: SecurityApiService, useClass: SecurityApiServiceMock },
        { provide: SecurityRealtimeService, useClass: SecurityRealtimeServiceMock },
        { provide: AuthService, useClass: AuthServiceMock },
      ],
    }).compileComponents();
  });

  it('carga el estado, la revisión y los siete horarios', () => {
    const fixture = TestBed.createComponent(Security);
    fixture.detectChanges();

    expect(fixture.componentInstance.status()?.mode).toBe('DISARMED');
    expect(fixture.componentInstance.precheck()?.ready).toBe(true);
    expect(fixture.componentInstance.scheduleForm()?.days).toHaveLength(7);
    fixture.destroy();
  });

  it('solicita confirmación antes de armar y luego ejecuta la orden', () => {
    const fixture = TestBed.createComponent(Security);
    const component = fixture.componentInstance;
    const api = TestBed.inject(SecurityApiService) as unknown as SecurityApiServiceMock;
    fixture.detectChanges();

    component.requestAction('ARM');
    expect(component.pendingAction()).toBe('ARM');

    component.confirmAction();
    expect(api.arm).toHaveBeenCalledTimes(1);
    expect(component.status()?.mode).toBe('ARMING');
    fixture.destroy();
  });

  it('guarda los horarios completos como administrador', () => {
    const fixture = TestBed.createComponent(Security);
    const component = fixture.componentInstance;
    const api = TestBed.inject(SecurityApiService) as unknown as SecurityApiServiceMock;
    fixture.detectChanges();

    component.updateNumber('lightInactivityMinutes', 15);
    component.saveSchedules();

    expect(api.updateSchedules).toHaveBeenCalledWith(
      expect.objectContaining({
        lightInactivityMinutes: 15,
        days: expect.arrayContaining([expect.objectContaining({ dayOfWeek: 1 })]),
      }),
    );
    fixture.destroy();
  });

  it('actualiza el estado cuando llega un mensaje por WebSocket', () => {
    const fixture = TestBed.createComponent(Security);
    const realtime = TestBed.inject(
      SecurityRealtimeService,
    ) as unknown as SecurityRealtimeServiceMock;
    fixture.detectChanges();

    realtime.emit({
      ...status,
      mode: 'ARMED',
      armed: true,
      message: 'La alarma quedó armada correctamente.',
    });

    expect(fixture.componentInstance.status()?.mode).toBe('ARMED');
    fixture.destroy();
  });
});
