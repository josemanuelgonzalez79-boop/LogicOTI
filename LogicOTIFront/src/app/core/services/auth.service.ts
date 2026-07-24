import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { map, Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../http/api.endpoints';
import {
  AuthSession,
  LoginCredentials,
  LoginResponse,
} from '../models/auth.model';

const SESSION_KEY = 'logicoti_session';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  private readonly authenticatedState = signal(this.hasValidStoredSession());

  readonly authenticated = this.authenticatedState.asReadonly();

  login(
    credentials: LoginCredentials,
    rememberSession: boolean,
  ): Observable<AuthSession> {
    const request: LoginCredentials = {
      username: credentials.username.trim(),
      password: credentials.password,
    };

    return this.http
      .post<LoginResponse>(
        `${this.baseUrl}${API_ENDPOINTS.auth.login}`,
        request,
      )
      .pipe(
        map((response) => {
          const session: AuthSession = {
            token: response.token,
            expiresIn: response.expiresIn,
            expiresAt: Date.now() + response.expiresIn * 1000,
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
    const session = this.getSession();

    if (!session || session.expiresAt <= Date.now()) {
      this.logout();
      return false;
    }

    return true;
  }

  getToken(): string | null {
    return this.getSession()?.token ?? null;
  }

  getSession(): AuthSession | null {
    const storedSession =
      sessionStorage.getItem(SESSION_KEY) ??
      localStorage.getItem(SESSION_KEY);

    if (!storedSession) {
      return null;
    }

    try {
      const session = JSON.parse(storedSession) as AuthSession;

      if (
        !session.token ||
        !session.user ||
        session.expiresAt <= Date.now()
      ) {
        this.logout();
        return null;
      }

      return session;
    } catch {
      this.logout();
      return null;
    }
  }

  private storeSession(
    session: AuthSession,
    rememberSession: boolean,
  ): void {
    localStorage.removeItem(SESSION_KEY);
    sessionStorage.removeItem(SESSION_KEY);

    const storage = rememberSession
      ? localStorage
      : sessionStorage;

    storage.setItem(SESSION_KEY, JSON.stringify(session));
  }

  private hasValidStoredSession(): boolean {
    return this.getSession() !== null;
  }
}