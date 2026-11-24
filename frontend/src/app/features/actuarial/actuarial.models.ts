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

/**
 * Devise d'affichage. Les modèles calculent en euros (données françaises) ;
 * les montants sont convertis au taux `rateFromModel` (ex. 3,36 DT pour 1 €).
 */
export interface Currency {
  code: string;
  label: string;
  rateFromModel: number;
  modelCurrency: string;
  source: string | null;
}

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
  currency: Currency;
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
  currency: Currency;
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
  currency: string;
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
  currency: Currency;
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
  currency: Currency;
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
  currency: Currency;
  method: string;
  base_premium: number;
  global_sample_size: number;
  global_importance: { premium: ImportanceRow[]; frequency: ImportanceRow[]; severity: ImportanceRow[] };
  dependence: Record<string, DependencePoint[]>;
}

export type ReportName = 'frequency' | 'severity' | 'pricing' | 'explainability' | 'reserving';

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

// ---------------------------------------------------------------------------
// Provisionnement (Chain-Ladder et Mack)
// ---------------------------------------------------------------------------
export interface ReserveOrigin {
  origin: number;
  latest: number;
  development_to_date: number;
  ultimate: number;
  reserve: number;
  mack_se: number;
  cv: number | null;
  ci_low: number;
  ci_high: number;
}

export interface ChainLadderResult {
  confidence_level: number;
  development_factors: number[];
  cumulative_factors: number[];
  sigma2: number[];
  /** Triangle connu (null = cellule future). */
  triangle: Array<Array<number | null>>;
  /** Triangle complété par la projection Chain-Ladder. */
  projected: number[][];
  origins: ReserveOrigin[];
  total: {
    latest: number;
    ultimate: number;
    reserve: number;
    mack_se: number;
    cv: number | null;
    ci_low: number;
    ci_high: number;
  };
  size: number;
}

export interface BacktestOrigin {
  origin: number;
  estimated_reserve: number;
  actual_reserve: number;
  error: number;
  relative_error: number | null;
  within_interval: boolean | null;
  premium: number;
  estimated_loss_ratio: number;
  actual_loss_ratio: number;
}

export interface Backtest {
  origins: BacktestOrigin[];
  total: {
    estimated_reserve: number;
    actual_reserve: number;
    error: number;
    relative_error: number;
    within_interval: boolean;
  };
  interval_coverage: number;
}

export interface ReservingPortfolio {
  code: string;
  name: string;
  companies: number;
  chain_ladder: ChainLadderResult;
  actual_triangle: number[][];
  backtest: Backtest;
}

export interface ReservingReport {
  source: string;
  unit: string;
  valuation_year: number;
  confidence_level: number;
  method: string;
  portfolios: ReservingPortfolio[];
  summary: {
    portfolios: number;
    origins_backtested: number;
    interval_coverage: number;
    totals_within_interval: number;
  };
}

// ---------------------------------------------------------------------------
// Stress test du portefeuille
// ---------------------------------------------------------------------------
export type StressSegmentation = 'by_risk_class' | 'by_area' | 'by_bonus_malus';

export interface StressRow {
  segment: string | number;
  policies: number;
  premiums: number;
  expected_claims: number;
  /** Sinistres réellement observés sur le portefeuille de test (base du stress). */
  actual_claims: number;
  stressed_claims: number;
  actual_margin: number;
  stressed_margin: number;
  expected_loss_ratio: number;
  actual_loss_ratio: number;
  stressed_loss_ratio: number;
  break_even_shock: number | null;
}

export interface StressResult {
  shock: { frequency: number; severity: number; claims_multiplier: number };
  portfolio: StressRow;
  segments: StressRow[];
  segments_in_loss: number;
  currency: Currency;
}

// ---------------------------------------------------------------------------
// Administration
// ---------------------------------------------------------------------------
export interface ModelCard {
  name: string;
  task: string;
  algorithm: string;
  version: string;
  created_at: string;
  data_source: string;
  data_fingerprint: string;
  metrics: ModelMetrics;
  environment: { python: string; scikit_learn: string };
}

export interface AdminOverview {
  engine: {
    status: string;
    modelVersions: { frequency: string; severity: string };
    largeClaimLoading: number;
    assumptions: PricingAssumptions;
    currency: Currency;
  } | null;
  modelCards: { frequency_model: ModelCard; severity_model: ModelCard } | null;
  engineError: string | null;
  usage: {
    totalQuotes: number;
    quotesLast30Days: number;
    distinctUsers: number;
    byModelVersion: Array<{ version: string; quotes: number; averageCommercialPremium: number | null; lastQuoteAt: string }>;
  };
  recentActions: Array<{ id: number; type: 'ASSUMPTIONS_UPDATED' | 'CURRENCY_UPDATED' | 'MODELS_RELOADED'; actorEmail: string; createdAt: string; details: string | null }>;
}

export interface AdminQuote {
  id: number;
  createdAt: string;
  userEmail: string;
  driverAge: number;
  bonusMalus: number;
  area: string;
  purePremium: number;
  commercialPremium: number;
  expenseRatio: number;
  profitRatio: number;
  frequencyModelVersion: string;
  currency: string;
}
