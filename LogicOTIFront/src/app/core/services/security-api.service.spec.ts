import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import {
  AreaInactivityStatus,
  AutomaticLightingStatus,
  SecurityPrecheck,
  SecuritySettings,
  SecuritySettingsUpdateRequest,
  SecurityStatus,
  SecurityZoneList,
} from '../models/security.model';
import { SecurityApiService } from './security-api.service';

const status: SecurityStatus = {
  mode: 'DISARMED',
  armed: false,
  alarmActive: false,
  message: 'La alarma está desarmada.',
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
  days: [],
  lightingTargets: [],
  updatedAt: '2026-08-07T17:00:00Z',
  updatedBy: 'admin',
};

const automaticLightingStatus: AutomaticLightingStatus = {
  enabled: true,
  withinSchedule: true,
  startTime: '18:00:00',
  endTime: '08:00:00',
  inactivityMinutes: 10,
  configuredLights: 0,
  automaticLightsOn: 0,
  lastMotionAt: null,
  nextTurnOffAt: null,
  lights: [],
  message: 'Horario activo, sin luces encendidas automáticamente.',
  timestamp: '2026-08-07T17:00:00Z',
};

const areaInactivityStatus: AreaInactivityStatus = {
  enabled: true,
  lightInactivityMinutes: 10,
  minisplitInactivityMinutes: 30,
  trackedAreas: 1,
  pendingAreas: 1,
  lightsTurnedOff: 0,
  minisplitsTurnedOff: 0,
  lastMotionAt: '2026-08-10T17:00:00Z',
  nextActionAt: '2026-08-10T17:10:00Z',
  areas: [],
  message: 'Vigilando la actividad de las áreas.',
  timestamp: '2026-08-10T17:00:00Z',
};

const zoneStatus: SecurityZoneList = {
  aggregateMode: 'PARTIALLY_ARMED',
  message: '1 de 4 zonas están armadas.',
  totalZones: 4,
  armedZones: 1,
  alarmZones: 0,
  zones: [],
  timestamp: '2026-08-10T17:00:00Z',
};

describe('SecurityApiService', () => {
  let service: SecurityApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SecurityApiService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(SecurityApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta el estado de seguridad', () => {
    service.getStatus().subscribe((response) => expect(response).toEqual(status));

    const request = http.expectOne((item) => item.url.endsWith('/security/status'));
    expect(request.request.method).toBe('GET');
    request.flush(status);
  });

  it('consulta la revisión previa', () => {
    service.getPrecheck().subscribe((response) => expect(response.ready).toBe(true));

    const request = http.expectOne((item) => item.url.endsWith('/security/precheck'));
    expect(request.request.method).toBe('GET');
    request.flush(precheck);
  });

  it('envía la orden de armado', () => {
    service.arm().subscribe((response) => expect(response.status.mode).toBe('ARMING'));

    const request = http.expectOne((item) => item.url.endsWith('/security/arm'));
    expect(request.request.method).toBe('POST');
    request.flush({ status: { ...status, mode: 'ARMING' }, precheck });
  });

  it('envía la orden de desarmado', () => {
    service.disarm().subscribe((response) => expect(response.status.mode).toBe('DISARMED'));

    const request = http.expectOne((item) => item.url.endsWith('/security/disarm'));
    expect(request.request.method).toBe('POST');
    request.flush({ status, precheck: null });
  });

  it('consulta el estado de las zonas', () => {
    service.getZones().subscribe((response) => expect(response.totalZones).toBe(4));

    const request = http.expectOne((item) => item.url.endsWith('/security/zones'));
    expect(request.request.method).toBe('GET');
    request.flush(zoneStatus);
  });

  it('envía las zonas seleccionadas al precheck', () => {
    service.getZonePrecheck(['P1', 'PATIO']).subscribe((response) => {
      expect(response.ready).toBe(true);
    });

    const request = http.expectOne((item) => item.url.endsWith('/security/zones/precheck'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ zoneCodes: ['P1', 'PATIO'] });
    request.flush(precheck);
  });

  it('arma solamente las zonas seleccionadas', () => {
    service.armZones(['PB', 'P2']).subscribe((response) => {
      expect(response.status.aggregateMode).toBe('PARTIALLY_ARMED');
    });

    const request = http.expectOne((item) => item.url.endsWith('/security/zones/arm'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ zoneCodes: ['PB', 'P2'] });
    request.flush({ status: zoneStatus, precheck });
  });

  it('reconoce la alarma de una zona', () => {
    service.acknowledgeZone('PATIO').subscribe((response) => {
      expect(response.alarmZones).toBe(0);
    });

    const request = http.expectOne((item) =>
      item.url.endsWith('/security/zones/PATIO/acknowledgement'),
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(zoneStatus);
  });

  it('consulta los horarios', () => {
    service
      .getSchedules()
      .subscribe((response) => expect(response.timezone).toBe('America/Mazatlan'));

    const request = http.expectOne((item) => item.url.endsWith('/security/schedules'));
    expect(request.request.method).toBe('GET');
    request.flush(settings);
  });

  it('actualiza los horarios', () => {
    const body: SecuritySettingsUpdateRequest = {
      ...settings,
      days: [],
      automaticLightingTargetDeviceCodes: [],
    };

    service.updateSchedules(body).subscribe((response) => expect(response).toEqual(settings));

    const request = http.expectOne((item) => item.url.endsWith('/security/schedules'));
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(body);
    request.flush(settings);
  });

  it('consulta el estado de iluminación automática', () => {
    service
      .getAutomaticLightingStatus()
      .subscribe((response) => expect(response.withinSchedule).toBe(true));

    const request = http.expectOne((item) =>
      item.url.endsWith('/security/automatic-lighting/status'),
    );
    expect(request.request.method).toBe('GET');
    request.flush(automaticLightingStatus);
  });

  it('consulta el estado de apagado por inactividad', () => {
    service
      .getAreaInactivityStatus()
      .subscribe((response) => expect(response.pendingAreas).toBe(1));

    const request = http.expectOne((item) => item.url.endsWith('/security/inactivity/status'));
    expect(request.request.method).toBe('GET');
    request.flush(areaInactivityStatus);
  });
});
