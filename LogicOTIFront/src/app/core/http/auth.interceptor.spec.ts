import { HttpErrorResponse, HttpRequest } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { throwError } from 'rxjs';

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
});
