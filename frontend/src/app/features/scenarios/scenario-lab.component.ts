
import {
  Component,
  OnInit,
  OnDestroy,
  inject
} from '@angular/core';

import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import {
  Subscription,
  timer,
  forkJoin,
  switchMap,
  finalize,
  map
} from 'rxjs';

import {
  TradingService
} from '../trading/services/trading.service';

import {
  OrderBookMetricsResponse
} from '../trading/models/trading.models';

import {
  ScenarioService,
  ScenarioStatus,
  ScenarioMarketSnapshot,
  ScenarioRecoveryStatus
} from './scenario.service';
import {
  PortfolioStressService,
  PortfolioStressResponse
} from './portfolio-stress.service';

// ==========================================
// INTERFACES
// ==========================================

interface StressBaseline {
  market: ScenarioMarketSnapshot;
  book: OrderBookMetricsResponse;
  startedAt: string | null;
}

interface PricePoint {
  time: number;
  price: number;
}

// ==========================================
type LiquidityRecoveryPhase =
  | 'NOT_STARTED'
  | 'CRASH'
  | 'RECOVERING'
  | 'STABILIZING'
  | 'RECOVERED';

interface LiquidityRecoveryMetrics {
  phase: LiquidityRecoveryPhase;
  baselineSpread: number;
  currentSpread: number | null;
  spreadThreshold: number;
  baselineDepth: number;
  currentDepth: number | null;
  depthThreshold: number;
  stableSince: number | null;
  recoveredAt: number | null;
  recoveryTimeSeconds: number | null;
}
// COMPONENT
// ==========================================

@Component({
  selector: 'app-scenario-lab',
  standalone: true,

  imports: [
    CommonModule,
    FormsModule
  ],

  templateUrl: './scenario-lab.component.html',
  styleUrl: './scenario-lab.component.scss'
})
export class ScenarioLabComponent implements OnInit, OnDestroy {

  // ========================================
  // SERVICES
  // ========================================

private updateLiquidityRecovery(
  book: OrderBookMetricsResponse
): void {

  const metrics = this.liquidityRecovery;

  // Aucun scénario à analyser.
  if (
    !metrics ||
    !this.baseline ||
    this.recoveryStartedAt === null ||
    metrics.phase === 'RECOVERED'
  ) {
    return;
  }

  // Le choc n'est pas encore terminé.
  if (this.recovery?.phase === 'CRASH') {
    return;
  }

  // Vérifier que les données concernent
  // bien le même actif que la référence.
  if (
    book.assetSymbol !== this.baseline.book.assetSymbol ||
    book.assetSymbol !== this.symbol
  ) {
    return;
  }

  // Ignorer les mesures datant d'avant
  // le lancement du scénario.
  const bookTime = Date.parse(book.generatedAt);
  const startTime = Date.parse(
    this.baseline.startedAt ?? ''
  );

  if (
    !Number.isFinite(bookTime) ||
    !Number.isFinite(startTime) ||
    bookTime < startTime
  ) {
    return;
  }

  // Ne jamais compter deux fois
  // la même observation du carnet.
  if (
    book.generatedAt ===
    this.lastLiquidityBookTimestamp
  ) {
    return;
  }

  const now = Date.now();

  const spread = book.spread;

  const depth =
    book.totalBidVolume +
    book.totalAskVolume;

  if (
    spread == null ||
    !Number.isFinite(spread) ||
    spread < 0 ||
    !Number.isFinite(depth) ||
    depth < 0
  ) {
    metrics.stableSince = null;
    metrics.phase = 'RECOVERING';
    return;
  }

  // Si les observations sont trop éloignées,
  // on ne peut pas confirmer une stabilité
  // continue de 10 secondes.
  if (
    this.lastLiquidityObservationAt !== null &&
    now - this.lastLiquidityObservationAt > 6000
  ) {
    metrics.stableSince = null;
  }

  this.lastLiquidityObservationAt = now;
  this.lastLiquidityBookTimestamp = book.generatedAt;

  // Mettre à jour les dernières mesures.
  metrics.currentSpread = spread;
  metrics.currentDepth = depth;

  // =====================================
  // CONDITIONS FINANCIÈRES
  // =====================================

  const spreadRecovered =
    spread <= metrics.spreadThreshold;

  const depthRecovered =
    depth >= metrics.depthThreshold;

  const liquidityAcceptable =
    spreadRecovered && depthRecovered;

  // =====================================
  // CONDITIONS NON SATISFAITES
  // =====================================

  if (!liquidityAcceptable) {

    metrics.stableSince = null;
    metrics.phase = 'RECOVERING';

    return;
  }

  // =====================================
  // DÉBUT DE LA STABILISATION
  // =====================================

  if (metrics.stableSince === null) {

    metrics.stableSince = now;
    metrics.phase = 'STABILIZING';

    return;
  }

  const stableDuration =
    now - metrics.stableSince;

  // =====================================
  // STABILITÉ EN COURS
  // =====================================

  if (
    stableDuration <
    this.stabilityDurationMs
  ) {

    metrics.phase = 'STABILIZING';

    return;
  }

  // =====================================
  // LIQUIDITÉ RÉTABLIE
  // =====================================

  metrics.phase = 'RECOVERED';

  metrics.recoveredAt = now;

  metrics.recoveryTimeSeconds =
    Math.round(
      (now - this.recoveryStartedAt) / 1000
    );
}

