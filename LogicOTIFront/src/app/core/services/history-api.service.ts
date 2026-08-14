import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { API_ENDPOINTS } from '../http/api.endpoints';
import {
  CommandHistoryFilters,
  CommandHistoryItem,
  ExecutiveMonthlyReport,
  HistoryPage,
  SensorEventHistoryFilters,
  SensorEventHistoryItem,
} from '../models/history.model';

@Injectable({ providedIn: 'root' })
export class HistoryApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  getCommandHistory(filters: CommandHistoryFilters): Observable<HistoryPage<CommandHistoryItem>> {
    return this.http.get<HistoryPage<CommandHistoryItem>>(
      `${this.baseUrl}${API_ENDPOINTS.history.commands}`,
      { params: this.createParams(filters) },
    );
  }

  getSensorEventHistory(
    filters: SensorEventHistoryFilters,
  ): Observable<HistoryPage<SensorEventHistoryItem>> {
    return this.http.get<HistoryPage<SensorEventHistoryItem>>(
      `${this.baseUrl}${API_ENDPOINTS.history.events}`,
      { params: this.createParams(filters) },
    );
  }

  getExecutiveMonthlyReport(month: string): Observable<ExecutiveMonthlyReport> {
    return this.http.get<ExecutiveMonthlyReport>(
      `${this.baseUrl}${API_ENDPOINTS.history.executiveMonthlyReport}`,
      { params: new HttpParams().set('month', month) },
    );
  }

  private createParams(values: object): HttpParams {
    let params = new HttpParams();

    Object.entries(values).forEach(([name, value]) => {
      if (value === undefined || value === null || value === '') {
        return;
      }

      params = params.set(name, String(value));
    });

    return params;
  }
}
