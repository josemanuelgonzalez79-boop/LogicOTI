import { TestBed } from '@angular/core/testing';
import { BehaviorSubject, Subject, of } from 'rxjs';

import { AreaState } from '../../core/models/area-state.model';
import { Building } from '../../core/models/building.model';
import { AreaApiService } from '../../core/services/area-api.service';
import { AreaRealtimeService } from '../../core/services/area-realtime.service';
import { AuthService } from '../../core/services/auth.service';
import { RealtimeConnectionStatus } from '../../core/services/smoke-alert-realtime.service';
import { SystemApiService } from '../../core/services/system-api.service';
import { Control } from './control';

const building: Building = {
  code: 'OTI',
  name: 'Edificio OTI',
  totals: {
    floors: 2,
    areas: 2,
    lamps: 7,
    motionSensors: 1,
    doorSensors: 0,
    smokeSensors: 1,
    outlets: 0,
    switches: 0,
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
            lamps: 1,
            motionSensors: 1,
            doorSensors: 0,
            smokeSensors: 0,
            outlets: 0,
            switches: 0,
            minisplits: 0,
          },
        },
      ],
    },
    {
      id: 2,
      code: 'P1',
      name: 'Piso 1',
      displayOrder: 2,
      cameraCount: 5,
      areas: [
        {
          id: 2,
          code: 'P1_A01',
          name: 'Dirección',
          type: 'Oficina',
          displayOrder: 1,
          inventory: {
            lamps: 6,
            motionSensors: 0,
            doorSensors: 0,
            smokeSensors: 1,
            outlets: 0,
            switches: 0,
            minisplits: 1,
          },
        },
      ],
    },
  ],
};

const receptionState: AreaState = {
  areaCode: 'PB_A01',
  areaName: 'Recepción',
  plcEnabled: true,
  connected: true,
  devices: [
    {
      id: 1,
      code: 'PB_A01_LUZ01',
      name: 'Luz 1',
      type: 'LIGHT',
      number: 1,
      controllable: true,
      command: false,
      state: false,
      fault: null,
    },
    {
      id: 2,
      code: 'PB_A01_MOV01',
      name: 'Sensor de movimiento 1',
      type: 'MOTION',
      number: 1,
      controllable: false,
      command: null,
      state: false,
      fault: null,
    },
  ],
  message: 'Estados leídos correctamente.',
  timestamp: '2026-08-03T22:00:00Z',
};

const directionState: AreaState = {
  areaCode: 'P1_A01',
  areaName: 'Dirección',
  plcEnabled: true,
  connected: true,
  devices: [
    {
      id: 3,
      code: 'P1_A01_LUZ01',
      name: 'Iluminación general',
      type: 'LIGHT',
      number: 1,
      controllable: true,
      command: false,
      state: false,
      fault: null,
    },
    {
      id: 4,
      code: 'P1_A01_MS01',
      name: 'Minisplit 1',
      type: 'MINISPLIT',
      number: 1,
      controllable: true,
      command: false,
      state: false,
      fault: null,
    },
    {
      id: 5,
      code: 'P1_A01_HUM01',
      name: 'Sensor de humo 1',
      type: 'SMOKE',
      number: 1,
      controllable: false,
      command: null,
      state: false,
      fault: null,
    },
  ],
  message: 'Estados leídos correctamente.',
  timestamp: '2026-08-03T22:00:00Z',
};

let authRole = 'ADMIN';

class SystemApiServiceMock {
  readonly getBuilding = vi.fn(() => of(building));
}

class AreaApiServiceMock {
  readonly getAreaState = vi.fn((areaCode: string) =>
    of(areaCode === 'P1_A01' ? directionState : receptionState),
  );

  readonly sendDeviceCommand = vi.fn((deviceCode: string, on: boolean) =>
    of({
      ...(deviceCode.startsWith('P1_') ? directionState : receptionState),
      devices: (deviceCode.startsWith('P1_') ? directionState.devices : receptionState.devices).map(
        (device) => (device.code === deviceCode ? { ...device, command: on, state: on } : device),
      ),
    }),
  );
}

class AreaRealtimeServiceMock {
  readonly stateSubject = new Subject<AreaState>();
  readonly statusSubject = new BehaviorSubject<RealtimeConnectionStatus>('CONNECTED');

  readonly states$ = this.stateSubject.asObservable();
  readonly connectionStatus$ = this.statusSubject.asObservable();
  readonly watchArea = vi.fn();
  readonly clearArea = vi.fn();
  readonly disconnect = vi.fn(async () => undefined);
}

