import { Component, input } from '@angular/core';
import { ProgressSpinnerModule } from 'primeng/progressspinner';

@Component({
  selector: 'app-loading',
  standalone: true,
  imports: [ProgressSpinnerModule],
  templateUrl: './loading.html',
  styleUrl: './loading.scss'
})
export class Loading {
  message = input<string>('Cargando información...');
}