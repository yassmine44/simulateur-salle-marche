import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { formatMoney, formatPercent, MODEL_CURRENCY } from '../actuarial.format';
import { PricingAssumptions } from '../actuarial.models';

export interface PremiumPart {
  key: 'claims' | 'expenses' | 'safety' | 'profit';
  label: string;
  hint: string;
  amount: number;
  share: number;
}

/**
 * Décomposition de la prime commerciale P :
 *   P = prime pure + sécurité + frais + bénéfice
 * avec sécurité = prime pure × s, frais = P × f, bénéfice = P × b
 * (identité de P = prime pure × (1 + s) / (1 − f − b)).
 */
export function premiumParts(
  purePremium: number,
  commercialPremium: number,
  assumptions: PricingAssumptions
): PremiumPart[] {
  const parts: Omit<PremiumPart, 'share'>[] = [
    { key: 'claims', label: 'Sinistres attendus', hint: 'Prime pure : coût moyen des sinistres de ce profil', amount: purePremium },
    { key: 'expenses', label: 'Frais', hint: 'Gestion des contrats et des sinistres, acquisition', amount: commercialPremium * assumptions.expenseRatio },
    { key: 'safety', label: 'Sécurité', hint: 'Marge de prudence face à l\'incertitude des modèles', amount: purePremium * assumptions.safetyLoading },
    { key: 'profit', label: 'Bénéfice', hint: 'Rémunération du capital de l\'assureur', amount: commercialPremium * assumptions.profitRatio }
  ];
  return parts.map(part => ({ ...part, share: commercialPremium > 0 ? part.amount / commercialPremium : 0 }));
}

/** « Où va votre prime ? » : barre segmentée et légende chiffrée. */
@Component({
  selector: 'app-premium-breakdown',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <figure class="breakdown" [attr.aria-label]="ariaLabel()">
      <div class="bar" role="presentation">
        @for (part of parts(); track part.key) {
          @if (part.share > 0) {
            <span [class]="'segment ' + part.key" [style.flex-grow]="part.share" [title]="part.label + ' : ' + money(part.amount)"></span>
          }
        }
      </div>
      <ul class="legend">
        @for (part of parts(); track part.key) {
          <li>
            <span [class]="'swatch ' + part.key" aria-hidden="true"></span>
            <span class="text">
              <span class="label">{{ part.label }}</span>
              <span class="hint">{{ part.hint }}</span>
            </span>
            <span class="figures">
              <strong>{{ money(part.amount) }}</strong>
              <span>{{ percent(part.share, 0) }}</span>
            </span>
          </li>
        }
      </ul>
    </figure>
  `,
  styleUrl: './premium-breakdown.component.scss'
})
export class PremiumBreakdownComponent {
  readonly purePremium = input.required<number>();
  readonly commercialPremium = input.required<number>();
  readonly assumptions = input.required<PricingAssumptions>();
  readonly currency = input<string>(MODEL_CURRENCY);

  readonly parts = computed(() => premiumParts(this.purePremium(), this.commercialPremium(), this.assumptions()));

  readonly ariaLabel = computed(() =>
    'Répartition de la prime : ' +
    this.parts().map(p => `${p.label} ${this.money(p.amount)} (${formatPercent(p.share, 0)})`).join(', ')
  );

  readonly percent = formatPercent;

  money(value: number): string {
    return formatMoney(value, this.currency());
  }
}
