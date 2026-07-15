import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { SystemStatus } from '../models/system-status.model';

@Injectable({ providedIn: 'root' })
export class SystemApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  getStatus(): Observable<SystemStatus> {
    return this.http.get<SystemStatus>(`${this.baseUrl}/system/status`);
  }
}
