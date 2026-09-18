import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnDestroy,
  Output,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

@Component({
  selector: 'app-building-layout',
  standalone: true,
  imports: [],
  templateUrl: './building-layout.html',
  styleUrl: './building-layout.scss',
})
export class BuildingLayout implements OnDestroy {
  @Input() mapName = 'Mapa del nivel';

  private activeSmokeCodes = new Set<string>();
  private registeredCodes = new Set<string>();

  @Input()
  set activeSmokeAreaCodes(values: readonly string[]) {
    this.activeSmokeCodes = new Set((values ?? []).map((value) => value.trim()).filter(Boolean));
    this.scheduleAreaStateUpdate();
  }

  @Input()
  set registeredAreaCodes(values: readonly string[]) {
    this.registeredCodes = new Set((values ?? []).map((value) => value.trim()).filter(Boolean));
    this.scheduleAreaStateUpdate();
  }

  @Output()
  readonly areaSelected = new EventEmitter<string>();

  @ViewChild('svgContainer')
  private svgContainer?: ElementRef<HTMLDivElement>;

  readonly svgContent = signal<SafeHtml | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal('');

  private readonly sanitizer = inject(DomSanitizer);

  private currentMapPath = '';
  private abortController: AbortController | null = null;
  private animationFrameId: number | null = null;
  private requestId = 0;

  @Input({ required: true })
  set mapPath(value: string) {
    const normalizedPath = value?.trim() ?? '';

    if (normalizedPath === this.currentMapPath) {
      return;
    }

    this.currentMapPath = normalizedPath;
    void this.loadSvg(normalizedPath);
  }

  ngOnDestroy(): void {
    this.abortController?.abort();

    if (this.animationFrameId !== null) {
      cancelAnimationFrame(this.animationFrameId);
    }
  }

  onMapClick(event: MouseEvent): void {
    const target = event.target as Element | null;

    const areaElement = target?.closest<SVGElement>('[data-area-code]');

    const areaCode = areaElement?.getAttribute('data-area-code');

    if (!areaCode) {
      return;
    }

    this.areaSelected.emit(areaCode);
  }

  onMapKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Enter' && event.key !== ' ') {
      return;
    }

    const target = event.target as Element | null;
    const areaElement = target?.closest<SVGElement>('[data-area-code]');
    const areaCode = areaElement?.getAttribute('data-area-code');

    if (!areaCode) {
      return;
    }

    event.preventDefault();
    this.areaSelected.emit(areaCode);
  }

  private async loadSvg(mapPath: string): Promise<void> {
    this.abortController?.abort();

    const controller = new AbortController();
    const currentRequestId = ++this.requestId;

    this.abortController = controller;

    this.svgContent.set(null);
    this.errorMessage.set('');

    if (!mapPath) {
      this.loading.set(false);
      this.errorMessage.set('No hay un mapa configurado para este nivel.');
      return;
    }

    this.loading.set(true);

    try {
      const response = await fetch(mapPath, {
        method: 'GET',
        cache: 'no-cache',
        signal: controller.signal,
      });

      if (!response.ok) {
        throw new Error(`Error HTTP ${response.status} al cargar ${mapPath}`);
      }

      const svgText = await response.text();

      if (!/<svg[\s>]/i.test(svgText)) {
        throw new Error('El archivo recibido no contiene un SVG válido.');
      }

      if (currentRequestId !== this.requestId) {
        return;
      }

      this.svgContent.set(this.sanitizer.bypassSecurityTrustHtml(svgText));
      this.scheduleAreaStateUpdate();
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') {
        return;
      }

      console.error('Error al cargar el mapa SVG:', error);

      if (currentRequestId === this.requestId) {
        this.errorMessage.set('No fue posible cargar el mapa del nivel.');
      }
    } finally {
      if (currentRequestId === this.requestId) {
        this.loading.set(false);
      }
    }
  }

  private scheduleAreaStateUpdate(): void {
    if (this.animationFrameId !== null) {
      cancelAnimationFrame(this.animationFrameId);
    }

    this.animationFrameId = requestAnimationFrame(() => {
      this.animationFrameId = null;
      this.applyAreaStates();
    });
  }

  private applyAreaStates(): void {
    const container = this.svgContainer?.nativeElement;

    if (!container) {
      return;
    }

    container.querySelectorAll<SVGElement>('[data-area-code]').forEach((areaElement) => {
      const areaCode = areaElement.getAttribute('data-area-code') ?? '';
      const isRegistered = this.registeredCodes.size === 0 || this.registeredCodes.has(areaCode);

      areaElement.setAttribute(
        'data-status',
        this.activeSmokeCodes.has(areaCode) ? 'smoke' : isRegistered ? 'available' : 'unknown',
      );

      areaElement.setAttribute('role', 'button');
      areaElement.setAttribute('aria-label', `Abrir área ${areaCode}`);
    });
  }
}
