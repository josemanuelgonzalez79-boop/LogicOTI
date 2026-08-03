import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../http/api.endpoints';
import { ActiveAlarmList } from '../models/history.model';

@Injectable({ providedIn: 'root' })
export class AlarmApiService {
  private readonly http = inject(HttpClient);

  getActiveAlarms(): Observable<ActiveAlarmList> {
    return this.http.get<ActiveAlarmList>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.alarms.active}`,
    );
  }
}
