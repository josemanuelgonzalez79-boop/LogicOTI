import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthSession } from '../models/auth.model';
import { AuthService } from './auth.service';

const SESSION_KEY = 'logicoti_session';

function jwtThatExpiresAt(expiresAt: number): string {
  const encode = (value: object) =>
    btoa(JSON.stringify(value)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');

  return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({
    exp: Math.floor(expiresAt / 1000),
  })}.firma-prueba`;
}

function storedSession(expiresAt: number): AuthSession {
  return {
    token: jwtThatExpiresAt(expiresAt),
    expiresIn: 3600,
    expiresAt,
    authenticated: true,
    user: {
      id: 1,
      username: 'admin',
      fullName: 'Administrador',
      role: 'ADMIN',
    },
  };
}

describe('AuthService - restauración de sesión', () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('conserva una sesión vigente guardada con Mantener sesión iniciada', () => {
    const session = storedSession(Date.now() + 60_000);
    localStorage.setItem(SESSION_KEY, JSON.stringify(session));

    const service = TestBed.inject(AuthService);

    expect(service.isAuthenticated()).toBe(true);
    expect(service.getToken()).toBe(session.token);
    expect(localStorage.getItem(SESSION_KEY)).not.toBeNull();
  });

  it('conserva una sesión vigente de la pestaña actual', () => {
    const session = storedSession(Date.now() + 60_000);
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session));

    const service = TestBed.inject(AuthService);

    expect(service.isAuthenticated()).toBe(true);
    expect(sessionStorage.getItem(SESSION_KEY)).not.toBeNull();
  });

  it('elimina una sesión cuyo JWT ya venció', () => {
    localStorage.setItem(SESSION_KEY, JSON.stringify(storedSession(Date.now() - 60_000)));

    const service = TestBed.inject(AuthService);

    expect(service.isAuthenticated()).toBe(false);
    expect(service.getSession()).toBeNull();
    expect(localStorage.getItem(SESSION_KEY)).toBeNull();
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull();
  });

  it('no crea una sesión cuando el backend exige el segundo factor', () => {
    const service = TestBed.inject(AuthService);
    const http = TestBed.inject(HttpTestingController);
    let authenticated: boolean | undefined;

    service.login({ username: 'admin', password: 'correcta' }, true).subscribe((outcome) => {
      authenticated = outcome.authenticated;
    });

    http.expectOne('/api/auth/login').flush({
      token: null,
      expiresIn: 0,
      user: null,
      requiresTwoFactor: true,
      challengeToken: 'desafio-opaco',
      challengeExpiresIn: 300,
    });

    expect(authenticated).toBe(false);
    expect(service.isAuthenticated()).toBe(false);
    expect(localStorage.getItem(SESSION_KEY)).toBeNull();
    expect(sessionStorage.getItem(SESSION_KEY)).toBeNull();
  });

  it('guarda la sesión únicamente después de verificar el segundo factor', () => {
    const service = TestBed.inject(AuthService);
    const http = TestBed.inject(HttpTestingController);
    const expiresAt = Date.now() + 60_000;
    const token = jwtThatExpiresAt(expiresAt);

    service.verifyTwoFactor('desafio-opaco', '123456', false).subscribe();

    http.expectOne('/api/auth/2fa/verify').flush({
      token,
      expiresIn: 3600,
      user: {
        id: 1,
        username: 'admin',
        fullName: 'Administrador',
        role: 'ADMIN',
      },
      requiresTwoFactor: false,
      challengeToken: null,
      challengeExpiresIn: 0,
    });

    expect(service.isAuthenticated()).toBe(true);
    expect(service.getToken()).toBe(token);
    expect(sessionStorage.getItem(SESSION_KEY)).not.toBeNull();
  });
});
