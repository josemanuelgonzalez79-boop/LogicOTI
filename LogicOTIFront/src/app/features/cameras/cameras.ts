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
  CameraPtzDirection,
  CameraRecordingSegment,
} from '../../core/models/camera.model';
import { CameraApiService } from '../../core/services/camera-api.service';
import { AuthService } from '../../core/services/auth.service';

type CameraFloorFilter = '' | CameraFloorCode;
type ViewerMode = 'live' | 'history';
type TimelineKind = 'motion' | 'recorded' | 'gap';

interface TimelineSlice {
  kind: TimelineKind;
  percent: number;
}

@Component({
  selector: 'app-cameras',
  standalone: true,
  imports: [FormsModule, DatePipe],
  templateUrl: './cameras.html',
  styleUrl: './cameras.scss',
})
export class Cameras implements OnInit {
  private readonly cameraApi = inject(CameraApiService);
  private readonly authService = inject(AuthService);
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
  readonly canControlPtz = ['ADMIN', 'OPERATOR'].includes(
    this.authService.getSession()?.user.role ?? '',
  );
  readonly ptzMoving = signal(false);
  readonly ptzError = signal('');
  readonly historyDate = signal('');
  readonly historyStartTime = signal('');
  readonly historyEndTime = signal('');
  readonly historyLoading = signal(false);
  readonly historyError = signal('');
  readonly recordingSegments = signal<CameraRecordingSegment[]>([]);
  readonly historySearched = signal(false);
  readonly historySearchRange = signal<{ startTime: string; endTime: string } | null>(null);
  readonly historyTimelinePositionSeconds = signal(0);
  readonly historyTimelineError = signal('');
  readonly historyPlaybackConfigured = signal(false);
  readonly historyPlaybackLoadingSequence = signal<number | null>(null);
  readonly historyPlaybackError = signal('');
  readonly historyPlaybackUrl = signal<SafeResourceUrl | null>(null);
  readonly historyPlaybackExpiresAt = signal('');
  readonly historyPlaybackSegment = signal<CameraRecordingSegment | null>(null);
  readonly historyPlaybackSeekSeconds = signal(0);
  private historyPlaybackRequestId = 0;
  private historySearchRequestId = 0;

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

  readonly historyTimelineDurationSeconds = computed(() => {
    const range = this.historySearchRange();
    return range ? this.secondsBetween(range.startTime, range.endTime) : 0;
  });

  readonly historyTimelineSlices = computed<TimelineSlice[]>(() => {
    const range = this.historySearchRange();
    const duration = this.historyTimelineDurationSeconds();
    if (!range || duration <= 0) {
      return [];
    }

    const start = Date.parse(`${range.startTime}Z`);
    const intervals = this.recordingSegments()
      .map((segment) => ({
        start: Math.max(0, (Date.parse(`${segment.startTime}Z`) - start) / 1000),
        end: Math.min(duration, (Date.parse(`${segment.endTime}Z`) - start) / 1000),
        kind: this.isMotionRecording(segment) ? ('motion' as const) : ('recorded' as const),
      }))
      .filter(
        (item) => Number.isFinite(item.start) && Number.isFinite(item.end) && item.start < item.end,
      );
    const boundaries = [
      ...new Set([0, duration, ...intervals.flatMap((interval) => [interval.start, interval.end])]),
    ].sort((a, b) => a - b);
    const slices: { kind: TimelineKind; seconds: number }[] = [];

    for (let index = 1; index < boundaries.length; index++) {
      const from = boundaries[index - 1];
      const to = boundaries[index];
      const covering = intervals.filter((item) => item.start < to && item.end > from);
      const kind: TimelineKind = covering.some((item) => item.kind === 'motion')
        ? 'motion'
        : covering.length > 0
          ? 'recorded'
          : 'gap';
      const previous = slices.at(-1);
      if (previous?.kind === kind) {
        previous.seconds += to - from;
      } else {
        slices.push({ kind, seconds: to - from });
      }
    }

    return slices.map((slice) => ({ kind: slice.kind, percent: (slice.seconds / duration) * 100 }));
  });

  readonly historyHasMotion = computed(() =>
    this.historyTimelineSlices().some((slice) => slice.kind === 'motion'),
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
    this.ptzError.set('');
    if (changed) {
      this.clearHistoryResults();
    }
  }

