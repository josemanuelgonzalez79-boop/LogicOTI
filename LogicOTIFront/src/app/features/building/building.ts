import { Component } from '@angular/core';
import { Router } from '@angular/router';

import { LevelDefinition } from './floor/floor.models';
import { LEVEL_REGISTRY } from './floor/floor.registry';

@Component({
  selector: 'app-building',
  standalone: true,
  imports: [],
  templateUrl: './building.html',
  styleUrl: './building.scss',
})
export class Building {
  readonly levels: LevelDefinition[] = Object.values(
    LEVEL_REGISTRY,
  ).sort((a, b) => a.order - b.order);

  constructor(private readonly router: Router) {}

  openLevel(level: LevelDefinition): void {
    void this.router.navigate([
      '/building',
      'floor',
      level.id,
    ]);
  }
}