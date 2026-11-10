export type BankingScenarioSeverity = 'LOW' | 'MODERATE' | 'HIGH' | 'SEVERE';

export type EconomicStressFactorType =
  | 'UNEMPLOYMENT_INCREASE'
  | 'GDP_CONTRACTION'
  | 'BANK_REVENUE_DECLINE'
  | 'ASSET_QUALITY_DETERIORATION'
  | 'DEFAULT_PROBABILITY_INCREASE';

export interface BankingScenarioProposalRequest {
  severity: BankingScenarioSeverity;
  horizonDays: number;
  context: string | null;
}

export interface EconomicStressFactorProposal {
  type: EconomicStressFactorType;
  value: number;
  unit: 'PERCENT' | 'PERCENTAGE_POINTS';
  rationale: string;
}

export interface ProposedEngineParameters {
  rateShock: number | null;
  recessionProbabilityMultiplier: number | null;
  recessionLossGivenDefaultAddOn: number | null;
  equityReturnShock: number | null;
}

export interface BankingScenarioProposal {
  scenarioName: string;
  severity: BankingScenarioSeverity;
  horizonDays: number;
  factors: EconomicStressFactorProposal[];
  engineParameters: ProposedEngineParameters;
  justifications: string[];
  assumptions: string[];
}

export function percentagePointsToFraction(value: number): number {
  return Math.round(value / 100 * 100_000_000) / 100_000_000;
}

const FACTOR_UNITS: Record<EconomicStressFactorType, string> = {
  UNEMPLOYMENT_INCREASE: 'PERCENTAGE_POINTS',
  GDP_CONTRACTION: 'PERCENT',
  BANK_REVENUE_DECLINE: 'PERCENT',
  ASSET_QUALITY_DETERIORATION: 'PERCENT',
  DEFAULT_PROBABILITY_INCREASE: 'PERCENTAGE_POINTS'
};

const FACTOR_TYPES = Object.keys(FACTOR_UNITS) as EconomicStressFactorType[];
const SEVERITIES: BankingScenarioSeverity[] = ['LOW', 'MODERATE', 'HIGH', 'SEVERE'];

export function validateBankingScenarioProposal(
  proposal: BankingScenarioProposal,
  request: BankingScenarioProposalRequest
): void {
  if (!proposal || !SEVERITIES.includes(proposal.severity)
      || proposal.severity !== request.severity
      || proposal.horizonDays !== request.horizonDays
      || !Number.isInteger(proposal.horizonDays)
      || proposal.horizonDays < 1 || proposal.horizonDays > 3650) {
    throw new Error('La proposition ne respecte pas la sévérité ou l’horizon demandé.');
  }
  if (typeof proposal.scenarioName !== 'string'
      || proposal.scenarioName.trim().length === 0
      || proposal.scenarioName.length > 120) {
    throw new Error('Le nom du scénario proposé est invalide.');
  }
  if (!Array.isArray(proposal.factors) || proposal.factors.length !== FACTOR_TYPES.length) {
    throw new Error('La proposition doit contenir les cinq facteurs économiques.');
  }

  const seen = new Set<EconomicStressFactorType>();
  for (const factor of proposal.factors) {
    if (!factor || !FACTOR_TYPES.includes(factor.type) || seen.has(factor.type)) {
      throw new Error('La proposition contient un facteur absent ou non autorisé.');
    }
    seen.add(factor.type);
    if (factor.unit !== FACTOR_UNITS[factor.type]) {
      throw new Error(`L’unité proposée pour ${factor.type} est incorrecte.`);
    }
    if (!Number.isFinite(factor.value) || factor.value < 0 || factor.value > 100
        || Number(factor.value.toFixed(8)) !== factor.value) {
      throw new Error(`La valeur proposée pour ${factor.type} est hors limites.`);
    }
    validateText(factor.rationale, 500, 'justification du facteur');
  }
  if (seen.size !== FACTOR_TYPES.length) {
    throw new Error('Un ou plusieurs facteurs économiques sont absents.');
  }

  const parameters = proposal.engineParameters;
  if (!parameters) {
    throw new Error('Les paramètres du moteur sont absents.');
  }
  validateOptionalRange(parameters.rateShock, -1, 1, 'choc de taux');
  validateOptionalRange(
    parameters.recessionProbabilityMultiplier, 1, 100, 'multiplicateur PD'
  );
  validateOptionalRange(
    parameters.recessionLossGivenDefaultAddOn, 0, 1, 'supplément LGD'
  );
  validateOptionalRange(parameters.equityReturnShock, -1, 1, 'choc actions');
  if ((parameters.recessionProbabilityMultiplier === null)
      !== (parameters.recessionLossGivenDefaultAddOn === null)) {
    throw new Error('Les deux paramètres de récession doivent être proposés ensemble.');
  }

  validateTextList(proposal.justifications, 1, 8, 'justifications');
  validateTextList(proposal.assumptions, 0, 12, 'hypothèses');
}

function validateOptionalRange(
  value: number | null,
  minimum: number,
  maximum: number,
  label: string
): void {
  if (value !== null && (!Number.isFinite(value) || value < minimum || value > maximum
      || Number(value.toFixed(8)) !== value)) {
    throw new Error(`Le paramètre ${label} est hors limites.`);
  }
}

function validateTextList(
  values: string[],
  minimum: number,
  maximum: number,
  label: string
): void {
  if (!Array.isArray(values) || values.length < minimum || values.length > maximum
      || values.some(value => typeof value !== 'string'
        || value.trim().length === 0 || value.length > 500)) {
    throw new Error(`La liste ${label} est invalide.`);
  }
}

function validateText(value: string, maximum: number, label: string): void {
  if (typeof value !== 'string' || value.trim().length === 0 || value.length > maximum) {
    throw new Error(`La ${label} est invalide.`);
  }
}
