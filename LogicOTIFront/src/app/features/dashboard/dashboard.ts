import {
  ChangeDetectorRef,
  Component,
  OnInit,
  inject,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';

import {
  AreaInventory,
  BuildingFloor,
} from '../../core/models/building.model';
import { SystemApiService } from '../../core/services/system-api.service';

interface SummaryCard {
  label: string;
  value: string;
  detail: string;
  icon: string;
  status: 'warning' | 'danger' | 'success' | 'info';
}

interface FloorCard {
  name: string;
  description: string;
  status: string;
  health: 'normal' | 'warning' | 'danger';
  healthLabel: string;
  devices: number;
  sensors: number;
  cameras: number;
  alarms: number;
  icon: string;
  route: string;
}

interface RecentEvent {
  time: string;
  title: string;
  location: string;
  severity: 'info' | 'danger' | 'success' | 'warning';
  icon: string;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  private readonly systemApiService = inject(SystemApiService);
  private readonly changeDetectorRef = inject(ChangeDetectorRef);

  isLoading = true;
  errorMessage = '';

  systemConnected = false;
  databaseConnected = false;
  plcEnabled = false;
  lastUpdated: Date | null = null;

  summaryCards: SummaryCard[] = [];
  floors: FloorCard[] = [];

  readonly recentEvents: RecentEvent[] = [
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

  ngOnInit(): void {
    this.loadDashboard();
  }

  private loadDashboard(): void {
    this.isLoading = true;
    this.errorMessage = '';

    forkJoin({
      systemStatus: this.systemApiService.getStatus(),
      building: this.systemApiService.getBuilding(),
    })
      .pipe(
        finalize(() => {
          this.isLoading = false;
          this.changeDetectorRef.detectChanges();
        }),
      )
      .subscribe({
        next: ({ systemStatus, building }) => {
          this.systemConnected = systemStatus.status === 'UP';
          this.databaseConnected = systemStatus.database === 'UP';
          this.plcEnabled = systemStatus.plcEnabled;
          this.lastUpdated = new Date(systemStatus.timestamp);

          const totalSensors =
            building.totals.motionSensors +
            building.totals.doorSensors +
            building.totals.smokeSensors;

          this.summaryCards = [
            {
              label: 'Sensores instalados',
              value: totalSensors.toString(),
              detail:
                `${building.totals.motionSensors} movimiento · ` +
                `${building.totals.doorSensors} puerta · ` +
                `${building.totals.smokeSensors} humo`,
              icon: 'pi pi-broadcast-tower',
              status: 'info',
            },
            {
              label: 'Áreas monitoreadas',
              value: building.totals.areas.toString(),
              detail: `${building.totals.floors} niveles registrados`,
              icon: 'pi pi-building',
              status: 'success',
            },
            {
              label: 'Lámparas registradas',
              value: building.totals.lamps.toString(),
              detail: `${building.totals.switches} interruptores`,
              icon: 'pi pi-lightbulb',
              status: 'warning',
            },
            {
              label: 'Minisplits registrados',
              value: building.totals.minisplits.toString(),
              detail: `${building.totals.outlets} contactos eléctricos`,
              icon: 'pi pi-sliders-h',
              status: 'info',
            },
          ];

          this.floors = building.floors
            .sort((a, b) => a.displayOrder - b.displayOrder)
            .map((floor) => this.mapFloorToCard(floor));
        },
        error: (error) => {
          console.error('Error al cargar el dashboard:', error);

          this.errorMessage =
            'No fue posible obtener la información del sistema.';

          this.summaryCards = [];
          this.floors = [];
        },
      });
  }

  private mapFloorToCard(floor: BuildingFloor): FloorCard {
    const inventory = this.getFloorInventory(floor);

    const sensors =
      inventory.motionSensors +
      inventory.doorSensors +
      inventory.smokeSensors;

    const devices =
      inventory.lamps +
      inventory.outlets +
      inventory.switches +
      inventory.minisplits;

    return {
      name: floor.name,
      description: `${floor.areas.length} áreas registradas en este nivel`,
      status: 'Información cargada desde el sistema',
      health: 'normal',
      healthLabel: 'Estructura disponible',
      devices,
      sensors,
      cameras: 0,
      alarms: 0,
      icon: this.getFloorIcon(floor.code),
      route: `/building/floor/${floor.code}`,
    };
  }

  private getFloorInventory(floor: BuildingFloor): AreaInventory {
    return floor.areas.reduce<AreaInventory>(
      (total, area) => ({
        lamps: total.lamps + area.inventory.lamps,
        motionSensors:
          total.motionSensors + area.inventory.motionSensors,
        doorSensors:
          total.doorSensors + area.inventory.doorSensors,
        smokeSensors:
          total.smokeSensors + area.inventory.smokeSensors,
        outlets: total.outlets + area.inventory.outlets,
        switches: total.switches + area.inventory.switches,
        minisplits: total.minisplits + area.inventory.minisplits,
      }),
      {
        lamps: 0,
        motionSensors: 0,
        doorSensors: 0,
        smokeSensors: 0,
        outlets: 0,
        switches: 0,
        minisplits: 0,
      },
    );
  }

  private getFloorIcon(floorCode: string): string {
    switch (floorCode) {
      case 'PB':
        return 'pi pi-home';

      case 'P1':
        return 'pi pi-building';

      case 'P2':
        return 'pi pi-sitemap';

      default:
        return 'pi pi-map';
    }
  }
}