import { Component, input } from '@angular/core';

@Component({
  selector: 'app-kpi-card',
  standalone: true,
  templateUrl: './kpi-card.html',
  styleUrl: './kpi-card.scss'
})
export class KpiCard {
  title = input.required<string>();
  value = input.required<string | number>();
  icon = input<string>();
  unit = input<string>();
  description = input<string>();
}