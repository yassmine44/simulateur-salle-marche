import {
  BankingScenarioProposal,
  BankingScenarioProposalRequest,
  percentagePointsToFraction,
  validateBankingScenarioProposal
} from './banking-scenario-proposal.model';

describe('banking scenario proposal validation', () => {
  const request: BankingScenarioProposalRequest = {
    severity: 'HIGH',
    horizonDays: 365,
    context: null
  };

  it('accepts all five factors with their declared units and bounded engine shocks', () => {
    expect(() => validateBankingScenarioProposal(proposal(), request)).not.toThrow();
  });

  it('rejects a proposal whose severity or horizon differs from the request', () => {
    const wrongSeverity = { ...proposal(), severity: 'SEVERE' as const };
    expect(() => validateBankingScenarioProposal(wrongSeverity, request)).toThrow();

    const wrongHorizon = { ...proposal(), horizonDays: 180 };
    expect(() => validateBankingScenarioProposal(wrongHorizon, request)).toThrow();
  });

  it('rejects unsupported, duplicate, or missing economic factors', () => {
    const missing = { ...proposal(), factors: proposal().factors.slice(0, 4) };
    expect(() => validateBankingScenarioProposal(missing, request)).toThrow();

    const duplicate = {
      ...proposal(),
      factors: [
        proposal().factors[0],
        proposal().factors[0],
        ...proposal().factors.slice(2)
      ]
    };
    expect(() => validateBankingScenarioProposal(duplicate, request)).toThrow();
  });

  it('rejects incompatible units, out-of-range values and incomplete recession inputs', () => {
    const wrongUnit = proposal();
    wrongUnit.factors[0].unit = 'PERCENT';
    expect(() => validateBankingScenarioProposal(wrongUnit, request)).toThrow();

    const outOfRange = proposal();
    outOfRange.factors[1].value = 100.1;
    expect(() => validateBankingScenarioProposal(outOfRange, request)).toThrow();

    const partialRecession = proposal();
    partialRecession.engineParameters.recessionLossGivenDefaultAddOn = null;
    expect(() => validateBankingScenarioProposal(partialRecession, request)).toThrow();
  });

  it('converts percentage points to the engine fraction rounded to eight decimals', () => {
    expect(percentagePointsToFraction(2)).toBe(0.02);
    expect(percentagePointsToFraction(1.23456789)).toBe(0.01234568);
  });

  function proposal(): BankingScenarioProposal {
    return {
      scenarioName: 'Proposition de test',
      severity: 'HIGH',
      horizonDays: 365,
      factors: [
        {
          type: 'UNEMPLOYMENT_INCREASE',
          value: 2,
          unit: 'PERCENTAGE_POINTS',
          rationale: 'Facteur contextuel de test.'
        },
        {
          type: 'GDP_CONTRACTION',
          value: 3,
          unit: 'PERCENT',
          rationale: 'Facteur contextuel de test.'
        },
        {
          type: 'BANK_REVENUE_DECLINE',
          value: 4,
          unit: 'PERCENT',
          rationale: 'Facteur contextuel de test.'
        },
        {
          type: 'ASSET_QUALITY_DETERIORATION',
          value: 5,
          unit: 'PERCENT',
          rationale: 'Facteur contextuel de test.'
        },
        {
          type: 'DEFAULT_PROBABILITY_INCREASE',
          value: 2,
          unit: 'PERCENTAGE_POINTS',
          rationale: 'Incrément PD explicite de test.'
        }
      ],
      engineParameters: {
        rateShock: 0.01,
        recessionProbabilityMultiplier: 1.5,
        recessionLossGivenDefaultAddOn: 0.05,
        equityReturnShock: -0.1
      },
      justifications: ['Justification de test.'],
      assumptions: ['Hypothèse de test.']
    };
  }
});
