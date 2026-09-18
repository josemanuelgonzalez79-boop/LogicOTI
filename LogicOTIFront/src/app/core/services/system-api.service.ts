import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../http/api.endpoints';
import { AreaState } from '../models/area-state.model';
import { Building } from '../models/building.model';
import { DeviceSummary } from '../models/device-summary.model';
import { SystemStatus } from '../models/system-status.model';

@Injectable({
  providedIn: 'root',
})
export class SystemApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  /**
   * Estado general del sistema
   */
  getStatus(): Observable<SystemStatus> {
    return this.http.get<SystemStatus>(`${this.baseUrl}/system/status`);
  }

  /**
   * Obtiene la estructura completa del edificio
   */
  getBuilding(): Observable<Building> {
    return this.http.get<Building>(`${this.baseUrl}/building`);
  }

  /**
   * Cantidad de luces y minisplits confirmados como encendidos por el PLC.
   */
  getDeviceSummary(): Observable<DeviceSummary> {
    return this.http.get<DeviceSummary>(`${this.baseUrl}${API_ENDPOINTS.building.deviceSummary}`);
  }

  /**
   * Obtiene el estado actual de un área
   */
  getAreaState(areaCode: string): Observable<AreaState> {
    return this.http.get<AreaState>(`${this.baseUrl}/areas/${encodeURIComponent(areaCode)}/state`);
  }

  /**
   * Envía un comando de encendido o apagado a un dispositivo.
   *
   * El backend devuelve nuevamente el estado completo del área,
   * por lo que el frontend puede actualizar toda la pantalla con
   * la respuesta recibida.
   */
  sendDeviceCommand(deviceCode: string, on: boolean): Observable<AreaState> {
    return this.http.put<AreaState>(
      `${this.baseUrl}/devices/${encodeURIComponent(deviceCode)}/command`,
      {
        on,
      },
    );
  }
}
