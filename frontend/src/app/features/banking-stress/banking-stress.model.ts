export type BankingModel = 'CONVENTIONAL' | 'NON_CONVENTIONAL';

export type BankingStressScenarioType =
  | 'RATE_SHOCK'
  | 'RECESSION'
  | 'CREDIT_DEFAULT'
  | 'EQUITY_SHOCK'
  | 'COMBINED';

export type FinancialContractType =
  | 'LOAN'
  | 'DEPOSIT'
  | 'BOND'
  | 'SUKUK'
  | 'MURABAHA'
  | 'IJARA'
  | 'MUSHARAKA'
  | 'MUDARABA'
  | 'EQUITY'
  | 'OTHER';

export type ReturnBasis =
  | 'FIXED_RATE'
  | 'BENCHMARK_LINKED'
  | 'PROFIT_SHARING'
  | 'VARIABLE'
  | 'NOT_APPLICABLE';

export type BalanceSheetSide = 'ASSET' | 'LIABILITY';

export interface ExposureTerms {
  contractType: FinancialContractType;
  returnBasis: ReturnBasis;
  bankLossShare: number;
  investorLossShare: number;
}

export interface ValidatedLossShares {
  bankLossShare: number;
  investorLossShare: number;
}

const LOSS_SHARE_SCALE = 100_000_000n;

export function validateLossShares(
  bankInput: string | null,
  investorInput: string | null,
  exposureId: string
): ValidatedLossShares {
  const bankScaled = parseLossShare(bankInput, 'Part de perte banque', exposureId);
  const investorScaled = parseLossShare(
    investorInput,
    'Part de perte investisseurs',
    exposureId
  );

  if (bankScaled + investorScaled !== LOSS_SHARE_SCALE) {
    throw new Error(
      `Les parts de pertes banque et investisseurs de ${exposureId} `
      + 'doivent totaliser exactement 100 %.'
    );
  }

  return {
    bankLossShare: Number(bankInput?.trim().replace(',', '.')),
    investorLossShare: Number(investorInput?.trim().replace(',', '.'))
  };
}

function parseLossShare(
  input: string | null,
  label: string,
  exposureId: string
): bigint {
  if (input === null || input.trim() === '') {
    throw new Error(`${label} de ${exposureId} doit être comprise entre 0 et 1.`);
  }

  const trimmed = input.trim();
  const normalized = trimmed.replace(',', '.');
  if ((trimmed.includes(',') && trimmed.includes('.'))
      || (trimmed.match(/[.,]/g)?.length ?? 0) > 1
      || !/^(?:\d+(?:\.\d+)?|\.\d+)$/.test(normalized)) {
    throw new Error(`${label} de ${exposureId} doit être un nombre décimal entre 0 et 1.`);
  }

  const [wholePart, fractionalPart = ''] = normalized.split('.');
  if (fractionalPart.length > 8) {
    throw new Error(`${label} de ${exposureId} ne peut pas comporter plus de 8 décimales.`);
  }

  const scaled = BigInt(wholePart || '0') * LOSS_SHARE_SCALE
    + BigInt(fractionalPart.padEnd(8, '0') || '0');
  if (scaled > LOSS_SHARE_SCALE) {
    throw new Error(`${label} de ${exposureId} doit être comprise entre 0 et 1.`);
  }
  return scaled;
}

export interface CreditExposure {
  exposureId: string;
  terms: ExposureTerms;
  exposureAtDefault: number;
  probabilityOfDefault: number;
  lossGivenDefault: number;
}

export interface RateExposure {
  exposureId: string;
  terms: ExposureTerms;
  side: BalanceSheetSide;
  presentValue: number;
  modifiedDurationYears: number;
  convexityYearsSquared: number;
}

export interface MarketExposure {
  exposureId: string;
  terms: ExposureTerms;
  marketValue: number;
}

export interface BankInput {
  bankId: string;
  bankName: string;
  model: BankingModel;
  capitalProxy: number;
  riskWeightedAssets: number;
  creditExposures: CreditExposure[];
  rateExposures: RateExposure[];
  marketExposures: MarketExposure[];
}

export interface ScenarioInput {
  scenarioId: string;
  type: BankingStressScenarioType;
  rateShock: number | null;
  recessionProbabilityMultiplier: number | null;
  recessionLossGivenDefaultAddOn: number | null;
  defaultProbabilityAddOn: number | null;
  equityReturnShock: number | null;
}

export interface BankingStressTestRequest {
  currency: string;
  creditHorizonDays: number;
  banks: BankInput[];
  scenarios: ScenarioInput[];
}

export interface BankScenarioResult {
  bankId: string;
  bankName: string;
  model: BankingModel;
  scenarioId: string;
  scenarioType: BankingStressScenarioType;
  creditExpectedLossBefore: number;
  creditExpectedLossAfter: number;
  creditLoss: number;
  creditLossBank: number;
  creditLossInvestors: number;
  economicValueChange: number;
  bankEconomicValueChange: number;
  investorEconomicValueChange: number;
  rateRiskLoss: number;
  rateRiskLossBank: number;
  rateRiskLossInvestors: number;
  marketProfitAndLoss: number;
  marketLoss: number;
  marketLossBank: number;
  marketLossInvestors: number;
  bankBorneLoss: number;
  investorBorneLoss: number;
  capitalProxyBefore: number;
  capitalProxyAfter: number;
  riskWeightedAssets: number;
  capitalToRwaPercentBefore: number | null;
  capitalToRwaPercentAfter: number | null;
}

export interface BankingStressTestResponse {
  currency: string;
  creditHorizonDays: number;
  simulatedResults: boolean;
  regulatoryStatus: string;
  probabilityOfDefaultHorizon: string;
  assumptionsAndLimitations: string[];
  results: BankScenarioResult[];
}
