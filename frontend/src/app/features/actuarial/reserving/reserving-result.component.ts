import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { formatNumber, formatPercent } from '../actuarial.format';
import { Backtest, ChainLadderResult } from '../actuarial.models';
import { IntervalChartComponent, IntervalRow } from '../charts/interval-chart.component';
import { Formatter } from '../charts/line-chart.component';

interface TriangleCell {
  value: number;
  kind: 'known' | 'diagonal' | 'projected';
}

/**
 * Résultat d'un provisionnement Chain-Ladder : indicateurs, provisions par
 * année avec intervalle de Mack, triangle complété et, s'il est connu,
 * comparaison avec ce qui a réellement été payé (backtest).
 */
@Component({
  selector: 'app-reserving-result',
  standalone: true,
  imports: [IntervalChartComponent],
  templateUrl: './reserving-result.component.html',
  styleUrl: './reserving-result.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReservingResultComponent {
  readonly result = input.required<ChainLadderResult>();
  readonly backtest = input<Backtest | null>(null);
  /** Formate un montant (tuiles, graphique). */
  readonly amount = input<Formatter>(v => formatNumber(v, 0));
  /** Formate une cellule du triangle (plus compact). */
  readonly cell = input<Formatter>(v => formatNumber(v, 0));

  readonly percent = formatPercent;
  readonly factor = (v: number) => formatNumber(v, 3);

  readonly level = computed(() => formatPercent(this.result().confidence_level, 0));

  readonly lags = computed(() => Array.from({ length: this.result().size }, (_, i) => i + 1));

  readonly rows = computed(() => {
    const result = this.result();
    const n = result.size;
    return result.origins.map((origin, i) => ({
      origin,
      cells: result.projected[i].map<TriangleCell>((value, k) => ({
        value,
        kind: k < n - 1 - i ? 'known' : k === n - 1 - i ? 'diagonal' : 'projected'
      }))
    }));
  });

  readonly intervalRows = computed<IntervalRow[]>(() => {
    const actualByOrigin = new Map(
      (this.backtest()?.origins ?? []).map(o => [o.origin, o.actual_reserve])
    );
    return this.result()
      .origins.filter(o => o.reserve > 0)
      .map(o => ({
        key: String(o.origin),
        label: String(o.origin),
        estimate: o.reserve,
        low: o.ci_low,
        high: o.ci_high,
        actual: actualByOrigin.get(o.origin) ?? null
      }));
  });

  readonly backtestRows = computed(() => {
    const backtest = this.backtest();
    return backtest ? backtest.origins.filter(o => o.within_interval !== null) : [];
  });
}
