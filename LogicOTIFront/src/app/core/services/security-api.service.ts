import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../http/api.endpoints';
import {
  AreaInactivityStatus,
  AutomaticLightingStatus,
  SecurityActionResponse,
  SecurityPrecheck,
  SecuritySettings,
  SecuritySettingsUpdateRequest,
  SecurityStatus,
  SecurityZoneActionResponse,
  SecurityZoneList,
  SecurityZoneSelectionRequest,
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

  getZones(): Observable<SecurityZoneList> {
    return this.http.get<SecurityZoneList>(`${this.baseUrl}${API_ENDPOINTS.security.zones}`);
  }

  getZonePrecheck(zoneCodes: string[]): Observable<SecurityPrecheck> {
    return this.http.post<SecurityPrecheck>(
      `${this.baseUrl}${API_ENDPOINTS.security.zonePrecheck}`,
      { zoneCodes } satisfies SecurityZoneSelectionRequest,
    );
  }

  armZones(zoneCodes: string[]): Observable<SecurityZoneActionResponse> {
    return this.http.post<SecurityZoneActionResponse>(
      `${this.baseUrl}${API_ENDPOINTS.security.armZones}`,
      { zoneCodes } satisfies SecurityZoneSelectionRequest,
    );
  }

  disarmZones(zoneCodes: string[]): Observable<SecurityZoneActionResponse> {
    return this.http.post<SecurityZoneActionResponse>(
      `${this.baseUrl}${API_ENDPOINTS.security.disarmZones}`,
      { zoneCodes } satisfies SecurityZoneSelectionRequest,
    );
  }

  acknowledgeZone(zoneCode: string): Observable<SecurityZoneList> {
    return this.http.post<SecurityZoneList>(
      `${this.baseUrl}${API_ENDPOINTS.security.acknowledgeZone(zoneCode)}`,
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

  getAutomaticLightingStatus(): Observable<AutomaticLightingStatus> {
    return this.http.get<AutomaticLightingStatus>(
      `${this.baseUrl}${API_ENDPOINTS.security.automaticLightingStatus}`,
    );
  }

  getAreaInactivityStatus(): Observable<AreaInactivityStatus> {
    return this.http.get<AreaInactivityStatus>(
      `${this.baseUrl}${API_ENDPOINTS.security.areaInactivityStatus}`,
    );
  }
}
