import { NgIf } from '@angular/common';
import { ChangeDetectorRef, Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs';

import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';

import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [NgIf, FormsModule, ButtonModule, CheckboxModule, InputTextModule, PasswordModule],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  username = '';
  password = '';
  rememberSession = false;
  loading = false;
  errorMessage = '';
  challengeToken = '';
  verificationCode = '';
  challengeExpiresIn = 0;

  constructor(
    private readonly router: Router,
    private readonly route: ActivatedRoute,
    private readonly authService: AuthService,
    private readonly cdr: ChangeDetectorRef,
  ) {}

  login(): void {
    if (!this.username.trim() || !this.password.trim() || this.loading) {
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    this.authService
      .login(
        {
          username: this.username,
          password: this.password,
        },
        this.rememberSession,
      )
      .pipe(
        finalize(() => {
          this.loading = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: (outcome) => {
          if (!outcome.authenticated) {
            this.challengeToken = outcome.challengeToken;
            this.challengeExpiresIn = outcome.expiresIn;
            this.password = '';
            this.cdr.detectChanges();
            return;
          }

          this.completeLogin();
        },
        error: () => {
          this.errorMessage = 'Usuario o contraseña incorrectos.';
          this.cdr.detectChanges();
        },
      });
  }

  verifyTwoFactor(): void {
    if (!this.challengeToken || !this.verificationCode.trim() || this.loading) {
      return;
    }

    this.loading = true;
    this.errorMessage = '';

    this.authService
      .verifyTwoFactor(this.challengeToken, this.verificationCode, this.rememberSession)
      .pipe(
        finalize(() => {
          this.loading = false;
          this.cdr.detectChanges();
        }),
      )
      .subscribe({
        next: () => this.completeLogin(),
        error: () => {
          this.errorMessage =
            'El código no es válido, ya fue utilizado o el tiempo de verificación terminó.';
          this.cdr.detectChanges();
        },
      });
  }

  restartLogin(): void {
    this.challengeToken = '';
    this.verificationCode = '';
    this.challengeExpiresIn = 0;
    this.errorMessage = '';
  }

  private completeLogin(): void {
    const returnUrl = this.getSafeReturnUrl();

    if (returnUrl) {
      void this.router.navigateByUrl(returnUrl);
      return;
    }

    void this.router.navigate(['/dashboard']);
  }

  private getSafeReturnUrl(): string | null {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');

    if (
      !returnUrl ||
      !returnUrl.startsWith('/') ||
      returnUrl.startsWith('//') ||
      returnUrl.startsWith('/login')
    ) {
      return null;
    }

    return returnUrl;
  }
}
