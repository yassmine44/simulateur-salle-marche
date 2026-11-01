import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { Observable } from 'rxjs';

import {
  GamificationCandle,
  GamificationEventKey,
  GamificationOrderRequest,
  GamificationPortfolio
} from '../models/gamification.models';


@Injectable({
  providedIn: 'root'
})
export class GamificationService {

  private readonly apiUrl =
    'http://localhost:8081/api/gamification';


  constructor(
    private readonly http: HttpClient
  ) {}


  getHistoricalData(
    event: GamificationEventKey
  ): Observable<GamificationCandle[]> {

    return this.http.get<GamificationCandle[]>(
      `${this.apiUrl}/data/${event}`,
      {
        withCredentials: true
      }
    );
  }


  startChallenge(
    event: GamificationEventKey
  ): Observable<GamificationPortfolio> {

    return this.http.post<GamificationPortfolio>(
      `${this.apiUrl}/sessions/${event}/start`,
      {},
      {
        withCredentials: true
      }
    );
  }


  getSession(
    event: GamificationEventKey
  ): Observable<GamificationPortfolio> {

    return this.http.get<GamificationPortfolio>(
      `${this.apiUrl}/sessions/${event}`,
      {
        withCredentials: true
      }
    );
  }


  advanceWeek(
    event: GamificationEventKey
  ): Observable<GamificationPortfolio> {

    return this.http.post<GamificationPortfolio>(
      `${this.apiUrl}/sessions/${event}/advance`,
      {},
      {
        withCredentials: true
      }
    );
  }


  placeOrder(
    event: GamificationEventKey,
    request: GamificationOrderRequest
  ): Observable<GamificationPortfolio> {

    return this.http.post<GamificationPortfolio>(
      `${this.apiUrl}/sessions/${event}/orders`,
      request,
      {
        withCredentials: true
      }
    );
  }
}
