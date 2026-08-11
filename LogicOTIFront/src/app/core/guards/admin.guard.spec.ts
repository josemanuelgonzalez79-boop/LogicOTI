import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';

import { UserRole } from '../models/auth.model';
import { AuthService } from '../services/auth.service';
import { adminGuard } from './admin.guard';

let authenticated = true;
let role: UserRole = 'ADMIN';

class AuthServiceMock {
  isAuthenticated(): boolean {
    return authenticated;
  }
  getSession() {
    return {
      token: 'token-prueba',
      expiresIn: 3600,
      expiresAt: Date.now() + 3600000,
      authenticated: true,
      user: { id: 1, username: 'usuario-prueba', fullName: 'Usuario Prueba', role },
    };
  }
}

describe('adminGuard', () => {
  beforeEach(() => {
    authenticated = true;
    role = 'ADMIN';
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useClass: AuthServiceMock }],
    });
  });

  it('permite entrar a un administrador', () => {
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    expect(result).toBe(true);
  });

  it('regresa al dashboard a un usuario sin permiso', () => {
    role = 'MONITORING';
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() => adminGuard({} as never, {} as never));
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/dashboard');
  });

  it('envía al login cuando no existe una sesión válida', () => {
    authenticated = false;
    const router = TestBed.inject(Router);
    const result = TestBed.runInInjectionContext(() =>
      adminGuard({} as never, { url: '/administration' } as never),
    );
    expect(router.serializeUrl(result as UrlTree)).toBe('/login?returnUrl=%2Fadministration');
  });
});
