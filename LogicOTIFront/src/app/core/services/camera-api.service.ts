import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../http/api.endpoints';
import { CameraFloorCode, CameraItem, CameraListResponse } from '../models/camera.model';

@Injectable({ providedIn: 'root' })
export class CameraApiService {
  private readonly http = inject(HttpClient);

  getCameras(floorCode?: CameraFloorCode): Observable<CameraListResponse> {
    const params = floorCode ? new HttpParams().set('floorCode', floorCode) : undefined;

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
}
