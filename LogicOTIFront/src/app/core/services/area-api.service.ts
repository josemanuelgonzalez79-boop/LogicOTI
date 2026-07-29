import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../http/api.endpoints';
import { AreaState } from '../models/area-state.model';

@Injectable({
  providedIn: 'root',
})
export class AreaApiService {
  private readonly http = inject(HttpClient);

  getAreaState(areaCode: string): Observable<AreaState> {
    return this.http.get<AreaState>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.areas.state(areaCode)}`,
    );
  }

  sendDeviceCommand(
    deviceCode: string,
    on: boolean,
  ): Observable<AreaState> {
    return this.http.put<AreaState>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.devices.command(deviceCode)}`,
      { on },
    );
  }
}