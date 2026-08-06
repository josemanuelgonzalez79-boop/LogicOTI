import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { UserRole } from '../../core/models/auth.model';
import { AppUser, CreateUserRequest } from '../../core/models/user.model';
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

  readonly users = signal<AppUser[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly showPassword = signal(false);
  readonly errorMessage = signal('');
  readonly successMessage = signal('');

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

  private getErrorMessage(error: HttpErrorResponse): string {
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

    return 'No fue posible crear el usuario. Intenta nuevamente.';
  }
}
