import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, of, Subject } from 'rxjs';

import { SensorEventHistoryItem } from '../models/history.model';
import { AlarmApiService } from './alarm-api.service';
import { SmokeAlertStateService } from './smoke-alert-state.service';
import {
  RealtimeConnectionStatus,
  SmokeAlertRealtimeService,
} from './smoke-alert-realtime.service';

const activatedEvent: SensorEventHistoryItem = {
  id: 10,
  deviceCode: 'P1_A01_HUM01',
  deviceName: 'Sensor de humo 1',
  areaCode: 'P1_A01',
  areaName: 'Dirección',
  deviceType: 'SMOKE',
  plcStateTag: 'OTI_P1_A01_HUM01_ALM',
  previousState: false,
  currentState: true,
  eventType: 'ACTIVATED',
  severity: 'CRITICAL',
  message: 'Humo detectado en Dirección.',
  detectedAt: '2026-08-03T18:00:00Z',
};

const clearedEvent: SensorEventHistoryItem = {
  ...activatedEvent,
  id: 11,
  previousState: true,
  currentState: false,
  eventType: 'CLEARED',
  severity: 'INFO',
  message: 'Sensor de humo restablecido en Dirección.',
  detectedAt: '2026-08-03T18:05:00Z',
};

class AlarmApiServiceMock {
  readonly getActiveAlarms = vi.fn(() =>
    of({
      items: [activatedEvent],
      total: 1,
      timestamp: '2026-08-03T18:00:01Z',
    }),
  );
}

class SmokeAlertRealtimeServiceMock {
  private readonly alertsSubject = new Subject<SensorEventHistoryItem>();
  private readonly statusSubject = new BehaviorSubject<RealtimeConnectionStatus>('DISCONNECTED');

  readonly alerts$ = this.alertsSubject.asObservable();
  readonly connectionStatus$ = this.statusSubject.asObservable();
  readonly connect = vi.fn();
  readonly disconnect = vi.fn(async () => undefined);

  emit(event: SensorEventHistoryItem): void {
    this.alertsSubject.next(event);
  }
}

describe('SmokeAlertStateService', () => {
  let service: SmokeAlertStateService;
  let alarmApi: AlarmApiServiceMock;
  let realtime: SmokeAlertRealtimeServiceMock;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        SmokeAlertStateService,
        {
          provide: AlarmApiService,
          useClass: AlarmApiServiceMock,
        },
        {
          provide: SmokeAlertRealtimeService,
          useClass: SmokeAlertRealtimeServiceMock,
        },
      ],
    });

    service = TestBed.inject(SmokeAlertStateService);
    alarmApi = TestBed.inject(AlarmApiService) as unknown as AlarmApiServiceMock;
    realtime = TestBed.inject(
      SmokeAlertRealtimeService,
    ) as unknown as SmokeAlertRealtimeServiceMock;
  });

  it('carga las alarmas activas y abre una sola conexión', () => {
    service.start();
    service.start();

    expect(service.activeCount()).toBe(1);
    expect(service.activeAlerts()[0].deviceCode).toBe('P1_A01_HUM01');
    expect(alarmApi.getActiveAlarms).toHaveBeenCalledTimes(1);
    expect(realtime.connect).toHaveBeenCalledTimes(1);
  });

  it('restablece una alarma cuando recibe el cambio por WebSocket', () => {
    const notifications: SensorEventHistoryItem[] = [];

    service.start();
    service.notifications$.subscribe((event) => notifications.push(event));
    realtime.emit(clearedEvent);

    expect(service.activeCount()).toBe(0);
    expect(service.latestEvent()?.id).toBe(11);
    expect(notifications).toEqual([clearedEvent]);
  });

  it('actualiza la lista completa cuando se solicita una recarga', () => {
    service.start();

    alarmApi.getActiveAlarms.mockReturnValue(
      of({
        items: [],
        total: 0,
        timestamp: '2026-08-03T18:10:00Z',
      }),
    );

    service.refreshActiveAlarms();

    expect(service.activeCount()).toBe(0);
    expect(alarmApi.getActiveAlarms).toHaveBeenCalledTimes(2);
  });
});
