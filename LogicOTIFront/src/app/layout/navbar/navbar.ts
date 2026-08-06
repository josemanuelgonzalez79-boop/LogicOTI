import { Component, inject, input, output } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { UserRole } from '../../core/models/auth.model';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './navbar.html',
  styleUrl: './navbar.scss',
})
export class Navbar {
  private readonly theme = inject(ThemeService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  menuToggle = output<void>();
  activeAlarmCount = input(0);
  readonly darkMode = this.theme.darkMode;
  readonly currentUser = this.authService.getSession()?.user ?? null;

  openMobileMenu(): void {
    this.menuToggle.emit();
  }

  toggleTheme(): void {
    this.theme.toggle();
  }

  logout(): void {
    this.authService.logout();
    void this.router.navigate(['/login']);
  }

  roleLabel(role: UserRole | undefined): string {
    const labels: Record<UserRole, string> = {
      ADMIN: 'Administrador',
      OPERATOR: 'Operador',
      MONITORING: 'Monitoreo',
    };

    return role ? labels[role] : '';
  }
}
