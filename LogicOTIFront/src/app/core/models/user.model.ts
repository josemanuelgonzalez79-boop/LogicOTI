import { UserRole } from './auth.model';

export interface AppUser {
  id: number;
  username: string;
  fullName: string;
  role: UserRole;
  active: boolean;
  createdAt: string;
  protectedUser: boolean;
}

export interface CreateUserRequest {
  username: string;
  password: string;
  fullName: string;
  role: UserRole;
  active: boolean;
}

export interface UpdateUserRequest {
  username: string;
  password: string | null;
  fullName: string;
  role: UserRole;
  active: boolean;
}
