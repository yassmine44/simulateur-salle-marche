import {
  BankingStressScenarioType,
  BankingStressTestRequest,
  BankingStressTestResponse,
  BankScenarioResult
} from './banking-stress.model';

const DEMO_BANKS = {
  biat: {
    capitalProxy: 320_000,
    riskWeightedAssets: 2_000_000,
    creditLoss: 30_000,
    rateLoss: 18_000,
    marketLoss: 12_500,
    expectedLossBefore: 6_000
  },
  'banque-zitouna': {
    capitalProxy: 210_000,
    riskWeightedAssets: 1_250_000,
    creditLoss: 23_400,
    rateLoss: 14_040,
    marketLoss: 9_750,
    expectedLossBefore: 4_680
  }
} as const;

const DEMO_SCENARIOS: Record<
  BankingStressScenarioType,
  { credit: number; rate: number; market: number }
> = {
  RATE_SHOCK: { credit: 0.05, rate: 1, market: 0 },
  RECESSION: { credit: 0.75, rate: 0, market: 0 },
  CREDIT_DEFAULT: { credit: 1, rate: 0, market: 0 },
  EQUITY_SHOCK: { credit: 0, rate: 0, market: 1 },
  COMBINED: { credit: 1, rate: 1, market: 1 }
};

const OTHER_BANK_DEMO = {
  capitalProxy: 150_000,
  riskWeightedAssets: 900_000,
  creditLoss: 18_000,
  rateLoss: 10_000,
  marketLoss: 7_000,
  expectedLossBefore: 3_600
};

const roundMoney = (value: number): number => Math.round(value * 100) / 100;

function splitLoss(total: number): { bank: number; investors: number } {
  const bank = roundMoney(total * 0.65);
  return { bank, investors: roundMoney(total - bank) };
}

export function createDemoComparisonRequest(): BankingStressTestRequest {
  const makeTerms = (
    contractType: BankingStressTestRequest['banks'][number]['creditExposures'][number]['terms']['contractType'],
    returnBasis: BankingStressTestRequest['banks'][number]['creditExposures'][number]['terms']['returnBasis']
  ) => ({
    contractType,
    returnBasis,
    bankLossShare: 0.65,
    investorLossShare: 0.35
  });

  return {
    currency: 'TND',
    creditHorizonDays: 365,
    banks: [
      {
        bankId: 'biat',
        bankName: 'BIAT',
        model: 'CONVENTIONAL',
        capitalProxy: 320_000,
        riskWeightedAssets: 2_000_000,
        creditExposures: [{
          exposureId: 'demo-credit-biat',
          terms: makeTerms('LOAN', 'FIXED_RATE'),
          exposureAtDefault: 600_000,
          probabilityOfDefault: 0.025,
          lossGivenDefault: 0.45
        }],
        rateExposures: [{
          exposureId: 'demo-rate-biat',
          terms: makeTerms('BOND', 'FIXED_RATE'),
          side: 'ASSET',
          presentValue: 250_000,
          modifiedDurationYears: 3.2,
          convexityYearsSquared: 14
        }],
        marketExposures: [{
          exposureId: 'demo-market-biat',
          terms: makeTerms('EQUITY', 'VARIABLE'),
          marketValue: 90_000
        }]
      },
      {
        bankId: 'banque-zitouna',
        bankName: 'Banque Zitouna',
        model: 'NON_CONVENTIONAL',
        capitalProxy: 210_000,
        riskWeightedAssets: 1_250_000,
        creditExposures: [{
          exposureId: 'demo-credit-zitouna',
          terms: makeTerms('MURABAHA', 'FIXED_RATE'),
          exposureAtDefault: 460_000,
          probabilityOfDefault: 0.03,
          lossGivenDefault: 0.4
        }],
        rateExposures: [{
          exposureId: 'demo-rate-zitouna',
          terms: makeTerms('SUKUK', 'BENCHMARK_LINKED'),
          side: 'ASSET',
          presentValue: 180_000,
          modifiedDurationYears: 2.8,
          convexityYearsSquared: 11
        }],
        marketExposures: [{
          exposureId: 'demo-market-zitouna',
          terms: makeTerms('EQUITY', 'VARIABLE'),
          marketValue: 70_000
        }]
      }
    ],
    scenarios: [{
      scenarioId: 'demo-combined-shock',
      type: 'COMBINED',
      rateShock: 0.02,
      recessionProbabilityMultiplier: 1.5,
      recessionLossGivenDefaultAddOn: 0.08,
      defaultProbabilityAddOn: 0.01,
      equityReturnShock: -0.2
    }]
  };
}

