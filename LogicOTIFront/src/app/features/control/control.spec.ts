import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, Subject, of } from 'rxjs';

import { AreaState } from '../../core/models/area-state.model';
import { Building } from '../../core/models/building.model';
import { CameraListResponse } from '../../core/models/camera.model';
import { AreaApiService } from '../../core/services/area-api.service';
import { AreaRealtimeService } from '../../core/services/area-realtime.service';
import { AuthService } from '../../core/services/auth.service';
import { CameraApiService } from '../../core/services/camera-api.service';
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

const goodSignalMetadata = {
  quality: 'GOOD' as const,
  lastUpdatedAt: '2026-08-03T22:00:00Z',
  qualityDetail: 'Lectura válida recibida desde el PLC.',
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
      ...goodSignalMetadata,
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
      ...goodSignalMetadata,
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
      ...goodSignalMetadata,
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
      ...goodSignalMetadata,
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
      ...goodSignalMetadata,
    },
  ],
  message: 'Estados leídos correctamente.',
  timestamp: '2026-08-03T22:00:00Z',
};

const receptionCameras: CameraListResponse = {
  items: [
    {
      id: 8,
      code: 'CAM-008',
      channelNumber: 8,
      name: 'Recepción',
      floorCode: 'PB',
      areaCode: 'PB_A01',
      sourceName: 'NVR-1',
      streamKey: 'oti-cam-08',
      active: true,
      videoAvailable: true,
      viewUrl: 'http://video.local:8889/oti-cam-08/',
    },
  ],
  total: 1,
  playbackConfigured: true,
  timestamp: '2026-08-05T18:00:00Z',
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

class CameraApiServiceMock {
  readonly getCameras = vi.fn((_floorCode?: string, areaCode?: string) =>
    of(
      areaCode === 'PB_A01'
        ? receptionCameras
        : {
            ...receptionCameras,
            items: [],
            total: 0,
          },
    ),
  );
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
        {
          provide: CameraApiService,
          useClass: CameraApiServiceMock,
        },
        provideRouter([]),
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

  it('consulta y muestra solamente las cámaras relacionadas con el área', () => {
    const fixture = TestBed.createComponent(Control);
    const cameraApi = TestBed.inject(CameraApiService) as unknown as CameraApiServiceMock;

    fixture.detectChanges();

    expect(cameraApi.getCameras).toHaveBeenCalledWith(undefined, 'PB_A01');
    expect(fixture.componentInstance.areaCameras()).toHaveLength(1);
    expect(fixture.componentInstance.areaCameras()[0].code).toBe('CAM-008');
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

  it('muestra la calidad individual y bloquea comandos cuando la señal es BAD', () => {
    const fixture = TestBed.createComponent(Control);
    const areaApi = TestBed.inject(AreaApiService) as unknown as AreaApiServiceMock;

    fixture.detectChanges();
    fixture.componentInstance.areaState.update((state) =>
      state
        ? {
            ...state,
            devices: state.devices.map((device, index) =>
              index === 0
                ? {
                    ...device,
                    quality: 'BAD',
                    qualityDetail: 'El PLC no pudo leer la señal.',
                  }
                : device,
            ),
          }
        : null,
    );
    fixture.detectChanges();

    const light = fixture.componentInstance.controllableDevices()[0];
    const qualityBadge = fixture.nativeElement.querySelector(
      '.device-card--control .signal-quality[data-quality="BAD"]',
    ) as HTMLElement | null;

    expect(qualityBadge?.textContent).toContain('BAD');
    expect(fixture.componentInstance.commandDisabled(light)).toBe(true);

    fixture.componentInstance.sendCommand(light, true);
    expect(areaApi.sendDeviceCommand).not.toHaveBeenCalled();
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
