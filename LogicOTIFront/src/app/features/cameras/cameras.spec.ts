import { TestBed } from '@angular/core/testing';
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
  timestamp: '2026-08-04T18:00:00Z',
};

class CameraApiServiceMock {
  readonly getCameras = vi.fn(() => of(response));
}

describe('Cameras', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Cameras],
      providers: [
        {
          provide: CameraApiService,
          useClass: CameraApiServiceMock,
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
});
