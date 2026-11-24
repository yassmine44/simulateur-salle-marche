import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { apiErrorMessage, formatCompactUsd, formatNumber, formatPercent } from '../actuarial.format';
import { ChainLadderResult, ReservingReport } from '../actuarial.models';
import { ActuarialService } from '../actuarial.service';
import { ReservingResultComponent } from '../reserving/reserving-result.component';
import { parseTriangle, TAYLOR_ASHE_EXAMPLE, TriangleParseError } from '../reserving/triangle-parser';

type Mode = 'portfolios' | 'simulation';

/** Provisionnement des sinistres (cahier des charges §3.7). */
@Component({
  selector: 'app-reserving-page',
  standalone: true,
  imports: [ReactiveFormsModule, ReservingResultComponent],
  templateUrl: './reserving-page.component.html',
  styleUrl: './reserving-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ReservingPageComponent {
  private readonly service = inject(ActuarialService);
  private readonly destroyRef = inject(DestroyRef);

  readonly mode = signal<Mode>('portfolios');

  // --- Portefeuilles réels ------------------------------------------------------
  readonly report = signal<ReservingReport | null>(null);
  readonly reportError = signal<string | null>(null);
  readonly selectedCode = signal('market');
  readonly selected = computed(
    () => this.report()?.portfolios.find(p => p.code === this.selectedCode()) ?? null
  );

  readonly overestimated = computed(
    () => this.report()?.portfolios.filter(p => p.backtest.total.error > 0).length ?? 0
  );

  /** Montants du rapport en milliers de dollars. */
  readonly thousandsUsd = (v: number) => formatCompactUsd(v * 1000);
  readonly plain = (v: number) => formatNumber(v, 0);
  readonly percent = formatPercent;

  // --- Simulation ---------------------------------------------------------------
  readonly triangleText = new FormControl(TAYLOR_ASHE_EXAMPLE, { nonNullable: true });
  readonly confidence = new FormControl(0.95, { nonNullable: true });
  readonly confidenceLevels = [0.8, 0.9, 0.95, 0.99];
  readonly computing = signal(false);
  readonly simulationError = signal<string | null>(null);
  readonly simulation = signal<ChainLadderResult | null>(null);

  constructor() {
    this.service
      .report<ReservingReport>('reserving')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: report => this.report.set(report),
        error: error => this.reportError.set(apiErrorMessage(error))
      });
  }

  onPortfolio(event: Event): void {
    this.selectedCode.set((event.target as HTMLSelectElement).value);
  }

  loadExample(): void {
    this.triangleText.setValue(TAYLOR_ASHE_EXAMPLE);
    this.simulation.set(null);
    this.simulationError.set(null);
  }

  simulate(): void {
    let parsed;
    try {
      parsed = parseTriangle(this.triangleText.value);
    } catch (error) {
      this.simulationError.set(error instanceof TriangleParseError ? error.message : 'Triangle illisible.');
      this.simulation.set(null);
      return;
    }
    this.computing.set(true);
    this.simulationError.set(null);
    this.service
      .chainLadder(parsed.triangle, this.confidence.value, parsed.origins)
      .pipe(
        finalize(() => this.computing.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: result => this.simulation.set(result),
        error: error => {
          this.simulation.set(null);
          this.simulationError.set(apiErrorMessage(error));
        }
      });
  }
}