  private readonly scenarioService =
    inject(ScenarioService);
  private readonly portfolioStressService =
  inject(PortfolioStressService);

  private readonly tradingService =
    inject(TradingService);

  private readonly subscriptions =
    new Subscription();
    

  // ========================================
  // CONFIGURATION
  // ========================================

  readonly assets = [
    'AAPL',
    'MSFT',
    'NVDA',
    'AMZN'
  ];

  symbol = 'AAPL';
  intensity = 0.5;
  durationSeconds = 120;

  // ========================================
  // MARKET STATE
  // ========================================

  status: ScenarioStatus | null = null;

  market: ScenarioMarketSnapshot | null = null;

  orderBookMetrics: OrderBookMetricsResponse | null = null;

  recovery: ScenarioRecoveryStatus | null = null;

  portfolioStress: PortfolioStressResponse | null = null;

portfolioStressError = '';

private portfolioStressPending = false;

  // ========================================
  // HISTORICAL DATA
  // ========================================

  priceHistory: PricePoint[] = [];

  baseline: StressBaseline | null = null;

  liquidityRecovery: LiquidityRecoveryMetrics | null = null;
  private readonly spreadTolerance = 1.10;
  private readonly depthTolerance = 0.90;
  private readonly stabilityDurationMs = 10_000;
  private recoveryStartedAt: number | null = null;

  private lastLiquidityObservationAt: number | null = null;

private lastLiquidityBookTimestamp: string | null = null;

  // ========================================
  // LOADING STATES
  // ========================================

  busy = false;

  statusPending = false;
  marketPending = false;

  private orderBookMetricsPending = false;
  private recoveryPending = false;

  // ========================================
  // ERRORS
  // ========================================

  statusError = '';
  marketError = '';
  orderBookMetricsError = '';
  recoveryError = '';
  actionError = '';

  // ========================================
  // LIFECYCLE
  // ========================================


ngOnInit(): void {

  // Actualisation des données du scénario toutes les 2 s.
  this.subscriptions.add(
    timer(0, 2000).subscribe(() => {

      if (this.busy) {
        return;
      }

      this.refreshStatus();
      this.refreshMarket();
      this.refreshOrderBookMetrics();
      this.refreshRecovery();

    })
  );

  // Actualisation du portefeuille toutes les 5 s.
  this.subscriptions.add(
    timer(0, 5000).subscribe(() => {
      this.refreshPortfolioStress();
    })
  );

}


  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  // ========================================
  // SYMBOL CHANGE
  // ========================================

