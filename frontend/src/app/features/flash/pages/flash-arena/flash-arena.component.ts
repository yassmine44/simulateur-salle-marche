import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  inject
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import {
  CandlestickData,
  CandlestickSeries,
  IChartApi,
  ISeriesApi,
  createChart,
  Time
} from 'lightweight-charts';

import { Subscription } from 'rxjs';

import {
  FlashCandle,
  FlashOrderBookSnapshot,
  FlashPortfolio,
  FlashSession
} from '../../models/flash.models';
import { FlashService } from '../../services/flash.service';
import { AiVoiceAnalystService } from '../../../../shared/ai/ai-voice-analyst.service';
import { TradingTourService } from '../../../../shared/onboarding/trading-tour.service';
import { AuthStateService } from '../../../../services/auth-state.service';


@Component({
  selector: 'app-flash-arena',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink
  ],
  templateUrl: './flash-arena.component.html',
  styleUrl: './flash-arena.component.scss'
})
export class FlashArenaComponent
  implements AfterViewInit, OnDestroy {

  @ViewChild('chartHost', { static: true })
  chartHost!: ElementRef<HTMLDivElement>;

  private readonly flashService = inject(FlashService);
  private readonly voice = inject(AiVoiceAnalystService);
  private readonly tradingTour = inject(TradingTourService);
  private readonly authState = inject(AuthStateService);
  private tourStartedForSession: string | null = null;

  private chart: IChartApi | null = null;
  private series: ISeriesApi<'Candlestick'> | null = null;
  private eventSub: Subscription | null = null;
  private orderBookSub: Subscription | null = null;
  private userSub: Subscription | null = null;
  private countdownTimer: ReturnType<typeof setInterval> | null = null;
  private resizeObserver: ResizeObserver | null = null;
  private pendingVisibleCount = 0;
  private loadingCandles = false;

  readonly datasetLabel = 'Dataset: 2018 - 2026 Historical Market';

  session: FlashSession | null = null;
  portfolio: FlashPortfolio | null = null;
  orderBook: FlashOrderBookSnapshot | null = null;
  allCandles: FlashCandle[] = [];
  phase: 'WAITING' | 'COUNTDOWN' | 'LIVE' | 'DONE' = 'WAITING';
  countdownSec = 0;
  quantity = 1;
  limitPrice: number | null = null;
  busy = false;
  error = '';
  isAdmin = false;
  clockOffsetMs = 0;
  joinedSessionId: string | null = null;
  chartReady = false;


  get lastInsight(): string {
    return this.voice.lastInsight;
  }


  get aiCalendarEvents() {
    return this.voice.calendarEvents;
  }


  get calendarLoading(): boolean {
    return this.voice.calendarLoading;
  }


  get currentCandleDate(): string | null {
    const count = this.session?.visibleCandleCount
      || this.pendingVisibleCount
      || this.allCandles.length;
    if (!count || !this.allCandles.length) {
      return null;
    }
    const idx = Math.min(count, this.allCandles.length) - 1;
    return this.allCandles[idx]?.date || null;
  }


  isCalendarHighlight(entryDate: string): boolean {
    return this.voice.isCalendarHighlight(entryDate, this.currentCandleDate);
  }


  get voiceMuted(): boolean {
    return this.voice.muted;
  }


  get audioUnlocked(): boolean {
    return this.voice.audioUnlocked;
  }


  get chartLoading(): boolean {
    return this.phase !== 'WAITING'
      && (!this.chartReady || this.loadingCandles || this.allCandles.length === 0);
  }


  ngAfterViewInit(): void {
    this.userSub = this.authState.currentUser$.subscribe(user => {
      this.isAdmin = user?.role === 'ADMIN';
    });

    this.initChartShell();

    this.flashService.connect();

    this.eventSub = this.flashService.events$.subscribe(
      session => this.onSession(session)
    );
    this.orderBookSub = this.flashService.orderBook$.subscribe(
      book => { this.orderBook = book; }
    );
    this.flashService.getOrderBook().subscribe({
      next: book => { this.orderBook = book; }
    });
    this.flashService.getStatus().subscribe({
      next: session => this.onSession(session),
      error: () => { this.phase = 'WAITING'; }
    });
  }


  ngOnDestroy(): void {
    this.clearTimers();
    this.eventSub?.unsubscribe();
    this.orderBookSub?.unsubscribe();
    this.userSub?.unsubscribe();
    this.resizeObserver?.disconnect();
    this.destroyChart();
  }


  enableAiVoice(): void {
    const count = this.session?.visibleCandleCount
      || this.pendingVisibleCount
      || this.allCandles.length;
    const slice = this.allCandles.slice(0, Math.max(count, 0));
    this.voice.unlockAudio(
      slice.length ? slice : this.allCandles,
      this.session?.symbol || 'BIAT'
    );
  }


  toggleMute(): void {
    this.voice.toggleMute();
  }


  placeOrder(side: 'BUY' | 'SELL'): void {
    const price = Number(this.limitPrice);
    if (
      this.busy
      || this.phase !== 'LIVE'
      || !this.quantity
      || !Number.isFinite(price)
      || price <= 0
    ) {
      this.error = 'Enter a valid limit price and quantity.';
      return;
    }
    this.busy = true;
    this.error = '';
    this.flashService.placeOrder(side, Number(this.quantity), price).subscribe({
      next: portfolio => {
        this.portfolio = portfolio;
        this.busy = false;
        this.flashService.getOrderBook().subscribe({
          next: book => { this.orderBook = book; }
        });
      },
      error: err => {
        this.busy = false;
        this.error =
          err?.error?.message
          || err?.error?.detail
          || 'Order rejected.';
      }
    });
  }


  private onSession(session: FlashSession): void {
    if (!session) {
      return;
    }

    this.session = session;
    this.clockOffsetMs =
      Date.now() - (session.serverNow || Date.now());

    if (session.status === 'IDLE' || !session.sessionId) {
      this.phase = 'WAITING';
      this.clearTimers();
      return;
    }

    if (session.status === 'STOPPED') {
      this.phase = 'DONE';
      this.clearTimers();
      this.refreshPortfolio();
      return;
    }

    if (session.status === 'RUNNING') {
      this.ensureCandles(session);
      this.syncPhase(session);
      this.pendingVisibleCount = session.visibleCandleCount || 0;
      this.applyVisible(this.pendingVisibleCount);

      if (session.messageType === 'START') {
        // Flow 1: one-time pre-game calendar (separate from live voice).
        this.voice.clearCalendar();
        this.voice.fetchMarketCalendar(session.symbol || 'BIAT');
      } else if (
        !this.voice.calendarLoading
        && this.voice.calendarEvents.length === 0
      ) {
        // Late join / status poll: still seed calendar once (voice untouched).
        this.voice.fetchMarketCalendar(session.symbol || 'BIAT');
      }

      if (session.messageType === 'TICK'
          || session.messageType === 'START') {
        this.joinIfNeeded(session);
        this.refreshPortfolio();
      }

      // Voice fires immediately when a new candle is rendered (15s tick).
      if (session.messageType === 'TICK' && this.phase === 'LIVE') {
        this.maybeSpeak(session);
      }
    }
  }


  private ensureCandles(session: FlashSession): void {
    const incoming = this.sanitizeCandles(session.candles);
    if (incoming.length) {
      this.allCandles = incoming;
      this.loadingCandles = false;
      this.applyVisible(session.visibleCandleCount || this.pendingVisibleCount);
      return;
    }

    if (this.allCandles.length > 0) {
      return;
    }

    const symbol = session.symbol || 'BIAT';
    if (this.loadingCandles) {
      return;
    }

    this.loadingCandles = true;
    this.flashService.getCandles(symbol).subscribe({
      next: candles => {
        this.allCandles = this.sanitizeCandles(candles);
        this.loadingCandles = false;
        this.applyVisible(
          session.visibleCandleCount || this.pendingVisibleCount
        );
      },
      error: err => {
        this.loadingCandles = false;
        console.warn('[Flash Arena] failed to load candles', err);
        this.error = 'Could not load historical candles for the chart.';
      }
    });
  }


  private sanitizeCandles(raw: FlashCandle[] | null | undefined): FlashCandle[] {
    if (!Array.isArray(raw)) {
      return [];
    }
    return raw.filter(c => {
      if (!c?.date) {
        return false;
      }
      const open = Number(c.open);
      const high = Number(c.high);
      const low = Number(c.low);
      const close = Number(c.close);
      return Number.isFinite(open)
        && Number.isFinite(high)
        && Number.isFinite(low)
        && Number.isFinite(close)
        && high >= low;
    });
  }


  private syncPhase(session: FlashSession): void {
    const now = Date.now() - this.clockOffsetMs;
    if (now < session.startAtEpochMs) {
      this.phase = 'COUNTDOWN';
      this.countdownSec = Math.max(
        0,
        Math.ceil((session.startAtEpochMs - now) / 1000)
      );
      this.startCountdownWatch(session);
      return;
    }
    this.phase = 'LIVE';
    this.countdownSec = 0;
    this.maybeStartTour(session);
  }


  private startCountdownWatch(session: FlashSession): void {
    if (this.countdownTimer) {
      return;
    }
    this.countdownTimer = setInterval(() => {
      const now = Date.now() - this.clockOffsetMs;
      if (now >= session.startAtEpochMs) {
        this.phase = 'LIVE';
        this.countdownSec = 0;
        if (this.countdownTimer) {
          clearInterval(this.countdownTimer);
          this.countdownTimer = null;
        }
        this.joinIfNeeded(session);
        this.refreshPortfolio();
        this.maybeStartTour(session);
        this.maybeSpeak(session);
        this.applyVisible(
          session.visibleCandleCount || this.pendingVisibleCount
        );
      } else {
        this.countdownSec = Math.max(
          0,
          Math.ceil((session.startAtEpochMs - now) / 1000)
        );
      }
    }, 250);
  }


  private joinIfNeeded(session: FlashSession): void {
    if (!session.sessionId
        || this.joinedSessionId === session.sessionId) {
      return;
    }
    this.flashService.join().subscribe({
      next: portfolio => {
        this.portfolio = portfolio;
        this.joinedSessionId = session.sessionId;
      },
      error: () => {
        // still waiting / not running yet
      }
    });
  }


  private refreshPortfolio(): void {
    if (this.phase === 'WAITING') {
      return;
    }
    this.flashService.getPortfolio().subscribe({
      next: portfolio => {
        this.portfolio = portfolio;
        if (
          (this.limitPrice === null || this.limitPrice === undefined)
          && portfolio.currentPrice
        ) {
          this.limitPrice = Number(portfolio.currentPrice);
        }
      },
      error: () => { /* ignore when idle */ }
    });
  }


  private applyVisible(count: number): void {
    if (!this.series || !this.allCandles.length) {
      return;
    }
    const safeCount = Math.max(
      0,
      Math.min(count || 0, this.allCandles.length)
    );
    if (safeCount < 1) {
      return;
    }

    const visible = this.allCandles.slice(0, safeCount);
    const data: CandlestickData[] = [];
    for (const c of visible) {
      const point: CandlestickData = {
        time: c.date as Time,
        open: Number(c.open),
        high: Number(c.high),
        low: Number(c.low),
        close: Number(c.close)
      };
      data.push(point);
    }

    try {
      this.series.setData(data);
      this.chart?.timeScale().fitContent();
      this.chartReady = true;
    } catch (err) {
      console.warn('[Flash Arena] chart setData failed', err);
    }
  }


  private maybeSpeak(session: FlashSession): void {
    const count = session.visibleCandleCount || this.pendingVisibleCount || 0;
    const slice = this.allCandles.slice(0, Math.max(count, 1));
    if (slice.length < 1) {
      return;
    }
    this.voice.speakInsight(session.symbol || 'BIAT', slice);
  }


  private maybeStartTour(session: FlashSession): void {
    if (!session?.sessionId || this.phase !== 'LIVE') {
      return;
    }
    if (this.tourStartedForSession === session.sessionId) {
      return;
    }
    this.tourStartedForSession = session.sessionId;
    this.tradingTour.startIfNeeded('multiplayer');
  }


  private initChartShell(): void {
    const host = this.chartHost?.nativeElement;
    if (!host || this.chart) {
      return;
    }

    // Guarantee non-zero layout before Lightweight Charts measures the host.
    host.style.width = '100%';
    host.style.minHeight = '420px';
    host.style.height = '420px';

    this.chart = createChart(host, {
      width: host.clientWidth || 640,
      height: 420,
      layout: {
        background: { color: '#ffffff' },
        textColor: '#475569'
      },
      grid: {
        vertLines: { color: '#e2e8f0' },
        horzLines: { color: '#e2e8f0' }
      },
      rightPriceScale: {
        borderVisible: false
      },
      timeScale: {
        borderVisible: false
      }
    });

    this.series = this.chart.addSeries(CandlestickSeries, {
      upColor: '#16a34a',
      downColor: '#dc2626',
      borderUpColor: '#16a34a',
      borderDownColor: '#dc2626',
      wickUpColor: '#16a34a',
      wickDownColor: '#dc2626'
    });

    this.resizeObserver = new ResizeObserver(entries => {
      const entry = entries[0];
      if (!entry || !this.chart) {
        return;
      }
      const width = Math.max(320, Math.floor(entry.contentRect.width));
      this.chart.applyOptions({ width, height: 420 });
    });
    this.resizeObserver.observe(host);
  }


  private destroyChart(): void {
    this.chart?.remove();
    this.chart = null;
    this.series = null;
    this.chartReady = false;
  }


  private clearTimers(): void {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }
  }


  formatMoney(value: number | null | undefined): string {
    if (value === null || value === undefined) {
      return '—';
    }
    return Number(value).toLocaleString('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
  }
}
