import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { Observable } from 'rxjs';

import {
  BuyingPowerResponse,
  HistoryDays,
  PerformanceResponse,
  PortfolioResponse,
  RebalanceExecutionResponse,
  RebalancePlanResponse,
  RebalanceTarget,
  StatementResponse
} from '../models/portfolio.models';


@Injectable({
  providedIn: 'root'
})
export class PortfolioService {

  private readonly apiUrl =
    'http://localhost:8081/api/portfolio';


  constructor(
    private readonly http: HttpClient
  ) {}


  // Liquidités, positions, résultats, répartition,
  // indicateurs de risque et historique.

  getPortfolio(
    historyDays: HistoryDays
  ): Observable<PortfolioResponse> {

    return this.http.get<PortfolioResponse>(
      `${this.apiUrl}/me`,
      {
        params: {
          historyDays
        },
        withCredentials: true
      }
    );
  }


  // Liquidités et titres disponibles pour un actif.

  getBuyingPower(
    symbol: string
  ): Observable<BuyingPowerResponse> {

    return this.http.get<BuyingPowerResponse>(
      `${this.apiUrl}/me/buying-power`,
      {
        params: {
          symbol: symbol.trim().toUpperCase()
        },
        withCredentials: true
      }
    );
  }


  // Repart du capital de départ ; l'historique est conservé.

  reset(): Observable<PortfolioResponse> {

    return this.http.post<PortfolioResponse>(
      `${this.apiUrl}/me/reset`,
      {},
      {
        withCredentials: true
      }
    );
  }


  // Statistiques, contribution par actif, qualité d'exécution
  // et comparaison à la référence.

  getPerformance(
    historyDays: HistoryDays
  ): Observable<PerformanceResponse> {

    return this.http.get<PerformanceResponse>(
      `${this.apiUrl}/me/performance`,
      {
        params: {
          historyDays
        },
        withCredentials: true
      }
    );
  }


  // Ordres nécessaires pour atteindre la cible (rien n'est envoyé).

  planRebalance(
    targets: RebalanceTarget[]
  ): Observable<RebalancePlanResponse> {

    return this.http.post<RebalancePlanResponse>(
      `${this.apiUrl}/me/rebalance/plan`,
      { targets },
      {
        withCredentials: true
      }
    );
  }


  executeRebalance(
    targets: RebalanceTarget[]
  ): Observable<RebalanceExecutionResponse> {

    return this.http.post<RebalanceExecutionResponse>(
      `${this.apiUrl}/me/rebalance/execute`,
      { targets },
      {
        withCredentials: true
      }
    );
  }


  // Dates au format AAAA-MM-JJ.

  getStatement(
    from: string,
    to: string
  ): Observable<StatementResponse> {

    return this.http.get<StatementResponse>(
      `${this.apiUrl}/me/statement`,
      {
        params: {
          from,
          to
        },
        withCredentials: true
      }
    );
  }
}