class AuthServiceMock {
  getSession() {
    return {
      token: 'token-prueba',
      expiresIn: 3600,
      expiresAt: Date.now() + 3600000,
      authenticated: true,
      user: {
        id: 1,
        username: 'usuario-prueba',
        fullName: 'Usuario Prueba',
        role: authRole,
      },
    };
  }
}

describe('Control', () => {
  beforeEach(async () => {
    authRole = 'ADMIN';

    await TestBed.configureTestingModule({
      imports: [Control],
      providers: [
        {
          provide: SystemApiService,
          useClass: SystemApiServiceMock,
        },
        {
          provide: AreaApiService,
          useClass: AreaApiServiceMock,
        },
        {
          provide: AreaRealtimeService,
          useClass: AreaRealtimeServiceMock,
        },
        {
          provide: AuthService,
          useClass: AuthServiceMock,
        },
      ],
    }).compileComponents();
  });

  it('carga la primera oficina y comienza su suscripción en tiempo real', () => {
    const fixture = TestBed.createComponent(Control);
    const realtime = TestBed.inject(AreaRealtimeService) as unknown as AreaRealtimeServiceMock;

    fixture.detectChanges();

    expect(fixture.componentInstance.selectedFloorCode()).toBe('PB');
    expect(fixture.componentInstance.selectedAreaCode()).toBe('PB_A01');
    expect(fixture.componentInstance.areaState()?.areaName).toBe('Recepción');
    expect(realtime.watchArea).toHaveBeenCalledWith('PB_A01');
  });

  it('cambia la lectura y la suscripción al seleccionar otro piso', () => {
    const fixture = TestBed.createComponent(Control);
    const realtime = TestBed.inject(AreaRealtimeService) as unknown as AreaRealtimeServiceMock;

    fixture.detectChanges();
    fixture.componentInstance.selectFloor('P1');

    expect(fixture.componentInstance.selectedAreaCode()).toBe('P1_A01');
    expect(fixture.componentInstance.areaState()?.areaName).toBe('Dirección');
    expect(realtime.watchArea).toHaveBeenLastCalledWith('P1_A01');
  });

  it('muestra el control de iluminación fuera de Recepción', () => {
    const fixture = TestBed.createComponent(Control);

    fixture.detectChanges();
    fixture.componentInstance.selectFloor('P1');

    const light = fixture.componentInstance
      .controllableDevices()
      .find((device) => device.type === 'LIGHT');

    expect(light?.code).toBe('P1_A01_LUZ01');
    expect(light?.name).toBe('Iluminación general');
  });

  it('envía el comando y usa la respuesta para actualizar toda el área', () => {
    const fixture = TestBed.createComponent(Control);
    const areaApi = TestBed.inject(AreaApiService) as unknown as AreaApiServiceMock;

    fixture.detectChanges();

    const light = fixture.componentInstance.controllableDevices()[0];
    fixture.componentInstance.sendCommand(light, true);

    expect(areaApi.sendDeviceCommand).toHaveBeenCalledWith('PB_A01_LUZ01', true);
    expect(fixture.componentInstance.areaState()?.devices[0].state).toBe(true);
  });

  it('aplica los cambios recibidos por WebSocket solamente al área seleccionada', () => {
    const fixture = TestBed.createComponent(Control);
    const realtime = TestBed.inject(AreaRealtimeService) as unknown as AreaRealtimeServiceMock;

    fixture.detectChanges();

    realtime.stateSubject.next({
      ...receptionState,
      devices: receptionState.devices.map((device) => ({
        ...device,
        state: true,
      })),
    });

    expect(fixture.componentInstance.areaState()?.devices[0].state).toBe(true);

    realtime.stateSubject.next(directionState);

    expect(fixture.componentInstance.areaState()?.areaCode).toBe('PB_A01');
  });

  it('mantiene los controles bloqueados para un usuario de monitoreo', () => {
    authRole = 'MONITORING';

    const fixture = TestBed.createComponent(Control);
    const areaApi = TestBed.inject(AreaApiService) as unknown as AreaApiServiceMock;

    fixture.detectChanges();

    const light = fixture.componentInstance.controllableDevices()[0];

    expect(fixture.componentInstance.canControl).toBe(false);
    expect(fixture.componentInstance.commandDisabled(light)).toBe(true);

    fixture.componentInstance.sendCommand(light, true);

    expect(areaApi.sendDeviceCommand).not.toHaveBeenCalled();
  });
});
