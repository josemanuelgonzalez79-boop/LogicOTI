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
        next: () => {
          const returnUrl = this.getSafeReturnUrl();

          if (returnUrl) {
            void this.router.navigateByUrl(returnUrl);
            return;
          }

          void this.router.navigate(['/dashboard']);
        },
        error: () => {
          this.errorMessage = 'Usuario o contraseña incorrectos.';
          this.cdr.detectChanges();
        },
      });
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
