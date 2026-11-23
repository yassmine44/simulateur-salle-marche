import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import {
  Observable,
  catchError,
  firstValueFrom,
  of,
  tap,
  timeout
} from 'rxjs';

export interface VoiceCandle {
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface VoiceInsightResponse {
  voiceScript: string;
  calendarEvent: string;
  simulationDate: string;
  spokenReady: boolean;
}

export interface MarketCalendarEventDto {
  date: string;
  event: string;
  text?: string;
}

export interface MarketCalendarResponse {
  symbol: string;
  events: MarketCalendarEventDto[];
}

export interface CalendarEventEntry {
  date: string;
  text: string;
  at: number;
}

/**
 * Two distinct AI flows (do not mix prompts / parsers):
 * 1) {@link #fetchMarketCalendar} — once on session init
 * 2) {@link #speakInsight} — every new candle tick (live voice only)
 */
@Injectable({
  providedIn: 'root'
})
export class AiVoiceAnalystService {

  private readonly http = inject(HttpClient);
  private readonly voiceEndpoint =
    'http://localhost:8081/api/flash/voice-insight';
  private readonly calendarEndpoint =
    'http://localhost:8081/api/flash/market-calendar';

  muted = false;
  lastInsight = '';
  audioUnlocked = false;
  calendarEvents: CalendarEventEntry[] = [];
  calendarLoading = false;

  private calendarSeededForSymbol: string | null = null;
  private lastSpokenCandleDate: string | null = null;


  /**
   * Flow 1 — Pre-Game Calendar Fetch (runs ONCE on load / session START).
   * Never called from the live voice tick loop.
   * `calendarLoading` ALWAYS clears in `finally` so the UI cannot soft-lock.
   */
  fetchMarketCalendar(company: string | null | undefined): void {
    void this.fetchMarketCalendarAsync(company);
  }


  private async fetchMarketCalendarAsync(
    company: string | null | undefined
  ): Promise<void> {
    const selected = (company || 'BIAT').trim();
    if (
      this.calendarSeededForSymbol === selected
      && this.calendarEvents.length > 0
    ) {
      this.calendarLoading = false;
      return;
    }

    // Boolean that drives "Generating…" / unlocks "Waiting for AI timeline…"
    this.calendarLoading = true;
    console.log('[AI Calendar] fetchMarketCalendar (once)', { selected });

    try {
      // 1. Make the API call (hard timeout so a hanging Ollama cannot lock UI)
      const response = await firstValueFrom(
        this.http.post<MarketCalendarResponse | string>(
          this.calendarEndpoint,
          { symbol: selected },
          { withCredentials: true }
        ).pipe(
          timeout(25_000),
          tap(res => console.log('[AI Calendar] response', res)),
          catchError(err => {
            console.error('Market Calendar failed, using fallback:', err);
            return of(null);
          })
        )
      );

      // 2. Extract JSON from raw text (or Spring DTO events[])
      const rawAiResponse = this.toCalendarRawText(response);
      const jsonMatch = rawAiResponse.match(/\[[\s\S]*\]/);

      if (jsonMatch) {
        const parsedEvents = JSON.parse(jsonMatch[0]);
        const mapped = this.mapCalendarArray(parsedEvents);
        if (!mapped.length) {
          throw new Error('No usable events in parsed JSON array.');
        }
        // Actual calendar state variable used by the arena template
        this.calendarEvents = mapped;
        this.calendarSeededForSymbol = selected;
      } else if (
        response
        && typeof response === 'object'
        && Array.isArray((response as MarketCalendarResponse).events)
        && (response as MarketCalendarResponse).events.length > 0
      ) {
        this.calendarEvents = this.mapCalendarArray(
          (response as MarketCalendarResponse).events
        );
        this.calendarSeededForSymbol = selected;
      } else {
        throw new Error('No JSON array found in response.');
      }
    } catch (error) {
      console.error('Market Calendar failed, using fallback:', error);

      // Actual calendar state variable → fallback timeline
      this.calendarEvents = this.mapCalendarArray([
        {
          date: '2018-01-15',
          event: 'Tunisian Central Bank hints at monetary policy adjustments.'
        },
        {
          date: '2018-06-01',
          event: 'Mid-year sector earnings show mixed volatility.'
        },
        {
          date: '2018-10-15',
          event: 'Macroeconomic inflation data released, impacting local markets.'
        }
      ]);
      this.calendarSeededForSymbol = selected;
    } finally {
      // CRITICAL: unlock the UI that shows "Waiting for AI timeline…"
      this.calendarLoading = false;
    }
  }


  /** @deprecated use {@link #fetchMarketCalendar} */
  seedMarketCalendar(symbol: string | null | undefined): void {
    this.fetchMarketCalendar(symbol);
  }


  private toCalendarRawText(
    response: MarketCalendarResponse | string | null | undefined
  ): string {
    if (response == null) {
      return '';
    }
    if (typeof response === 'string') {
      return response;
    }
    // Prefer serializing events if Spring already wrapped them.
    if (Array.isArray(response.events)) {
      return JSON.stringify(response.events);
    }
    return JSON.stringify(response);
  }