  private initializeLiquidityRecovery(): void {
    this.liquidityRecovery = null;
    this.recoveryStartedAt = null;
    this.lastLiquidityObservationAt = null;
this.lastLiquidityBookTimestamp = null;
    if (!this.baseline) return;

    const baselineSpread = this.baseline.book.spread;
    const baselineDepth = this.baseline.book.totalBidVolume + this.baseline.book.totalAskVolume;
    if (baselineSpread == null || baselineSpread <= 0 || !Number.isFinite(baselineSpread)
        || !Number.isFinite(baselineDepth) || baselineDepth <= 0) return;

    this.liquidityRecovery = {
      phase: 'CRASH',
      baselineSpread,
      currentSpread: null,
      spreadThreshold: baselineSpread * this.spreadTolerance,
      baselineDepth,
      currentDepth: null,
      depthThreshold: baselineDepth * this.depthTolerance,
      stableSince: null,
      recoveredAt: null,
      recoveryTimeSeconds: null
    };
  }
  onSymbolChange(): void {
    this.liquidityRecovery = null;
    this.recoveryStartedAt = null;

    this.baseline = null;

    this.market = null;
    this.orderBookMetrics = null;
    this.recovery = null;

    this.priceHistory = [];

    this.marketError = '';
    this.orderBookMetricsError = '';
    this.recoveryError = '';
    this.actionError = '';

    this.refreshMarket();
    this.refreshOrderBookMetrics();
    this.refreshRecovery();
  }

  // ========================================
  // SCENARIO STATUS
  // ========================================

  refreshStatus(): void {

    if (this.statusPending) {
      return;
    }

    this.statusPending = true;

    this.subscriptions.add(
      this.scenarioService.status()
        .pipe(
          finalize(() => {
            this.statusPending = false;
          })
        )
        .subscribe({

          next: response => {
            this.status = response;
            this.statusError = '';
          },

          error: error => {
            this.statusError = this.httpError(
              'Statut du scénario',
              error
            );
          }

        })
    );
  }

  // ========================================
  // MARKET SNAPSHOT
  // ========================================

  refreshMarket(): void {

    if (this.marketPending) {
      return;
    }

    const requestedSymbol = this.symbol;

    this.marketPending = true;

    this.subscriptions.add(
      this.scenarioService
        .marketSnapshot(requestedSymbol)
        .pipe(
          finalize(() => {
            this.marketPending = false;
          })
        )
        .subscribe({

          next: response => {

            if (requestedSymbol !== this.symbol) {
              return;
            }

            this.market = response;
            this.marketError = '';

            if (response.quotedMidPrice !== null) {

              this.priceHistory.push({
                time: Date.now(),
                price: response.quotedMidPrice
              });

              if (this.priceHistory.length > 180) {
                this.priceHistory.shift();
              }
            }
          },

          error: error => {

            if (requestedSymbol !== this.symbol) {
              return;
            }

            this.marketError = this.httpError(
              'Données de marché',
              error
            );
          }

        })
    );
  }

  // ========================================
  // ORDER BOOK METRICS
  // ========================================

  refreshOrderBookMetrics(): void {

    if (this.orderBookMetricsPending) {
      return;
    }

    const requestedSymbol = this.symbol;

    this.orderBookMetricsPending = true;

    this.subscriptions.add(
      this.tradingService
        .getOrderBookMetrics(requestedSymbol)
        .pipe(
          finalize(() => {
            this.orderBookMetricsPending = false;
          })
        )
        .subscribe({

          next: response => {

            if (requestedSymbol !== this.symbol) {
              return;
            }

            this.orderBookMetrics = response;
            this.orderBookMetricsError = '';
            this.updateLiquidityRecovery(response);
          },

          error: error => {

            if (requestedSymbol !== this.symbol) {
              return;
            }

            this.orderBookMetrics = null;
            // Une erreur de collecte interrompt
// la continuité des observations.

if (
  this.liquidityRecovery &&
  this.liquidityRecovery.phase === 'STABILIZING'
) {
  this.liquidityRecovery.stableSince = null;
  this.liquidityRecovery.phase = 'RECOVERING';
}

this.lastLiquidityObservationAt = null;

            this.orderBookMetricsError = this.httpError(
              'Carnet d’ordres',
              error
            );
          }

        })
    );
  }

  // ========================================
  // MARKET RECOVERY MONITOR
  // ========================================