  selectViewerMode(mode: ViewerMode): void {
    if (mode === 'history' && !this.historyConfigured()) {
      return;
    }
    this.viewerMode.set(mode);
    this.ptzError.set('');
  }

  movePtz(direction: CameraPtzDirection): void {
    const camera = this.selectedCamera();
    if (
      !camera?.ptzAvailable ||
      !this.canControlPtz ||
      this.ptzMoving() ||
      this.viewerMode() !== 'live'
    ) {
      return;
    }

    this.ptzError.set('');
    this.ptzMoving.set(true);
    this.cameraApi
      .movePtz(camera.code, direction)
      .pipe(finalize(() => this.ptzMoving.set(false)))
      .subscribe({
        error: (error: HttpErrorResponse) => {
          if (this.selectedCamera()?.code === camera.code) {
            this.ptzError.set(
              error.error?.detail ??
                'No fue posible completar el movimiento PTZ. Comprueba la cámara.',
            );
          }
        },
      });
  }

  searchHistory(): void {
    const camera = this.selectedCamera();
    const date = this.historyDate();
    const start = this.historyStartTime();
    const end = this.historyEndTime();
    const requestId = ++this.historySearchRequestId;

    this.historyError.set('');
    this.historyLoading.set(false);
    this.historyTimelineError.set('');
    this.historySearchRange.set(null);
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
      .pipe(
        finalize(() => {
          if (requestId === this.historySearchRequestId) {
            this.historyLoading.set(false);
          }
        }),
      )
      .subscribe({
        next: (response) => {
          if (requestId !== this.historySearchRequestId) {
            return;
          }
          this.recordingSegments.set(response.items);
          this.historySearchRange.set({
            startTime: response.requestedStartTime,
            endTime: response.requestedEndTime,
          });
          this.historyTimelinePositionSeconds.set(0);
          this.historyPlaybackConfigured.set(response.playbackConfigured);
          this.historySearched.set(true);
        },
        error: (error: HttpErrorResponse) => {
          if (requestId !== this.historySearchRequestId) {
            return;
          }
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
          const range = this.historySearchRange();
          if (range) {
            this.historyTimelinePositionSeconds.set(
              this.secondsBetween(range.startTime, segment.startTime) + seconds,
            );
          }
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

  seekTimeline(): void {
    const range = this.historySearchRange();
    if (!range || this.historyPlaybackLoadingSequence() !== null) {
      return;
    }

    const offset = Math.max(
      0,
      Math.min(this.historyTimelinePositionSeconds(), this.historyTimelineDurationSeconds() - 1),
    );
    const time = new Date(Date.parse(`${range.startTime}Z`) + offset * 1000)
      .toISOString()
      .slice(0, 19);
    const segment = this.recordingSegments().find(
      (item) => item.startTime <= time && item.endTime > time,
    );
    if (!segment) {
      this.historyTimelineError.set('El NVR no devolvió una grabación para esa hora.');
      return;
    }

    this.historyTimelineError.set('');
    this.playRecording(segment, this.secondsBetween(segment.startTime, time));
  }

  timelineClockTime(): string {
    const range = this.historySearchRange();
    return range
      ? new Date(Date.parse(`${range.startTime}Z`) + this.historyTimelinePositionSeconds() * 1000)
          .toISOString()
          .slice(11, 19)
      : '';
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
    return this.secondsBetween(segment.startTime, segment.endTime);
  }

  private secondsBetween(startTime: string, endTime: string): number {
    return Math.max(
      0,
      Math.floor((Date.parse(`${endTime}Z`) - Date.parse(`${startTime}Z`)) / 1000) || 0,
    );
  }

  private isMotionRecording(segment: CameraRecordingSegment): boolean {
    const type = segment.recordingType.toLowerCase();
    return type.includes('motion') || type.includes('vmd');
  }

  private segmentTimeAt(segment: CameraRecordingSegment, seconds: number): string {
    return new Date(Date.parse(`${segment.startTime}Z`) + seconds * 1000)
      .toISOString()
      .slice(0, 19);
  }

  private clearHistoryResults(): void {
    this.historySearchRequestId++;
    this.historyLoading.set(false);
    this.historyError.set('');
    this.recordingSegments.set([]);
    this.historySearched.set(false);
    this.historySearchRange.set(null);
    this.historyTimelinePositionSeconds.set(0);
    this.historyTimelineError.set('');
    this.historyPlaybackConfigured.set(false);
    this.closeHistoryPlayback();
  }
}
