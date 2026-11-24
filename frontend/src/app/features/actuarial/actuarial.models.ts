/**
 * Contrat de l'API /api/actuarial (Spring Boot), miroir des DTO Java
 * et du moteur Python (ml-service/actuarial/api.py).
 */

// ---------------------------------------------------------------------------
// Profil, hypothèses, devis
// ---------------------------------------------------------------------------
export interface PolicyProfile {
  driverAge: number;
  bonusMalus: number;
  vehicleAge: number;
  vehiclePower: number;
  density: number;
  area: string;
  vehicleBrand: string;
  vehicleGas: string;
  region: string;
}

export type ProfileField = keyof PolicyProfile;

export interface PricingAssumptions {
  expenseRatio: number;
  profitRatio: number;
  safetyLoading: number;
  /** Présent en réponse uniquement. */
  commercialFactor?: number;
}

export interface QuoteRequest {
  profile: PolicyProfile;
  years?: number;
  assumptions?: PricingAssumptions;
  explain?: boolean;
  top?: number;
}

export type Direction = 'hausse' | 'baisse' | 'neutre';

export interface Contribution {
  feature: string;
  field: ProfileField;
  label: string;
  value: number | string;
  phi: number;
  relativity: number;
  frequencyPhi: number;
  severityPhi: number;
  direction: Direction;
}

export interface QuoteResponse {
  years: number;
  frequency: number;
  claimProbability: number;
  severity: number;
  largeClaimLoading: number;
  purePremium: number;
  commercialPremium: number;
  premiumMargin: number;
  expectedLossRatio: number;
  assumptions: PricingAssumptions;
  modelVersions: { frequency: string; severity: string };
  explanation: {
    basePremium: number;
    purePremium: number;
    contributions: Contribution[];
  } | null;
}

export interface ComparisonContribution {
  feature: string;
  field: ProfileField;
  label: string;
  valueA: number | string;
  valueB: number | string;
  phiDifference: number;
  relativity: number;
}

export interface ComparisonResponse {
  premiumA: number;
  premiumB: number;
  premiumRatio: number;
  contributions: ComparisonContribution[];
}

export interface QuoteHistoryItem {
  id: number;
  createdAt: string;
  driverAge: number;
  bonusMalus: number;
  area: string;
  vehicleBrand: string;
  claimProbability: number;
  purePremium: number;
  commercialPremium: number;
  premiumMargin: number;
  frequencyModelVersion: string;
}

// ---------------------------------------------------------------------------
// Métadonnées du formulaire
// ---------------------------------------------------------------------------
export interface MetadataField {
  field: ProfileField;
  feature: string;
  label: string;
  type: 'integer' | 'category';
  min?: number;
  max?: number;
  values?: string[];
}

export interface ActuarialMetadata {
  fields: MetadataField[];
  defaultAssumptions: PricingAssumptions;
}

// ---------------------------------------------------------------------------
// Rapports d'évaluation (clés snake_case produites par le pipeline Python)
// ---------------------------------------------------------------------------
export interface ModelMetrics {
  deviance_power: number;
  mean_deviance: number;
  d2_explained: number;
  gini: number;
  observed_total: number;
  predicted_total: number;
  calibration_ratio: number;
}

export interface CandidateSummary {
  name: string;
  algorithm: string;
  description: string;
  fit_seconds: number;
  train: ModelMetrics;
  test: ModelMetrics;
}

export interface LorenzCurve {
  cumulative_weight: number[];
  cumulative_total: number[];
}

export interface CalibrationRow {
  risk_class: number;
  weight: number;
  observed_total: number;
  predicted_total: number;
  observed_mean: number;
  predicted_mean: number;
}

export interface ModelReport {
  task: string;
  champion: string;
  model_version: string;
  candidates: CandidateSummary[];
  lorenz_curves: Record<string, LorenzCurve>;
  calibration: CalibrationRow[];
}

export interface MarginRow {
  segment: string | number;
  policies: number;
  exposure: number;
  premiums: number;
  expected_claims: number;
  actual_claims: number;
  expected_margin: number;
  actual_margin: number;
  expected_loss_ratio: number;
  actual_loss_ratio: number;
}

export interface PricingReport {
  large_claim_cap: { quantile: number; threshold: number; loading: number; excess_share: number };
  assumptions: PricingAssumptions & { expense_ratio?: number };
  pure_premium_metrics: ModelMetrics;
  large_claims_diagnostic: {
    threshold: number;
    train_excess_share: number;
    test_excess_share: number;
    capped_calibration_ratio: number;
    largest_train_claims: number[];
    largest_test_claims: number[];
  };
  pure_premium_lorenz: { oracle: LorenzCurve; model: LorenzCurve };
  margin: {
    portfolio: MarginRow[];
    by_risk_class: MarginRow[];
    by_area: MarginRow[];
    by_bonus_malus: MarginRow[];
  };
}

export interface ImportanceRow {
  feature: string;
  label: string;
  mean_abs_phi: number;
  share: number;
}

export interface DependencePoint {
  /** Valeur (variable catégorielle) ou moyenne de la classe (variable continue). */
  value: number | string;
  /** Bornes de la classe, pour les variables continues regroupées en déciles. */
  range?: [number, number];
  mean_phi: number;
  relativity: number;
  count: number;
}

export interface ExplainabilityReport {
  method: string;
  base_premium: number;
  global_sample_size: number;
  global_importance: { premium: ImportanceRow[]; frequency: ImportanceRow[]; severity: ImportanceRow[] };
  dependence: Record<string, DependencePoint[]>;
}

export type ReportName = 'frequency' | 'severity' | 'pricing' | 'explainability';

// ---------------------------------------------------------------------------
// Profils types (mêmes profils que le rapport d'entraînement)
// ---------------------------------------------------------------------------
export interface ProfilePreset {
  id: string;
  label: string;
  profile: PolicyProfile;
}

export const PROFILE_PRESETS: readonly ProfilePreset[] = [
  {
    id: 'young-urban',
    label: 'Jeune conducteur urbain, malus',
    profile: {
      driverAge: 20, bonusMalus: 100, vehicleAge: 2, vehiclePower: 7, density: 5000,
      area: 'E', vehicleBrand: 'B12', vehicleGas: 'Regular', region: 'R11'
    }
  },
  {
    id: 'experienced-rural',
    label: 'Conducteur expérimenté rural, bonus maximal',
    profile: {
      driverAge: 50, bonusMalus: 50, vehicleAge: 8, vehiclePower: 6, density: 50,
      area: 'A', vehicleBrand: 'B1', vehicleGas: 'Diesel', region: 'R24'
    }
  },
  {
    id: 'senior',
    label: 'Conducteur senior, ville moyenne',
    profile: {
      driverAge: 72, bonusMalus: 50, vehicleAge: 5, vehiclePower: 5, density: 800,
      area: 'C', vehicleBrand: 'B2', vehicleGas: 'Regular', region: 'R82'
    }
  }
];
