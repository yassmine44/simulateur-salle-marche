import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators
} from '@angular/forms';
import { finalize, map, startWith } from 'rxjs';

import { apiErrorMessage, currencyCodeOf, formatMoney, formatNumber, formatPercent } from '../actuarial.format';
import { AdminOverview, AdminQuote, ModelCard } from '../actuarial.models';
import { actionLabel, describeAction } from './admin-actions';
import { ActuarialAdminService } from './actuarial-admin.service';

type AssumptionsForm = FormGroup<{
  expensePct: FormControl<number | null>;
  profitPct: FormControl<number | null>;
  safetyPct: FormControl<number | null>;
}>;

function ratiosBelowHundred(group: AbstractControl): ValidationErrors | null {
  const { expensePct, profitPct } = group.value as { expensePct: number; profitPct: number };
  return (expensePct ?? 0) + (profitPct ?? 0) >= 100 ? { ratioSum: true } : null;
}

/** Administration du module d'actuariat (cahier des charges §5). */
@Component({
  selector: 'app-actuarial-admin',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule],
  templateUrl: './actuarial-admin.component.html',
  styleUrl: './actuarial-admin.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ActuarialAdminComponent {
  private readonly admin = inject(ActuarialAdminService);
  private readonly destroyRef = inject(DestroyRef);

  readonly overview = signal<AdminOverview | null>(null);
  readonly quotes = signal<AdminQuote[]>([]);
  readonly loadError = signal<string | null>(null);
  readonly saving = signal(false);
  readonly reloading = signal(false);
  readonly message = signal<{ kind: 'ok' | 'error'; text: string } | null>(null);

  readonly form: AssumptionsForm = new FormGroup(
    {
      expensePct: new FormControl<number | null>(null, [Validators.required, Validators.min(0), Validators.max(99)]),
      profitPct: new FormControl<number | null>(null, [Validators.required, Validators.min(0), Validators.max(99)]),
      safetyPct: new FormControl<number | null>(null, [Validators.required, Validators.min(0), Validators.max(100)])
    },
    { validators: ratiosBelowHundred }
  );

  private readonly formValue = toSignal(
    this.form.valueChanges.pipe(
      startWith(this.form.value),
      map(() => this.form.getRawValue())
    ),
    { initialValue: this.form.getRawValue() }
  );

  /** Aperçu du multiplicateur prime pure → prime commerciale avant d'enregistrer. */
  readonly previewFactor = computed(() => {
    const { expensePct, profitPct, safetyPct } = this.formValue();
    const remaining = 1 - ((expensePct ?? 0) + (profitPct ?? 0)) / 100;
    return remaining > 0 ? (1 + (safetyPct ?? 0) / 100) / remaining : null;
  });

  /** Vrai si le formulaire reprend exactement les hypothèses en vigueur (rien à enregistrer). */
  readonly unchanged = computed(() => {
    const current = this.overview()?.engine?.assumptions;
    if (!current) {
      return true;
    }
    const { expensePct, profitPct, safetyPct } = this.formValue();
    const same = (pct: number | null, ratio: number) => Math.abs((pct ?? NaN) / 100 - ratio) < 1e-9;
    return (
      same(expensePct, current.expenseRatio) &&
      same(profitPct, current.profitRatio) &&
      same(safetyPct, current.safetyLoading)
    );
  });

  readonly currencyForm = new FormGroup({
    code: new FormControl('TND', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[A-Z]{3}$/)] }),
    label: new FormControl('dinar tunisien', { nonNullable: true, validators: [Validators.required, Validators.maxLength(60)] }),
    rateFromModel: new FormControl<number | null>(null, [Validators.required, Validators.min(0.0001)]),
    source: new FormControl<string | null>(null, [Validators.maxLength(200)])
  });
  readonly savingCurrency = signal(false);

  readonly cards = computed<ModelCard[]>(() => {
    const cards = this.overview()?.modelCards;
    return cards ? [cards.frequency_model, cards.severity_model] : [];
  });

  readonly money = (value: number, currency?: string | null) => formatMoney(value, currencyCodeOf(currency));
  /** Devise d'affichage en vigueur (statistiques par version). */
  readonly engineCurrency = computed(() => currencyCodeOf(this.overview()?.engine?.currency));
  readonly percent = formatPercent;
  readonly number = formatNumber;
  readonly actionLabel = actionLabel;
  readonly describeAction = describeAction;

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.admin
      .overview()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: overview => {
          this.overview.set(overview);
          this.loadError.set(null);
          const currency = overview.engine?.currency;
          if (currency && this.currencyForm.pristine) {
            this.currencyForm.reset({
              code: currency.code,
              label: currency.label,
              rateFromModel: currency.rateFromModel,
              source: currency.source
            });
          }
          const assumptions = overview.engine?.assumptions;
          if (assumptions && this.form.pristine) {
            this.form.reset({
              expensePct: Math.round(assumptions.expenseRatio * 1000) / 10,
              profitPct: Math.round(assumptions.profitRatio * 1000) / 10,
              safetyPct: Math.round(assumptions.safetyLoading * 1000) / 10
            });
          }
        },
        error: error => this.loadError.set(apiErrorMessage(error))
      });
    this.admin
      .quotes(20)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: quotes => this.quotes.set(quotes), error: () => this.quotes.set([]) });
  }

  saveAssumptions(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { expensePct, profitPct, safetyPct } = this.form.getRawValue();
    this.saving.set(true);
    this.message.set(null);
    this.admin
      .updateAssumptions({
        expenseRatio: (expensePct ?? 0) / 100,
        profitRatio: (profitPct ?? 0) / 100,
        safetyLoading: (safetyPct ?? 0) / 100
      })
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.message.set({ kind: 'ok', text: 'Hypothèses enregistrées : elles s\'appliquent dès maintenant à tous les nouveaux devis.' });
          this.form.markAsPristine();
          this.refresh();
        },
        error: error => this.message.set({ kind: 'error', text: apiErrorMessage(error) })
      });
  }

  saveCurrency(): void {
    if (this.currencyForm.invalid) {
      this.currencyForm.markAllAsTouched();
      return;
    }
    const value = this.currencyForm.getRawValue();
    this.savingCurrency.set(true);
    this.message.set(null);
    this.admin
      .updateCurrency({ ...value, rateFromModel: value.rateFromModel ?? 0, source: value.source || null })
      .pipe(
        finalize(() => this.savingCurrency.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.message.set({ kind: 'ok', text: `Devise enregistrée : les montants s'affichent désormais en ${value.label}.` });
          this.currencyForm.markAsPristine();
          this.refresh();
        },
        error: error => this.message.set({ kind: 'error', text: apiErrorMessage(error) })
      });
  }

  reloadModels(): void {
    this.reloading.set(true);
    this.message.set(null);
    this.admin
      .reload()
      .pipe(
        finalize(() => this.reloading.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: () => {
          this.message.set({ kind: 'ok', text: 'Modèles rechargés.' });
          this.refresh();
        },
        error: error => this.message.set({ kind: 'error', text: apiErrorMessage(error) })
      });
  }
}
