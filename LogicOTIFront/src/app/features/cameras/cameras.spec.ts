import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of, Subject } from 'rxjs';

import { CameraListResponse, CameraRecordingPlaybackResponse } from '../../core/models/camera.model';
import { CameraApiService } from '../../core/services/camera-api.service';
import { AuthService } from '../../core/services/auth.service';
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
      ptzAvailable: false,
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
      ptzAvailable: true,
    },
  ],
  total: 2,
  playbackConfigured: true,
  historyConfigured: true,
  timestamp: '2026-08-04T18:00:00Z',
};

class CameraApiServiceMock {
  readonly movePtz = vi.fn(() => of(undefined));
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
      playbackConfigured: true,
      timestamp: '2026-09-22T17:00:00Z',
    }),
  );
  readonly startRecordingPlayback = vi.fn(() =>
    of({
      cameraCode: 'CAM-001',
      cameraName: 'Frente acceso',
      viewUrl: 'https://video.local/camera/logicoti-history-session/',
      expiresAt: '2026-09-22T19:00:00Z',
      timestamp: '2026-09-22T17:00:00Z',
    }),
  );
}

describe('Cameras', () => {
  let requestedCameraCode: string | null;
  let role: string;

  beforeEach(async () => {
    requestedCameraCode = null;
    role = 'ADMIN';

    await TestBed.configureTestingModule({
      imports: [Cameras],
      providers: [
        { provide: AuthService, useValue: { getSession: () => ({ user: { role } }) } },
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

  it('inicia la reproducción temporal del segmento seleccionado', () => {
    const fixture = TestBed.createComponent(Cameras);
    const cameraApi = TestBed.inject(CameraApiService) as unknown as CameraApiServiceMock;
    fixture.detectChanges();
    fixture.componentInstance.historyDate.set('2026-09-22');
    fixture.componentInstance.historyStartTime.set('09:00');
    fixture.componentInstance.historyEndTime.set('10:00');
    fixture.componentInstance.searchHistory();

    const segment = fixture.componentInstance.recordingSegments()[0];
    fixture.componentInstance.playRecording(segment);

    expect(cameraApi.startRecordingPlayback).toHaveBeenCalledWith('CAM-001', {
      startTime: '2026-09-22T09:07:21',
      endTime: '2026-09-22T09:44:09',
    });
    expect(fixture.componentInstance.historyPlaybackSegment()).toEqual(segment);
  });

  it('navega con la única línea de tiempo después de iniciar la reproducción', () => {
    const fixture = TestBed.createComponent(Cameras);
    const cameraApi = TestBed.inject(CameraApiService) as unknown as CameraApiServiceMock;
    fixture.detectChanges();
    fixture.componentInstance.searchHistory();

    const segment = fixture.componentInstance.recordingSegments()[0];
    fixture.componentInstance.playRecording(segment);
    fixture.componentInstance.selectViewerMode('history');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('input[type="range"]')).toHaveLength(1);
    const timeline = fixture.nativeElement.querySelector('input[type="range"]') as HTMLInputElement;
    timeline.value = String(8 * 60 + 51);
    timeline.dispatchEvent(new Event('input', { bubbles: true }));
    timeline.dispatchEvent(new Event('change', { bubbles: true }));

    expect(cameraApi.startRecordingPlayback).toHaveBeenLastCalledWith('CAM-001', {
      startTime: '2026-09-22T09:08:51',
      endTime: '2026-09-22T09:44:09',
    });
    expect(fixture.componentInstance.historyTimelinePositionSeconds()).toBe(8 * 60 + 51);
    fixture.componentInstance.jumpRecording(30);
    expect(cameraApi.startRecordingPlayback).toHaveBeenLastCalledWith('CAM-001', {
      startTime: '2026-09-22T09:09:21',
      endTime: '2026-09-22T09:44:09',
    });
  });

  it('conserva el último salto aunque el anterior responda después', () => {
    const fixture = TestBed.createComponent(Cameras);
    const cameraApi = TestBed.inject(CameraApiService) as unknown as CameraApiServiceMock;
    fixture.detectChanges();
    fixture.componentInstance.searchHistory();
    fixture.componentInstance.selectViewerMode('history');
    fixture.detectChanges();

    const first = new Subject<CameraRecordingPlaybackResponse>();
    cameraApi.startRecordingPlayback.mockReturnValueOnce(first.asObservable());
    const timeline = fixture.nativeElement.querySelector('input[type="range"]') as HTMLInputElement;
    timeline.value = String(8 * 60 + 51);
    timeline.dispatchEvent(new Event('input', { bubbles: true }));
    timeline.dispatchEvent(new Event('change', { bubbles: true }));
    expect(fixture.componentInstance.historyPlaybackLoadingSequence()).toBe(1);

    timeline.value = String(9 * 60 + 21);
    timeline.dispatchEvent(new Event('input', { bubbles: true }));
    timeline.dispatchEvent(new Event('change', { bubbles: true }));
    expect(cameraApi.startRecordingPlayback).toHaveBeenLastCalledWith('CAM-001', {
      startTime: '2026-09-22T09:09:21', endTime: '2026-09-22T09:44:09',
    });
    first.next({
      cameraCode: 'CAM-001', cameraName: 'Frente acceso',
      viewUrl: 'https://video.local/camera/old-session/',
      expiresAt: '2026-09-22T19:00:00Z', timestamp: '2026-09-22T17:00:00Z',
    });
    first.complete();

    expect(fixture.componentInstance.historyTimelinePositionSeconds()).toBe(9 * 60 + 21);
    expect(fixture.componentInstance.historyPlaybackLoadingSequence()).toBeNull();
  });

  it('distingue movimiento, grabación sin marca y huecos en el periodo', () => {
    const cameraApi = TestBed.inject(CameraApiService) as unknown as CameraApiServiceMock;
    cameraApi.searchRecordings.mockReturnValueOnce(
      of({
        cameraCode: 'CAM-001',
        cameraName: 'Frente acceso',
        channelNumber: 1,
        requestedStartTime: '2026-09-22T09:00:00',
        requestedEndTime: '2026-09-22T10:00:00',
        items: [
          {
            sequence: 1,
            startTime: '2026-09-22T09:00:00',
            endTime: '2026-09-22T09:30:00',
            codecType: 'H.264',
            recordingType: 'timing',
          },
          {
            sequence: 2,
            startTime: '2026-09-22T09:10:00',
            endTime: '2026-09-22T09:15:00',
            codecType: 'H.264',
            recordingType: 'motion',
          },
        ],
        total: 2,
        playbackConfigured: true,
        timestamp: '2026-09-22T17:00:00Z',
      }),
    );
    const fixture = TestBed.createComponent(Cameras);
    fixture.detectChanges();
    fixture.componentInstance.searchHistory();

    expect(fixture.componentInstance.historyTimelineSlices().map((slice) => slice.kind)).toEqual([
      'recorded',
      'motion',
      'recorded',
      'gap',
    ]);
    expect(fixture.componentInstance.historyTimelineSlices().at(-1)?.percent).toBe(50);
    fixture.componentInstance.historyTimelinePositionSeconds.set(12 * 60);
    fixture.componentInstance.seekTimeline();
    expect(cameraApi.startRecordingPlayback).toHaveBeenCalledWith('CAM-001', {
      startTime: '2026-09-22T09:12:00',
      endTime: '2026-09-22T09:30:00',
    });
  });

  it('no intenta reproducir una hora sin grabación y no inventa movimiento', () => {
    const fixture = TestBed.createComponent(Cameras);
    const cameraApi = TestBed.inject(CameraApiService) as unknown as CameraApiServiceMock;
    fixture.detectChanges();
    fixture.componentInstance.searchHistory();

    expect(fixture.componentInstance.historyHasMotion()).toBe(false);
    expect(fixture.componentInstance.historyTimelineSlices().map((slice) => slice.kind)).toEqual([
      'gap',
      'recorded',
      'gap',
    ]);
    fixture.componentInstance.historyTimelinePositionSeconds.set(45 * 60);
    fixture.componentInstance.seekTimeline();
    expect(cameraApi.startRecordingPlayback).not.toHaveBeenCalled();
    expect(fixture.componentInstance.historyTimelineError()).toContain('no devolvió');
  });

  it('pinta eventos del SDK en rojo y sigue reproduciendo la grabación ISAPI', () => {
    const cameraApi = TestBed.inject(CameraApiService) as unknown as CameraApiServiceMock;
    cameraApi.searchRecordings.mockReturnValueOnce(of({
      cameraCode: 'CAM-017', cameraName: 'Pasillo', channelNumber: 17,
      requestedStartTime: '2026-09-23T09:30:00', requestedEndTime: '2026-09-23T10:00:00',
      items: [{ sequence: 1, startTime: '2026-09-23T09:30:00',
        endTime: '2026-09-23T10:00:00', codecType: 'H.264', recordingType: 'timing' }],
      motionItems: [{ sequence: 1, startTime: '2026-09-23T09:43:00',
        endTime: '2026-09-23T09:58:02', codecType: '', recordingType: 'MOTION' }],
      motionStatus: 'available' as const, total: 1, playbackConfigured: true,
      timestamp: '2026-09-23T17:00:00Z',
    }));
    const fixture = TestBed.createComponent(Cameras);
    fixture.detectChanges();
    fixture.componentInstance.searchHistory();

    expect(fixture.componentInstance.historyTimelineSlices().map((slice) => slice.kind))
      .toEqual(['recorded', 'motion', 'recorded']);
    expect(fixture.componentInstance.recordingSegments()).toHaveLength(1);
    fixture.componentInstance.historyTimelinePositionSeconds.set(14 * 60);
    fixture.componentInstance.seekTimeline();
    expect(cameraApi.startRecordingPlayback).toHaveBeenLastCalledWith('CAM-001', {
      startTime: '2026-09-23T09:44:00', endTime: '2026-09-23T10:00:00',
    });
  });

  it('envía un movimiento solo desde una cámara PTZ para un rol operativo', () => {
    requestedCameraCode = 'CAM-025';
    const fixture = TestBed.createComponent(Cameras);
    const cameraApi = TestBed.inject(CameraApiService) as unknown as CameraApiServiceMock;
    fixture.detectChanges();

    fixture.componentInstance.movePtz('RIGHT');

    expect(cameraApi.movePtz).toHaveBeenCalledWith('CAM-025', 'RIGHT');
    expect(fixture.nativeElement.querySelector('[aria-label="Control PTZ"]')).not.toBeNull();
  });

  it('oculta PTZ al rol de monitoreo aunque el canal esté habilitado', () => {
    role = 'MONITORING';
    requestedCameraCode = 'CAM-025';
    const fixture = TestBed.createComponent(Cameras);
    const cameraApi = TestBed.inject(CameraApiService) as unknown as CameraApiServiceMock;
    fixture.detectChanges();

    fixture.componentInstance.movePtz('LEFT');

    expect(cameraApi.movePtz).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('[aria-label="Control PTZ"]')).toBeNull();
  });
});
