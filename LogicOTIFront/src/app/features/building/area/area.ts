import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { Control } from '../../control/control';

@Component({
  selector: 'app-area',
  standalone: true,
  imports: [Control, RouterLink],
  templateUrl: './area.html',
  styleUrl: './area.scss',
})
export class Area {
  private readonly route = inject(ActivatedRoute);

  readonly floorCode = this.route.snapshot.paramMap.get('floorId')?.toUpperCase() ?? '';
  readonly areaCode = this.route.snapshot.paramMap.get('areaId')?.toUpperCase() ?? '';
}
