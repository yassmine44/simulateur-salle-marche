import { TUNISIAN_BANK_CATALOG } from './bank-catalog';
import {
  createDemoComparisonRequest,
  createDemoResponse
} from './banking-stress.demo';
import { FINANCIAL_REFERENCE_2025 } from './financial-reference-2025';

describe('Banking Stress demo fixtures', () => {
  it('offers the 13 Tunisian banks and 2025 KDT references', () => {
    const banks = TUNISIAN_BANK_CATALOG.filter(bank => !bank.custom);
    expect(banks.length).toBe(13);
    expect(banks.every(bank => FINANCIAL_REFERENCE_2025[bank.id])).toBeTrue();
    expect(banks.every(bank => {
      const financials = FINANCIAL_REFERENCE_2025[bank.id];
      return financials.unit === 'KDT'
        && financials.sourceUrl.startsWith('https://')
        && financials.sourceDocument.length > 0;
    })).toBeTrue();
  });

  it('prepares an explicitly fictional BIAT and Banque Zitouna comparison', () => {
    const request = createDemoComparisonRequest();
    expect(request.banks.map(bank => bank.bankId)).toEqual(['biat', 'banque-zitouna']);
    expect(request.banks.every(bank =>
      bank.creditExposures.length === 1
      && bank.rateExposures.length === 1
      && bank.marketExposures.length === 1
    )).toBeTrue();
    expect(request.scenarios[0].type).toBe('COMBINED');
    expect(request.scenarios[0].rateShock).toBe(0.02);
    expect(request.scenarios[0].recessionProbabilityMultiplier).toBe(1.5);
    expect(request.scenarios[0].defaultProbabilityAddOn).toBe(0.01);
    expect(request.scenarios[0].equityReturnShock).toBe(-0.2);
  });

  it('returns deterministic, clearly labeled response fixtures for both banks', () => {
    const request = createDemoComparisonRequest();
    const first = createDemoResponse(request);
    const second = createDemoResponse(request);

    expect(first).toEqual(second);
    expect(first.simulatedResults).toBeTrue();
    expect(first.results.map(result => result.bankName))
      .toEqual(['BIAT', 'Banque Zitouna']);
    expect(first.assumptionsAndLimitations.join(' '))
      .toContain('non officiels');
    expect(first.assumptionsAndLimitations.join(' '))
      .toContain('ne sont pas calculés à partir des expositions');
    for (const result of first.results) {
      expect(result.creditLossBank + result.creditLossInvestors).toBe(result.creditLoss);
      expect(result.rateRiskLossBank + result.rateRiskLossInvestors).toBe(result.rateRiskLoss);
      expect(result.marketLossBank + result.marketLossInvestors).toBe(result.marketLoss);
      expect(
        result.bankEconomicValueChange + result.investorEconomicValueChange
      ).toBe(result.economicValueChange);
    }
  });

  it('does not use entered financial assumptions to calculate the demo fixture', () => {
    const request = createDemoComparisonRequest();
    const baseline = createDemoResponse(request);
    request.banks[0].creditExposures[0].exposureAtDefault = 1;
    request.banks[0].capitalProxy = 1;
    request.scenarios[0].equityReturnShock = 0.9;

    expect(createDemoResponse(request).results).toEqual(baseline.results);
  });
});
