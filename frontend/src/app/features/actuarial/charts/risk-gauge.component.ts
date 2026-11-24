import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { formatNumber } from '../actuarial.format';

/** Bornes de la jauge en multiples du risque moyen (échelle logarithmique). */
export const GAUGE_MIN = 0.25;
export const GAUGE_MAX = 8;

export interface RiskLevel {
  label: string;
  tone: 'low' | 'average' | 'high' | 'very-high';
}

export function riskLevel(relativeRisk: number): RiskLevel {
  if (relativeRisk < 0.8) {
    return { label: 'Risque faible', tone: 'low' };
  }
  if (relativeRisk <= 1.25) {
    return { label: 'Risque moyen', tone: 'average' };
  }
  if (relativeRisk <= 2.5) {
    return { label: 'Risque élevé', tone: 'high' };
  }
  return { label: 'Risque très élevé', tone: 'very-high' };
}

/** Position (0 à 1) d'un multiple du risque moyen sur la jauge logarithmique. */
export function gaugePosition(relativeRisk: number): number {
  const clamped = Math.min(Math.max(relativeRisk, GAUGE_MIN), GAUGE_MAX);
  return Math.log(clamped / GAUGE_MIN) / Math.log(GAUGE_MAX / GAUGE_MIN);
}

const CX = 100;
const CY = 100;
const R = 80;

/** Point de l'arc (demi-cercle de gauche à droite) à la position t ∈ [0, 1]. */
function arcPoint(t: number, radius = R): { x: number; y: number } {
  const angle = Math.PI * (1 - t);
  return { x: CX + radius * Math.cos(angle), y: CY - radius * Math.sin(angle) };
}

/**
 * Indice de risque : prime pure de l'assuré rapportée à la prime pure moyenne
 * du portefeuille (×1 = assuré moyen), sur une jauge logarithmique où ×2 et
 * ×0,5 sont à égale distance de la moyenne.
 */
@Component({
  selector: 'app-risk-gauge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <figure class="gauge" role="img" [attr.aria-label]="ariaLabel()">
      <svg viewBox="0 0 200 118" aria-hidden="true">
        <path class="track" [attr.d]="trackPath" />
        <path class="value" [attr.d]="valuePath()" />
        <line class="average" [attr.x1]="averageTick.inner.x" [attr.y1]="averageTick.inner.y"
              [attr.x2]="averageTick.outer.x" [attr.y2]="averageTick.outer.y" />
        <circle class="needle" [attr.cx]="needle().x" [attr.cy]="needle().y" r="7" />
        <text class="end" x="20" y="116">×{{ format(min) }}</text>
        <text class="end" x="180" y="116" text-anchor="end">×{{ format(max) }}</text>
      </svg>
      <div class="reading">
        <span class="multiple">×{{ format(relativeRisk()) }}</span>
        <span class="caption">le risque moyen</span>
        <span [class]="'level ' + level().tone">{{ level().label }}</span>
      </div>
    </figure>
  `,
  styleUrl: './risk-gauge.component.scss'
})
export class RiskGaugeComponent {
  /** Prime pure de l'assuré ÷ prime pure moyenne du portefeuille. */
  readonly relativeRisk = input.required<number>();

  readonly min = GAUGE_MIN;
  readonly max = GAUGE_MAX;

  readonly trackPath = `M${arcPoint(0).x},${arcPoint(0).y} A${R},${R} 0 0 1 ${arcPoint(1).x},${arcPoint(1).y}`;

  readonly averageTick = (() => {
    const t = gaugePosition(1);
    return { inner: arcPoint(t, R - 14), outer: arcPoint(t, R + 14) };
  })();

  readonly position = computed(() => gaugePosition(this.relativeRisk()));
  readonly needle = computed(() => arcPoint(this.position()));
  readonly valuePath = computed(() => {
    const end = this.needle();
    return `M${arcPoint(0).x},${arcPoint(0).y} A${R},${R} 0 0 1 ${end.x.toFixed(2)},${end.y.toFixed(2)}`;
  });

  readonly level = computed(() => riskLevel(this.relativeRisk()));

  readonly ariaLabel = computed(
    () => `Indice de risque : ${this.format(this.relativeRisk())} fois le risque moyen du portefeuille, ${this.level().label.toLowerCase()}.`
  );

  format(value: number): string {
    return formatNumber(value, value < 1 ? 2 : 1);
  }
}
