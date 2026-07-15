import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MessageService } from 'primeng/api';
import { catchError, throwError } from 'rxjs';

export const apiErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const messages = inject(MessageService);

  return next(request).pipe(
    catchError((error: HttpErrorResponse) => {
      const detail = error.error?.detail ?? error.error?.message ?? error.message;

      messages.add({
        severity: 'error',
        summary: 'No fue posible completar la solicitud',
        detail: typeof detail === 'string' ? detail : 'Ocurrió un error inesperado.',
      });

      return throwError(() => error);
    }),
  );
};
