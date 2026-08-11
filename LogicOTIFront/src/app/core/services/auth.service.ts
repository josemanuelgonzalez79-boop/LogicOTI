import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { map, Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../http/api.endpoints';
import { AuthSession, LoginCredentials, LoginResponse } from '../models/auth.model';

const SESSION_KEY = 'logicoti_session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  private readonly authenticatedState = signal(false);

  readonly authenticated = this.authenticatedState.asReadonly();

  constructor() {
    // La señal ya existe cuando se valida lo almacenado. De esta forma una
    // sesión corrupta o vencida puede limpiarse con seguridad al arrancar.
    this.authenticatedState.set(this.hasValidStoredSession());
  }

  login(credentials: LoginCredentials, rememberSession: boolean): Observable<AuthSession> {
    const request: LoginCredentials = {
      username: credentials.username.trim(),
      password: credentials.password,
    };

    return this.http
      .post<LoginResponse>(`${this.baseUrl}${API_ENDPOINTS.auth.login}`, request)
      .pipe(
        map((response) => {
          const jwtExpiresAt = this.getJwtExpiration(response.token);

          if (jwtExpiresAt === null || jwtExpiresAt <= Date.now()) {
            throw new Error('El servidor devolvió un token de sesión inválido.');
          }

          const session: AuthSession = {
            token: response.token,
            expiresIn: response.expiresIn,
            expiresAt: Math.min(Date.now() + response.expiresIn * 1000, jwtExpiresAt),
            user: response.user,
            authenticated: true,
          };

          this.storeSession(session, rememberSession);
          this.authenticatedState.set(true);

          return session;
        }),
      );
  }

  logout(): void {
    localStorage.removeItem(SESSION_KEY);
    sessionStorage.removeItem(SESSION_KEY);
    this.authenticatedState.set(false);
  }

  isAuthenticated(): boolean {
    return this.getSession() !== null;
  }

  getToken(): string | null {
    return this.getSession()?.token ?? null;
  }

  getSession(): AuthSession | null {
    const storedSession = sessionStorage.getItem(SESSION_KEY) ?? localStorage.getItem(SESSION_KEY);

    if (!storedSession) {
      return null;
    }

    try {
      const session = JSON.parse(storedSession) as unknown;

      if (!this.isValidSession(session)) {
        this.logout();
        return null;
      }

      return session;
    } catch {
      this.logout();
      return null;
    }
  }

  private storeSession(session: AuthSession, rememberSession: boolean): void {
    localStorage.removeItem(SESSION_KEY);
    sessionStorage.removeItem(SESSION_KEY);

    const storage = rememberSession ? localStorage : sessionStorage;

    storage.setItem(SESSION_KEY, JSON.stringify(session));
  }

  private hasValidStoredSession(): boolean {
    return this.getSession() !== null;
  }

  private isValidSession(session: unknown): session is AuthSession {
    if (!session || typeof session !== 'object') {
      return false;
    }

    const candidate = session as Partial<AuthSession>;
    const jwtExpiresAt =
      typeof candidate.token === 'string' ? this.getJwtExpiration(candidate.token) : null;

    return Boolean(
      candidate.authenticated === true &&
      candidate.user &&
      typeof candidate.user.id === 'number' &&
      typeof candidate.user.username === 'string' &&
      typeof candidate.user.fullName === 'string' &&
      ['ADMIN', 'OPERATOR', 'MONITORING'].includes(candidate.user.role ?? '') &&
      typeof candidate.expiresAt === 'number' &&
      Number.isFinite(candidate.expiresAt) &&
      candidate.expiresAt > Date.now() &&
      jwtExpiresAt !== null &&
      jwtExpiresAt > Date.now(),
    );
  }

  private getJwtExpiration(token: string): number | null {
    const parts = token.split('.');

    if (parts.length !== 3 || !parts[1]) {
      return null;
    }

    try {
      const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const paddedBase64 = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
      const payload = JSON.parse(atob(paddedBase64)) as { exp?: unknown };

      if (typeof payload.exp !== 'number' || !Number.isFinite(payload.exp)) {
        return null;
      }

      return payload.exp * 1000;
    } catch {
      return null;
    }
  }
}
