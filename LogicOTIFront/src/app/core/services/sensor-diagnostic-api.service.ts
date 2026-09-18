import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../http/api.endpoints';
import {
  DiagnosticStatus,
  SecurityWarningListResponse,
  SensorBypass,
  SensorBypassListResponse,
  SensorDiagnostic,
  SensorDiagnosticDueResponse,
  SensorDiagnosticListResponse,
} from '../models/sensor-diagnostic.model';

@Injectable({ providedIn: 'root' })
export class SensorDiagnosticApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  getDueSensors(): Observable<SensorDiagnosticDueResponse> {
    return this.http.get<SensorDiagnosticDueResponse>(
      `${this.baseUrl}${API_ENDPOINTS.sensorDiagnostics.due}`,
    );
  }

  getDiagnostics(status?: DiagnosticStatus, limit = 20): Observable<SensorDiagnosticListResponse> {
    let params = new HttpParams().set('limit', limit);

    if (status) {
      params = params.set('status', status);
    }

    return this.http.get<SensorDiagnosticListResponse>(
      `${this.baseUrl}${API_ENDPOINTS.sensorDiagnostics.list}`,
      { params },
    );
  }

  startDiagnostic(sensorCodes: string[]): Observable<SensorDiagnostic> {
    return this.http.post<SensorDiagnostic>(
      `${this.baseUrl}${API_ENDPOINTS.sensorDiagnostics.list}`,
      { sensorCodes },
    );
  }

  cancelDiagnostic(id: number): Observable<SensorDiagnostic> {
    return this.http.post<SensorDiagnostic>(
      `${this.baseUrl}${API_ENDPOINTS.sensorDiagnostics.cancel(id)}`,
      {},
    );
  }

  getBypasses(): Observable<SensorBypassListResponse> {
    return this.http.get<SensorBypassListResponse>(
      `${this.baseUrl}${API_ENDPOINTS.security.bypasses}`,
    );
  }

  createBypass(sensorCode: string, reason: string): Observable<SensorBypass> {
    return this.http.post<SensorBypass>(`${this.baseUrl}${API_ENDPOINTS.security.bypasses}`, {
      sensorCode,
      reason,
    });
  }

  revokeBypass(sensorCode: string): Observable<SensorBypass> {
    return this.http.delete<SensorBypass>(
      `${this.baseUrl}${API_ENDPOINTS.security.bypass(sensorCode)}`,
    );
  }

  getWarnings(limit = 50): Observable<SecurityWarningListResponse> {
    return this.http.get<SecurityWarningListResponse>(
      `${this.baseUrl}${API_ENDPOINTS.security.warnings}`,
      { params: { limit } },
    );
  }
}
