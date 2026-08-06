import { Component, inject, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { UserRole } from '../../core/models/auth.model';
import { AuthService } from '../../core/services/auth.service';

interface MenuItem {
  label: string;
  icon: string;
  route: string;
  roles?: UserRole[];
}

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class Sidebar {
  private readonly authService = inject(AuthService);

  navigationSelected = output<void>();

  private readonly allMenuItems: MenuItem[] = [
    {
      label: 'Dashboard',
      icon: 'pi pi-home',
      route: '/dashboard',
    },
    {
      label: 'Control',
      icon: 'pi pi-sliders-h',
      route: '/control',
    },
    {
      label: 'Cámaras',
      icon: 'pi pi-video',
      route: '/cameras',
    },
    {
      label: 'Alarmas',
      icon: 'pi pi-bell',
      route: '/alarms',
    },
    {
      label: 'Históricos',
      icon: 'pi pi-chart-line',
      route: '/history',
    },
    {
      label: 'Usuarios',
      icon: 'pi pi-users',
      route: '/administration',
      roles: ['ADMIN'],
    },
  ];

  protected readonly menuItems = this.allMenuItems.filter((item) => {
    const role = this.authService.getSession()?.user.role;

    return !item.roles || (role !== undefined && item.roles.includes(role));
  });

  protected onNavigation(): void {
    this.navigationSelected.emit();
  }
}
