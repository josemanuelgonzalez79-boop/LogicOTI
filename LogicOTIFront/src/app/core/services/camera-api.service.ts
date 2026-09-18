import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../http/api.endpoints';
import {
  CameraAlertView,
  CameraFloorCode,
  CameraItem,
  CameraListResponse,
} from '../models/camera.model';

@Injectable({ providedIn: 'root' })
export class CameraApiService {
  private readonly http = inject(HttpClient);

  getCameras(floorCode?: CameraFloorCode, areaCode?: string): Observable<CameraListResponse> {
    let params = new HttpParams();

    if (floorCode) {
      params = params.set('floorCode', floorCode);
    }

    if (areaCode?.trim()) {
      params = params.set('areaCode', areaCode.trim().toUpperCase());
    }

    return this.http.get<CameraListResponse>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.cameras.list}`,
      { params },
    );
  }

  getCamera(cameraCode: string): Observable<CameraItem> {
    return this.http.get<CameraItem>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.cameras.detail(cameraCode)}`,
    );
  }

  getCameraAlert(token: string): Observable<CameraAlertView> {
    const params = new HttpParams().set('token', token);

    return this.http.get<CameraAlertView>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.cameras.alertView}`,
      { params },
    );
  }
}
