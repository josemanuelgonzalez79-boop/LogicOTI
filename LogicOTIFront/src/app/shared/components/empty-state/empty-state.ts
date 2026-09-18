import { Component, input } from '@angular/core';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  templateUrl: './empty-state.html',
  styleUrl: './empty-state.scss'
})
export class EmptyState {
  title = input<string>('Sin información');
  message = input<string>('No hay datos disponibles para mostrar.');
  icon = input<string>('pi pi-inbox');
}