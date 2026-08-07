import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, RouterOutlet } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { MessageService } from 'primeng/api';
import { DrawerModule } from 'primeng/drawer';
import { ToastModule } from 'primeng/toast';

import { SensorEventHistoryItem } from '../../core/models/history.model';
import { CameraItem } from '../../core/models/camera.model';
import { MotionAlarmAlert } from '../../core/models/motion-alarm.model';
import { MotionAlarmRealtimeService } from '../../core/services/motion-alarm-realtime.service';
import { SmokeAlertStateService } from '../../core/services/smoke-alert-state.service';
import { Navbar } from '../navbar/navbar';
import { Sidebar } from '../sidebar/sidebar';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [DatePipe, Navbar, Sidebar, RouterLink, RouterOutlet, DrawerModule, ToastModule],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell implements OnInit, OnDestroy {
  private readonly destroyRef = inject(DestroyRef);
  private readonly messages = inject(MessageService);
  private readonly smokeAlerts = inject(SmokeAlertStateService);
  private readonly motionAlerts = inject(MotionAlarmRealtimeService);
  private readonly sanitizer = inject(DomSanitizer);

  mobileMenuVisible = false;
  readonly activeAlarmCount = this.smokeAlerts.activeCount;
  readonly motionAlert = signal<MotionAlarmAlert | null>(null);
  readonly motionCamera = signal<CameraItem | null>(null);
  readonly safeMotionCameraUrl = signal<SafeResourceUrl | null>(null);

  ngOnInit(): void {
    this.smokeAlerts.notifications$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => this.showSmokeNotification(event));

    this.motionAlerts.alerts$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((alert) => this.showMotionAlarm(alert));

    this.smokeAlerts.start();
    this.motionAlerts.connect();
  }

  ngOnDestroy(): void {
    this.messages.clear('global-smoke-alert');
    this.messages.clear('global-motion-alert');
    void this.smokeAlerts.stop();
    void this.motionAlerts.disconnect();
  }

  openMobileMenu(): void {
    this.mobileMenuVisible = true;
  }

  closeMobileMenu(): void {
    this.mobileMenuVisible = false;
  }

  closeMotionAlarm(): void {
    this.motionAlert.set(null);
    this.motionCamera.set(null);
    this.safeMotionCameraUrl.set(null);
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

  private showMotionAlarm(alert: MotionAlarmAlert): void {
    const camera =
      alert.cameras.find((item) => item.videoAvailable && item.viewUrl) ??
      alert.cameras[0] ??
      null;

    this.motionAlert.set(alert);
    this.motionCamera.set(camera);
    this.safeMotionCameraUrl.set(
      camera?.videoAvailable && camera.viewUrl
        ? this.sanitizer.bypassSecurityTrustResourceUrl(camera.viewUrl)
        : null,
    );

    this.messages.add({
      key: 'global-motion-alert',
      severity: 'error',
      summary: 'Alarma de movimiento activada',
      detail: `${alert.event.areaName}: ${alert.event.message}`,
      sticky: true,
      closable: true,
    });
  }
}
