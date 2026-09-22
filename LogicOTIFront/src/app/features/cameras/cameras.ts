import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs';

import {
  CameraFloorCode,
  CameraItem,
  CameraRecordingSegment,
} from '../../core/models/camera.model';
import { CameraApiService } from '../../core/services/camera-api.service';

type CameraFloorFilter = '' | CameraFloorCode;
type ViewerMode = 'live' | 'history';

@Component({
  selector: 'app-cameras',
  standalone: true,
  imports: [FormsModule, DatePipe],
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
  readonly historyConfigured = signal(false);
  readonly lastUpdate = signal('');

  readonly searchText = signal('');
  readonly floorFilter = signal<CameraFloorFilter>('');
  readonly selectedCamera = signal<CameraItem | null>(null);
  readonly safeViewUrl = signal<SafeResourceUrl | null>(null);
  readonly viewerMode = signal<ViewerMode>('live');
  readonly historyDate = signal('');
  readonly historyStartTime = signal('');
  readonly historyEndTime = signal('');
  readonly historyLoading = signal(false);
  readonly historyError = signal('');
  readonly recordingSegments = signal<CameraRecordingSegment[]>([]);
  readonly historySearched = signal(false);
  readonly historyPlaybackConfigured = signal(false);
  readonly historyPlaybackLoadingSequence = signal<number | null>(null);
  readonly historyPlaybackError = signal('');
  readonly historyPlaybackUrl = signal<SafeResourceUrl | null>(null);
  readonly historyPlaybackExpiresAt = signal('');
  readonly historyPlaybackSegment = signal<CameraRecordingSegment | null>(null);
  readonly historyPlaybackSeekSeconds = signal(0);
  private historyPlaybackRequestId = 0;

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
    this.setDefaultHistoryRange();
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
    const changed = this.selectedCamera()?.code !== camera.code;
    this.selectedCamera.set(camera);
    this.safeViewUrl.set(this.createSafeViewUrl(camera));
    if (changed) {
      this.clearHistoryResults();
    }
  }

  selectViewerMode(mode: ViewerMode): void {
    if (mode === 'history' && !this.historyConfigured()) {
      return;
    }
    this.viewerMode.set(mode);
  }

  searchHistory(): void {
    const camera = this.selectedCamera();
    const date = this.historyDate();
    const start = this.historyStartTime();
    const end = this.historyEndTime();

    this.historyError.set('');
    this.closeHistoryPlayback();
    this.recordingSegments.set([]);
    this.historySearched.set(false);

    if (!camera || !date || !start || !end) {
      this.historyError.set('Selecciona una cámara, fecha, hora inicial y hora final.');
      return;
    }

    if (end <= start) {
      this.historyError.set('La hora final debe ser posterior a la hora inicial.');
      return;
    }

    this.historyLoading.set(true);
    this.cameraApi
      .searchRecordings(camera.code, {
        startTime: `${date}T${start}:00`,
        endTime: `${date}T${end}:00`,
      })
      .pipe(finalize(() => this.historyLoading.set(false)))
      .subscribe({
        next: (response) => {
          this.recordingSegments.set(response.items);
          this.historyPlaybackConfigured.set(response.playbackConfigured);
          this.historySearched.set(true);
        },
        error: (error: HttpErrorResponse) => {
          this.historyError.set(
            error.error?.detail ?? 'No fue posible consultar las grabaciones del NVR.',
          );
        },
      });
  }

  playRecording(segment: CameraRecordingSegment, seekSeconds = 0): void {
    const camera = this.selectedCamera();
    if (
      !camera ||
      !this.historyPlaybackConfigured() ||
      this.historyPlaybackLoadingSequence() !== null
    ) {
      return;
    }

    const seconds = Math.max(0, Math.min(Math.floor(seekSeconds), this.lastSeekSecond(segment)));
    const requestId = ++this.historyPlaybackRequestId;
    this.historyPlaybackError.set('');
    this.historyPlaybackLoadingSequence.set(segment.sequence);

    this.cameraApi
      .startRecordingPlayback(camera.code, {
        startTime: this.segmentTimeAt(segment, seconds),
        endTime: segment.endTime,
      })
      .pipe(
        finalize(() => {
          if (requestId === this.historyPlaybackRequestId) {
            this.historyPlaybackLoadingSequence.set(null);
          }
        }),
      )
      .subscribe({
        next: (response) => {
          if (requestId !== this.historyPlaybackRequestId) {
            return;
          }
          const safeUrl = this.createSafeHistoryUrl(response.viewUrl);
          if (!safeUrl) {
            this.historyPlaybackError.set(
              'El servidor devolvió una dirección de reproducción inválida.',
            );
            return;
          }

          this.historyPlaybackUrl.set(safeUrl);
          this.historyPlaybackExpiresAt.set(response.expiresAt);
          this.historyPlaybackSegment.set(segment);
          this.historyPlaybackSeekSeconds.set(seconds);
        },
        error: (error: HttpErrorResponse) => {
          if (requestId !== this.historyPlaybackRequestId) {
            return;
          }
          this.historyPlaybackError.set(
            error.error?.detail ?? 'No fue posible iniciar la reproducción histórica.',
          );
        },
      });
  }

  closeHistoryPlayback(): void {
    this.historyPlaybackRequestId++;
    this.historyPlaybackUrl.set(null);
    this.historyPlaybackExpiresAt.set('');
    this.historyPlaybackSegment.set(null);
    this.historyPlaybackSeekSeconds.set(0);
    this.historyPlaybackLoadingSequence.set(null);
    this.historyPlaybackError.set('');
  }

  seekRecording(): void {
    const segment = this.historyPlaybackSegment();
    if (segment) {
      this.playRecording(segment, this.historyPlaybackSeekSeconds());
    }
  }

  jumpRecording(seconds: number): void {
    const segment = this.historyPlaybackSegment();
    if (segment) {
      this.playRecording(segment, this.historyPlaybackSeekSeconds() + seconds);
    }
  }

  lastSeekSecond(segment: CameraRecordingSegment): number {
    return Math.max(0, this.segmentSeconds(segment) - 1);
  }

  seekClockTime(segment: CameraRecordingSegment): string {
    return this.segmentTimeAt(segment, this.historyPlaybackSeekSeconds()).slice(11);
  }

  recordingDuration(segment: CameraRecordingSegment): string {
    const milliseconds =
      new Date(segment.endTime).getTime() - new Date(segment.startTime).getTime();
    const totalMinutes = Math.max(0, Math.round(milliseconds / 60_000));
    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    return hours > 0 ? `${hours} h ${minutes} min` : `${minutes} min`;
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
          this.historyConfigured.set(response.historyConfigured);
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

  private createSafeHistoryUrl(viewUrl: string): SafeResourceUrl | null {
    if (!viewUrl.startsWith('http://') && !viewUrl.startsWith('https://')) {
      return null;
    }

    const separator = viewUrl.includes('?') ? '&' : '?';
    return this.sanitizer.bypassSecurityTrustResourceUrl(
      `${viewUrl}${separator}controls=true&autoplay=true&muted=true&playsInline=true`,
    );
  }

  private setDefaultHistoryRange(): void {
    const now = new Date();
    const start = new Date(now.getTime() - 60 * 60 * 1000);
    const sameDay = start.toDateString() === now.toDateString();
    this.historyDate.set(this.localDate(now));
    this.historyStartTime.set(sameDay ? this.localTime(start) : '00:00');
    this.historyEndTime.set(this.localTime(now));
  }

  private localDate(value: Date): string {
    const year = value.getFullYear();
    const month = String(value.getMonth() + 1).padStart(2, '0');
    const day = String(value.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private localTime(value: Date): string {
    return `${String(value.getHours()).padStart(2, '0')}:${String(value.getMinutes()).padStart(2, '0')}`;
  }

  private segmentSeconds(segment: CameraRecordingSegment): number {
    return Math.max(
      0,
      Math.floor((Date.parse(`${segment.endTime}Z`) - Date.parse(`${segment.startTime}Z`)) / 1000),
    );
  }

  private segmentTimeAt(segment: CameraRecordingSegment, seconds: number): string {
    return new Date(Date.parse(`${segment.startTime}Z`) + seconds * 1000)
      .toISOString()
      .slice(0, 19);
  }

  private clearHistoryResults(): void {
    this.historyError.set('');
    this.recordingSegments.set([]);
    this.historySearched.set(false);
    this.historyPlaybackConfigured.set(false);
    this.closeHistoryPlayback();
  }
}
