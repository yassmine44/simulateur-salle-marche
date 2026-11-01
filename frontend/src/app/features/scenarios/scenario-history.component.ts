import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription, finalize } from 'rxjs';
import {
  ScenarioExecutionStatus,
  ScenarioHistoryItem,
  ScenarioHistoryResponse,
  ScenarioReportResponse,
  ScenarioMarketHistory
} from './scenario-history.model';
import { ScenarioHistoryService } from './scenario-history.service';

@Component({
  selector: 'app-scenario-history',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './scenario-history.component.html',
  styleUrl: './scenario-history.component.scss'
})
export class ScenarioHistoryComponent implements OnInit, OnDestroy {
  private readonly api = inject(ScenarioHistoryService);
  private listRequest?: Subscription;
  private detailRequest?: Subscription;
  private marketRequest?: Subscription;

  readonly pageSize = 10;
  page = 0;
  history: ScenarioHistoryResponse | null = null;
  selected: ScenarioHistoryItem | null = null;
  report: ScenarioReportResponse | null = null;
marketHistory: ScenarioMarketHistory | null = null;
marketLoading = false;
marketError = '';
  loading = false;
  reportLoading = false;
  listError = '';
  reportError = '';
  priceChartPoints = '';
priceChartMin: number | null = null;
priceChartMax: number | null = null;
priceChartCount = 0;
liquidityBidPoints = '';
liquidityAskPoints = '';
liquidityChartCount = 0;
liquidityChartMax = 0;
  

private buildPriceChart(market: ScenarioMarketHistory): void {
  this.priceChartPoints = '';
  this.priceChartMin = null;
  this.priceChartMax = null;
  this.priceChartCount = 0;

  const observations = market.during
    .filter(o =>
      o.midPrice !== null &&
      Number.isFinite(o.midPrice) &&
      o.midPrice > 0 &&
      Number.isFinite(Date.parse(o.observedAt))
    );

  if (observations.length === 0) {
    return;
  }

  const prices = observations.map(o => o.midPrice as number);
  const times = observations.map(o => Date.parse(o.observedAt));

  const minimum = Math.min(...prices);
  const maximum = Math.max(...prices);

  this.priceChartMin = minimum;
  this.priceChartMax = maximum;
  this.priceChartCount = prices.length;

  const padding = 25;
  const width = 800;
  const height = 240;

  const range = maximum - minimum;
  const verticalRange = range > 0 ? range : Math.max(maximum * 0.01, 0.01);

  const startTime = Math.min(...times);
  const endTime = Math.max(...times);
  const timeRange = endTime - startTime;

  this.priceChartPoints = prices.map((price, index) => {
    const x = timeRange > 0
      ? padding + ((times[index] - startTime) / timeRange) * (width - 2 * padding)
      : width / 2;

    const y = padding +
      ((maximum - price) / verticalRange) * (height - 2 * padding);

    return `${x.toFixed(2)},${y.toFixed(2)}`;
  }).join(' ');
}


  ngOnInit(): void {
    this.loadPage(0);
  }

  ngOnDestroy(): void {
    this.listRequest?.unsubscribe();
    this.detailRequest?.unsubscribe();
    this.marketRequest?.unsubscribe();
  }

