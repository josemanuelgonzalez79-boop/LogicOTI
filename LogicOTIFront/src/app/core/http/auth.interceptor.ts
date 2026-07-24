import {
  HttpInterceptorFn,
} from '@angular/common/http';
import { inject } from '@angular/core';

import { AuthService } from '../services/auth.service';
import { API_ENDPOINTS } from './api.endpoints';

export const authInterceptor: HttpInterceptorFn = (
  request,
  next,
) => {
  const authService = inject(AuthService);

  if (request.url.includes(API_ENDPOINTS.auth.login)) {
    return next(request);
  }

  const token = authService.getToken();

  if (!token) {
    return next(request);
  }

  const authenticatedRequest = request.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`,
    },
  });

  return next(authenticatedRequest);
};