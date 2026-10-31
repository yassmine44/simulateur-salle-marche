import {
  calculatePortfolioScenarioImpact
} from './portfolio-scenario-impact';

import {
  PortfolioPositionImpact,
  PortfolioStressResponse
} from './portfolio-stress.service';

describe('Portfolio Scenario Impact', () => {

  function position(
    assetSymbol: string,
    quantity: number,
    quotedMidPrice: number | null
  ): PortfolioPositionImpact {
    return {
      assetSymbol,
      quantity,
      lastTradePrice: 100,
      quotedMidPrice,
      normalMarketValue: quantity * 100,
      quotedMarketValue:
        quotedMidPrice === null
          ? null
          : quantity * quotedMidPrice,
      valuationDifference:
        quotedMidPrice === null
          ? null
          : quantity * (quotedMidPrice - 100)
    };
  }

  function portfolio(
    positions: PortfolioPositionImpact[],
    quotedTotalValue: number | null = 100000
  ): PortfolioStressResponse {
    return {
      currency: 'TND',
      normalTotalValue: 100000,
      quotedTotalValue,
      valuationDifference: null,
      valuationDifferencePercent: null,
      quoteCoverageComplete: quotedTotalValue !== null,
      positions,
      generatedAt: '2026-10-28T10:00:00Z'
    };
  }

  it('calculates a loss on a LONG position during a price crash', () => {
    const before = portfolio([
      position('BIAT', 10, 100)
    ]);

    const after = portfolio([
      position('BIAT', 10, 80)
    ]);

    const result = calculatePortfolioScenarioImpact(
      'BIAT',
      before,
      after
    );

    expect(result).not.toBeNull();
    expect(result!.impact).toBe(-200);
    expect(result!.projectedPortfolio).toBe(99800);
  });

  it('calculates a gain on a SHORT position during a price crash', () => {
    const before = portfolio([
      position('BIAT', -10, 100)
    ]);

    const after = portfolio([
      position('BIAT', -10, 80)
    ]);

    const result = calculatePortfolioScenarioImpact(
      'BIAT',
      before,
      after
    );

    expect(result).not.toBeNull();
    expect(result!.impact).toBe(200);
    expect(result!.projectedPortfolio).toBe(100200);
  });

  it('calculates only the selected asset impact in a multi-asset portfolio', () => {
    const before = portfolio([
      position('BIAT', 10, 100),
      position('SFBT', 20, 50)
    ]);

    const after = portfolio([
      position('BIAT', 10, 90),
      position('SFBT', 20, 60)
    ]);

    const result = calculatePortfolioScenarioImpact(
      'BIAT',
      before,
      after
    );

    expect(result).not.toBeNull();
    expect(result!.impact).toBe(-100);
    expect(result!.projectedPortfolio).toBe(99900);
  });

  it('returns null when the selected asset is not held', () => {
    const before = portfolio([
      position('SFBT', 20, 50)
    ]);

    const after = portfolio([
      position('SFBT', 20, 45)
    ]);

    expect(
      calculatePortfolioScenarioImpact('BIAT', before, after)
    ).toBeNull();
  });

  it('returns null when a quoted price is missing', () => {
    const before = portfolio([
      position('BIAT', 10, 100)
    ]);

    const after = portfolio([
      position('BIAT', 10, null)
    ], null);

    expect(
      calculatePortfolioScenarioImpact('BIAT', before, after)
    ).toBeNull();
  });

  it('returns null when the quantity changes during the scenario', () => {
    const before = portfolio([
      position('BIAT', 10, 100)
    ]);

    const after = portfolio([
      position('BIAT', 15, 80)
    ]);

    expect(
      calculatePortfolioScenarioImpact('BIAT', before, after)
    ).toBeNull();
  });

  it('keeps projected portfolio unavailable without baseline valuation', () => {
    const before = portfolio([
      position('BIAT', 10, 100)
    ], null);

    const after = portfolio([
      position('BIAT', 10, 80)
    ]);

    const result = calculatePortfolioScenarioImpact(
      'BIAT',
      before,
      after
    );

    expect(result).not.toBeNull();
    expect(result!.impact).toBe(-200);
    expect(result!.portfolioBefore).toBeNull();
    expect(result!.projectedPortfolio).toBeNull();
  });

});