  refreshRecovery(): void {

    if (this.recoveryPending) {
      return;
    }

    const requestedSymbol = this.symbol;

    this.recoveryPending = true;

    this.subscriptions.add(
      this.scenarioService
        .recoveryStatus(requestedSymbol)
        .pipe(
          finalize(() => {
            this.recoveryPending = false;
          })
        )
        .subscribe({

          next: response => {

            if (requestedSymbol !== this.symbol) {
              return;
            }

            this.recovery = response;
            this.recoveryError = '';
            // Début de la période de mesure LRT.
// La mesure démarre à la première détection
// de la fin du scénario.

if (
  this.liquidityRecovery &&
  this.recoveryStartedAt === null &&
  response.phase !== 'CRASH'
) {
  this.recoveryStartedAt = Date.now();

  this.liquidityRecovery.phase = 'RECOVERING';
}
          },

          error: error => {

            if (requestedSymbol !== this.symbol) {
              return;
            }

            this.recovery = null;

            this.recoveryError = this.httpError(
              'Recovery Monitor',
              error
            );
          }

        })
    );
  }

  // ========================================
  // START CRASH
  // ========================================

  startScenario(): void {

    if (this.busy || this.status?.active) {
      return;
    }

    const symbol = this.symbol;

    const request = {
      type: 'CRASH' as const,
      symbol,
      intensity: Number(this.intensity),
      durationSeconds: Number(this.durationSeconds)
    };

    this.busy = true;
    this.actionError = '';

    // Capture des données AVANT le lancement
    // pour mesurer ensuite l'impact du CRASH.

    this.subscriptions.add(
      forkJoin({

        market: this.scenarioService
          .marketSnapshot(symbol),

        book: this.tradingService
          .getOrderBookMetrics(symbol),

        status: this.scenarioService.status()

      })
        .pipe(

          switchMap(snapshot => {

            if (snapshot.status.active) {

              this.status = snapshot.status;

              throw new Error(
                'Un scénario est déjà actif. ' +
                'La référence avant crise ne peut pas être enregistrée.'
              );
            }

            if (
              snapshot.market.symbol !== symbol ||
              snapshot.book.assetSymbol !== symbol
            ) {
              throw new Error(
                'Les mesures reçues ne correspondent pas à l’actif sélectionné.'
              );
            }

            const candidate: StressBaseline = {
              market: { ...snapshot.market },
              book: { ...snapshot.book },
              startedAt: null
            };

            return this.scenarioService
              .start(request)
              .pipe(
                map(response => ({
                  response,
                  candidate
                }))
              );
          }),

          finalize(() => {
            this.busy = false;
          })

        )
        .subscribe({

          next: ({ response, candidate }) => {

            this.baseline = {
              ...candidate,
              startedAt: response.startedAt
            };

            this.initializeLiquidityRecovery();

            this.status = response;

            this.market = null;
            this.orderBookMetrics = null;

            this.refreshMarket();
            this.refreshOrderBookMetrics();
            this.refreshRecovery();
          },

          error: error => {

            this.actionError =
              error instanceof HttpErrorResponse
                ? this.httpError(
                    'Capture de référence / démarrage',
                    error
                  )
                : error instanceof Error
                  ? error.message
                  : 'Impossible de démarrer le scénario.';
          }

        })
    );
  }

  // ========================================
  // STOP CRASH
  // ========================================

  stopScenario(): void {

    if (this.busy || !this.status?.active) {
      return;
    }

    this.busy = true;
    this.actionError = '';

    this.subscriptions.add(
      this.scenarioService.stop()
        .pipe(
          finalize(() => {
            this.busy = false;
          })
        )
        .subscribe({

          next: response => {

            this.status = response;

            this.refreshMarket();
            this.refreshOrderBookMetrics();
            this.refreshRecovery();
          },

          error: error => {

            this.actionError = this.httpError(
              'Arrêt du scénario',
              error
            );
          }

        })
    );
  }

  // ========================================
  // STRESS IMPACT CALCULATIONS
  // ========================================

  private percentChange(
    before: number | null,
    current: number | null
  ): number | null {

    if (
      before == null ||
      current == null ||
      before <= 0
    ) {
      return null;
    }

    return ((current - before) / before) * 100;
  }

  private depth(
    book: OrderBookMetricsResponse | null
  ): number | null {

    if (book === null) {
      return null;
    }

    return (
      book.totalBidVolume +
      book.totalAskVolume
    );
  }

  // ========================================
  // STRESS IMPACT DASHBOARD
  // ========================================

