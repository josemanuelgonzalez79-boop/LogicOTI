import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../http/api.endpoints';
import {
  SecurityActionResponse,
  SecurityPrecheck,
  SecuritySettings,
  SecuritySettingsUpdateRequest,
  SecurityStatus,
} from '../models/security.model';

@Injectable({ providedIn: 'root' })
export class SecurityApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  getStatus(): Observable<SecurityStatus> {
    return this.http.get<SecurityStatus>(`${this.baseUrl}${API_ENDPOINTS.security.status}`);
  }

  getPrecheck(): Observable<SecurityPrecheck> {
    return this.http.get<SecurityPrecheck>(`${this.baseUrl}${API_ENDPOINTS.security.precheck}`);
  }

  arm(): Observable<SecurityActionResponse> {
    return this.http.post<SecurityActionResponse>(
      `${this.baseUrl}${API_ENDPOINTS.security.arm}`,
      {},
    );
  }

  disarm(): Observable<SecurityActionResponse> {
    return this.http.post<SecurityActionResponse>(
      `${this.baseUrl}${API_ENDPOINTS.security.disarm}`,
      {},
    );
  }

  getSchedules(): Observable<SecuritySettings> {
    return this.http.get<SecuritySettings>(`${this.baseUrl}${API_ENDPOINTS.security.schedules}`);
  }

  updateSchedules(request: SecuritySettingsUpdateRequest): Observable<SecuritySettings> {
    return this.http.put<SecuritySettings>(
      `${this.baseUrl}${API_ENDPOINTS.security.schedules}`,
      request,
    );
  }
}
