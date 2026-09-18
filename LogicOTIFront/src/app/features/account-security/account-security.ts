import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { TwoFactorSetup, TwoFactorStatus } from '../../core/models/auth.model';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-account-security',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './account-security.html',
  styleUrl: './account-security.scss',
})
export class AccountSecurity implements OnInit {
  private readonly authService = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  status: TwoFactorStatus | null = null;
  setup: TwoFactorSetup | null = null;
  recoveryCodes: string[] = [];
  currentPassword = '';
  confirmationCode = '';
  disablePassword = '';
  disableCode = '';
  loading = false;
  errorMessage = '';
  successMessage = '';

  ngOnInit(): void {
    this.loadStatus();
  }

  beginSetup(): void {
    if (!this.currentPassword || this.loading) {
      return;
    }

    this.startRequest();
    this.authService
      .beginTwoFactorSetup(this.currentPassword)
      .pipe(finalize(() => this.finishRequest()))
      .subscribe({
        next: (setup) => {
          this.setup = setup;
          this.currentPassword = '';
          this.successMessage = 'Escanea el código QR y confirma con el código de seis dígitos.';
          this.cdr.detectChanges();
        },
        error: () =>
          this.showError('No fue posible iniciar la configuración. Revisa tu contraseña.'),
      });
  }

  confirmSetup(): void {
    if (!this.confirmationCode.trim() || this.loading) {
      return;
    }

    this.startRequest();
    this.authService
      .confirmTwoFactorSetup(this.confirmationCode)
      .pipe(finalize(() => this.finishRequest()))
      .subscribe({
        next: (response) => {
          this.status = {
            enabled: true,
            unusedRecoveryCodes: response.recoveryCodes.length,
          };
          this.recoveryCodes = response.recoveryCodes;
          this.setup = null;
          this.confirmationCode = '';
          this.successMessage =
            'Autenticación en dos pasos activada. Guarda ahora tus códigos de recuperación.';
          this.cdr.detectChanges();
        },
        error: () => this.showError('El código temporal no es válido o la configuración venció.'),
      });
  }

  disable(): void {
    if (!this.disablePassword || !this.disableCode.trim() || this.loading) {
      return;
    }

    this.startRequest();
    this.authService
      .disableTwoFactor(this.disablePassword, this.disableCode)
      .pipe(finalize(() => this.finishRequest()))
      .subscribe({
        next: (status) => {
          this.status = status;
          this.setup = null;
          this.recoveryCodes = [];
          this.disablePassword = '';
          this.disableCode = '';
          this.successMessage = 'Autenticación en dos pasos desactivada.';
          this.cdr.detectChanges();
        },
        error: () =>
          this.showError('No fue posible desactivar 2FA. Revisa la contraseña y el código.'),
      });
  }

  async copyRecoveryCodes(): Promise<void> {
    if (this.recoveryCodes.length === 0) {
      return;
    }

    try {
      await navigator.clipboard.writeText(this.recoveryCodes.join('\n'));
      this.successMessage = 'Códigos copiados. Guárdalos fuera de este equipo.';
    } catch {
      this.errorMessage =
        'El navegador no permitió copiar. Selecciona y guarda los códigos manualmente.';
    }
    this.cdr.detectChanges();
  }

  private loadStatus(): void {
    this.loading = true;
    this.authService
      .getTwoFactorStatus()
      .pipe(finalize(() => this.finishRequest()))
      .subscribe({
        next: (status) => {
          this.status = status;
          this.cdr.detectChanges();
        },
        error: () => this.showError('No fue posible consultar la seguridad de la cuenta.'),
      });
  }

  private startRequest(): void {
    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';
  }

  private finishRequest(): void {
    this.loading = false;
    this.cdr.detectChanges();
  }

  private showError(message: string): void {
    this.errorMessage = message;
    this.cdr.detectChanges();
  }
}
