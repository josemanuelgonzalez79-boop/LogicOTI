import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AppUser } from '../../core/models/user.model';
import { UserApiService } from '../../core/services/user-api.service';
import { Administration } from './administration';

const existingUser: AppUser = {
  id: 1,
  username: 'operador1',
  fullName: 'Operador 1',
  role: 'OPERATOR',
  active: true,
  createdAt: '2026-07-29T16:39:21Z',
};

const createdUser: AppUser = {
  id: 2,
  username: 'monitorista2',
  fullName: 'Monitorista 2',
  role: 'MONITORING',
  active: true,
  createdAt: '2026-08-05T18:00:00Z',
};

class UserApiServiceMock {
  readonly getUsers = vi.fn(() => of([existingUser]));
  readonly createUser = vi.fn(() => of(createdUser));
}

describe('Administration', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Administration],
      providers: [{ provide: UserApiService, useClass: UserApiServiceMock }],
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
});
