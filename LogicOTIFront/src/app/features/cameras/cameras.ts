import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs';

import { CameraFloorCode, CameraItem } from '../../core/models/camera.model';
import { CameraApiService } from '../../core/services/camera-api.service';

type CameraFloorFilter = '' | CameraFloorCode;

@Component({
  selector: 'app-cameras',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './cameras.html',
  styleUrl: './cameras.scss',
})
export class Cameras implements OnInit {
  private readonly cameraApi = inject(CameraApiService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly route = inject(ActivatedRoute);

  readonly loading = signal(false);
  readonly errorMessage = signal('');
  readonly cameras = signal<CameraItem[]>([]);
  readonly total = signal(0);
  readonly playbackConfigured = signal(false);
  readonly lastUpdate = signal('');

  readonly searchText = signal('');
  readonly floorFilter = signal<CameraFloorFilter>('');
  readonly selectedCamera = signal<CameraItem | null>(null);
  readonly safeViewUrl = signal<SafeResourceUrl | null>(null);

  readonly floorOptions: ReadonlyArray<{
    value: CameraFloorFilter;
    label: string;
  }> = [
    { value: '', label: 'Todas las ubicaciones' },
    { value: 'PB', label: 'Planta Baja' },
    { value: 'P1', label: 'Piso 1' },
    { value: 'P2', label: 'Piso 2' },
    { value: 'EXT', label: 'Exteriores' },
  ];

  readonly filteredCameras = computed(() => {
    const search = this.searchText().trim().toLowerCase();
    const floor = this.floorFilter();

    return this.cameras().filter((camera) => {
      const matchesFloor = !floor || camera.floorCode === floor;
      const matchesSearch =
        !search ||
        camera.name.toLowerCase().includes(search) ||
        camera.code.toLowerCase().includes(search) ||
        camera.streamKey.toLowerCase().includes(search) ||
        camera.sourceName.toLowerCase().includes(search) ||
        camera.areaCode?.toLowerCase().includes(search);

      return matchesFloor && Boolean(matchesSearch);
    });
  });

  readonly availableCameras = computed(
    () => this.cameras().filter((camera) => camera.videoAvailable).length,
  );

  readonly interiorCameras = computed(
    () => this.cameras().filter((camera) => camera.floorCode !== 'EXT').length,
  );

  readonly exteriorCameras = computed(
    () => this.cameras().filter((camera) => camera.floorCode === 'EXT').length,
  );

  ngOnInit(): void {
    this.loadCameras();
  }

  refresh(): void {
    this.loadCameras();
  }

  clearFilters(): void {
    this.searchText.set('');
    this.floorFilter.set('');
  }

  selectCamera(camera: CameraItem): void {
    this.selectedCamera.set(camera);
    this.safeViewUrl.set(this.createSafeViewUrl(camera));
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

  private loadCameras(): void {
    const selectedCode = this.selectedCamera()?.code;
    const requestedCode =
      this.route.snapshot.queryParamMap.get('camera')?.trim().toUpperCase() ?? '';

    this.loading.set(true);
    this.errorMessage.set('');

    this.cameraApi
      .getCameras()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.cameras.set(response.items);
          this.total.set(response.total);
          this.playbackConfigured.set(response.playbackConfigured);
          this.lastUpdate.set(response.timestamp);

          const nextSelection =
            response.items.find((camera) => camera.code === requestedCode) ??
            response.items.find((camera) => camera.code === selectedCode) ??
            response.items[0] ??
            null;

          if (nextSelection) {
            this.selectCamera(nextSelection);
          } else {
            this.selectedCamera.set(null);
            this.safeViewUrl.set(null);
          }
        },
        error: () => {
          this.errorMessage.set('No fue posible cargar el catálogo de cámaras.');
        },
      });
  }

  private createSafeViewUrl(camera: CameraItem): SafeResourceUrl | null {
    if (!camera.videoAvailable || !camera.viewUrl) {
      return null;
    }

    if (!camera.viewUrl.startsWith('http://') && !camera.viewUrl.startsWith('https://')) {
      return null;
    }

    return this.sanitizer.bypassSecurityTrustResourceUrl(camera.viewUrl);
  }
}
