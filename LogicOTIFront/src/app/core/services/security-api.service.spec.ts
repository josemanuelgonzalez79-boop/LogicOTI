import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import {
  AutomaticLightingStatus,
  SecurityPrecheck,
  SecuritySettings,
  SecuritySettingsUpdateRequest,
  SecurityStatus,
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
});
