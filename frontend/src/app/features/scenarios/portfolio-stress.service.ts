
import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface PortfolioPositionImpact {
  assetSymbol: string;
  quantity: number;
  lastTradePrice: number | null;
  quotedMidPrice: number | null;
  normalMarketValue: number;
  quotedMarketValue: number | null;
  valuationDifference: number | null;
}

export interface PortfolioStressResponse {
  currency: string;
  normalTotalValue: number;
  quotedTotalValue: number | null;
  valuationDifference: number | null;
  valuationDifferencePercent: number | null;
  quoteCoverageComplete: boolean;
  positions: PortfolioPositionImpact[];
  generatedAt: string;
}

@Injectable({
  providedIn: 'root'
})
export class PortfolioStressService {

  private readonly http = inject(HttpClient);

  private readonly api =
    'http://localhost:8081/api/portfolio/me/stress';

  getMyStress(): Observable<PortfolioStressResponse> {
    return this.http.get<PortfolioStressResponse>(
      this.api,
      { withCredentials: true }
    );
  }
}
