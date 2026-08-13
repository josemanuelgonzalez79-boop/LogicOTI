import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';
import { API_ENDPOINTS } from './api.endpoints';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const isPublicRequest =
    request.url.includes(API_ENDPOINTS.auth.login) ||
    request.url.includes(API_ENDPOINTS.cameras.alertView);

  if (isPublicRequest) {
    return next(request);
  }

  const token = authService.getToken();
  const authenticatedRequest = token
    ? request.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`,
        },
      })
    : request;

  return next(authenticatedRequest).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        const returnUrl = router.url.split('?')[0] === '/login' ? null : router.url;

        authService.logout();

        void router.navigate(['/login'], {
          queryParams: returnUrl ? { returnUrl } : undefined,
        });
      }

      return throwError(() => error);
    }),
  );
};
