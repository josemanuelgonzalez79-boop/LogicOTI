import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { Building } from '../../core/models/building.model';
import { SensorEventHistoryItem } from '../../core/models/history.model';
import { AlarmApiService } from '../../core/services/alarm-api.service';
import { HistoryApiService } from '../../core/services/history-api.service';
import { SystemApiService } from '../../core/services/system-api.service';
import { Dashboard } from './dashboard';

const smokeAlarm: SensorEventHistoryItem = {
  id: 7,
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
  detectedAt: '2026-08-03T18:00:00Z',
  acknowledged: false,
  acknowledgedBy: null,
  acknowledgedAt: null,
  commentCount: 0,
};

const building: Building = {
  code: 'OTI',
  name: 'Edificio OTI',
  totals: {
    floors: 1,
    areas: 1,
    lamps: 4,
    motionSensors: 2,
    doorSensors: 7,
    smokeSensors: 3,
    outlets: 6,
    switches: 2,
    minisplits: 1,
  },
  floors: [
    {
      id: 1,
      code: 'PB',
      name: 'Planta Baja',
      displayOrder: 1,
      cameraCount: 13,
      areas: [
        {
          id: 1,
          code: 'PB_A01',
          name: 'Recepción',
          type: 'Recepción',
          displayOrder: 1,
          inventory: {
            lamps: 4,
            motionSensors: 2,
            doorSensors: 7,
            smokeSensors: 3,
            outlets: 6,
            switches: 2,
            minisplits: 1,
          },
        },
      ],
    },
  ],
};

class SystemApiServiceMock {
  readonly getStatus = vi.fn(() =>
    of({
      application: 'LogicOTI',
      status: 'UP' as const,
      database: 'UP' as const,
      plcEnabled: true,
      timestamp: '2026-08-03T18:01:00Z',
    }),
  );

  readonly getBuilding = vi.fn(() => of(building));

  readonly getDeviceSummary = vi.fn(() =>
    of({
      plcEnabled: true,
      connected: true,
      totalControllable: 5,
      poweredOn: 2,
      lightsOn: 1,
      minisplitsOn: 1,
      message: 'Estados confirmados por el PLC.',
      timestamp: '2026-08-03T18:01:00Z',
    }),
  );
}

class HistoryApiServiceMock {
  readonly getSensorEventHistory = vi.fn(() =>
    of({
      items: [smokeAlarm],
      total: 1,
      limit: 4,
      offset: 0,
    }),
  );
}

class AlarmApiServiceMock {
  readonly getActiveAlarms = vi.fn(() =>
    of({
      items: [smokeAlarm],
      total: 1,
      timestamp: '2026-08-03T18:01:00Z',
    }),
  );
}

describe('Dashboard', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [
        provideRouter([]),
        {
          provide: SystemApiService,
          useClass: SystemApiServiceMock,
        },
        {
          provide: HistoryApiService,
          useClass: HistoryApiServiceMock,
        },
        {
          provide: AlarmApiService,
          useClass: AlarmApiServiceMock,
        },
      ],
    }).compileComponents();
  });

  it('muestra cantidades reales sin contar sensores de puerta', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();

    const component = fixture.componentInstance;

    expect(component.summaryCards[0].value).toBe('5');
    expect(component.summaryCards[0].detail).toBe('2 movimiento · 3 humo');
    expect(component.floors[0].sensors).toBe(5);
    expect(component.floors[0].cameras).toBe(13);
    expect(component.summaryCards[4].label).toBe('Equipos encendidos');
    expect(component.summaryCards[4].value).toBe('2');
    expect(component.summaryCards[4].detail).toBe('1 luz · 1 minisplit');
  });

  it('muestra eventos reales y alarmas activas por piso', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();

    const component = fixture.componentInstance;

    expect(component.recentEvents[0].title).toBe('Humo detectado en Recepción');
    expect(component.floors[0].alarms).toBe(1);
    expect(component.floors[0].health).toBe('danger');
    expect(component.floors[0].healthLabel).toBe('1 alarma activa');
  });
});
