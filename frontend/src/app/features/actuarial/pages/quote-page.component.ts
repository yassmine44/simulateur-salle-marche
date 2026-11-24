import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { finalize } from 'rxjs';

import {
  apiErrorMessage,
  formatEuros,
  formatNumber,
  formatPercent,
  formatRelativity,
  formatRelativityChange
} from '../actuarial.format';
import {
  ActuarialMetadata,
  Contribution,
  PricingAssumptions,
  PROFILE_PRESETS,
  QuoteHistoryItem,
  QuoteResponse
} from '../actuarial.models';
import { ActuarialService } from '../actuarial.service';
import { RelativityChartComponent, RelativityRow } from '../charts/relativity-chart.component';
import { createProfileForm, ProfileForm, profileValue } from '../profile-form/profile-form';
import { ProfileFormComponent } from '../profile-form/profile-form.component';

type AssumptionsForm = FormGroup<{
  expensePct: FormControl<number | null>;
  profitPct: FormControl<number | null>;
  safetyPct: FormControl<number | null>;
  years: FormControl<number | null>;
}>;

/** Frais + bénéfice doivent laisser une part de la prime pour les sinistres. */
function ratiosBelowHundred(group: AbstractControl): ValidationErrors | null {
  const { expensePct, profitPct } = group.value as { expensePct: number; profitPct: number };
  return (expensePct ?? 0) + (profitPct ?? 0) >= 100 ? { ratioSum: true } : null;
}

@Component({
  selector: 'app-quote-page',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe, ProfileFormComponent, RelativityChartComponent],
  templateUrl: './quote-page.component.html',
  styleUrl: './quote-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class QuotePageComponent {
  private readonly service = inject(ActuarialService);
  private readonly destroyRef = inject(DestroyRef);

  readonly metadata = signal<ActuarialMetadata | null>(null);
  readonly loadError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);
  readonly quote = signal<QuoteResponse | null>(null);
  readonly history = signal<QuoteHistoryItem[]>([]);

  profileForm: ProfileForm | null = null;
  readonly assumptionsForm: AssumptionsForm = new FormGroup(
    {
      expensePct: new FormControl<number | null>(25, [Validators.required, Validators.min(0), Validators.max(99)]),
      profitPct: new FormControl<number | null>(5, [Validators.required, Validators.min(0), Validators.max(99)]),
      safetyPct: new FormControl<number | null>(3, [Validators.required, Validators.min(0), Validators.max(100)]),
      years: new FormControl<number | null>(1, [Validators.required, Validators.min(0.1), Validators.max(10)])
    },
    { validators: ratiosBelowHundred }
  );
  readonly showAssumptions = signal(false);

  readonly rows = computed<RelativityRow[]>(() =>
    (this.quote()?.explanation?.contributions ?? []).map(c => ({
      key: c.field,
      label: c.label,
      detail: String(c.value),
      relativity: c.relativity
    }))
  );

  /** Phrase de lecture : les deux facteurs les plus influents. */
  readonly reading = computed(() => {
    const contributions = (this.quote()?.explanation?.contributions ?? []).filter(
      c => c.direction !== 'neutre'
    );
    return contributions.slice(0, 2).map(c => this.describe(c));
  });

  readonly format = { euros: formatEuros, percent: formatPercent, number: formatNumber };

  constructor() {
    this.service
      .metadata()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: metadata => {
          this.profileForm = createProfileForm(metadata.fields, PROFILE_PRESETS[0].profile);
          this.applyDefaultAssumptions(metadata.defaultAssumptions);
          this.metadata.set(metadata);
        },
        error: error => this.loadError.set(apiErrorMessage(error))
      });
    this.loadHistory();
  }

  /**
   * Le <form> n'est pas lié à un FormGroup (le profil et les hypothèses sont
   * deux formulaires distincts) : on empêche la soumission native, qui
   * rechargerait la page.
   */
  onSubmit(event: Event): void {
    event.preventDefault();
    this.submit();
  }

  submit(): void {
    const metadata = this.metadata();
    if (!metadata || !this.profileForm) {
      return;
    }
    if (this.profileForm.invalid || this.assumptionsForm.invalid) {
      this.profileForm.markAllAsTouched();
      this.assumptionsForm.markAllAsTouched();
      this.showAssumptions.set(this.showAssumptions() || this.assumptionsForm.invalid);
      return;
    }

    const { expensePct, profitPct, safetyPct, years } = this.assumptionsForm.getRawValue();
    this.submitting.set(true);
    this.submitError.set(null);
    this.service
      .quote({
        profile: profileValue(this.profileForm, metadata.fields),
        years: years ?? 1,
        assumptions: {
          expenseRatio: (expensePct ?? 0) / 100,
          profitRatio: (profitPct ?? 0) / 100,
          safetyLoading: (safetyPct ?? 0) / 100
        },
        explain: true
      })
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: quote => {
          this.quote.set(quote);
          this.loadHistory();
        },
        error: error => this.submitError.set(apiErrorMessage(error))
      });
  }

  resetAssumptions(): void {
    const defaults = this.metadata()?.defaultAssumptions;
    if (defaults) {
      this.applyDefaultAssumptions(defaults);
    }
  }

  private applyDefaultAssumptions(defaults: PricingAssumptions): void {
    this.assumptionsForm.patchValue({
      expensePct: Math.round(defaults.expenseRatio * 1000) / 10,
      profitPct: Math.round(defaults.profitRatio * 1000) / 10,
      safetyPct: Math.round(defaults.safetyLoading * 1000) / 10
    });
  }

  private loadHistory(): void {
    this.service
      .history(8)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: items => this.history.set(items),
        // L'historique est secondaire : son absence ne bloque pas la tarification.
        error: () => this.history.set([])
      });
  }

  private describe(contribution: Contribution): string {
    const verb = contribution.relativity > 1 ? 'augmente' : 'réduit';
    return `${contribution.label} (${contribution.value}) ${verb} la prime : ${formatRelativity(
      contribution.relativity
    )}, soit ${formatRelativityChange(contribution.relativity)}.`;
  }
}
