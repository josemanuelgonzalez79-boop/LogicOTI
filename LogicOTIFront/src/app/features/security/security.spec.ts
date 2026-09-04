import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, of, Subject } from 'rxjs';

import {
  AreaInactivityStatus,
  AutomaticLightingStatus,
  SecurityPrecheck,
  SecuritySettings,
  SecurityStatus,
  SecurityZoneActionResponse,
  SecurityZoneList,
} from '../../core/models/security.model';
import { AuthService } from '../../core/services/auth.service';
import { RealtimeConnectionStatus } from '../../core/services/smoke-alert-realtime.service';
import { SecurityApiService } from '../../core/services/security-api.service';
import { PushNotificationService } from '../../core/services/push-notification.service';
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

const zoneStatus: SecurityZoneList = {
  aggregateMode: 'DISARMED',
  message: 'Todas las zonas se encuentran desarmadas.',
  totalZones: 4,
  armedZones: 0,
  alarmZones: 0,
  zones: ['PB', 'P1', 'P2', 'PATIO'].map((code, index) => ({
    code,
    name: code === 'PATIO' ? 'Patio y exterior' : code,
    displayOrder: index + 1,
    motionDetectionEnabled: code !== 'PATIO',
    motionSensorCount: code === 'PATIO' ? 0 : 8,
    lightCircuitCount: 2,
    availableLightCircuitCount: 2,
    controlledLightCount: 0,
    mode: 'DISARMED',
    armed: false,
    alarmActive: false,
    message: 'La zona se encuentra desarmada.',
    changedBy: 'SYSTEM',
    changeSource: 'SYSTEM',
    changedAt: '2026-08-07T17:00:00Z',
    armingCompletesAt: null,
    alarmEventId: null,
  })),
  timestamp: '2026-08-07T17:00:00Z',
};

const armingZoneStatus: SecurityZoneList = {
  ...zoneStatus,
  aggregateMode: 'ARMING',
  message: 'Una o más zonas están completando el tiempo de salida.',
  zones: zoneStatus.zones.map((zone) => ({
    ...zone,
    mode: 'ARMING',
    message: `${zone.name} se está armando.`,
    armingCompletesAt: '2026-08-07T17:01:00Z',
  })),
};

const settings: SecuritySettings = {
  automaticScheduleEnabled: true,
  automaticLightingEnabled: true,
  automaticLightingStartTime: '18:00:00',
  automaticLightingEndTime: '08:00:00',
  areaInactivityEnabled: false,
  timezone: 'America/Mazatlan',
  exitDelaySeconds: 60,
  lightInactivityMinutes: 10,
  minisplitInactivityMinutes: 30,
  diagnosticTimeoutSeconds: 120,
  diagnosticValidityMonths: 4,
  days,
  lightingTargets: [
    {
      deviceId: 1,
      deviceCode: 'P1_A03_LUZ01',
      deviceName: 'Iluminación general',
      areaCode: 'P1_A03',
      areaName: 'Pasillo principal',
      floorCode: 'P1',
      floorName: 'Piso 1',
      selected: true,
    },
  ],
  updatedAt: '2026-08-07T17:00:00Z',
  updatedBy: 'SYSTEM',
};

const automaticLightingStatus: AutomaticLightingStatus = {
  enabled: true,
  withinSchedule: true,
  startTime: '18:00:00',
  endTime: '08:00:00',
  inactivityMinutes: 10,
  configuredLights: 1,
  automaticLightsOn: 0,
  lastMotionAt: null,
  nextTurnOffAt: null,
  lights: [],
  message: 'Horario activo, sin luces encendidas automáticamente.',
  timestamp: '2026-08-07T17:00:00Z',
};

const areaInactivityStatus: AreaInactivityStatus = {
  enabled: false,
  lightInactivityMinutes: 10,
  minisplitInactivityMinutes: 30,
  trackedAreas: 0,
  pendingAreas: 0,
  lightsTurnedOff: 0,
  minisplitsTurnedOff: 0,
  lastMotionAt: null,
  nextActionAt: null,
  areas: [],
  message: 'El apagado por inactividad está deshabilitado.',
  timestamp: '2026-08-10T17:00:00Z',
};

class SecurityApiServiceMock {
  readonly getStatus = vi.fn(() => of(status));
  readonly getZones = vi.fn(() => of(zoneStatus));
  readonly getPrecheck = vi.fn(() => of(precheck));
  readonly getZonePrecheck = vi.fn(() => of(precheck));
  readonly getSchedules = vi.fn(() => of(settings));
  readonly getAutomaticLightingStatus = vi.fn(() => of(automaticLightingStatus));
  readonly getAreaInactivityStatus = vi.fn(() => of(areaInactivityStatus));
  readonly armZones = vi.fn(() =>
    of<SecurityZoneActionResponse>({
      status: armingZoneStatus,
      precheck,
    }),
  );
  readonly disarmZones = vi.fn(() =>
    of<SecurityZoneActionResponse>({ status: zoneStatus, precheck: null }),
  );
  readonly acknowledgeZone = vi.fn(() => of(zoneStatus));
  readonly updateSchedules = vi.fn(() => of(settings));
}

