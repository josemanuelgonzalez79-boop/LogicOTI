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
  token: string | null;
  expiresIn: number;
  user: AuthUser | null;
  requiresTwoFactor: boolean;
  challengeToken: string | null;
  challengeExpiresIn: number;
}

export interface AuthSession {
  token: string;
  expiresIn: number;
  expiresAt: number;
  user: AuthUser;
  authenticated: true;
}

export interface TwoFactorChallenge {
  challengeToken: string;
  expiresIn: number;
  authenticated: false;
}

export type LoginOutcome = AuthSession | TwoFactorChallenge;

export interface TwoFactorStatus {
  enabled: boolean;
  unusedRecoveryCodes: number;
}

export interface TwoFactorSetup {
  manualKey: string;
  qrCodeDataUrl: string;
  accountName: string;
  issuer: string;
  expiresAt: string;
}

export interface TwoFactorConfirmation {
  enabled: boolean;
  recoveryCodes: string[];
}
