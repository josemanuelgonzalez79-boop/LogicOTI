import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    FormsModule,
    ButtonModule,
    CheckboxModule,
    InputTextModule,
    PasswordModule,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  username = '';
  password = '';
  rememberSession = false;
  loading = false;

  constructor(private readonly router: Router) {}

  login(): void {
    if (!this.username.trim() || !this.password.trim()) {
      return;
    }

    this.loading = true;

    setTimeout(() => {
      this.loading = false;
      void this.router.navigate(['/dashboard']);
    }, 600);
  }
}