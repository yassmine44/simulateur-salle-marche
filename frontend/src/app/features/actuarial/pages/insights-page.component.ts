import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';

import {
  apiErrorMessage,
  currencyCodeOf,
  formatCompactMoney,
  formatMoney,
  formatNumber,
  formatPercent,
  formatRelativity
} from '../actuarial.format';
import {
  CandidateSummary,
  DependencePoint,
  ExplainabilityReport,
  LorenzCurve,
  MarginRow,
  ModelReport,
  PricingReport
} from '../actuarial.models';
import { ActuarialService } from '../actuarial.service';
import { ImportanceChartComponent, ImportanceRow } from '../charts/importance-chart.component';
import { LineChartComponent, LineSeries } from '../charts/line-chart.component';
import { RelativityChartComponent, RelativityRow } from '../charts/relativity-chart.component';
import { Point } from '../charts/chart-scales';
import { StressPanelComponent } from '../stress/stress-panel.component';

interface Reports {
  frequency: ModelReport;
  severity: ModelReport;
  pricing: PricingReport;
  explainability: ExplainabilityReport;
}

type Segmentation = 'by_risk_class' | 'by_area' | 'by_bonus_malus';

/** Libellés des modèles candidats (noms techniques du pipeline). */
const CANDIDATE_LABELS: Record<string, string> = {
  baseline: 'Moyenne simple (référence)',
  poisson_glm: 'GLM de Poisson',
  poisson_gbm: 'Gradient boosting (Poisson)',
  gamma_glm: 'GLM Gamma',
  gamma_gbm: 'Gradient boosting (Gamma)'
};

/** Une valeur de dépendance n'est affichée qu'à partir de ce nombre de contrats. */
const MIN_DEPENDENCE_COUNT = 5;

function lorenzPoints(curve: LorenzCurve): Point[] {
  return curve.cumulative_weight.map((x, i) => ({ x, y: curve.cumulative_total[i] }));
}

