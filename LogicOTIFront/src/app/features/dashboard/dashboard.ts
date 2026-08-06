import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { finalize } from 'rxjs/operators';

import { AreaInventory, BuildingFloor } from '../../core/models/building.model';
import { SensorEventHistoryItem } from '../../core/models/history.model';
import { AlarmApiService } from '../../core/services/alarm-api.service';
import { AuthService } from '../../core/services/auth.service';
import { HistoryApiService } from '../../core/services/history-api.service';
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
  private readonly historyApiService = inject(HistoryApiService);
  private readonly alarmApiService = inject(AlarmApiService);
  private readonly authService = inject(AuthService);
  private readonly changeDetectorRef = inject(ChangeDetectorRef);

  readonly isAdmin = this.authService.getSession()?.user.role === 'ADMIN';

  isLoading = true;
  errorMessage = '';

  systemConnected = false;
  databaseConnected = false;
  plcEnabled = false;
  lastUpdated: Date | null = null;

  summaryCards: SummaryCard[] = [];
  floors: FloorCard[] = [];
  recentEvents: RecentEvent[] = [];

  ngOnInit(): void {
    this.loadDashboard();
  }

  private loadDashboard(): void {
    this.isLoading = true;
    this.errorMessage = '';

    forkJoin({
      systemStatus: this.systemApiService.getStatus(),
      building: this.systemApiService.getBuilding(),
      recentEvents: this.historyApiService.getSensorEventHistory({
        limit: 4,
        offset: 0,
      }),
      activeAlarms: this.alarmApiService.getActiveAlarms(),
    })
      .pipe(
        finalize(() => {
          this.isLoading = false;
          this.changeDetectorRef.detectChanges();
        }),
      )
      .subscribe({
        next: ({ systemStatus, building, recentEvents, activeAlarms }) => {
          this.systemConnected = systemStatus.status === 'UP';
          this.databaseConnected = systemStatus.database === 'UP';
          this.plcEnabled = systemStatus.plcEnabled;
          this.lastUpdated = new Date(systemStatus.timestamp);

          const totalSensors = building.totals.motionSensors + building.totals.smokeSensors;

          this.summaryCards = [
            {
              label: 'Sensores instalados',
              value: totalSensors.toString(),
              detail:
                `${building.totals.motionSensors} movimiento · ` +
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
            .map((floor) => this.mapFloorToCard(floor, activeAlarms.items));

          this.recentEvents = recentEvents.items.map((event) => this.mapRecentEvent(event));
        },
        error: (error) => {
          console.error('Error al cargar el dashboard:', error);

          this.errorMessage = 'No fue posible obtener la información del sistema.';

          this.summaryCards = [];
          this.floors = [];
          this.recentEvents = [];
        },
      });
  }

  private mapFloorToCard(floor: BuildingFloor, activeAlarms: SensorEventHistoryItem[]): FloorCard {
    const inventory = this.getFloorInventory(floor);

    const sensors = inventory.motionSensors + inventory.smokeSensors;

    const devices = inventory.lamps + inventory.outlets + inventory.switches + inventory.minisplits;

    const floorAreaCodes = new Set(floor.areas.map((area) => area.code));

    const alarms = activeAlarms.filter((alarm) => floorAreaCodes.has(alarm.areaCode)).length;

    return {
      name: floor.name,
      description: `${floor.areas.length} áreas registradas en este nivel`,
      status: 'Información cargada desde el sistema',
      health: alarms > 0 ? 'danger' : 'normal',
      healthLabel:
        alarms > 0
          ? `${alarms} ${alarms === 1 ? 'alarma activa' : 'alarmas activas'}`
          : 'Sin alarmas activas',
      devices,
      sensors,
      cameras: floor.cameraCount,
      alarms,
      icon: this.getFloorIcon(floor.code),
      route: `/building/floor/${floor.code}`,
    };
  }

  private getFloorInventory(floor: BuildingFloor): AreaInventory {
    return floor.areas.reduce<AreaInventory>(
      (total, area) => ({
        lamps: total.lamps + area.inventory.lamps,
        motionSensors: total.motionSensors + area.inventory.motionSensors,
        doorSensors: 0,
        smokeSensors: total.smokeSensors + area.inventory.smokeSensors,
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

  private mapRecentEvent(event: SensorEventHistoryItem): RecentEvent {
    const isSmokeAlarm = event.deviceType === 'SMOKE' && event.currentState;

    const isCleared = event.eventType === 'CLEARED';

    return {
      time: new Date(event.detectedAt).toLocaleTimeString('es-MX', {
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      }),
      title: event.message.replace(/\.$/, ''),
      location: `${event.areaName} · ${event.deviceName}`,
      severity: isSmokeAlarm ? 'danger' : isCleared ? 'success' : 'info',
      icon: isSmokeAlarm
        ? 'pi pi-exclamation-triangle'
        : isCleared
          ? 'pi pi-check-circle'
          : 'pi pi-eye',
    };
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
