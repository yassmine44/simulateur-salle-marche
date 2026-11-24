import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, debounceTime, map, of, scan, startWith, switchMap } from 'rxjs';

import { apiErrorMessage, currencyCodeOf, formatCompactMoney, formatNumber, formatPercent } from '../actuarial.format';
import { StressResult, StressRow, StressSegmentation } from '../actuarial.models';
import { ActuarialService } from '../actuarial.service';

interface StressState {
  result: StressResult | null;
  error: string | null;
  loading: boolean;
}

/** Délai avant recalcul pendant que l'utilisateur fait glisser un curseur. */
export const STRESS_DEBOUNCE_MS = 250;

const INITIAL_STATE: StressState = { result: null, error: null, loading: true };

/**
 * Stress test du portefeuille d'assurance : que deviennent les marges si les
 * sinistres sont plus fréquents ou plus chers que prévu, primes inchangées ?
 */
@Component({
  selector: 'app-stress-panel',
  standalone: true,
  templateUrl: './stress-panel.component.html',
  styleUrl: './stress-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class StressPanelComponent {
  private readonly service = inject(ActuarialService);

  /** Chocs en pourcentage entier (20 = +20 %). */
  readonly frequencyPct = signal(20);
  readonly severityPct = signal(10);
  readonly segmentation = signal<StressSegmentation>('by_risk_class');

  readonly segmentations: ReadonlyArray<{ id: StressSegmentation; label: string }> = [
    { id: 'by_risk_class', label: 'Classe de risque' },
    { id: 'by_area', label: 'Zone géographique' },
    { id: 'by_bonus_malus', label: 'Tranche de bonus-malus' }
  ];

  private readonly request = computed(() => ({
    frequency: this.frequencyPct() / 100,
    severity: this.severityPct() / 100,
    segmentation: this.segmentation()
  }));

  /**
   * Chaque changement de paramètre relance un calcul après une pause ; un
   * calcul plus récent annule le précédent (switchMap). Pendant le calcul, le
   * dernier résultat reste affiché (scan), pour éviter tout clignotement.
   */
  readonly state = toSignal(
    toObservable(this.request).pipe(
      debounceTime(STRESS_DEBOUNCE_MS),
      switchMap(r =>
        this.service.stress(r.frequency, r.severity, r.segmentation).pipe(
          map((result): Partial<StressState> => ({ result, error: null, loading: false })),
          catchError(error => of<Partial<StressState>>({ result: null, error: apiErrorMessage(error), loading: false })),
          startWith<Partial<StressState>>({ loading: true })
        )
      ),
      scan((previous: StressState, update: Partial<StressState>): StressState => ({ ...previous, ...update }), INITIAL_STATE)
    ),
    { initialValue: INITIAL_STATE }
  );

  readonly result = computed(() => this.state().result);

  readonly euros = (value: number) => formatCompactMoney(value, currencyCodeOf(this.result()?.currency));
  readonly multiplier = (value: number) => `×${formatNumber(value, 2)}`;
  readonly percent = formatPercent;

  signedPercent(ratio: number): string {
    const rounded = Math.round(ratio * 100);
    return `${rounded > 0 ? '+' : rounded < 0 ? '−' : ''}${Math.abs(rounded)} %`;
  }

  segmentLabel(row: StressRow): string {
    switch (this.segmentation()) {
      case 'by_risk_class':
        return `Classe ${row.segment}`;
      case 'by_area':
        return `Zone ${row.segment}`;
      default:
        return String(row.segment);
    }
  }

  /** Largeur de la barre S/P, l'échelle allant jusqu'à 150 % (marqueur à 100 %). */
  barWidth(lossRatio: number): number {
    return Math.min(lossRatio / 1.5, 1) * 100;
  }

  onSlider(target: 'frequency' | 'severity', event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    (target === 'frequency' ? this.frequencyPct : this.severityPct).set(value);
  }

  onSegmentation(event: Event): void {
    this.segmentation.set((event.target as HTMLSelectElement).value as StressSegmentation);
  }
}
