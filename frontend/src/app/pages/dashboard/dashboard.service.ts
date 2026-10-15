import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { Observable } from 'rxjs';

import { DashboardResponse } from './dashboard.models';


@Injectable({
  providedIn: 'root'
})
export class DashboardService {

  private readonly apiUrl =
    'http://localhost:8081/api/dashboard';


  constructor(
    private readonly http: HttpClient
  ) {}


  // Tout le tableau de bord en un seul appel.

  getDashboard(): Observable<DashboardResponse> {

    return this.http.get<DashboardResponse>(
      `${this.apiUrl}/me`,
      {
        withCredentials: true
      }
    );
  }
}
