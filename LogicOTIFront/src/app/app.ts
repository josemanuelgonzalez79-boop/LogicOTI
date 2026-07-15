import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { ToolbarModule } from 'primeng/toolbar';

@Component({
  selector: 'app-root',
  imports: [ButtonModule, RouterLink, RouterOutlet, ToastModule, ToolbarModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected toggleDarkMode(): void {
    document.documentElement.classList.toggle('app-dark');
  }
}
