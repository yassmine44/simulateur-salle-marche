import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';

import { apiErrorMessage, formatEuros, formatNumber } from '../actuarial.format';
import { ActuarialMetadata, ComparisonResponse, PROFILE_PRESETS } from '../actuarial.models';
import { ActuarialService } from '../actuarial.service';
import { RelativityChartComponent, RelativityRow } from '../charts/relativity-chart.component';
import { createProfileForm, ProfileForm, profileValue } from '../profile-form/profile-form';
import { ProfileFormComponent } from '../profile-form/profile-form.component';

/** Explique l'écart de prime entre deux assurés (XAI, cahier des charges §3.6). */
@Component({
  selector: 'app-compare-page',
  standalone: true,
  imports: [ProfileFormComponent, RelativityChartComponent],
  templateUrl: './compare-page.component.html',
  styleUrl: './compare-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ComparePageComponent {
  private readonly service = inject(ActuarialService);
  private readonly destroyRef = inject(DestroyRef);

  readonly metadata = signal<ActuarialMetadata | null>(null);
  readonly loadError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);
  readonly result = signal<ComparisonResponse | null>(null);

  formA: ProfileForm | null = null;
  formB: ProfileForm | null = null;

  readonly rows = computed<RelativityRow[]>(() =>
    (this.result()?.contributions ?? []).map(c => ({
      key: c.field,
      label: c.label,
      detail: c.valueA === c.valueB ? `${c.valueA} (identique)` : `${c.valueA} vs ${c.valueB}`,
      relativity: c.relativity
    }))
  );

  readonly headline = computed(() => {
    const result = this.result();
    if (!result) {
      return null;
    }
    const ratio = result.premiumRatio;
    return ratio >= 1
      ? `L'assuré A paie ${formatNumber(ratio, 2)} fois la prime pure de l'assuré B.`
      : `L'assuré A paie ${formatNumber(1 / ratio, 2)} fois moins que l'assuré B.`;
  });

  readonly euros = formatEuros;

  constructor() {
    this.service
      .metadata()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: metadata => {
          this.formA = createProfileForm(metadata.fields, PROFILE_PRESETS[0].profile);
          this.formB = createProfileForm(metadata.fields, PROFILE_PRESETS[1].profile);
          this.metadata.set(metadata);
        },
        error: error => this.loadError.set(apiErrorMessage(error))
      });
  }

  swap(): void {
    if (!this.formA || !this.formB) {
      return;
    }
    const a = this.formA.getRawValue();
    this.formA.setValue(this.formB.getRawValue());
    this.formB.setValue(a);
    this.result.set(null);
  }

  submit(): void {
    const metadata = this.metadata();
    if (!metadata || !this.formA || !this.formB) {
      return;
    }
    if (this.formA.invalid || this.formB.invalid) {
      this.formA.markAllAsTouched();
      this.formB.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.submitError.set(null);
    this.service
      .compare(profileValue(this.formA, metadata.fields), profileValue(this.formB, metadata.fields))
      .pipe(
        finalize(() => this.submitting.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: result => this.result.set(result),
        error: error => this.submitError.set(apiErrorMessage(error))
      });
  }
}
