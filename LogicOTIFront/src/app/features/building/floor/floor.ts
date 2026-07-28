import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subject, takeUntil } from 'rxjs';

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
  level: LevelDefinition | null = null;

  private readonly destroy$ = new Subject<void>();

  constructor(private readonly route: ActivatedRoute) {}

  ngOnInit(): void {
    this.route.paramMap
      .pipe(takeUntil(this.destroy$))
      .subscribe((params) => {
        const levelId = params.get('floorId')?.toUpperCase() ?? '';

        this.level = LEVEL_REGISTRY[levelId] ?? null;
      });
  }

  /**
   * Se ejecuta cuando el usuario selecciona un área del plano.
   * Por ahora solo registramos el código en consola.
   * Más adelante aquí navegaremos al detalle del área
   * o cargaremos su información desde la API.
   */
  onAreaSelected(areaCode: string): void {
    console.log('Área seleccionada:', areaCode);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}