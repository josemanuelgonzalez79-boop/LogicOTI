import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../http/api.endpoints';
import { ActiveAlarmList, AlarmActivity } from '../models/history.model';

@Injectable({ providedIn: 'root' })
export class AlarmApiService {
  private readonly http = inject(HttpClient);

  getActiveAlarms(): Observable<ActiveAlarmList> {
    return this.http.get<ActiveAlarmList>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.alarms.active}`,
    );
  }

  getActivity(eventId: number): Observable<AlarmActivity> {
    return this.http.get<AlarmActivity>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.alarms.activity(eventId)}`,
    );
  }

  acknowledge(eventId: number, comment: string | null): Observable<AlarmActivity> {
    return this.http.post<AlarmActivity>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.alarms.acknowledgement(eventId)}`,
      { comment },
    );
  }

  addComment(eventId: number, comment: string): Observable<AlarmActivity> {
    return this.http.post<AlarmActivity>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.alarms.comments(eventId)}`,
      { comment },
    );
  }
}
