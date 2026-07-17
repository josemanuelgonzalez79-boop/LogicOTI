import { Component, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

interface MenuItem {
  label: string;
  icon: string;
  route: string;
}

@Component({
  selector: 'app-sidebar',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class Sidebar {
  navigationSelected = output<void>();

  protected readonly menuItems: MenuItem[] = [
    {
      label: 'Dashboard',
      icon: 'pi pi-home',
      route: '/dashboard',
    },
    {
      label: 'Edificio',
      icon: 'pi pi-building',
      route: '/building',
    },
    {
      label: 'Control',
      icon: 'pi pi-sliders-h',
      route: '/control',
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
      label: 'Diagnóstico',
      icon: 'pi pi-wrench',
      route: '/diagnostics',
    },
    {
      label: 'Administración',
      icon: 'pi pi-cog',
      route: '/administration',
    },
  ];

  protected onNavigation(): void {
    this.navigationSelected.emit();
  }
}