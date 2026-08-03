import { Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';

import { AreaDevice, AreaState } from '../../core/models/area-state.model';
import { Building, BuildingArea, BuildingFloor } from '../../core/models/building.model';
import { AreaApiService } from '../../core/services/area-api.service';
import { AreaRealtimeService } from '../../core/services/area-realtime.service';
import { AuthService } from '../../core/services/auth.service';
import { RealtimeConnectionStatus } from '../../core/services/smoke-alert-realtime.service';
import { SystemApiService } from '../../core/services/system-api.service';

@Component({
  selector: 'app-control',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './control.html',
  styleUrl: './control.scss',
})
export class Control implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  private readonly systemApi = inject(SystemApiService);
  private readonly areaApi = inject(AreaApiService);
  private readonly realtime = inject(AreaRealtimeService);
  private readonly authService = inject(AuthService);

  readonly building = signal<Building | null>(null);
  readonly areaState = signal<AreaState | null>(null);
  readonly selectedFloorCode = signal('');
  readonly selectedAreaCode = signal('');
  readonly loadingBuilding = signal(true);
  readonly loadingArea = signal(false);
  readonly errorMessage = signal('');
  readonly pendingDevices = signal<Set<string>>(new Set());
  readonly connectionStatus = signal<RealtimeConnectionStatus>('DISCONNECTED');

  readonly userRole = this.authService.getSession()?.user.role ?? 'MONITORING';

  readonly canControl = ['ADMIN', 'OPERATOR'].includes(this.userRole);

  readonly floors = computed(() =>
    [...(this.building()?.floors ?? [])].sort((a, b) => a.displayOrder - b.displayOrder),
  );

  readonly selectedFloor = computed<BuildingFloor | null>(
    () => this.floors().find((floor) => floor.code === this.selectedFloorCode()) ?? null,
  );

  readonly areas = computed(() =>
    [...(this.selectedFloor()?.areas ?? [])].sort((a, b) => a.displayOrder - b.displayOrder),
  );

  readonly selectedArea = computed<BuildingArea | null>(
    () => this.areas().find((area) => area.code === this.selectedAreaCode()) ?? null,
  );

  readonly controllableDevices = computed(() =>
    (this.areaState()?.devices ?? []).filter((device) => device.controllable),
  );

  readonly sensorDevices = computed(() =>
    (this.areaState()?.devices ?? []).filter((device) => !device.controllable),
  );

  readonly activeSensors = computed(
    () => this.sensorDevices().filter((device) => device.state === true).length,
  );

  ngOnInit(): void {
    this.realtime.states$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((state) => {
      if (state.areaCode === this.selectedAreaCode()) {
        this.areaState.set(state);
        this.loadingArea.set(false);
        this.errorMessage.set('');
      }
    });

    this.realtime.connectionStatus$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((status) => this.connectionStatus.set(status));

    this.loadBuilding();
  }

  ngOnDestroy(): void {
    void this.realtime.disconnect();
  }

  selectFloor(floorCode: string): void {
    if (floorCode === this.selectedFloorCode()) {
      return;
    }

    this.selectedFloorCode.set(floorCode);

    const firstArea = [
      ...(this.floors().find((floor) => floor.code === floorCode)?.areas ?? []),
    ].sort((a, b) => a.displayOrder - b.displayOrder)[0];

    this.selectArea(firstArea?.code ?? '');
  }

  selectArea(areaCode: string): void {
    const normalizedAreaCode = areaCode.trim();

    if (normalizedAreaCode === this.selectedAreaCode() && this.areaState()) {
      return;
    }

    this.selectedAreaCode.set(normalizedAreaCode);
    this.areaState.set(null);
    this.errorMessage.set('');

    if (!normalizedAreaCode) {
      this.loadingArea.set(false);
      this.realtime.clearArea();
      return;
    }

    this.loadSelectedArea();
  }

  refreshArea(): void {
    if (this.selectedAreaCode()) {
      this.loadSelectedArea();
    }
  }

  sendCommand(device: AreaDevice, on: boolean): void {
    if (
      !this.canControl ||
      !device.controllable ||
      this.isPending(device.code) ||
      !this.areaState()?.plcEnabled ||
      !this.areaState()?.connected
    ) {
      return;
    }

    this.setPending(device.code, true);
    this.errorMessage.set('');

    this.areaApi
      .sendDeviceCommand(device.code, on)
      .pipe(finalize(() => this.setPending(device.code, false)))
      .subscribe({
        next: (state) => {
          if (state.areaCode === this.selectedAreaCode()) {
            this.areaState.set(state);
          }
        },
        error: () => {
          this.errorMessage.set(`No fue posible ${on ? 'encender' : 'apagar'} ${device.name}.`);
        },
      });
  }

  isPending(deviceCode: string): boolean {
    return this.pendingDevices().has(deviceCode);
  }

  commandDisabled(device: AreaDevice): boolean {
    const state = this.areaState();

    return (
      !this.canControl ||
      !device.controllable ||
      this.isPending(device.code) ||
      !state?.plcEnabled ||
      !state?.connected
    );
  }

  connectionLabel(): string {
    const labels: Record<RealtimeConnectionStatus, string> = {
      DISCONNECTED: 'Tiempo real desconectado',
      CONNECTING: 'Conectando tiempo real...',
      CONNECTED: 'Tiempo real conectado',
      RECONNECTING: 'Reconectando tiempo real...',
      UNAUTHORIZED: 'Sesión no disponible',
      ERROR: 'Error de conexión en tiempo real',
    };

    return labels[this.connectionStatus()];
  }

  deviceIcon(device: AreaDevice): string {
    const icons: Record<string, string> = {
      LIGHT: 'pi pi-lightbulb',
      MINISPLIT: 'pi pi-sparkles',
      MOTION: 'pi pi-eye',
      SMOKE: 'pi pi-exclamation-triangle',
    };

    return icons[device.type] ?? 'pi pi-box';
  }

  deviceTypeLabel(device: AreaDevice): string {
    const labels: Record<string, string> = {
      LIGHT: 'Iluminación',
      MINISPLIT: 'Climatización',
      MOTION: 'Movimiento',
      SMOKE: 'Humo',
    };

    return labels[device.type] ?? device.type;
  }

  deviceStateLabel(device: AreaDevice): string {
    if (device.state === null) {
      return 'Sin información';
    }

    if (device.type === 'MOTION') {
      return device.state ? 'Movimiento detectado' : 'Sin movimiento';
    }

    if (device.type === 'SMOKE') {
      return device.state ? 'Alarma de humo activa' : 'Estado normal';
    }

    return device.state ? 'Encendido' : 'Apagado';
  }

  deviceStateClass(device: AreaDevice): string {
    if (device.fault === true) {
      return 'fault';
    }

    if (device.state === null) {
      return 'unavailable';
    }

    return device.state ? 'active' : 'inactive';
  }

  private loadBuilding(): void {
    this.loadingBuilding.set(true);
    this.errorMessage.set('');

    this.systemApi
      .getBuilding()
      .pipe(finalize(() => this.loadingBuilding.set(false)))
      .subscribe({
        next: (building) => {
          this.building.set(building);

          const firstFloor = [...building.floors].sort(
            (a, b) => a.displayOrder - b.displayOrder,
          )[0];
          const firstArea = [...(firstFloor?.areas ?? [])].sort(
            (a, b) => a.displayOrder - b.displayOrder,
          )[0];

          this.selectedFloorCode.set(firstFloor?.code ?? '');
          this.selectArea(firstArea?.code ?? '');
        },
        error: () => {
          this.errorMessage.set('No fue posible cargar los pisos y oficinas del edificio.');
        },
      });
  }

  private loadSelectedArea(): void {
    const areaCode = this.selectedAreaCode();

    if (!areaCode) {
      return;
    }

    this.loadingArea.set(true);
    this.realtime.watchArea(areaCode);

    this.areaApi
      .getAreaState(areaCode)
      .pipe(
        finalize(() => {
          if (areaCode === this.selectedAreaCode()) {
            this.loadingArea.set(false);
          }
        }),
      )
      .subscribe({
        next: (state) => {
          if (state.areaCode === this.selectedAreaCode()) {
            this.areaState.set(state);
          }
        },
        error: () => {
          if (areaCode === this.selectedAreaCode()) {
            this.errorMessage.set('No fue posible consultar el estado de esta oficina.');
          }
        },
      });
  }

  private setPending(deviceCode: string, pending: boolean): void {
    const devices = new Set(this.pendingDevices());

    if (pending) {
      devices.add(deviceCode);
    } else {
      devices.delete(deviceCode);
    }

    this.pendingDevices.set(devices);
  }
}
