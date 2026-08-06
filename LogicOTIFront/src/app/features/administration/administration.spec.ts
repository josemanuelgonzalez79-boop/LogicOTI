import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AppUser } from '../../core/models/user.model';
import { AuthService } from '../../core/services/auth.service';
import { UserApiService } from '../../core/services/user-api.service';
import { Administration } from './administration';

const existingUser: AppUser = {
  id: 1,
  username: 'operador1',
  fullName: 'Operador 1',
  role: 'OPERATOR',
  active: true,
  createdAt: '2026-07-29T16:39:21Z',
  protectedUser: false,
};

const createdUser: AppUser = {
  id: 2,
  username: 'monitorista2',
  fullName: 'Monitorista 2',
  role: 'MONITORING',
  active: true,
  createdAt: '2026-08-05T18:00:00Z',
  protectedUser: false,
};

class UserApiServiceMock {
  readonly getUsers = vi.fn(() => of([existingUser]));
  readonly createUser = vi.fn(() => of(createdUser));
  readonly updateUser = vi.fn(() => of({ ...existingUser }));
  readonly deleteUser = vi.fn(() => of(undefined));
}

class AuthServiceMock {
  readonly getSession = vi.fn(() => ({
    user: {
      id: 99,
      username: 'admin',
      fullName: 'Administrador',
      role: 'ADMIN' as const,
    },
  }));
}

describe('Administration', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Administration],
      providers: [
        { provide: UserApiService, useClass: UserApiServiceMock },
        { provide: AuthService, useClass: AuthServiceMock },
      ],
    }).compileComponents();
  });

  it('carga los usuarios registrados', () => {
    const fixture = TestBed.createComponent(Administration);
    fixture.detectChanges();
    expect(fixture.componentInstance.users()[0].username).toBe('operador1');
  });

  it('crea un usuario válido', () => {
    const fixture = TestBed.createComponent(Administration);
    const component = fixture.componentInstance;
    const api = TestBed.inject(UserApiService) as unknown as UserApiServiceMock;
    fixture.detectChanges();
    component.userForm.setValue({
      fullName: 'Monitorista 2',
      username: 'monitorista2',
      password: 'segura123',
      role: 'MONITORING',
      active: true,
    });
    component.createUser();
    expect(api.createUser).toHaveBeenCalled();
    expect(component.users()).toHaveLength(2);
  });

  it('rechaza contraseñas cortas', () => {
    const fixture = TestBed.createComponent(Administration);
    const component = fixture.componentInstance;
    const api = TestBed.inject(UserApiService) as unknown as UserApiServiceMock;
    fixture.detectChanges();
    component.userForm.setValue({
      fullName: 'Usuario Prueba',
      username: 'usuario.prueba',
      password: '123',
      role: 'MONITORING',
      active: true,
    });
    component.createUser();
    expect(api.createUser).not.toHaveBeenCalled();
  });

  it('cambia la contraseña usando el endpoint de actualización', () => {
    const fixture = TestBed.createComponent(Administration);
    const component = fixture.componentInstance;
    const api = TestBed.inject(UserApiService) as unknown as UserApiServiceMock;
    fixture.detectChanges();
    component.openPasswordChange(existingUser);
    component.passwordForm.setValue({
      password: 'nuevaClave123',
      confirmation: 'nuevaClave123',
    });
    component.changePassword();
    expect(api.updateUser).toHaveBeenCalledWith(
      existingUser.id,
      expect.objectContaining({ password: 'nuevaClave123' }),
    );
  });

  it('elimina un usuario que no está protegido', () => {
    const fixture = TestBed.createComponent(Administration);
    const component = fixture.componentInstance;
    const api = TestBed.inject(UserApiService) as unknown as UserApiServiceMock;
    fixture.detectChanges();
    component.requestDeletion(existingUser);
    component.confirmDeletion();
    expect(api.deleteUser).toHaveBeenCalledWith(existingUser.id);
    expect(component.users()).toHaveLength(0);
  });

  it('no permite eliminar al administrador principal', () => {
    const fixture = TestBed.createComponent(Administration);
    const component = fixture.componentInstance;
    const protectedAdministrator: AppUser = {
      ...existingUser,
      id: 2,
      username: 'admin',
      role: 'ADMIN',
      protectedUser: true,
    };
    expect(component.canDelete(protectedAdministrator)).toBe(false);
  });
});
