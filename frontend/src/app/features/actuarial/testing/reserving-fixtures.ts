import { Backtest, ChainLadderResult, ReservingReport } from '../actuarial.models';

/** Petit résultat Chain-Ladder cohérent (3 années d'origine). */
export const CHAIN_LADDER: ChainLadderResult = {
  confidence_level: 0.95,
  development_factors: [1.5, 1.1],
  cumulative_factors: [1.65, 1.1, 1],
  sigma2: [0.5, 0.2],
  triangle: [[100, 150, 165], [110, 165, null], [120, null, null]],
  projected: [[100, 150, 165], [110, 165, 181.5], [120, 180, 198]],
  origins: [
    { origin: 2019, latest: 165, development_to_date: 1, ultimate: 165, reserve: 0, mack_se: 0, cv: null, ci_low: 0, ci_high: 0 },
    { origin: 2020, latest: 165, development_to_date: 0.909, ultimate: 181.5, reserve: 16.5, mack_se: 3, cv: 0.18, ci_low: 11, ci_high: 23 },
    { origin: 2021, latest: 120, development_to_date: 0.606, ultimate: 198, reserve: 78, mack_se: 12, cv: 0.15, ci_low: 57, ci_high: 103 }
  ],
  total: { latest: 450, ultimate: 544.5, reserve: 94.5, mack_se: 13, cv: 0.14, ci_low: 71, ci_high: 122 },
  size: 3
};

export const BACKTEST: Backtest = {
  origins: [
    { origin: 2019, estimated_reserve: 0, actual_reserve: 0, error: 0, relative_error: null, within_interval: null, premium: 200, estimated_loss_ratio: 0.82, actual_loss_ratio: 0.82 },
    { origin: 2020, estimated_reserve: 16.5, actual_reserve: 15, error: 1.5, relative_error: 0.1, within_interval: true, premium: 220, estimated_loss_ratio: 0.82, actual_loss_ratio: 0.82 },
    { origin: 2021, estimated_reserve: 78, actual_reserve: 50, error: 28, relative_error: 0.56, within_interval: false, premium: 240, estimated_loss_ratio: 0.82, actual_loss_ratio: 0.71 }
  ],
  total: { estimated_reserve: 94.5, actual_reserve: 65, error: 29.5, relative_error: 0.4538, within_interval: false },
  interval_coverage: 0.5
};

export const RESERVING_REPORT: ReservingReport = {
  source: 'CAS Loss Reserve Database',
  unit: 'milliers de dollars US',
  valuation_year: 2021,
  confidence_level: 0.95,
  method: 'Chain-Ladder et Mack',
  portfolios: [
    { code: 'market', name: 'Marché (2 assureurs)', companies: 2, chain_ladder: CHAIN_LADDER, actual_triangle: [], backtest: BACKTEST },
    { code: '42', name: 'Assureur 42', companies: 1, chain_ladder: CHAIN_LADDER, actual_triangle: [], backtest: BACKTEST }
  ],
  summary: { portfolios: 2, origins_backtested: 4, interval_coverage: 0.5, totals_within_interval: 0 }
};
