import { Component, input } from '@angular/core';

@Component({
  selector: 'app-card',
  standalone: true,
  templateUrl: './app-card.html',
  styleUrl: './app-card.scss'
})
export class AppCard {
  title = input<string>();
  subtitle = input<string>();
}