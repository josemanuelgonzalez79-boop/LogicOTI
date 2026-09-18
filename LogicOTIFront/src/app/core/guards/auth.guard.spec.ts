import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  let authenticated: boolean;

  beforeEach(() => {
    authenticated = true;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: () => authenticated,
          },
        },
      ],
    });
  });

  it('mantiene la ruta solicitada cuando la sesión sigue vigente', () => {
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/security' } as never),
    );

    expect(result).toBe(true);
  });

  it('envía al login y conserva returnUrl cuando la sesión no es válida', () => {
    authenticated = false;
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/diagnostics' } as never),
    );

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login?returnUrl=%2Fdiagnostics');
  });
});
