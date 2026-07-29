import {
  Component,
  OnDestroy,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import {
  EMPTY,
  Subject,
  catchError,
  switchMap,
  takeUntil,
} from 'rxjs';

import { AreaState } from '../../../core/models/area-state.model';
import { AreaApiService } from '../../../core/services/area-api.service';

@Component({
  selector: 'app-area',
  standalone: true,
  imports: [],
  templateUrl: './area.html',
  styleUrl: './area.scss',
})
export class Area implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly areaApi = inject(AreaApiService);

  private readonly destroy$ = new Subject<void>();

  readonly areaState = signal<AreaState | null>(null);
  readonly loading = signal(true);
  readonly errorMessage = signal('');

  ngOnInit(): void {
    this.route.paramMap
      .pipe(
        takeUntil(this.destroy$),

        switchMap((params) => {
          const areaCode = params.get('areaId')?.trim() ?? '';

          this.loading.set(true);
          this.errorMessage.set('');
          this.areaState.set(null);

          if (!areaCode) {
            this.loading.set(false);
            this.errorMessage.set(
              'No se recibió el código del área.',
            );

            return EMPTY;
          }

          return this.areaApi.getAreaState(areaCode).pipe(
            catchError((error) => {
              console.error(
                'Error obteniendo el estado del área:',
                error,
              );

              this.loading.set(false);
              this.errorMessage.set(
                'No fue posible obtener el estado del área.',
              );

              return EMPTY;
            }),
          );
        }),
      )
      .subscribe((state) => {
        this.areaState.set(state);
        this.loading.set(false);
        this.errorMessage.set('');

        console.log('Estado del área:', state);
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}