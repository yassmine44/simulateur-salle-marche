import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

import { formatPercent } from '../actuarial.format';

export interface ImportanceRow {
  key: string;
  label: string;
  /** Part de l'importance totale, entre 0 et 1. */
  share: number;
}

/** Importance globale des variables : barres horizontales d'une seule couleur. */
@Component({
  selector: 'app-importance-chart',
  standalone: true,
  templateUrl: './importance-chart.component.html',
  styleUrl: './importance-chart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ImportanceChartComponent {
  readonly rows = input.required<ImportanceRow[]>();
  readonly caption = input<string>('');

  readonly showTable = signal(false);

  readonly bars = computed(() => {
    const largest = Math.max(...this.rows().map(r => r.share), 1e-9);
    return this.rows().map(row => ({ ...row, width: (row.share / largest) * 100 }));
  });

  readonly formatPercent = formatPercent;
}
