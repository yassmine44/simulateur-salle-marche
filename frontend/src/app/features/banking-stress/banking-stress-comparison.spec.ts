import {
  createDemoComparisonRequest,
  createDemoResponse
} from './banking-stress.demo';
import { createNormalizedComparisons } from './banking-stress-comparison';

describe('createNormalizedComparisons', () => {
  it('normalizes both fictional banks side by side with their scenario inputs', () => {
    const request = createDemoComparisonRequest();
    const response = createDemoResponse(request);
    response.results[0].capitalToRwaPercentAfter = null;

    const comparisons = createNormalizedComparisons(response, request);

    expect(comparisons.length).toBe(1);
    expect(comparisons[0].banks[0]?.bankName).toBe('BIAT');
    expect(comparisons[0].banks[1]?.bankName).toBe('Banque Zitouna');
    expect(comparisons[0].banks[0]?.shockSeverityToInitialCapitalPercent)
      .toBeCloseTo(60_500 / 320_000 * 100, 8);
    expect(comparisons[0].banks[0]?.bankCapitalImpactPercent)
      .toBeCloseTo(39_325 / 320_000 * 100, 8);
    expect(
      comparisons[0].banks[0]?.shockSeverityToInitialCapitalPercent
        === comparisons[0].banks[0]?.bankCapitalImpactPercent
    ).toBeFalse();
    expect(comparisons[0].banks[0]?.creditLossToCreditExposurePercent)
      .toBeCloseTo(30_000 / 600_000 * 100, 8);
    expect(comparisons[0].banks[0]?.marketLossToMarketExposurePercent)
      .toBeCloseTo(12_500 / 90_000 * 100, 8);
    expect(comparisons[0].banks[0]?.stressedCapitalToRwaPercent)
      .toBeCloseTo(280_675 / 2_000_000 * 100, 8);
  });

  it('shows market gains as negative loss ratios', () => {
    const request = createDemoComparisonRequest();
    const response = createDemoResponse(request);
    response.results[0].marketProfitAndLoss = 4_500;

    const comparisons = createNormalizedComparisons(response, request);

    expect(comparisons[0].banks[0]?.marketLossToMarketExposurePercent)
      .toBeCloseTo(-4_500 / 90_000 * 100, 8);
  });

  it('returns unavailable ratios for null or zero denominators', () => {
    const request = createDemoComparisonRequest();
    request.banks[0].capitalProxy = 0;
    request.banks[0].creditExposures[0].exposureAtDefault = 0;
    request.banks[0].marketExposures[0].marketValue = 0;
    const response = createDemoResponse(request);
    response.results[0].capitalProxyBefore = 0;
    response.results[0].riskWeightedAssets = 0;
    response.results[0].capitalToRwaPercentAfter = null;

    const comparisons = createNormalizedComparisons(response, request);
    const biat = comparisons[0].banks[0];

    expect(biat?.shockSeverityToInitialCapitalPercent).toBeNull();
    expect(biat?.bankCapitalImpactPercent).toBeNull();
    expect(biat?.creditLossToCreditExposurePercent).toBeNull();
    expect(biat?.marketLossToMarketExposurePercent).toBeNull();
    expect(biat?.stressedCapitalToRwaPercent).toBeNull();
  });

  it('does not normalize exposure ratios across mismatched currencies', () => {
    const request = createDemoComparisonRequest();
    const response = createDemoResponse(request);
    response.currency = 'EUR';

    const comparisons = createNormalizedComparisons(response, request);

    expect(comparisons[0].banks[0]?.creditLossToCreditExposurePercent).toBeNull();
    expect(comparisons[0].banks[0]?.marketLossToMarketExposurePercent).toBeNull();
  });

  it('does not generate a pair when the request does not contain exactly two banks', () => {
    const request = createDemoComparisonRequest();
    request.banks.push({ ...request.banks[0], bankId: 'third-bank' });

    expect(createNormalizedComparisons(createDemoResponse(request), request)).toEqual([]);
  });

  it('keeps incomplete or ambiguous bank-scenario results unavailable', () => {
    const request = createDemoComparisonRequest();
    const response = createDemoResponse(request);
    response.results = response.results.filter(result => result.bankId !== 'banque-zitouna');
    response.results.push({ ...response.results[0] });

    const comparisons = createNormalizedComparisons(response, request);

    expect(comparisons[0].banks[0]).toBeNull();
    expect(comparisons[0].banks[1]).toBeNull();
  });

  it('counts bank and investor allocations once for severity and isolates bank impact', () => {
    const request = createDemoComparisonRequest();
    const response = createDemoResponse(request);
    const result = response.results[0];

    expect(result.bankBorneLoss).toBe(39_325);
    expect(result.investorBorneLoss).toBe(21_175);

    const comparisons = createNormalizedComparisons(response, request);
    const biat = comparisons[0].banks[0];

    expect(biat?.shockSeverityToInitialCapitalPercent)
      .toBeCloseTo((39_325 + 21_175) / 320_000 * 100, 8);
    expect(biat?.bankCapitalImpactPercent)
      .toBeCloseTo(39_325 / 320_000 * 100, 8);
    expect(biat?.shockSeverityToInitialCapitalPercent)
      .toBeCloseTo(18.90625, 8);
    expect(biat?.bankCapitalImpactPercent).toBeCloseTo(12.2890625, 8);
  });
});
