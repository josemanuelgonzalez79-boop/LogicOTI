import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';

import { AlarmActivity, SensorEventHistoryItem } from '../../core/models/history.model';
import { AlarmApiService } from '../../core/services/alarm-api.service';
import { HistoryApiService } from '../../core/services/history-api.service';
import { SmokeAlertStateService } from '../../core/services/smoke-alert-state.service';
import { Alarms } from './alarms';

const alarm: SensorEventHistoryItem = {
  id: 70,
  deviceCode: 'PB_A01_HUM01',
  deviceName: 'Sensor de humo 1',
  areaCode: 'PB_A01',
  areaName: 'Recepción',
  deviceType: 'SMOKE',
  plcStateTag: 'OTI_PB_A01_HUM01_ALM',
  previousState: false,
  currentState: true,
  eventType: 'ACTIVATED',
  severity: 'CRITICAL',
  message: 'Humo detectado en Recepción.',
  detectedAt: '2026-08-13T18:00:00Z',
  acknowledged: false,
  acknowledgedBy: null,
  acknowledgedAt: null,
  commentCount: 0,
};

const emptyActivity: AlarmActivity = {
  eventId: 70,
  acknowledgement: null,
  comments: [],
  commentCount: 0,
  timestamp: '2026-08-13T18:00:10Z',
};

const acknowledgedActivity: AlarmActivity = {
  eventId: 70,
  acknowledgement: {
    id: 1,
    eventId: 70,
    acknowledgedBy: 'operador',
    acknowledgedAt: '2026-08-13T18:01:00Z',
  },
  comments: [
    {
      id: 1,
      eventId: 70,
      comment: 'Seguridad fue notificada.',
      createdBy: 'operador',
      createdAt: '2026-08-13T18:01:00Z',
    },
  ],
  commentCount: 1,
  timestamp: '2026-08-13T18:01:00Z',
};

class HistoryApiServiceMock {
  readonly getSensorEventHistory = vi.fn(() =>
    of({
      items: [alarm],
      total: 1,
      limit: 200,
      offset: 0,
    }),
  );
}

class AlarmApiServiceMock {
  readonly getActivity = vi.fn(() => of(emptyActivity));
  readonly acknowledge = vi.fn(() => of(acknowledgedActivity));
  readonly addComment = vi.fn(() => of(acknowledgedActivity));
}

class SmokeAlertStateServiceMock {
  private readonly notificationSubject = new Subject<SensorEventHistoryItem>();
  private readonly attentionSubject = new Subject<AlarmActivity>();

  readonly activeAlerts = signal([alarm]);
  readonly connectionStatus = signal<'CONNECTED'>('CONNECTED');
  readonly latestEvent = signal<SensorEventHistoryItem | null>(null);
  readonly notifications$ = this.notificationSubject.asObservable();
  readonly attentionUpdates$ = this.attentionSubject.asObservable();
  readonly refreshActiveAlarms = vi.fn();
  readonly ensureConnected = vi.fn();

  applyAlarmActivity(activity: AlarmActivity): void {
    this.activeAlerts.update((alarms) =>
      alarms.map((current) =>
        current.id === activity.eventId
          ? {
              ...current,
              acknowledged: activity.acknowledgement !== null,
              acknowledgedBy: activity.acknowledgement?.acknowledgedBy ?? null,
              acknowledgedAt: activity.acknowledgement?.acknowledgedAt ?? null,
              commentCount: activity.commentCount,
            }
          : current,
      ),
    );
    this.attentionSubject.next(activity);
  }
}

describe('Alarms', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Alarms],
      providers: [
        { provide: HistoryApiService, useClass: HistoryApiServiceMock },
        { provide: AlarmApiService, useClass: AlarmApiServiceMock },
        { provide: SmokeAlertStateService, useClass: SmokeAlertStateServiceMock },
      ],
    }).compileComponents();
  });

  it('carga alarmas e identifica las activas sin reconocer', () => {
    const fixture = TestBed.createComponent(Alarms);
    fixture.detectChanges();

    expect(fixture.componentInstance.events()).toEqual([alarm]);
    expect(fixture.componentInstance.unacknowledgedActiveAlerts()).toBe(1);
  });

  it('reconoce la alarma y registra el comentario inicial', () => {
    const fixture = TestBed.createComponent(Alarms);
    const component = fixture.componentInstance;
    const api = TestBed.inject(AlarmApiService) as unknown as AlarmApiServiceMock;
    fixture.detectChanges();

    component.openAttention(alarm);
    component.draftComment.set('Seguridad fue notificada.');
    component.saveAttention();

    expect(api.acknowledge).toHaveBeenCalledWith(70, 'Seguridad fue notificada.');
    expect(component.alarmActivity()?.acknowledgement?.acknowledgedBy).toBe('operador');
    expect(component.events()[0].acknowledged).toBe(true);
    expect(component.events()[0].commentCount).toBe(1);
  });

  it('exige texto antes de agregar otro comentario', () => {
    const fixture = TestBed.createComponent(Alarms);
    const component = fixture.componentInstance;
    const smokeAlerts = TestBed.inject(
      SmokeAlertStateService,
    ) as unknown as SmokeAlertStateServiceMock;
    const api = TestBed.inject(AlarmApiService) as unknown as AlarmApiServiceMock;
    fixture.detectChanges();

    component.openAttention(alarm);
    smokeAlerts.applyAlarmActivity(acknowledgedActivity);
    component.saveAttention();

    expect(api.addComment).not.toHaveBeenCalled();
    expect(component.activityError()).toContain('Escribe el comentario');
  });
});
