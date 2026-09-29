import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface HealthResponse {
  status: string;
}

@Injectable({
  providedIn: 'root'
})
export class HealthService {
  private readonly apiUrl = 'http://localhost:8081/api/health';

  constructor(private readonly http: HttpClient) {}

  checkHealth(): Observable<HealthResponse> {
    return this.http.get<HealthResponse>(this.apiUrl);
  }
}
