import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';

import { CameraAlertView } from '../../core/models/camera.model';
import { AuthService } from '../../core/services/auth.service';
import { CameraApiService } from '../../core/services/camera-api.service';
import { CameraAlert } from './camera-alert';

const response: CameraAlertView = {
  cameraCode: 'CAM-008',
  cameraName: 'Recepción',
  floorCode: 'PB',
  areaCode: 'PB_A01',
  viewUrl: 'https://video.local/camera/oti-cam-08/',
  expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
  timestamp: '2026-08-13T20:00:00Z',
};

class CameraApiServiceMock {
  readonly getCameraAlert = vi.fn(() => of(response));
}

describe('CameraAlert', () => {
  const navigate = vi.fn().mockResolvedValue(true);
  const navigateByUrl = vi.fn().mockResolvedValue(true);
  let token: string | null;

  beforeEach(async () => {
    token = 'token-firmado';
    navigate.mockClear();
    navigateByUrl.mockClear();

    await TestBed.configureTestingModule({
      imports: [CameraAlert],
      providers: [
        {
          provide: CameraApiService,
          useClass: CameraApiServiceMock,
        },
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: () => false,
          },
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (name: string) => (name === 'token' ? token : null),
              },
            },
          },
        },
        {
          provide: Router,
          useValue: {
            navigate,
            navigateByUrl,
          },
        },
      ],
    }).compileComponents();
  });

  it('abre el video indicado por el enlace temporal', () => {
    const fixture = TestBed.createComponent(CameraAlert);
    const api = TestBed.inject(CameraApiService) as unknown as CameraApiServiceMock;

    fixture.detectChanges();

    expect(api.getCameraAlert).toHaveBeenCalledWith('token-firmado');
    expect(fixture.componentInstance.alert()?.cameraCode).toBe('CAM-008');
    expect(fixture.nativeElement.textContent).toContain('Recepción');
  });

  it('muestra un mensaje seguro cuando el enlace no contiene token', () => {
    token = null;
    const fixture = TestBed.createComponent(CameraAlert);
    const api = TestBed.inject(CameraApiService) as unknown as CameraApiServiceMock;

    fixture.detectChanges();

    expect(api.getCameraAlert).not.toHaveBeenCalled();
    expect(fixture.componentInstance.errorMessage()).toContain('no contiene un enlace válido');
  });

  it('solicita iniciar sesión sólo para abrir el catálogo completo', () => {
    const fixture = TestBed.createComponent(CameraAlert);
    fixture.detectChanges();

    fixture.componentInstance.openFullApplication();

    expect(navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: {
        returnUrl: '/cameras?camera=CAM-008',
      },
    });
  });
});
