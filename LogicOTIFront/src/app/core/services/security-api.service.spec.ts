import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import {
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
  timezone: 'America/Mazatlan',
  exitDelaySeconds: 60,
  lightInactivityMinutes: 10,
  minisplitInactivityMinutes: 30,
  diagnosticTimeoutSeconds: 120,
  diagnosticValidityMonths: 4,
  days: [],
  updatedAt: '2026-08-07T17:00:00Z',
  updatedBy: 'admin',
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
    };

    service.updateSchedules(body).subscribe((response) => expect(response).toEqual(settings));

    const request = http.expectOne((item) => item.url.endsWith('/security/schedules'));
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(body);
    request.flush(settings);
  });
});
