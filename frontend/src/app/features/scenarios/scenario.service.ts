
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type ScenarioType =
  | 'CRASH'
  | 'LIQUIDITY_CRISIS'
  | 'SELLING_PANIC'
  | 'BULL_RALLY'
  | 'FLASH_CRASH';

export interface StartScenarioRequest {
  type: ScenarioType;
  symbol: string;
  intensity: number;
  durationSeconds: number;
}

export interface ScenarioStatus {
  active: boolean;
  type: ScenarioType | null;
  symbol: string | null;
  intensity: number | null;
  startedAt: string | null;
  endsAt: string | null;
  remainingSeconds: number;
}

export interface ScenarioMarketSnapshot {
  symbol: string;
  referencePrice: number | null;
  quotedMidPrice: number | null;
  variationPercent: number | null;
  capturedAt: string;
}
export type MarketPhase = 'NORMAL' | 'CRASH' | 'RECOVERY';

export interface ScenarioRecoveryStatus {
  symbol: string;
  phase: MarketPhase;
  residualIntensity: number;
  remainingSeconds: number;
}
@Injectable({
  providedIn: 'root'
})
export class ScenarioService {

  private readonly http = inject(HttpClient);

  private readonly api =
    'http://localhost:8081/api/admin/scenarios';

  marketSnapshot(symbol: string): Observable<ScenarioMarketSnapshot> {
    return this.http.get<ScenarioMarketSnapshot>(
      `${this.api}/market/${encodeURIComponent(symbol)}`,
      { withCredentials: true }
    );
  }
  status(): Observable<ScenarioStatus> {
    return this.http.get<ScenarioStatus>(
      `${this.api}/status`,
      { withCredentials: true }
    );
  }

  start(
    request: StartScenarioRequest
  ): Observable<ScenarioStatus> {
    return this.http.post<ScenarioStatus>(
      `${this.api}/start`,
      request,
      { withCredentials: true }
    );
  }

  stop(): Observable<ScenarioStatus> {
    return this.http.post<ScenarioStatus>(
      `${this.api}/stop`,
      {},
      { withCredentials: true }
    );
  }
  recoveryStatus(
  symbol: string
): Observable<ScenarioRecoveryStatus> {

  return this.http.get<ScenarioRecoveryStatus>(
    `${this.api}/recovery/${encodeURIComponent(symbol)}`,
    { withCredentials: true }
  );
}
}
