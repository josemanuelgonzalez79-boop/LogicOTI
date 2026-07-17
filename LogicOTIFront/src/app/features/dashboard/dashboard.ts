import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})

export class Dashboard {
    readonly summaryCards = [
      {
        label: 'Sensores activos',
        value: '24',
        detail: '2 requieren revisión',
        icon: 'pi pi-broadcast-tower',
        status: 'warning',
      },
      {
        label: 'Alarmas activas',
        value: '1',
        detail: 'Prioridad alta',
        icon: 'pi pi-bell',
        status: 'danger',
      },
      {
        label: 'Áreas monitoreadas',
        value: '18',
        detail: '4 niveles del edificio',
        icon: 'pi pi-building',
        status: 'success',
      },
      {
        label: 'Dispositivos conectados',
        value: '31',
        detail: 'Conectividad estable',
        icon: 'pi pi-link',
        status: 'info',
      },
    ];

    readonly floors = [
    {
      name: 'Exterior',
      description: 'Accesos, estacionamiento y terraza posterior',
      status: 'Vigilancia activa',
      health: 'normal',
      healthLabel: 'Todo operativo',
      devices: 7,
      sensors: 4,
      cameras: 2,
      alarms: 0,
      icon: 'pi pi-map',
      route: '/building/floor/exterior',
    },
    {
      name: 'Planta baja',
      description: 'Recepción, oficinas y áreas comunes',
      status: 'Operación normal',
      health: 'normal',
      healthLabel: 'Todo operativo',
      devices: 12,
      sensors: 8,
      cameras: 1,
      alarms: 0,
      icon: 'pi pi-home',
      route: '/building/floor/planta-baja',
    },
    {
      name: 'Piso 1',
      description: 'Oficinas administrativas y salas',
      status: 'Operación normal',
      health: 'normal',
      healthLabel: 'Todo operativo',
      devices: 9,
      sensors: 6,
      cameras: 1,
      alarms: 0,
      icon: 'pi pi-building',
      route: '/building/floor/piso-1',
    },
    {
      name: 'Piso 2',
      description: 'Áreas técnicas y terraza',
      status: 'Revisión requerida',
      health: 'warning',
      healthLabel: '1 advertencia',
      devices: 8,
      sensors: 5,
      cameras: 1,
      alarms: 1,
      icon: 'pi pi-sitemap',
      route: '/building/floor/piso-2',
    },
  ];

  readonly recentEvents = [
    {
      time: '10:24',
      title: 'Movimiento detectado',
      location: 'Exterior · Acceso principal',
      severity: 'info',
      icon: 'pi pi-eye',
    },
    {
      time: '10:18',
      title: 'Sensor de humo sin respuesta',
      location: 'Piso 2 · Área técnica',
      severity: 'danger',
      icon: 'pi pi-exclamation-triangle',
    },
    {
      time: '09:52',
      title: 'Iluminación activada',
      location: 'Planta baja · Recepción',
      severity: 'success',
      icon: 'pi pi-lightbulb',
    },
    {
      time: '09:35',
      title: 'Diagnóstico completado',
      location: 'Sistema general',
      severity: 'info',
      icon: 'pi pi-check-circle',
    },
  ];
}