@Component({
  selector: 'app-insights-page',
  standalone: true,
  imports: [NgTemplateOutlet, ImportanceChartComponent, LineChartComponent, RelativityChartComponent, StressPanelComponent],
  templateUrl: './insights-page.component.html',
  styleUrl: './insights-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class InsightsPageComponent {
  private readonly service = inject(ActuarialService);
  private readonly destroyRef = inject(DestroyRef);

  readonly reports = signal<Reports | null>(null);
  readonly error = signal<string | null>(null);
  readonly segmentation = signal<Segmentation>('by_risk_class');
  readonly dependenceFeature = signal('BonusMalus');

  // Constantes passées aux graphiques (références stables entre deux détections).
  readonly unitDomain: [number, number] = [0, 1];
  readonly neutralLine = { y: 1 };
  readonly minDependenceCount = MIN_DEPENDENCE_COUNT;

  readonly segmentations: ReadonlyArray<{ id: Segmentation; label: string }> = [
    { id: 'by_risk_class', label: 'Classe de risque (décile de prime)' },
    { id: 'by_area', label: 'Zone géographique' },
    { id: 'by_bonus_malus', label: 'Tranche de bonus-malus' }
  ];

  // --- Indicateurs clés -------------------------------------------------------
  readonly kpis = computed(() => {
    const pricing = this.reports()?.pricing;
    if (!pricing) {
      return null;
    }
    const portfolio = pricing.margin.portfolio[0];
    return {
      gini: pricing.pure_premium_metrics.gini,
      cappedCalibration: pricing.large_claims_diagnostic.capped_calibration_ratio,
      cappedGap: Math.abs(1 - pricing.large_claims_diagnostic.capped_calibration_ratio),
      totalCalibration: pricing.pure_premium_metrics.calibration_ratio,
      expectedLossRatio: portfolio.expected_loss_ratio,
      actualLossRatio: portfolio.actual_loss_ratio,
      threshold: pricing.large_claim_cap.threshold,
      loading: pricing.large_claim_cap.loading,
      trainExcess: pricing.large_claims_diagnostic.train_excess_share,
      testExcess: pricing.large_claims_diagnostic.test_excess_share
    };
  });

  // --- Comparaison des modèles -------------------------------------------------
  readonly frequencyCandidates = computed(() => this.candidates(this.reports()?.frequency));
  readonly severityCandidates = computed(() => this.candidates(this.reports()?.severity));

  // --- Courbe de Lorenz et calibration -----------------------------------------
  readonly lorenzSeries = computed<LineSeries[]>(() => {
    const report = this.reports()?.frequency;
    if (!report) {
      return [];
    }
    return [
      {
        key: 'champion',
        name: `Modèle retenu (${CANDIDATE_LABELS[report.champion] ?? report.champion})`,
        points: lorenzPoints(report.lorenz_curves[report.champion]),
        tone: 'series-1'
      },
      {
        key: 'oracle',
        name: 'Classement parfait (oracle)',
        points: lorenzPoints(report.lorenz_curves['oracle']),
        tone: 'series-2'
      }
    ];
  });

  readonly calibrationSeries = computed<LineSeries[]>(() => {
    const rows = this.reports()?.frequency.calibration ?? [];
    return [
      {
        key: 'predicted',
        name: 'Fréquence prédite',
        points: rows.map(r => ({ x: r.risk_class, y: r.predicted_mean })),
        tone: 'series-1'
      },
      {
        key: 'observed',
        name: 'Fréquence observée',
        points: rows.map(r => ({ x: r.risk_class, y: r.observed_mean })),
        tone: 'series-2'
      }
    ];
  });

  // --- Explicabilité globale ---------------------------------------------------
  readonly importance = computed<ImportanceRow[]>(() =>
    (this.reports()?.explainability.global_importance.premium ?? []).map(row => ({
      key: row.feature,
      label: row.label,
      share: row.share
    }))
  );

  readonly dependenceOptions = computed(() =>
    (this.reports()?.explainability.global_importance.premium ?? []).map(row => ({
      feature: row.feature,
      label: row.label
    }))
  );

  readonly dependencePoints = computed<DependencePoint[]>(() =>
    (this.reports()?.explainability.dependence[this.dependenceFeature()] ?? []).filter(
      point => point.count >= MIN_DEPENDENCE_COUNT
    )
  );

  readonly dependenceIsNumeric = computed(() =>
    this.dependencePoints().every(point => typeof point.value === 'number')
  );

  readonly dependenceSeries = computed<LineSeries[]>(() => [
    {
      key: 'relativity',
      name: 'Multiplicateur moyen',
      points: this.dependencePoints().map(p => ({ x: Number(p.value), y: p.relativity })),
      tone: 'series-1'
    }
  ]);

  readonly dependenceRows = computed<RelativityRow[]>(() =>
    this.dependencePoints().map(p => ({
      key: String(p.value),
      label: String(p.value),
      detail: `${p.count} contrat${p.count > 1 ? 's' : ''}`,
      relativity: p.relativity
    }))
  );

  readonly dependenceLabel = computed(
    () => this.dependenceOptions().find(o => o.feature === this.dependenceFeature())?.label ?? ''
  );

  // --- Marges par segment ------------------------------------------------------
  readonly marginRows = computed<MarginRow[]>(
    () => this.reports()?.pricing.margin[this.segmentation()] ?? []
  );

  /** Devise des montants du rapport de tarification (convertis par le moteur). */
  readonly currencyCode = computed(() => currencyCodeOf(this.reports()?.pricing.currency));

  readonly format = {
    euros: (v: number) => formatMoney(v, this.currencyCode()),
    compact: (v: number) => formatCompactMoney(v, this.currencyCode()),
    percent: formatPercent,
    number: formatNumber,
    relativity: formatRelativity,
    share: (v: number) => formatPercent(v, 0),
    decile: (v: number) => `${Math.round(v)}`,
    frequency: (v: number) => formatPercent(v, 1),
    plain: (v: number) => formatNumber(v, 0)
  };

  constructor() {
    forkJoin({
      frequency: this.service.report<ModelReport>('frequency'),
      severity: this.service.report<ModelReport>('severity'),
      pricing: this.service.report<PricingReport>('pricing'),
      explainability: this.service.report<ExplainabilityReport>('explainability')
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: reports => this.reports.set(reports),
        error: error => this.error.set(apiErrorMessage(error))
      });
  }

  candidateLabel(name: string): string {
    return CANDIDATE_LABELS[name] ?? name;
  }

  segmentLabel(row: MarginRow): string {
    if (this.segmentation() === 'by_risk_class') {
      return `Classe ${row.segment}`;
    }
    if (this.segmentation() === 'by_area') {
      return `Zone ${row.segment}`;
    }
    return String(row.segment);
  }

  onSegmentation(event: Event): void {
    this.segmentation.set((event.target as HTMLSelectElement).value as Segmentation);
  }

  onDependenceFeature(event: Event): void {
    this.dependenceFeature.set((event.target as HTMLSelectElement).value);
  }

  private candidates(report: ModelReport | undefined): Array<CandidateSummary & { champion: boolean }> {
    return (report?.candidates ?? []).map(c => ({ ...c, champion: c.name === report?.champion }));
  }
}
