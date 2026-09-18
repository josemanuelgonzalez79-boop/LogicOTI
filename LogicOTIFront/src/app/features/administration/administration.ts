import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { UserRole } from '../../core/models/auth.model';
import { AppUser, CreateUserRequest, UpdateUserRequest } from '../../core/models/user.model';
import { AuthService } from '../../core/services/auth.service';
import { UserApiService } from '../../core/services/user-api.service';

interface RoleOption {
  value: UserRole;
  label: string;
  description: string;
}

@Component({
  selector: 'app-administration',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule],
  templateUrl: './administration.html',
  styleUrl: './administration.scss',
})
export class Administration implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly userApi = inject(UserApiService);
  private readonly authService = inject(AuthService);

  readonly users = signal<AppUser[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly changingPassword = signal(false);
  readonly deletingUser = signal(false);
  readonly updatingAccessUserId = signal<number | null>(null);
  readonly showPassword = signal(false);
  readonly showNewPassword = signal(false);
  readonly errorMessage = signal('');
  readonly successMessage = signal('');
  readonly passwordTarget = signal<AppUser | null>(null);
  readonly deletionTarget = signal<AppUser | null>(null);
  readonly currentUserId = this.authService.getSession()?.user.id ?? null;

  readonly activeUsers = computed(() => this.users().filter((user) => user.active).length);
  readonly adminUsers = computed(
    () => this.users().filter((user) => user.role === 'ADMIN' && user.active).length,
  );

  readonly roleOptions: RoleOption[] = [
    {
      value: 'MONITORING',
      label: 'Monitoreo',
      description: 'Puede consultar estados, cámaras, alarmas e históricos.',
    },
    {
      value: 'OPERATOR',
      label: 'Operador',
      description: 'También puede enviar órdenes a luces y minisplits.',
    },
    {
      value: 'ADMIN',
      label: 'Administrador',
      description: 'Tiene acceso completo y puede administrar usuarios.',
    },
  ];

  readonly userForm = this.formBuilder.nonNullable.group({
    fullName: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(150)]],
    username: [
      '',
      [Validators.required, Validators.maxLength(50), Validators.pattern(/^[A-Za-z0-9._-]+$/)],
    ],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
    role: this.formBuilder.nonNullable.control<UserRole>('MONITORING', Validators.required),
    active: [true],
  });

  readonly passwordForm = this.formBuilder.nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
    confirmation: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(100)]],
  });

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading.set(true);
    this.errorMessage.set('');

    this.userApi
      .getUsers()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (users) => this.users.set(this.orderUsers(users)),
        error: () => this.errorMessage.set('No fue posible cargar los usuarios registrados.'),
      });
  }

  createUser(): void {
    this.successMessage.set('');
    this.errorMessage.set('');

    if (this.userForm.invalid) {
      this.userForm.markAllAsTouched();
      this.errorMessage.set('Revisa los datos marcados antes de guardar el usuario.');
      return;
    }

    const formValue = this.userForm.getRawValue();
    const request: CreateUserRequest = {
      fullName: formValue.fullName.trim(),
      username: formValue.username.trim(),
      password: formValue.password,
      role: formValue.role,
      active: formValue.active,
    };

    this.saving.set(true);

    this.userApi
      .createUser(request)
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (createdUser) => {
          this.users.update((users) => this.orderUsers([...users, createdUser]));
          this.userForm.reset({
            fullName: '',
            username: '',
            password: '',
            role: 'MONITORING',
            active: true,
          });
          this.showPassword.set(false);
          this.successMessage.set(`El usuario ${createdUser.username} se creó correctamente.`);
        },
        error: (error: HttpErrorResponse) => this.errorMessage.set(this.getErrorMessage(error)),
      });
  }

  togglePassword(): void {
    this.showPassword.update((visible) => !visible);
  }

  openPasswordChange(user: AppUser): void {
    this.clearMessages();
    this.passwordForm.reset({ password: '', confirmation: '' });
    this.showNewPassword.set(false);
    this.passwordTarget.set(user);
  }

  closePasswordChange(): void {
    if (!this.changingPassword()) {
      this.passwordTarget.set(null);
      this.passwordForm.reset({ password: '', confirmation: '' });
    }
  }

  toggleNewPassword(): void {
    this.showNewPassword.update((visible) => !visible);
  }

  changePassword(): void {
    const user = this.passwordTarget();
    this.clearMessages();

    if (!user || this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      this.errorMessage.set('La nueva contraseña debe tener al menos 8 caracteres.');
      return;
    }

    const formValue = this.passwordForm.getRawValue();

    if (formValue.password !== formValue.confirmation) {
      this.errorMessage.set('La confirmación no coincide con la nueva contraseña.');
      return;
    }

    const request: UpdateUserRequest = {
      username: user.username,
      fullName: user.fullName,
      password: formValue.password,
      role: user.role,
      active: user.active,
    };

    this.changingPassword.set(true);

    this.userApi
      .updateUser(user.id, request)
      .pipe(finalize(() => this.changingPassword.set(false)))
      .subscribe({
        next: (updatedUser) => {
          this.replaceUser(updatedUser);
          this.passwordTarget.set(null);
          this.passwordForm.reset({ password: '', confirmation: '' });
          this.successMessage.set(`La contraseña de ${updatedUser.username} se actualizó.`);
        },
        error: (error: HttpErrorResponse) =>
          this.errorMessage.set(
            this.getErrorMessage(error, 'No fue posible cambiar la contraseña.'),
          ),
      });
  }

  requestDeletion(user: AppUser): void {
    if (!this.canDelete(user)) {
      return;
    }

    this.clearMessages();
    this.deletionTarget.set(user);
  }

  cancelDeletion(): void {
    if (!this.deletingUser()) {
      this.deletionTarget.set(null);
    }
  }

  confirmDeletion(): void {
    const user = this.deletionTarget();

    if (!user || !this.canDelete(user)) {
      return;
    }

    this.clearMessages();
    this.deletingUser.set(true);

    this.userApi
      .deleteUser(user.id)
      .pipe(finalize(() => this.deletingUser.set(false)))
      .subscribe({
        next: () => {
          this.users.update((users) => users.filter((item) => item.id !== user.id));
          this.deletionTarget.set(null);
          this.successMessage.set(`El usuario ${user.username} se eliminó correctamente.`);
        },
        error: (error: HttpErrorResponse) =>
          this.errorMessage.set(this.getErrorMessage(error, 'No fue posible eliminar el usuario.')),
      });
  }

  canDelete(user: AppUser): boolean {
    return !user.protectedUser && user.id !== this.currentUserId;
  }

  toggleUserAccess(user: AppUser): void {
    if (!this.canChangeAccess(user)) {
      return;
    }

    this.clearMessages();
    this.updatingAccessUserId.set(user.id);

    const request: UpdateUserRequest = {
      username: user.username,
      fullName: user.fullName,
      password: null,
      role: user.role,
      active: !user.active,
    };

    this.userApi
      .updateUser(user.id, request)
      .pipe(finalize(() => this.updatingAccessUserId.set(null)))
      .subscribe({
        next: (updatedUser) => {
          this.replaceUser(updatedUser);
          this.successMessage.set(
            updatedUser.active
              ? `La cuenta ${updatedUser.username} quedó habilitada.`
              : `La cuenta ${updatedUser.username} quedó bloqueada.`,
          );
        },
        error: (error: HttpErrorResponse) =>
          this.errorMessage.set(
            this.getErrorMessage(error, 'No fue posible cambiar el acceso del usuario.'),
          ),
      });
  }

  canChangeAccess(user: AppUser): boolean {
    return !user.protectedUser && user.id !== this.currentUserId;
  }

  accessActionLabel(user: AppUser): string {
    if (user.protectedUser) {
      return 'El administrador principal siempre debe permanecer habilitado.';
    }

    if (user.id === this.currentUserId) {
      return 'No puedes bloquear la cuenta con la que tienes la sesión iniciada.';
    }

    return user.active ? 'Bloquear acceso' : 'Habilitar acceso';
  }

  deleteDisabledReason(user: AppUser): string {
    if (user.protectedUser) {
      return 'El administrador principal está protegido.';
    }

    if (user.id === this.currentUserId) {
      return 'No puedes eliminar tu propia sesión.';
    }

    return 'Eliminar usuario';
  }

  roleLabel(role: UserRole): string {
    return this.roleOptions.find((option) => option.value === role)?.label ?? role;
  }

  selectedRoleDescription(): string {
    return (
      this.roleOptions.find((option) => option.value === this.userForm.controls.role.value)
        ?.description ?? ''
    );
  }

  userInitials(user: AppUser): string {
    return user.fullName
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part.charAt(0).toUpperCase())
      .join('');
  }

  private orderUsers(users: AppUser[]): AppUser[] {
    return [...users].sort((first, second) => {
      if (first.active !== second.active) {
        return first.active ? -1 : 1;
      }

      return first.fullName.localeCompare(second.fullName, 'es');
    });
  }

  private replaceUser(updatedUser: AppUser): void {
    this.users.update((users) =>
      this.orderUsers(users.map((user) => (user.id === updatedUser.id ? updatedUser : user))),
    );
  }

  private clearMessages(): void {
    this.errorMessage.set('');
    this.successMessage.set('');
  }

  private getErrorMessage(
    error: HttpErrorResponse,
    fallback = 'No fue posible crear el usuario.',
  ): string {
    const detail = error.error?.detail;

    if (typeof detail === 'string' && detail.trim()) {
      return detail;
    }

    if (error.status === 409) {
      return 'Ese nombre de usuario ya está registrado.';
    }

    if (error.status === 403) {
      return 'Tu sesión no tiene permiso para administrar usuarios.';
    }

    return fallback;
  }
}
