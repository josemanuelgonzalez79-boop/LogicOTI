import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { DrawerModule } from 'primeng/drawer';

import { Navbar } from '../navbar/navbar';
import { Sidebar } from '../sidebar/sidebar';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    Navbar,
    Sidebar,
    RouterOutlet,
    DrawerModule
  ],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  mobileMenuVisible = false;

  openMobileMenu(): void {
    this.mobileMenuVisible = true;
  }

  closeMobileMenu(): void {
    this.mobileMenuVisible = false;
  }
}