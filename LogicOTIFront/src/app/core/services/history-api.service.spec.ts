import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ExecutiveMonthlyReport, HistoryRetentionPolicy } from '../models/history.model';
import { HistoryApiService } from './history-api.service';

const report: ExecutiveMonthlyReport = {
  month: '2026-08',
  periodStart: '2026-08-01',
  periodEnd: '2026-08-31',
  timezone: 'America/Mazatlan',
  generatedAt: '2026-08-14T18:00:00Z',
  alarms: {
    total: 2,
    smoke: 1,
    motion: 1,
    critical: 1,
    acknowledged: 1,
    acknowledgementRate: 50,
    averageRestoreMinutes: 15,
  },
  commands: {
    total: 2,
    confirmed: 1,
    failed: 1,
    pending: 0,
    confirmationRate: 50,
    averageLatencyMs: 120,
  },
  maintenance: {
    diagnosticsPassed: 1,
    diagnosticsRejected: 1,
    diagnosticsCancelled: 0,
    bypassesCreated: 1,
  },
  security: {
    rejectedArmings: 0,
  },
  alarmsByArea: [],
  alarmsBySensor: [],
  alarmsByDay: [],
  alarmsByTimeSlot: [],
  commandFailures: [],
  armRejectionReasons: [],
};

const retentionPolicy: HistoryRetentionPolicy = {
  enabled: false,
  retentionMonths: 24,
  cutoffAt: '2024-08-14T18:00:00Z',
  candidates: {
    events: 2,
    commands: 1,
    securityTransitions: 0,
    diagnostics: 0,
    revokedBypasses: 0,
    notifications: 1,
    total: 4,
  },
  lastRunAt: null,
  lastRunBy: null,
  lastCutoffAt: null,
  lastDeleted: {
    events: 0,
    commands: 0,
    securityTransitions: 0,
    diagnostics: 0,
    revokedBypasses: 0,
    notifications: 0,
    total: 0,
  },
  updatedAt: '2026-08-14T18:00:00Z',
  updatedBy: 'SYSTEM',
  timestamp: '2026-08-14T18:00:00Z',
};

describe('HistoryApiService', () => {
  let service: HistoryApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [HistoryApiService, provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(HistoryApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta el reporte ejecutivo del mes solicitado', () => {
    service
      .getExecutiveMonthlyReport('2026-08')
      .subscribe((response) => expect(response).toEqual(report));

    const request = http.expectOne(
      (item) =>
        item.url.endsWith('/reports/executive/monthly') && item.params.get('month') === '2026-08',
    );

    expect(request.request.method).toBe('GET');
    request.flush(report);
  });

  it('consulta y actualiza la política de retención', () => {
    service.getRetentionPolicy().subscribe((response) => expect(response).toEqual(retentionPolicy));

    const getRequest = http.expectOne((item) => item.url.endsWith('/history/retention'));
    expect(getRequest.request.method).toBe('GET');
    getRequest.flush(retentionPolicy);

    service
      .updateRetentionPolicy(true, 36)
      .subscribe((response) => expect(response.enabled).toBe(true));

    const putRequest = http.expectOne((item) => item.url.endsWith('/history/retention'));
    expect(putRequest.request.method).toBe('PUT');
    expect(putRequest.request.body).toEqual({ enabled: true, retentionMonths: 36 });
    putRequest.flush({ ...retentionPolicy, enabled: true, retentionMonths: 36 });
  });

  it('confirma explícitamente la limpieza manual', () => {
    service.runRetention().subscribe((response) => expect(response.deleted.total).toBe(4));

    const request = http.expectOne((item) => item.url.endsWith('/history/retention/run'));
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ confirmed: true });
    request.flush({
      cutoffAt: retentionPolicy.cutoffAt,
      deleted: retentionPolicy.candidates,
      executedAt: '2026-08-14T18:00:00Z',
      executedBy: 'admin',
    });
  });
});