  get impactRows() {

    const baseline = this.baseline;

    if (!baseline) {
      return [];
    }

    const started = Date.parse(
      baseline.startedAt ?? ''
    );

    const market =
      !this.marketError &&
      this.market?.symbol === baseline.market.symbol &&
      Date.parse(this.market.capturedAt) >= started
        ? this.market
        : null;

    const book =
      !this.orderBookMetricsError &&
      this.orderBookMetrics?.assetSymbol === baseline.book.assetSymbol &&
      Date.parse(this.orderBookMetrics.generatedAt) >= started
        ? this.orderBookMetrics
        : null;

    const beforeDepth = this.depth(
      baseline.book
    );

    const currentDepth = this.depth(
      book
    );

    const beforeRelative =
      baseline.book.relativeSpreadPercent;

    const currentRelative =
      book?.relativeSpreadPercent ?? null;

    return [

      {
        label: 'Mid-price coté',
        before: baseline.market.quotedMidPrice,
        current: market?.quotedMidPrice ?? null,
        impact: this.percentChange(
          baseline.market.quotedMidPrice,
          market?.quotedMidPrice ?? null
        ),
        unit: '%',
        meaning: 'Variation du prix'
      },

      {
        label: 'Spread',
        before: baseline.book.spread,
        current: book?.spread ?? null,
        impact: this.percentChange(
          baseline.book.spread,
          book?.spread ?? null
        ),
        unit: '%',
        meaning: 'Élargissement (+) / resserrement (−)'
      },

      {
        label: 'Spread relatif (%)',
        before: beforeRelative,
        current: currentRelative,
        impact:
          beforeRelative == null ||
          currentRelative == null
            ? null
            : currentRelative - beforeRelative,
        unit: 'pt',
        meaning: 'Écart en points de pourcentage'
      },

      {
        label: 'Profondeur du carnet',
        before: beforeDepth,
        current: currentDepth,
        impact: this.percentChange(
          beforeDepth,
          currentDepth
        ),
        unit: '%',
        meaning: 'Variation du volume BID + ASK'
      }

    ];
  }

  // ========================================
  // IMPACT PHASE
  // ========================================

  get impactPhase(): string {

    if (this.statusError || !this.status) {
      return 'Statut indisponible';
    }

    if (this.status.active) {

      return this.status.startedAt ===
        this.baseline?.startedAt
          ? 'Pendant CRASH'
          : 'Autre scénario actif';
    }

    if (this.recovery?.phase === 'RECOVERY') {
      return 'Pendant RECOVERY';
    }

    return 'Après CRASH';
  }

