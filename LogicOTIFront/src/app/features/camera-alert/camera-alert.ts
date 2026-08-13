import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, timer } from 'rxjs';

import { CameraAlertView, CameraFloorCode } from '../../core/models/camera.model';
import { AuthService } from '../../core/services/auth.service';
import { CameraApiService } from '../../core/services/camera-api.service';

@Component({
  selector: 'app-camera-alert',
  standalone: true,
  templateUrl: './camera-alert.html',
  styleUrl: './camera-alert.scss',
})
export class CameraAlert implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly authService = inject(AuthService);
  private readonly cameraApi = inject(CameraApiService);

  readonly loading = signal(true);
  readonly errorMessage = signal('');
  readonly alert = signal<CameraAlertView | null>(null);
  readonly safeViewUrl = signal<SafeResourceUrl | null>(null);

  readonly expirationLabel = computed(() => {
    const expiresAt = this.alert()?.expiresAt;

    if (!expiresAt) {
      return '';
    }

    return new Intl.DateTimeFormat('es-MX', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    }).format(new Date(expiresAt));
  });

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token')?.trim();

    if (!token) {
      this.loading.set(false);
      this.errorMessage.set(
        'Este aviso no contiene un enlace válido. Espera una nueva alerta de movimiento.',
      );
      return;
    }

    this.cameraApi
      .getCameraAlert(token)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.alert.set(response);
          this.safeViewUrl.set(this.createSafeViewUrl(response.viewUrl));
          this.closeWhenExpired(response.expiresAt);
        },
        error: (error: HttpErrorResponse) => {
          const detail = error.error?.detail;
          this.errorMessage.set(
            typeof detail === 'string'
              ? detail
              : 'No fue posible abrir la cámara relacionada con este aviso.',
          );
        },
      });
  }

  floorLabel(floorCode: CameraFloorCode): string {
    const labels: Record<CameraFloorCode, string> = {
      PB: 'Planta Baja',
      P1: 'Piso 1',
      P2: 'Piso 2',
      EXT: 'Exterior',
    };

    return labels[floorCode];
  }

  openFullApplication(): void {
    const cameraCode = this.alert()?.cameraCode;
    const returnUrl = cameraCode
      ? `/cameras?camera=${encodeURIComponent(cameraCode)}`
      : '/security';

    if (this.authService.isAuthenticated()) {
      void this.router.navigateByUrl(returnUrl);
      return;
    }

    void this.router.navigate(['/login'], {
      queryParams: { returnUrl },
    });
  }

  private createSafeViewUrl(viewUrl: string): SafeResourceUrl | null {
    if (!viewUrl.startsWith('https://') && !viewUrl.startsWith('http://')) {
      this.errorMessage.set('La dirección de video configurada no es válida.');
      return null;
    }

    return this.sanitizer.bypassSecurityTrustResourceUrl(viewUrl);
  }

  private closeWhenExpired(expiresAt: string): void {
    const expirationTime = new Date(expiresAt).getTime();
    const delay = expirationTime - Date.now();

    if (!Number.isFinite(expirationTime) || delay <= 0) {
      this.expireView();
      return;
    }

    timer(delay)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.expireView());
  }

  private expireView(): void {
    this.safeViewUrl.set(null);
    this.errorMessage.set('Este acceso temporal ya venció. Espera una nueva alerta de movimiento.');
  }
}
