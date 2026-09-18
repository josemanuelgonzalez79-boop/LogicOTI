import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, finalize, takeUntil } from 'rxjs';

import { BuildingFloor } from '../../../core/models/building.model';
import { SmokeAlertStateService } from '../../../core/services/smoke-alert-state.service';
import { SystemApiService } from '../../../core/services/system-api.service';
import { BuildingLayout } from '../building-layout/building-layout';
import { LevelDefinition } from './floor.models';
import { LEVEL_REGISTRY } from './floor.registry';

@Component({
  selector: 'app-floor',
  standalone: true,
  imports: [BuildingLayout],
  templateUrl: './floor.html',
  styleUrl: './floor.scss',
})
export class Floor implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly systemApi = inject(SystemApiService);
  private readonly smokeAlerts = inject(SmokeAlertStateService);
  private readonly destroy$ = new Subject<void>();

  readonly level = signal<LevelDefinition | null>(null);
  readonly floorData = signal<BuildingFloor | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal('');

  readonly areas = computed(() =>
    [...(this.floorData()?.areas ?? [])].sort((a, b) => a.displayOrder - b.displayOrder),
  );

  readonly registeredAreaCodes = computed(() => this.areas().map((area) => area.code));

  readonly activeSmokeAreaCodes = computed(() => {
    const floorCode = this.level()?.id;

    if (!floorCode) {
      return [];
    }

    return this.smokeAlerts
      .activeAlerts()
      .filter((alert) => alert.areaCode.startsWith(`${floorCode}_`))
      .map((alert) => alert.areaCode);
  });

  ngOnInit(): void {
    this.smokeAlerts.ensureConnected();

    this.route.paramMap.pipe(takeUntil(this.destroy$)).subscribe((params) => {
      const levelId = params.get('floorId')?.toUpperCase() ?? '';

      this.level.set(LEVEL_REGISTRY[levelId] ?? null);
      this.floorData.set(null);
      this.errorMessage.set('');

      if (!this.level()) {
        this.loading.set(false);
        return;
      }

      this.loadFloorData(levelId);
    });
  }

  onAreaSelected(areaCode: string): void {
    const currentLevel = this.level();

    if (!currentLevel || !areaCode) {
      return;
    }

    void this.router.navigate(['/building', 'floor', currentLevel.id, 'area', areaCode]);
  }

  isSmokeActive(areaCode: string): boolean {
    return this.activeSmokeAreaCodes().includes(areaCode);
  }

  areaSummary(areaCode: string): string {
    const area = this.areas().find((item) => item.code === areaCode);

    if (!area) {
      return 'Área registrada';
    }

    const sensors = area.inventory.motionSensors + area.inventory.smokeSensors;
    const controllable = area.inventory.lamps + area.inventory.minisplits;

    return `${sensors} sensores · ${controllable} equipos`;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private loadFloorData(levelId: string): void {
    this.loading.set(true);

    this.systemApi
      .getBuilding()
      .pipe(
        takeUntil(this.destroy$),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (building) => {
          this.floorData.set(building.floors.find((floor) => floor.code === levelId) ?? null);

          if (!this.floorData()) {
            this.errorMessage.set('El piso no está registrado en el catálogo del edificio.');
          }
        },
        error: () => {
          this.errorMessage.set('No fue posible cargar las áreas de este piso.');
        },
      });
  }
}