  /**
   * Flow 2 — Live AI Voice Commentator (every 15s candle).
   * Uses /voice-insight only; never overwrites calendarEvents.
   */
  requestInsight(
    symbol: string | null | undefined,
    candles: VoiceCandle[],
    calendarContext: string[] = []
  ): Observable<VoiceInsightResponse> {
    if (!candles?.length) {
      console.log('[AI Voice] skipped: no candles');
      return of(this.emptyResponse());
    }
    const latest = candles[candles.length - 1];
    console.log(
      '[AI Voice] live commentator request',
      { symbol, date: latest?.date }
    );
    return this.http.post<VoiceInsightResponse>(
      this.voiceEndpoint,
      {
        symbol: symbol ?? null,
        candles: candles.slice(-2).map(c => ({
          date: c.date,
          open: Number(c.open),
          high: Number(c.high),
          low: Number(c.low),
          close: Number(c.close),
          volume: Number(c.volume ?? 0)
        })),
        calendarContext
      },
      { withCredentials: true }
    ).pipe(
      tap(response => console.log('[AI Voice] response', response)),
      catchError(err => {
        console.error('[AI Voice] request failed; staying silent', err);
        return of(this.emptyResponse());
      })
    );
  }


  unlockAudio(candles?: VoiceCandle[], symbol?: string | null): void {
    this.audioUnlocked = true;
    this.muted = false;
    console.log('[AI Voice] audio unlocked by user gesture');

    // Do NOT re-fetch the calendar here — it must stay a one-time pre-game call.
    if (candles && candles.length >= 1) {
      this.speakInsight(symbol ?? 'BIAT', candles);
    }
  }


  speakInsight(
    symbol: string | null | undefined,
    candles: VoiceCandle[]
  ): void {
    if (!this.audioUnlocked) {
      console.log('[AI Voice] blocked until Enable AI Voice is clicked');
      return;
    }
    if (!candles?.length) {
      return;
    }

    const latestDate = candles[candles.length - 1].date;
    if (latestDate && latestDate === this.lastSpokenCandleDate) {
      return;
    }

    const calendarContext = this.matchingCalendarTexts(latestDate);

    this.requestInsight(symbol, candles, calendarContext).subscribe(response => {
      if (latestDate) {
        this.lastSpokenCandleDate = latestDate;
      }
      // Live voice must never mutate calendarEvents.
      if (this.muted) {
        return;
      }
      const script = response.spokenReady
        ? response.voiceScript?.trim()
        : '';
      if (!script) {
        console.log('[AI Voice] no voice_script; staying silent');
        return;
      }
      this.lastInsight = script;
      this.speak(script);
    });
  }


  matchingCalendarTexts(candleDate: string | null | undefined): string[] {
    return this.calendarEvents
      .filter(entry => this.datesAlign(entry.date, candleDate))
      .map(entry => `${entry.date}: ${entry.text}`);
  }


  isCalendarHighlight(
    entryDate: string,
    candleDate: string | null | undefined
  ): boolean {
    return this.datesAlign(entryDate, candleDate);
  }


  clearCalendar(): void {
    this.calendarEvents = [];
    this.calendarLoading = false;
    this.calendarSeededForSymbol = null;
    this.lastSpokenCandleDate = null;
    this.lastInsight = '';
  }


  speak(text: string): void {
    if (this.muted || !text || typeof window === 'undefined') {
      return;
    }
    if (!window.speechSynthesis) {
      console.warn('[AI Voice] speechSynthesis unavailable');
      return;
    }
    window.speechSynthesis.cancel();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.rate = 1.05;
    window.speechSynthesis.speak(utterance);
  }


  toggleMute(): boolean {
    this.muted = !this.muted;
    if (this.muted && typeof window !== 'undefined') {
      window.speechSynthesis?.cancel();
    }
    return this.muted;
  }


  private mapCalendarArray(raw: unknown): CalendarEventEntry[] {
    const list = Array.isArray(raw) ? raw : [];
    return list
      .map((item, index) => {
        if (!item || typeof item !== 'object') {
          return null;
        }
        const row = item as { date?: string; event?: string; text?: string };
        const text = (row.event || row.text || '').trim();
        if (!text) {
          return null;
        }
        return {
          date: (row.date || '').trim() || '2018-01-01',
          text,
          at: Date.now() + index
        } as CalendarEventEntry;
      })
      .filter((e): e is CalendarEventEntry => e !== null);
  }


  private datesAlign(
    eventDate: string | null | undefined,
    candleDate: string | null | undefined
  ): boolean {
    if (!eventDate || !candleDate) {
      return false;
    }
    const left = eventDate.trim().slice(0, 10);
    const right = candleDate.trim().slice(0, 10);
    if (!left || !right) {
      return false;
    }
    if (left === right) {
      return true;
    }
    return left.slice(0, 7) === right.slice(0, 7);
  }


  private emptyResponse(): VoiceInsightResponse {
    return {
      voiceScript: '',
      calendarEvent: '',
      simulationDate: '',
      spokenReady: false
    };
  }
}
