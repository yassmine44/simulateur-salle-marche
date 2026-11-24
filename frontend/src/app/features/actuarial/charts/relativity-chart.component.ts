import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

import { formatRelativity, formatRelativityChange } from '../actuarial.format';
import { DivergingBar, divergingBar, relativityHalfRange } from './chart-scales';

export interface RelativityRow {
  key: string;
  label: string;
  /** Valeur du facteur affichée sous le libellé (ex. « 100 » ou « 100 vs 50 »). */
  detail: string;
  relativity: number;
}

interface PositionedRow extends RelativityRow {
  bar: DivergingBar;
}

/**
 * Barres divergentes de relativités tarifaires, centrées sur ×1 :
 * hausse de prime à droite (rouge), baisse à gauche (bleu), échelle
 * logarithmique pour que ×2 et ×0,5 aient la même longueur.
 */
@Component({
  selector: 'app-relativity-chart',
  standalone: true,
  templateUrl: './relativity-chart.component.html',
  styleUrl: './relativity-chart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class RelativityChartComponent {
  readonly rows = input.required<RelativityRow[]>();
  readonly upLabel = input('Augmente la prime');
  readonly downLabel = input('Diminue la prime');
  readonly caption = input<string>('');

  readonly showTable = signal(false);

  readonly halfRange = computed(() => relativityHalfRange(this.rows().map(r => r.relativity)));

  readonly positioned = computed<PositionedRow[]>(() =>
    this.rows().map(row => ({ ...row, bar: divergingBar(row.relativity, this.halfRange()) }))
  );

  readonly scaleEnds = computed(() => {
    const factor = Math.exp(this.halfRange());
    return { low: formatRelativity(1 / factor), high: formatRelativity(factor) };
  });

  readonly formatRelativity = formatRelativity;
  readonly formatChange = formatRelativityChange;

  describe(row: RelativityRow): string {
    return `${row.label} (${row.detail}) : prime ${formatRelativity(row.relativity)}, ${formatRelativityChange(row.relativity)}`;
  }
}
