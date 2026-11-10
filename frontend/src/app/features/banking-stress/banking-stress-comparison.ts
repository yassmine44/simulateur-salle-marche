import {
  BankScenarioResult,
  BankingStressScenarioType,
  BankingStressTestRequest,
  BankingStressTestResponse
} from './banking-stress.model';

export interface NormalizedBankIndicators {
  bankId: string;
  bankName: string;
  shockSeverityToInitialCapitalPercent: number | null;
  bankCapitalImpactPercent: number | null;
  creditLossToCreditExposurePercent: number | null;
  marketLossToMarketExposurePercent: number | null;
  stressedCapitalToRwaPercent: number | null;
}

export interface NormalizedScenarioComparison {
  scenarioId: string;
  scenarioType: BankingStressScenarioType;
  banks: [NormalizedBankIndicators | null, NormalizedBankIndicators | null];
}

export function createNormalizedComparisons(
  response: BankingStressTestResponse,
  request: BankingStressTestRequest
): NormalizedScenarioComparison[] {
  if (request.banks.length !== 2) {
    return [];
  }
  const firstBank = request.banks[0];
  const secondBank = request.banks[1];
  if (!firstBank || !secondBank) {
    return [];
  }

  const currencyMatches = response.currency.trim().toUpperCase()
    === request.currency.trim().toUpperCase();
  const resultByKey = new Map<string, BankScenarioResult | null>();
  for (const result of response.results) {
    const key = resultKey(result.bankId, result.scenarioId);
    resultByKey.set(key, resultByKey.has(key) ? null : result);
  }
  return request.scenarios.map(scenario => {
    const banks: NormalizedScenarioComparison['banks'] = [
      normalizeRequestedBank(firstBank, scenario.scenarioId, resultByKey, currencyMatches),
      normalizeRequestedBank(secondBank, scenario.scenarioId, resultByKey, currencyMatches)
    ];
    return {
      scenarioId: scenario.scenarioId,
      scenarioType: scenario.type,
      banks
    };
  });
}

function normalizeRequestedBank(
  bank: BankingStressTestRequest['banks'][number],
  scenarioId: string,
  results: Map<string, BankScenarioResult | null>,
  currencyMatches: boolean
): NormalizedBankIndicators | null {
  const result = results.get(resultKey(bank.bankId, scenarioId));
  return result ? normalizeBankResult(result, bank, currencyMatches) : null;
}

function normalizeBankResult(
  result: BankScenarioResult,
  bank: BankingStressTestRequest['banks'][number],
  currencyMatches: boolean
): NormalizedBankIndicators {
  const creditExposure = sumAmounts(bank.creditExposures.map(item => item.exposureAtDefault));
  const marketExposure = sumAmounts(bank.marketExposures.map(item => item.marketValue));

  return {
    bankId: result.bankId,
    bankName: result.bankName,
    shockSeverityToInitialCapitalPercent: percentage(
      result.bankBorneLoss + result.investorBorneLoss,
      result.capitalProxyBefore
    ),
    bankCapitalImpactPercent: percentage(
      result.bankBorneLoss,
      result.capitalProxyBefore
    ),
    creditLossToCreditExposurePercent: currencyMatches
      ? percentage(result.creditLoss, creditExposure)
      : null,
    marketLossToMarketExposurePercent: currencyMatches
      ? percentage(-result.marketProfitAndLoss, marketExposure)
      : null,
    stressedCapitalToRwaPercent: percentage(
      result.capitalProxyAfter,
      result.riskWeightedAssets
    )
  };
}

function sumAmounts(amounts: number[]): number | null {
  let total = 0;
  for (const amount of amounts) {
    if (!isFiniteNumber(amount) || amount < 0) {
      return null;
    }
    total += amount;
    if (!Number.isFinite(total)) {
      return null;
    }
  }
  return total;
}

function percentage(numerator: number, denominator: number | null): number | null {
  if (!isFiniteNumber(numerator) || denominator === null
      || !Number.isFinite(denominator) || denominator <= 0) {
    return null;
  }
  const result = numerator / denominator * 100;
  return Number.isFinite(result) ? result : null;
}

function isFiniteNumber(value: number | null): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function resultKey(bankId: string, scenarioId: string): string {
  return `${bankId}\u0000${scenarioId}`;
}
