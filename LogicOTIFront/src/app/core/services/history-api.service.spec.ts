import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ExecutiveMonthlyReport } from '../models/history.model';
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
});
