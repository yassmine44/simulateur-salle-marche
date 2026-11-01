import {
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  inject
} from '@angular/core';

import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import {
  CandlestickData,
  CandlestickSeries,
  IChartApi,
  ISeriesApi,
  createChart
} from 'lightweight-charts';

import {
  AiAnalysisReport,
  GamificationCandle,
  GamificationChallenge,
  GamificationEventKey,
  GamificationOrderSide,
  GamificationPortfolio,
  HistoricalEvent,
  ImpactLevel,
  WeeklyContext
} from '../../models/gamification.models';

import { GamificationService }
  from '../../services/gamification.service';


export type GameStatus =
  | 'PLAYING'
  | 'WON'
  | 'LOST';


@Component({
  selector: 'app-gamification',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule
  ],
  templateUrl: './gamification.component.html',
  styleUrl: './gamification.component.scss'
})
export class GamificationComponent
  implements OnDestroy {

  private readonly gamificationService =
    inject(GamificationService);

  @ViewChild('chartHost')
  private chartHost?: ElementRef<HTMLDivElement>;

  private chart: IChartApi | null = null;

  private candleSeries:
    ISeriesApi<'Candlestick'> | null = null;

  private resizeObserver?: ResizeObserver;


  readonly startingCapital = 100_000;

  readonly stopOutEquity = 90_000;

  readonly victoryEquity = 110_000;


  gameStatus: GameStatus = 'PLAYING';

  endGameMessage = '';

  finalEquity: number | null = null;


  readonly challenges: GamificationChallenge[] = [
    {
      key: '2008',
      label: '2008 Subprime Mortgage Crisis',
      description:
        'Survivre à l’effondrement du crédit et au bear market.'
    },
    {
      key: '2020',
      label: '2020 COVID-19 Flash Crash',
      description:
        'Gérer le krach éclair puis le rebond historique.'
    },
    {
      key: '2022',
      label: '2022 Russia-Ukraine Shock',
      description:
        'Naviguer le choc géopolitique et l’inflation.'
    }
  ];


  selectedEvent: GamificationEventKey | null = null;

  allCandles: GamificationCandle[] = [];

  portfolio: GamificationPortfolio | null = null;

  quantity = 1;

  loading = false;

  busy = false;

  errorMessage = '';

  successMessage = '';


  ngOnDestroy(): void {

    this.destroyChart();
  }


  get isPlaying(): boolean {

    return this.gameStatus === 'PLAYING';
  }


  get isGameOver(): boolean {

    return this.gameStatus !== 'PLAYING';
  }


  selectChallenge(
    key: GamificationEventKey
  ): void {

    if (
      this.busy
      ||
      (
        key === this.selectedEvent
        &&
        this.isPlaying
      )
    ) {
      return;
    }

    this.startChallenge(key);
  }


  startChallenge(
    key: GamificationEventKey
  ): void {

    this.busy = true;
    this.loading = true;
    this.clearMessages();
    this.resetGameOutcome();

    this.gamificationService
      .getHistoricalData(key)
      .subscribe({

        next: (candles) => {

          this.allCandles = candles;

          this.gamificationService
            .startChallenge(key)
            .subscribe({

              next: (portfolio) => {

                this.selectedEvent = key;
                this.applyPortfolio(portfolio);
                this.queueChartRefresh();
                this.checkGameConditions();

                this.successMessage =
                  `Défi démarré : ${portfolio.eventLabel}. `
                  + 'Capital virtuel 100 000 $. Objectif +10%, stop-out -10%.';

                this.busy = false;
                this.loading = false;
              },

              error: (error) => {

                this.errorMessage =
                  this.extractError(
                    error,
                    'Impossible de démarrer le défi.'
                  );

                this.busy = false;
                this.loading = false;
              }
            });
        },

        error: (error) => {

          this.errorMessage =
            this.extractError(
              error,
              'Impossible de charger les données historiques.'
            );

          this.busy = false;
          this.loading = false;
        }
      });
  }


  advanceWeek(): void {

    if (
      !this.selectedEvent
      ||
      this.busy
      ||
      !this.isPlaying
      ||
      this.portfolio?.completed
    ) {
      return;
    }

    this.busy = true;
    this.clearMessages();

    this.gamificationService
      .advanceWeek(this.selectedEvent)
      .subscribe({

        next: (portfolio) => {

          this.applyPortfolio(portfolio);
          this.queueChartRefresh();
          this.checkGameConditions();

          if (this.isPlaying) {

            this.successMessage =
              portfolio.completed
                ? 'Dernière semaine atteinte.'
                : `Semaine suivante. Prix courant : ${this.formatMoney(portfolio.currentPrice)} $`;
          }

          this.busy = false;
        },

        error: (error) => {

          this.errorMessage =
            this.extractError(
              error,
              'Impossible d’avancer d’une période.'
            );

          this.busy = false;
        }
      });
  }


  placeOrder(
    side: GamificationOrderSide
  ): void {

    if (
      !this.selectedEvent
      ||
      this.busy
      ||
      !this.isPlaying
    ) {
      return;
    }

    if (
      !Number.isFinite(this.quantity)
      ||
      this.quantity <= 0
    ) {

      this.errorMessage =
        'Saisissez une quantité strictement positive.';

      return;
    }

    this.busy = true;
    this.clearMessages();

    this.gamificationService
      .placeOrder(
        this.selectedEvent,
        {
          side,
          quantity: this.quantity
        }
      )
      .subscribe({

        next: (portfolio) => {

          this.applyPortfolio(portfolio);
          this.checkGameConditions();

          if (this.isPlaying) {

            this.successMessage =
              side === 'BUY'
                ? `Achat exécuté à ${this.formatMoney(portfolio.currentPrice)} $.`
                : `Vente exécutée à ${this.formatMoney(portfolio.currentPrice)} $.`;
          }

          this.busy = false;
        },

        error: (error) => {

          this.errorMessage =
            this.extractError(
              error,
              'Ordre refusé.'
            );

          this.busy = false;
        }
      });
  }


  restartCurrentChallenge(): void {

    if (
      !this.selectedEvent
      ||
      this.busy
    ) {
      return;
    }

    this.startChallenge(this.selectedEvent);
  }


  chooseAnotherChallenge(): void {

    this.destroyChart();
    this.selectedEvent = null;
    this.portfolio = null;
    this.allCandles = [];
    this.resetGameOutcome();
    this.clearMessages();
  }


  checkGameConditions(): void {

    if (!this.portfolio) {
      return;
    }

    // Ne pas réécrire un résultat déjà figé.
    if (!this.isPlaying) {
      return;
    }

    const currentEquity =
      this.resolveCurrentEquity(this.portfolio);

    if (currentEquity <= this.stopOutEquity) {

      this.finishGame(
        'LOST',
        'Capital épuisé (-10%). Le marché a eu raison de vous.',
        currentEquity
      );

      return;
    }

    const isLastWeek =
      this.portfolio.completed
      ||
      this.portfolio.visibleCandleCount
      >=
      this.portfolio.totalCandles;

    if (!isLastWeek) {
      return;
    }

    if (currentEquity >= this.victoryEquity) {

      this.finishGame(
        'WON',
        'Félicitations ! Objectif de +10% atteint.',
        currentEquity
      );

      return;
    }

    this.finishGame(
      'LOST',
      'Temps écoulé. Objectif de +10% non atteint.',
      currentEquity
    );
  }


  get displayProgress(): number {

    if (!this.portfolio) {
      return 0;
    }

    return Math.round(
      (
        this.portfolio.visibleCandleCount
        /
        this.portfolio.totalCandles
      )
      * 100
    );
  }


  get challengeLabel(): string {

    return this.challenges.find(
      challenge =>
        challenge.key === this.selectedEvent
    )?.label
      ?? 'Aucun défi sélectionné';
  }


  get weeklyContext(): WeeklyContext {

    return this.portfolio?.weeklyContext ?? {
      aiAnalysis: null,
      events: []
    };
  }


  get aiAnalysis(): AiAnalysisReport | null {

    return this.weeklyContext.aiAnalysis;
  }


  get calendarEvents(): HistoricalEvent[] {

    return this.weeklyContext.events ?? [];
  }


  get currentWeekLabel(): string {

    return this.portfolio?.currentCandle?.date
      ?? '—';
  }


  get rsiTone(): 'oversold' | 'overbought' | 'neutral' | 'learning' {

    const rsi =
      this.aiAnalysis?.rsiValue;

    if (
      this.aiAnalysis?.learningPhase
      ||
      rsi === null
      ||
      rsi === undefined
    ) {
      return 'learning';
    }

    if (rsi >= 70) {
      return 'overbought';
    }

    if (rsi <= 30) {
      return 'oversold';
    }

    return 'neutral';
  }


  get confidencePercent(): number {

    return this.aiAnalysis?.mlConfidenceScore ?? 0;
  }


  get predictionTone(): 'up' | 'down' | 'flat' {

    const direction =
      this.aiAnalysis?.predictionDirection
        ?.toLowerCase() ?? '';

    if (direction.includes('hausse')) {
      return 'up';
    }

    if (direction.includes('baisse')) {
      return 'down';
    }

    return 'flat';
  }


  get objectiveProgressPercent(): number {

    const equity =
      this.portfolio
        ? this.resolveCurrentEquity(this.portfolio)
        : this.startingCapital;

    const span =
      this.victoryEquity - this.stopOutEquity;

    if (span <= 0) {
      return 0;
    }

    const raw =
      (
        (equity - this.stopOutEquity)
        /
        span
      )
      * 100;

    return Math.max(0, Math.min(100, raw));
  }


  get startMarkerPercent(): number {

    const span =
      this.victoryEquity - this.stopOutEquity;

    return (
      (
        (this.startingCapital - this.stopOutEquity)
        /
        span
      )
      * 100
    );
  }


  impactClass(
    impact: ImpactLevel
  ): string {

    return impact.toLowerCase();
  }


  private applyPortfolio(
    portfolio: GamificationPortfolio
  ): void {

    this.portfolio = {
      ...portfolio,
      weeklyContext: portfolio.weeklyContext ?? {
        aiAnalysis: null,
        events: []
      },
      currentCandle: portfolio.currentCandle ?? null,
      recentOrders: portfolio.recentOrders ?? []
    };
  }


  private resolveCurrentEquity(
    portfolio: GamificationPortfolio
  ): number {

    if (
      Number.isFinite(portfolio.equity)
    ) {
      return Number(portfolio.equity);
    }

    const cash =
      Number(portfolio.cashBalance) || 0;

    const shares =
      Number(portfolio.sharesHeld) || 0;

    const price =
      Number(portfolio.currentPrice) || 0;

    return cash + (shares * price);
  }


  private finishGame(
    status: Exclude<GameStatus, 'PLAYING'>,
    message: string,
    equity: number
  ): void {

    this.gameStatus = status;
    this.endGameMessage = message;
    this.finalEquity = equity;
    this.successMessage = '';
  }


  private resetGameOutcome(): void {

    this.gameStatus = 'PLAYING';
    this.endGameMessage = '';
    this.finalEquity = null;
  }


  private queueChartRefresh(): void {

    // Le conteneur n'existe qu'après sélection du défi.
    setTimeout(() => {
      this.initChart();
      this.renderVisibleCandles();
    });
  }


  private initChart(): void {

    const host =
      this.chartHost?.nativeElement;

    if (!host) {
      return;
    }

    if (this.chart) {
      return;
    }

    this.chart = createChart(host, {
      autoSize: true,
      layout: {
        background: { color: '#ffffff' },
        textColor: '#475569'
      },
      grid: {
        vertLines: { color: '#e2e8f0' },
        horzLines: { color: '#e2e8f0' }
      },
      rightPriceScale: {
        borderColor: '#cbd5e1'
      },
      timeScale: {
        borderColor: '#cbd5e1',
        fixLeftEdge: true,
        fixRightEdge: true
      }
    });

    this.candleSeries =
      this.chart.addSeries(
        CandlestickSeries,
        {
          upColor: '#16a34a',
          downColor: '#dc2626',
          borderUpColor: '#16a34a',
          borderDownColor: '#dc2626',
          wickUpColor: '#16a34a',
          wickDownColor: '#dc2626'
        }
      );

    this.resizeObserver =
      new ResizeObserver(() => {
        this.chart?.timeScale().fitContent();
      });

    this.resizeObserver.observe(host);
  }


  private renderVisibleCandles(): void {

    if (!this.portfolio) {
      return;
    }

    if (!this.candleSeries) {
      this.initChart();
    }

    if (!this.candleSeries) {
      return;
    }

    const visible =
      this.allCandles.slice(
        0,
        this.portfolio.visibleCandleCount
      );

    const data: CandlestickData[] =
      visible.map(candle => ({
        time: candle.date as CandlestickData['time'],
        open: Number(candle.open),
        high: Number(candle.high),
        low: Number(candle.low),
        close: Number(candle.close)
      }));

    this.candleSeries.setData(data);
    this.chart?.timeScale().fitContent();
  }


  private destroyChart(): void {

    this.resizeObserver?.disconnect();
    this.resizeObserver = undefined;

    this.chart?.remove();
    this.chart = null;
    this.candleSeries = null;
  }


  formatMoney(
    value: number | null | undefined
  ): string {

    if (
      value === null
      ||
      value === undefined
      ||
      Number.isNaN(Number(value))
    ) {
      return '—';
    }

    return Number(value).toLocaleString(
      'en-US',
      {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
      }
    );
  }


  private clearMessages(): void {

    this.errorMessage = '';
    this.successMessage = '';
  }


  private extractError(
    error: unknown,
    fallback: string
  ): string {

    if (
      typeof error === 'object'
      &&
      error !== null
      &&
      'error' in error
    ) {

      const httpError =
        error as {
          error?: {
            message?: string;
            detail?: string;
            error?: string;
          };
        };

      return (
        httpError.error?.message
        ||
        httpError.error?.detail
        ||
        httpError.error?.error
        ||
        fallback
      );
    }

    return fallback;
  }
}
