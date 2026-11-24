import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

import {
  ActuarialMetadata,
  ChainLadderResult,
  ComparisonResponse,
  PolicyProfile,
  QuoteHistoryItem,
  QuoteRequest,
  QuoteResponse,
  ReportName
} from './actuarial.models';

/** Accès à l'API du module d'actuariat (Spring Boot, /api/actuarial). */
@Injectable({ providedIn: 'root' })
export class ActuarialService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8081/api/actuarial';
  private readonly options = { withCredentials: true };

  /** Les métadonnées ne changent qu'avec un réentraînement : une requête par session. */
  private metadata$?: Observable<ActuarialMetadata>;
  private readonly reports = new Map<ReportName, Observable<unknown>>();

  metadata(): Observable<ActuarialMetadata> {
    this.metadata$ ??= this.http
      .get<ActuarialMetadata>(`${this.baseUrl}/metadata`, this.options)
      .pipe(shareReplay({ bufferSize: 1, refCount: false }));
    return this.metadata$;
  }

  quote(request: QuoteRequest): Observable<QuoteResponse> {
    return this.http.post<QuoteResponse>(`${this.baseUrl}/quotes`, request, this.options);
  }

  history(limit = 10): Observable<QuoteHistoryItem[]> {
    const params = new HttpParams().set('limit', limit);
    return this.http.get<QuoteHistoryItem[]>(`${this.baseUrl}/quotes/me`, { ...this.options, params });
  }

  compare(profileA: PolicyProfile, profileB: PolicyProfile): Observable<ComparisonResponse> {
    return this.http.post<ComparisonResponse>(
      `${this.baseUrl}/comparisons`,
      { profileA, profileB },
      this.options
    );
  }

  /** Provisions Chain-Ladder et erreurs de Mack d'un triangle saisi par l'utilisateur. */
  chainLadder(
    triangle: Array<Array<number | null>>,
    confidenceLevel = 0.95,
    origins?: number[]
  ): Observable<ChainLadderResult> {
    return this.http.post<ChainLadderResult>(
      `${this.baseUrl}/reserving/chain-ladder`,
      { triangle, confidenceLevel, ...(origins ? { origins } : {}) },
      this.options
    );
  }

  report<T>(name: ReportName): Observable<T> {
    if (!this.reports.has(name)) {
      this.reports.set(
        name,
        this.http
          .get<T>(`${this.baseUrl}/reports/${name}`, this.options)
          .pipe(shareReplay({ bufferSize: 1, refCount: false }))
      );
    }
    return this.reports.get(name) as Observable<T>;
  }
}
