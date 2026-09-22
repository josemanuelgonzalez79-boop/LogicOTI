import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { CameraListResponse } from '../../core/models/camera.model';
import { CameraApiService } from '../../core/services/camera-api.service';
import { Cameras } from './cameras';

const response: CameraListResponse = {
  items: [
    {
      id: 1,
      code: 'CAM-001',
      channelNumber: 1,
      name: 'Frente acceso',
      floorCode: 'PB',
      areaCode: null,
      sourceName: 'NVR-1',
      streamKey: 'oti-cam-01',
      active: true,
      videoAvailable: false,
      viewUrl: null,
    },
    {
      id: 25,
      code: 'CAM-025',
      channelNumber: 25,
      name: 'PTZ frente izquierdo',
      floorCode: 'EXT',
      areaCode: null,
      sourceName: 'SW3',
      streamKey: 'oti-cam-25',
      active: true,
      videoAvailable: true,
      viewUrl: 'http://video.local:8889/oti-cam-25',
    },
  ],
  total: 2,
  playbackConfigured: true,
  historyConfigured: true,
  timestamp: '2026-08-04T18:00:00Z',
};

class CameraApiServiceMock {
  readonly getCameras = vi.fn(() => of(response));
  readonly searchRecordings = vi.fn(() =>
    of({
      cameraCode: 'CAM-001',
      cameraName: 'Frente acceso',
      channelNumber: 1,
      requestedStartTime: '2026-09-22T09:00:00',
      requestedEndTime: '2026-09-22T10:00:00',
      items: [
        {
          sequence: 1,
          startTime: '2026-09-22T09:07:21',
          endTime: '2026-09-22T09:44:09',
          codecType: 'H.264-BP',
          recordingType: 'timing',
        },
      ],
      total: 1,
      playbackConfigured: false,
      timestamp: '2026-09-22T17:00:00Z',
    }),
  );
}

describe('Cameras', () => {
  let requestedCameraCode: string | null;

  beforeEach(async () => {
    requestedCameraCode = null;

    await TestBed.configureTestingModule({
      imports: [Cameras],
      providers: [
        {
          provide: CameraApiService,
          useClass: CameraApiServiceMock,
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (name: string) => (name === 'camera' ? requestedCameraCode : null),
              },
            },
          },
        },
      ],
    }).compileComponents();
  });

  it('carga el catálogo y selecciona la primera cámara', () => {
    const fixture = TestBed.createComponent(Cameras);
    fixture.detectChanges();

    expect(fixture.componentInstance.total()).toBe(2);
    expect(fixture.componentInstance.selectedCamera()?.code).toBe('CAM-001');
  });

  it('filtra las cámaras por ubicación', () => {
    const fixture = TestBed.createComponent(Cameras);
    fixture.detectChanges();

    fixture.componentInstance.floorFilter.set('EXT');

    expect(fixture.componentInstance.filteredCameras()).toHaveLength(1);
    expect(fixture.componentInstance.filteredCameras()[0].code).toBe('CAM-025');
  });

  it('abre directamente la cámara solicitada en la dirección', () => {
    requestedCameraCode = 'cam-025';

    const fixture = TestBed.createComponent(Cameras);
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedCamera()?.code).toBe('CAM-025');
  });

  it('consulta las grabaciones de la cámara seleccionada', () => {
    const fixture = TestBed.createComponent(Cameras);
    const cameraApi = TestBed.inject(CameraApiService) as unknown as CameraApiServiceMock;
    fixture.detectChanges();
    fixture.componentInstance.historyDate.set('2026-09-22');
    fixture.componentInstance.historyStartTime.set('09:00');
    fixture.componentInstance.historyEndTime.set('10:00');

    fixture.componentInstance.searchHistory();

    expect(cameraApi.searchRecordings).toHaveBeenCalledWith('CAM-001', {
      startTime: '2026-09-22T09:00:00',
      endTime: '2026-09-22T10:00:00',
    });
    expect(fixture.componentInstance.recordingSegments()).toHaveLength(1);
  });
});
