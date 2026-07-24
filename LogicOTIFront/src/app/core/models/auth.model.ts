export interface LoginCredentials {
  username: string;
  password: string;
}

export interface AuthUser {
  id: number;
  username: string;
  fullName: string;
  role: string;
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