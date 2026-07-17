import { Component, output } from '@angular/core';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [],
  templateUrl: './navbar.html',
  styleUrl: './navbar.scss',
})
export class Navbar {
  menuToggle = output<void>();

  openMobileMenu(): void {
    this.menuToggle.emit();
  }
}