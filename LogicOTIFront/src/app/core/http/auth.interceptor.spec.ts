import { HttpErrorResponse, HttpRequest, HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  const logout = vi.fn();
  const navigate = vi.fn().mockResolvedValue(true);

  beforeEach(() => {
    logout.mockClear();
    navigate.mockClear();
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: {
            getToken: () => 'jwt-prueba',
            logout,
          },
        },
        {
          provide: Router,
          useValue: {
            url: '/security',
            navigate,
          },
        },
      ],
    });
  });

  function executeWith(status: number): void {
    const request = new HttpRequest('GET', '/api/prueba');

    TestBed.runInInjectionContext(() =>
      authInterceptor(request, () => throwError(() => new HttpErrorResponse({ status }))),
    ).subscribe({ error: () => undefined });
  }

  it('limpia la sesión y conserva la ruta cuando el backend responde 401', () => {
    executeWith(401);

    expect(logout).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { returnUrl: '/security' },
    });
  });

  it('no cierra la sesión cuando el backend responde 403', () => {
    executeWith(403);

    expect(logout).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('no envía la sesión al endpoint público de una cámara de alerta', () => {
    const request = new HttpRequest('GET', '/api/camera-alerts/view?token=token-firmado');
    let forwardedRequest: HttpRequest<unknown> | undefined;

    TestBed.runInInjectionContext(() =>
      authInterceptor(request, (nextRequest) => {
        forwardedRequest = nextRequest;
        return of(new HttpResponse({ status: 200 }));
      }),
    ).subscribe();

    expect(forwardedRequest?.headers.has('Authorization')).toBe(false);
  });

  it('envía el JWT en las consultas protegidas', () => {
    const request = new HttpRequest('GET', '/api/cameras');
    let forwardedRequest: HttpRequest<unknown> | undefined;

    TestBed.runInInjectionContext(() =>
      authInterceptor(request, (nextRequest) => {
        forwardedRequest = nextRequest;
        return of(new HttpResponse({ status: 200 }));
      }),
    ).subscribe();

    expect(forwardedRequest?.headers.get('Authorization')).toBe('Bearer jwt-prueba');
  });
});