class SecurityRealtimeServiceMock {
  private readonly statusSubject = new Subject<SecurityStatus>();
  private readonly connectionSubject = new BehaviorSubject<RealtimeConnectionStatus>('CONNECTED');
  private readonly automaticLightingSubject = new Subject<AutomaticLightingStatus>();
  private readonly areaInactivitySubject = new Subject<AreaInactivityStatus>();
  private readonly zonesSubject = new Subject<SecurityZoneList>();

  readonly status$ = this.statusSubject.asObservable();
  readonly connectionStatus$ = this.connectionSubject.asObservable();
  readonly automaticLighting$ = this.automaticLightingSubject.asObservable();
  readonly areaInactivity$ = this.areaInactivitySubject.asObservable();
  readonly zones$ = this.zonesSubject.asObservable();
  readonly connect = vi.fn();
  readonly disconnect = vi.fn(async () => undefined);

  emit(value: SecurityStatus): void {
    this.statusSubject.next(value);
  }

  emitZones(value: SecurityZoneList): void {
    this.zonesSubject.next(value);
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

class PushNotificationServiceMock {
  readonly loading = signal(false);
  readonly browserSupported = signal(true);
  readonly serverConfigured = signal(true);
  readonly subscribed = signal(false);
  readonly subscriptionCount = signal(0);
  readonly message = signal('Avisos disponibles.');

  readonly initialize = vi.fn(async () => undefined);
  readonly enable = vi.fn(async () => undefined);
  readonly disable = vi.fn(async () => undefined);
  readonly sendTest = vi.fn(async () => undefined);
}

describe('Security', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Security],
      providers: [
        { provide: SecurityApiService, useClass: SecurityApiServiceMock },
        { provide: SecurityRealtimeService, useClass: SecurityRealtimeServiceMock },
        { provide: AuthService, useClass: AuthServiceMock },
        { provide: PushNotificationService, useClass: PushNotificationServiceMock },
      ],
    }).compileComponents();
  });

  it('carga el estado, la revisión y los siete horarios', () => {
    const fixture = TestBed.createComponent(Security);
    fixture.detectChanges();

    expect(fixture.componentInstance.status()?.mode).toBe('DISARMED');
    expect(fixture.componentInstance.precheck()?.ready).toBe(true);
    expect(fixture.componentInstance.scheduleForm()?.days).toHaveLength(7);
    expect(fixture.componentInstance.selectedLightingTargets()).toBe(1);
    expect(fixture.componentInstance.selectedZoneCount()).toBe(4);
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
    expect(api.armZones).toHaveBeenCalledWith(['PB', 'P1', 'P2', 'PATIO']);
    expect(component.zoneStatus()?.aggregateMode).toBe('ARMING');
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
        areaInactivityEnabled: false,
        automaticLightingTargetDeviceCodes: ['P1_A03_LUZ01'],
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

  it('conserva una selección vacía cuando llega el estado por WebSocket', () => {
    const fixture = TestBed.createComponent(Security);
    const component = fixture.componentInstance;
    const realtime = TestBed.inject(
      SecurityRealtimeService,
    ) as unknown as SecurityRealtimeServiceMock;
    fixture.detectChanges();

    component.clearZoneSelection();
    realtime.emitZones(zoneStatus);

    expect(component.selectedZoneCodes()).toEqual([]);
    expect(component.selectedZoneCount()).toBe(0);
    fixture.destroy();
  });

  it('reconoce una alarma de zona sin desarmarla', () => {
    const alarmZone = {
      ...zoneStatus.zones[0],
      mode: 'ALARM' as const,
      armed: true,
      alarmActive: true,
      alarmEventId: 24,
    };
    const fixture = TestBed.createComponent(Security);
    const component = fixture.componentInstance;
    const api = TestBed.inject(SecurityApiService) as unknown as SecurityApiServiceMock;
    fixture.detectChanges();

    component.acknowledgeZone(alarmZone);

    expect(api.acknowledgeZone).toHaveBeenCalledWith('PB');
    expect(component.successMessage()).toContain('permanece armada');
    fixture.destroy();
  });

  it('muestra el rechazo de una zona aunque otra permanezca armada', () => {
    const fixture = TestBed.createComponent(Security);
    const component = fixture.componentInstance;
    const api = TestBed.inject(SecurityApiService) as unknown as SecurityApiServiceMock;
    fixture.detectChanges();

    const rejectedStatus: SecurityZoneList = {
      ...zoneStatus,
      aggregateMode: 'PARTIALLY_ARMED',
      armedZones: 1,
      zones: zoneStatus.zones.map((zone) =>
        zone.code === 'PB'
          ? { ...zone, mode: 'REJECTED', message: 'Planta Baja tiene circuitos pendientes.' }
          : zone.code === 'P1'
            ? { ...zone, mode: 'ARMED', armed: true }
            : zone,
      ),
    };
    api.armZones.mockReturnValueOnce(
      of({
        status: rejectedStatus,
        precheck: { ...precheck, ready: false },
      }),
    );
    component.selectedZoneCodes.set(['PB']);

    component.requestAction('ARM');
    component.confirmAction();

    expect(component.errorMessage()).toContain('circuitos pendientes');
    expect(component.successMessage()).toBe('');
    fixture.destroy();
  });
});