export function createDemoResponse(
  request: BankingStressTestRequest
): BankingStressTestResponse {
  const results = request.banks.flatMap(bank => request.scenarios.map(scenario => {
    const bankFixture = bank.bankId === 'biat'
      ? DEMO_BANKS.biat
      : bank.bankId === 'banque-zitouna'
        ? DEMO_BANKS['banque-zitouna']
        : OTHER_BANK_DEMO;
    const severity = DEMO_SCENARIOS[scenario.type];
    const creditLoss = roundMoney(bankFixture.creditLoss * severity.credit);
    const rateRiskLoss = roundMoney(bankFixture.rateLoss * severity.rate);
    const marketLoss = roundMoney(bankFixture.marketLoss * severity.market);
    const creditAllocation = splitLoss(creditLoss);
    const rateAllocation = splitLoss(rateRiskLoss);
    const marketAllocation = splitLoss(marketLoss);
    const bankBorneLoss = roundMoney(
      creditAllocation.bank + rateAllocation.bank + marketAllocation.bank
    );
    const investorBorneLoss = roundMoney(
      creditAllocation.investors + rateAllocation.investors + marketAllocation.investors
    );
    const economicValueChange = roundMoney(-(rateRiskLoss + marketLoss));
    const economicValueAllocation = splitLoss(Math.abs(economicValueChange));
    const signedEconomicValueAllocation = economicValueChange < 0
      ? {
          bank: -economicValueAllocation.bank,
          investors: -economicValueAllocation.investors
        }
      : economicValueAllocation;
    const capitalToRwaPercentBefore =
      bankFixture.capitalProxy / bankFixture.riskWeightedAssets * 100;
    const capitalProxyAfter = roundMoney(bankFixture.capitalProxy - bankBorneLoss);

    return {
      bankId: bank.bankId,
      bankName: bank.bankName,
      model: bank.model,
      scenarioId: scenario.scenarioId,
      scenarioType: scenario.type,
      creditExpectedLossBefore: bankFixture.expectedLossBefore,
      creditExpectedLossAfter: roundMoney(bankFixture.expectedLossBefore + creditLoss),
      creditLoss,
      creditLossBank: creditAllocation.bank,
      creditLossInvestors: creditAllocation.investors,
      economicValueChange,
      bankEconomicValueChange: signedEconomicValueAllocation.bank,
      investorEconomicValueChange: signedEconomicValueAllocation.investors,
      rateRiskLoss,
      rateRiskLossBank: rateAllocation.bank,
      rateRiskLossInvestors: rateAllocation.investors,
      marketProfitAndLoss: -marketLoss,
      marketLoss,
      marketLossBank: marketAllocation.bank,
      marketLossInvestors: marketAllocation.investors,
      bankBorneLoss,
      investorBorneLoss,
      capitalProxyBefore: bankFixture.capitalProxy,
      capitalProxyAfter,
      riskWeightedAssets: bankFixture.riskWeightedAssets,
      capitalToRwaPercentBefore,
      capitalToRwaPercentAfter:
        capitalProxyAfter / bankFixture.riskWeightedAssets * 100
    } satisfies BankScenarioResult;
  }));

  return {
    currency: request.currency,
    creditHorizonDays: request.creditHorizonDays,
    simulatedResults: true,
    regulatoryStatus: 'Démo locale uniquement — ratios indicatifs non réglementaires.',
    probabilityOfDefaultHorizon:
      `Les PD du formulaire sont déclarées à ${request.creditHorizonDays} jours.`,
    assumptionsAndLimitations: [
      'Résultats simulés — démonstration : montants fictifs, prédéterminés et non officiels.',
      'Les résultats ne sont pas calculés à partir des expositions ni des chocs saisis.',
      'Aucune donnée financière publiée n’est utilisée par cette réponse de démonstration.',
      'La démonstration ne contacte pas le backend et ne constitue pas une analyse financière.'
    ],
    results
  };
}
