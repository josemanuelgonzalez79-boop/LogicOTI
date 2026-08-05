import { Component, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';

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

  menuToggle = output<void>();
  activeAlarmCount = input(0);
  readonly darkMode = this.theme.darkMode;

  openMobileMenu(): void {
    this.menuToggle.emit();
  }

  toggleTheme(): void {
    this.theme.toggle();
  }
}
