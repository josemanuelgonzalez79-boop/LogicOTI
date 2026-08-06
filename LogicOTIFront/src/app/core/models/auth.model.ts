export interface LoginCredentials {
  username: string;
  password: string;
}

export type UserRole = 'ADMIN' | 'OPERATOR' | 'MONITORING';

export interface AuthUser {
  id: number;
  username: string;
  fullName: string;
  role: UserRole;
}

export interface LoginResponse {
  token: string;
  expiresIn: number;
  user: AuthUser;
}

export interface AuthSession {
  token: string;
  expiresIn: number;
  expiresAt: number;
  user: AuthUser;
  authenticated: boolean;
}
