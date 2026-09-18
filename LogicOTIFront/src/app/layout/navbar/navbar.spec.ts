import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { Navbar } from './navbar';

class AuthServiceMock {
  readonly logout = vi.fn();
  readonly getSession = vi.fn(() => ({
    user: {
      id: 2,
      username: 'admin',
      fullName: 'Administrador OTI',
      role: 'ADMIN' as const,
    },
  }));
}

describe('Navbar', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Navbar],
      providers: [{ provide: AuthService, useClass: AuthServiceMock }, provideRouter([])],
    }).compileComponents();
  });

  it('muestra el nombre real del usuario de la sesión', () => {
    const fixture = TestBed.createComponent(Navbar);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Administrador OTI');
  });

  it('cierra la sesión y regresa al login', () => {
    const fixture = TestBed.createComponent(Navbar);
    const authService = TestBed.inject(AuthService) as unknown as AuthServiceMock;
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.logout();
    expect(authService.logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });
});
