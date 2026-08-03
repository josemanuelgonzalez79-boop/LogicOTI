import { Component, DestroyRef, OnDestroy, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MessageService } from 'primeng/api';
import { DrawerModule } from 'primeng/drawer';
import { ToastModule } from 'primeng/toast';

import { SensorEventHistoryItem } from '../../core/models/history.model';
import { SmokeAlertStateService } from '../../core/services/smoke-alert-state.service';
import { Navbar } from '../navbar/navbar';
import { Sidebar } from '../sidebar/sidebar';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [Navbar, Sidebar, RouterLink, RouterOutlet, DrawerModule, ToastModule],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  private readonly messages = inject(MessageService);
  private readonly smokeAlerts = inject(SmokeAlertStateService);

  mobileMenuVisible = false;
  readonly activeAlarmCount = this.smokeAlerts.activeCount;

  ngOnInit(): void {
    this.smokeAlerts.notifications$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => this.showSmokeNotification(event));

    this.smokeAlerts.start();
  }

  ngOnDestroy(): void {
    this.messages.clear('global-smoke-alert');
    void this.smokeAlerts.stop();
  }

  openMobileMenu(): void {
    this.mobileMenuVisible = true;
  }

  closeMobileMenu(): void {
    this.mobileMenuVisible = false;
  }

  private showSmokeNotification(event: SensorEventHistoryItem): void {
    this.messages.add({
      key: 'global-smoke-alert',
      severity: event.currentState ? 'error' : 'success',
      summary: event.currentState ? 'Alarma de humo activada' : 'Sensor de humo restablecido',
      detail: `${event.areaName}: ${event.message}`,
      sticky: false,
      life: event.currentState ? 15000 : 6000,
      closable: true,
    });
  }
}
