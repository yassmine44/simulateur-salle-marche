import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ScenarioHistoryResponse,
  ScenarioReportResponse
} from './scenario-history.model';

@Injectable({ providedIn: 'root' })
export class ScenarioHistoryService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8081/api/admin/scenarios/history';

  getHistory(page = 0, size = 10): Observable<ScenarioHistoryResponse> {
    const params = new HttpParams()
      .set('page', String(page))
      .set('size', String(size));

    return this.http.get<ScenarioHistoryResponse>(this.baseUrl, {
      params,
      withCredentials: true
    });
  }

  getReport(id: number): Observable<ScenarioReportResponse> {
    return this.http.get<ScenarioReportResponse>(
      `${this.baseUrl}/${encodeURIComponent(String(id))}/report`,
      { withCredentials: true }
    );
  }
}
