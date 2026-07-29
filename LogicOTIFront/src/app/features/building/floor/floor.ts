import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
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

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
    this.route.paramMap
      .pipe(takeUntil(this.destroy$))
      .subscribe((params) => {
        const levelId =
          params.get('floorId')?.toUpperCase() ?? '';

        this.level = LEVEL_REGISTRY[levelId] ?? null;
      });
  }

  onAreaSelected(areaCode: string): void {
    if (!this.level || !areaCode) {
      return;
    }

    void this.router.navigate([
      '/building',
      'floor',
      this.level.id,
      'area',
      areaCode,
    ]);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}