  loadPage(page: number): void {
    if (page < 0) return;
    if (this.history && page >= this.history.totalPages && page !== 0) return;

    this.listRequest?.unsubscribe();
    this.detailRequest?.unsubscribe();
    this.marketRequest?.unsubscribe();
this.marketHistory = null;
this.marketError = '';
this.marketLoading = false;
    this.selected = null;
    this.report = null;
    this.reportError = '';
    this.listError = '';
    this.loading = true;
    this.liquidityBidPoints = '';
this.liquidityAskPoints = '';
this.liquidityChartCount = 0;
this.liquidityChartMax = 0;
    

    this.listRequest = this.api.getHistory(page, this.pageSize)
      .pipe(finalize(() => { this.loading = false; }))
      .subscribe({
        next: response => {
          this.history = response;
          this.page = response.page;
        },
        error: error => {
          this.history = null;
          this.listError = this.describeError('Chargement de l’historique', error);
        }
      });
  }


selectExecution(item: ScenarioHistoryItem): void {
  this.detailRequest?.unsubscribe();
  this.marketRequest?.unsubscribe();

  this.selected = item;
  this.report = null;
  this.marketHistory = null;
  this.priceChartPoints = '';
this.priceChartCount = 0;
this.priceChartMin = null;
this.priceChartMax = null;
this.liquidityBidPoints = '';
this.liquidityAskPoints = '';
this.liquidityChartCount = 0;
this.liquidityChartMax = 0;


  this.reportError = '';
  this.marketError = '';

  this.reportLoading = true;
  this.marketLoading = true;

  // Bilan d'exécution existant
  this.detailRequest = this.api.getReport(item.id)
    .pipe(finalize(() => {
      this.reportLoading = false;
    }))
    .subscribe({
      next: report => {
        this.report = report;
      },
      error: error => {
        this.reportError = this.describeError(
          'Chargement du bilan',
          error
        );
      }
    });

  // Observations financières historiques
  this.marketRequest = this.api.getMarketHistory(item.id)
    .pipe(finalize(() => {
      this.marketLoading = false;
    }))
    .subscribe({
next: market => {
  this.marketHistory = market;
  this.buildPriceChart(market);
  this.buildLiquidityChart(market);
},
      error: error => {
        this.marketError = this.describeError(
          'Chargement des mesures de marché',
          error
        );
      }
    });
}

private buildLiquidityChart(market: ScenarioMarketHistory): void {
  this.liquidityBidPoints = '';
  this.liquidityAskPoints = '';
  this.liquidityChartCount = 0;
  this.liquidityChartMax = 0;

  const observations = market.during
    .map(o => ({
      time: Date.parse(o.observedAt),
      bid: o.weightedBidDepth,
      ask: o.weightedAskDepth
    }))
    .filter(o =>
      Number.isFinite(o.time) &&
      (
        (o.bid !== null && Number.isFinite(o.bid) && o.bid >= 0) ||
        (o.ask !== null && Number.isFinite(o.ask) && o.ask >= 0)
      )
    )
    .sort((a, b) => a.time - b.time);

  if (observations.length < 2) return;

  const depths = observations.flatMap(o => [
    ...(o.bid !== null && Number.isFinite(o.bid) && o.bid >= 0 ? [o.bid] : []),
    ...(o.ask !== null && Number.isFinite(o.ask) && o.ask >= 0 ? [o.ask] : [])
  ]);

  const maxDepth = Math.max(1, ...depths);
  const start = observations[0].time;
  const end = observations[observations.length - 1].time;

  const width = 800;
  const height = 240;
  const padding = 25;

  const x = (time: number): number =>
    end > start
      ? padding + ((time - start) / (end - start)) * (width - 2 * padding)
      : width / 2;

  const y = (depth: number): number =>
    height - padding -
    (depth / maxDepth) * (height - 2 * padding);

  const toPoints = (side: 'bid' | 'ask'): string =>
    observations
      .filter(o =>
        o[side] !== null &&
        Number.isFinite(o[side]) &&
        o[side]! >= 0
      )
      .map(o =>
        `${x(o.time).toFixed(2)},${y(o[side]!).toFixed(2)}`
      )
      .join(' ');

  this.liquidityBidPoints = toPoints('bid');
  this.liquidityAskPoints = toPoints('ask');
  this.liquidityChartCount = observations.length;
  this.liquidityChartMax = maxDepth;
}



  get hasPrevious(): boolean {
    return !this.loading && this.page > 0;
  }

  get hasNext(): boolean {
    return !this.loading && !!this.history && this.page + 1 < this.history.totalPages;
  }

  statusLabel(status: ScenarioExecutionStatus): string {
    switch (status) {
      case 'RUNNING': return 'En cours';
      case 'STOPPED': return 'Arrêté';
      case 'EXPIRED': return 'Terminé';
      case 'INTERRUPTED': return 'Interrompu';
    }
  }

  trackById(_index: number, item: ScenarioHistoryItem): number {
    return item.id;
  }

  private describeError(action: string, error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401 || error.status === 403) {
        return `${action} : accès refusé. Vérifiez votre session et vos droits.`;
      }
      if (error.status === 404) {
        return `${action} : ressource introuvable (404).`;
      }
      if (error.status === 0) {
        return `${action} : backend injoignable.`;
      }
      return `${action} : erreur HTTP ${error.status}.`;
    }
    return `${action} : erreur inconnue.`;
  }
  
}
