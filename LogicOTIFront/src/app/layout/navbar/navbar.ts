import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './navbar.html',
  styleUrl: './navbar.scss',
})
export class Navbar {
  menuToggle = output<void>();
  activeAlarmCount = input(0);

  openMobileMenu(): void {
    this.menuToggle.emit();
  }
}