  // ========================================
  // CHART BOUNDS
  // ========================================

// ========================================
// MARKET PRICE CHART
// ========================================

private readonly chartLeft = 78;
private readonly chartRight = 870;
private readonly chartTop = 30;
private readonly chartBottom = 278;

// ========================================
// PRICE BOUNDS
// ========================================

private get priceBounds(): {
  min: number;
  max: number;
} {

  const values = this.priceHistory
    .map(point => point.price)
    .filter(Number.isFinite);

  const reference = this.market?.referencePrice;

  if (reference != null && Number.isFinite(reference)) {
    values.push(reference);
  }

  const beforeCrash =
    this.baseline?.market.quotedMidPrice;

  if (
    this.baseline?.market.symbol === this.symbol &&
    beforeCrash != null &&
    Number.isFinite(beforeCrash)
  ) {
    values.push(beforeCrash);
  }

  if (values.length === 0) {
    return { min: 0, max: 1 };
  }

  const min = Math.min(...values);
  const max = Math.max(...values);

  const padding = Math.max(
    (max - min) * 0.12,
    Math.abs(min) * 0.0005,
    0.01
  );

  return {
    min: min - padding,
    max: max + padding
  };
}

// ========================================
// PRICE TO Y COORDINATE
// ========================================

private yPosition(price: number): number {

  const bounds = this.priceBounds;

  const ratio =
    (price - bounds.min) /
    (bounds.max - bounds.min);

  return this.chartBottom -
    ratio * (this.chartBottom - this.chartTop);
}

// ========================================
// TIMESTAMP TO X COORDINATE
// ========================================

private xPosition(time: number): number {

  if (this.priceHistory.length < 2) {
    return this.chartLeft;
  }

  const first = this.priceHistory[0].time;

  const last =
    this.priceHistory[this.priceHistory.length - 1].time;

  const duration = Math.max(1, last - first);

  const ratio = Math.max(
    0,
    Math.min(1, (time - first) / duration)
  );

  return this.chartLeft +
    ratio * (this.chartRight - this.chartLeft);
}

// ========================================
// POINTS WITH COORDINATES
// ========================================

get chartDataPoints(): {
  time: number;
  price: number;
  x: number;
  y: number;
}[] {

  return this.priceHistory
    .filter(
      point =>
        Number.isFinite(point.time) &&
        Number.isFinite(point.price)
    )
    .map(point => ({
      time: point.time,
      price: point.price,
      x: this.xPosition(point.time),
      y: this.yPosition(point.price)
    }));
}

// ========================================
// SVG POLYLINE
// ========================================

get chartPoints(): string {

  return this.chartDataPoints
    .map(point => `${point.x},${point.y}`)
    .join(' ');
}

// ========================================
// Y AXIS: FIVE PRICE TICKS
// ========================================

get yAxisTicks(): {
  price: number;
  y: number;
}[] {

  const bounds = this.priceBounds;

  return Array.from({ length: 5 }, (_, i) => {

    const price =
      bounds.max -
      (bounds.max - bounds.min) * i / 4;

    return {
      price,
      y: this.yPosition(price)
    };
  });
}

// ========================================
// X AXIS: FIVE TIME TICKS
// ========================================

get timeAxisTicks(): {
  time: number;
  x: number;
}[] {

  if (this.priceHistory.length < 2) {
    return [];
  }

  const first = this.priceHistory[0].time;

  const last =
    this.priceHistory[this.priceHistory.length - 1].time;

  return Array.from({ length: 5 }, (_, i) => {

    const time = first + (last - first) * i / 4;

    return {
      time,
      x: this.xPosition(time)
    };
  });
}

// ========================================
// REFERENCE PRICE LINE
// ========================================

get referenceY(): number | null {

  const reference = this.market?.referencePrice;

  return reference == null ||
    !Number.isFinite(reference)
      ? null
      : this.yPosition(reference);
}

// ========================================
// PRICE BEFORE CRASH LINE
// ========================================

get baselineY(): number | null {

  if (
    !this.baseline ||
    this.baseline.market.symbol !== this.symbol
  ) {
    return null;
  }

  const price = this.baseline.market.quotedMidPrice;

  return price == null || !Number.isFinite(price)
    ? null
    : this.yPosition(price);
}

// ========================================
// LAST OBSERVATION
// ========================================

get lastChartPoint(): {
  time: number;
  price: number;
  x: number;
  y: number;
} | null {

  const points = this.chartDataPoints;

  return points.length > 0
    ? points[points.length - 1]
    : null;
}

// ========================================
// MINIMUM AND MAXIMUM
// ========================================

get minObservedPrice(): number {

  return this.priceHistory.length > 0
    ? Math.min(...this.priceHistory.map(p => p.price))
    : 0;
}

get maxObservedPrice(): number {

  return this.priceHistory.length > 0
    ? Math.max(...this.priceHistory.map(p => p.price))
    : 0;
}

refreshPortfolioStress(): void {

  if (this.portfolioStressPending) {
    return;
  }

  this.portfolioStressPending = true;

  this.subscriptions.add(
    this.portfolioStressService
      .getMyStress()
      .pipe(
        finalize(() => {
          this.portfolioStressPending = false;
        })
      )
      .subscribe({

        next: response => {
          this.portfolioStress = response;
          this.portfolioStressError = '';
        },

        error: error => {
          this.portfolioStress = null;

          this.portfolioStressError =
            this.httpError(
              'Portfolio Stress Testing',
              error
            );
        }

      })
  );
}



  // ========================================
  // HTTP ERROR HANDLING
  // ========================================

  private httpError(
    operation: string,
    error: unknown
  ): string {

    if (error instanceof HttpErrorResponse) {

      return (
        `${operation} : erreur HTTP ${error.status}.`
      );
    }

    return `${operation} : erreur inconnue.`;
  }
}
