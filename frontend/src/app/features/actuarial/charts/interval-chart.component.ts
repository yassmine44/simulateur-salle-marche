import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  signal
} from '@angular/core';

import { formatPercent } from '../actuarial.format';
import { linearScale, logDomain, logScale, logTicks, niceDomain, niceTicks } from './chart-scales';
import { Formatter } from './line-chart.component';

export interface IntervalRow {
  key: string;
  label: string;
  estimate: number;
  low: number;
  high: number;
  /** Valeur réellement observée ensuite (backtest), si connue. */
  actual?: number | null;
}

const HEIGHT = 280;
const MARGIN = { top: 16, right: 12, bottom: 34, left: 64 };
const LOG_SCALE_RATIO = 50;

function formatSignedPercent(ratio: number): string {
  const rounded = Math.round(ratio * 1000) / 1000;
  return `${rounded > 0 ? '+' : rounded < 0 ? '−' : ''}${formatPercent(Math.abs(rounded), 0)}`;
}

/**
 * Estimation, intervalle de confiance et valeur réalisée, par catégorie.
 * Estimation = rond bleu, réalisé = losange orange (forme ≠ couleur seule),
 * intervalle = barre verticale translucide.
 */
@Component({
  selector: 'app-interval-chart',
  standalone: true,
  templateUrl: './interval-chart.component.html',
  styleUrl: './interval-chart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class IntervalChartComponent implements AfterViewInit {
  readonly rows = input.required<IntervalRow[]>();
  readonly yFormat = input<Formatter>(v => String(v));
  readonly estimateLabel = input('Estimation');
  readonly intervalLabel = input('Intervalle de confiance');
  readonly actualLabel = input('Réalisé');
  readonly caption = input('');
  readonly ariaLabel = input('Graphique d\'intervalles');
  /**
   * Mode relatif : chaque catégorie est rapportée à sa propre estimation
   * (0 % = estimation). L'intervalle devient ±x % et le réalisé un écart en %,
   * ce qui rend comparables des catégories de tailles très différentes.
   * Les montants absolus restent affichés dans l'info-bulle et le tableau.
   */
  readonly relative = input(false);

  readonly width = signal(640);
  readonly showTable = signal(false);
  readonly hoverIndex = signal<number | null>(null);
  readonly height = HEIGHT;
  readonly margin = MARGIN;

  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);

  readonly hasActual = computed(() => this.rows().some(r => r.actual != null));

  private readonly positiveValues = computed(() =>
    this.rows()
      .flatMap(r => [r.low, r.high, r.estimate, r.actual ?? r.estimate])
      .filter(v => v > 0)
  );

  /**
   * Échelle logarithmique quand les valeurs couvrent plus de deux ordres de
   * grandeur (ex. provisions de 10 k à 9 M) : en linéaire, les petites
   * catégories seraient écrasées contre zéro.
   */
  readonly logarithmic = computed(() => {
    if (this.relative()) {
      return false;
    }
    const values = this.positiveValues();
    return values.length > 0 && Math.max(...values) / Math.min(...values) > LOG_SCALE_RATIO;
  });

  /** Valeur tracée : brute, ou écart relatif à l'estimation de la catégorie. */
  private plotted(row: IntervalRow, value: number): number {
    return this.relative() ? value / row.estimate - 1 : value;
  }

  /** Format des graduations : montant, ou écart signé en %. */
  readonly axisFormat = computed<Formatter>(() =>
    this.relative() ? (v: number) => formatSignedPercent(v) : this.yFormat()
  );

  private readonly yDomain = computed<[number, number]>(() => {
    if (this.relative()) {
      const deviations = this.rows().flatMap(r =>
        [r.low, r.high, r.actual ?? r.estimate].map(v => Math.abs(this.plotted(r, v)))
      );
      const largest = Math.max(0.05, ...deviations) * 1.1;
      return niceDomain(-largest, largest, 6);
    }
    const values = this.positiveValues();
    if (this.logarithmic()) {
      return logDomain(Math.min(...values), Math.max(...values));
    }
    return niceDomain(0, Math.max(1, ...values), 5);
  });

  readonly yScale = computed(() => {
    const range: [number, number] = [HEIGHT - MARGIN.bottom, MARGIN.top];
    return this.logarithmic() ? logScale(this.yDomain(), range) : linearScale(this.yDomain(), range);
  });

  readonly yTicks = computed(() =>
    this.logarithmic()
      ? logTicks(this.yDomain()[0], this.yDomain()[1])
      : niceTicks(this.yDomain()[0], this.yDomain()[1], 5)
  );

  readonly band = computed(() => (this.width() - MARGIN.left - MARGIN.right) / Math.max(1, this.rows().length));

  readonly marks = computed(() => {
    const scale = this.yScale();
    const floor = this.yDomain()[0];
    // En échelle logarithmique, une valeur nulle (intervalle dégénéré) est
    // ramenée au bas de l'axe au lieu de produire −∞.
    const y = this.logarithmic() ? (v: number) => scale(Math.max(v, floor)) : scale;
    const band = this.band();
    return this.rows().map((row, i) => {
      const cx = MARGIN.left + band * (i + 0.5);
      const at = (value: number) => y(this.plotted(row, value));
      return {
        ...row,
        cx,
        bandX: MARGIN.left + band * i,
        yEstimate: at(row.estimate),
        yLow: at(row.low),
        yHigh: at(row.high),
        yActual: row.actual == null ? null : at(row.actual),
        deviation: row.actual == null ? null : row.actual / row.estimate - 1,
        inside: row.actual == null ? null : row.actual >= row.low && row.actual <= row.high
      };
    });
  });

  readonly axisFormatSigned = formatSignedPercent;

  /** Ligne de référence « estimation » (0 %) en mode relatif. */
  readonly zeroLine = computed(() => (this.relative() ? this.yScale()(0) : null));

  readonly hovered = computed(() => {
    const index = this.hoverIndex();
    return index === null ? null : this.marks()[index] ?? null;
  });

  ngAfterViewInit(): void {
    const observer = new ResizeObserver(entries => {
      const width = Math.round(entries[0].contentRect.width);
      if (width > 0) {
        this.width.set(Math.max(280, width));
      }
    });
    observer.observe(this.host.nativeElement);
    this.destroyRef.onDestroy(() => observer.disconnect());
  }

  diamond(cx: number, cy: number, r = 6): string {
    return `M${cx},${cy - r}L${cx + r},${cy}L${cx},${cy + r}L${cx - r},${cy}Z`;
  }

  onKeydown(event: KeyboardEvent): void {
    const count = this.rows().length;
    const current = this.hoverIndex() ?? -1;
    if (event.key === 'ArrowRight') {
      this.hoverIndex.set(Math.min(count - 1, current + 1));
      event.preventDefault();
    } else if (event.key === 'ArrowLeft') {
      this.hoverIndex.set(Math.max(0, current - 1));
      event.preventDefault();
    }
  }
}
