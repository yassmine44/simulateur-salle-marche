import {
  PortfolioStressResponse
} from './portfolio-stress.service';

export interface PortfolioScenarioImpact {
  symbol: string;
  quantity: number;
  priceBefore: number;
  priceNow: number;
  impact: number;
  portfolioBefore: number | null;
  projectedPortfolio: number | null;
  currency: string;
}

export function calculatePortfolioScenarioImpact(
  symbol: string,
  baseline: PortfolioStressResponse,
  current: PortfolioStressResponse
): PortfolioScenarioImpact | null {
  const before = baseline.positions.find(
    position => position.assetSymbol === symbol
  );

  const after = current.positions.find(
    position => position.assetSymbol === symbol
  );

  if (
    !before ||
    !after ||
    before.quotedMidPrice === null ||
    after.quotedMidPrice === null ||
    before.quantity !== after.quantity
  ) {
    return null;
  }

  const quantity = before.quantity;
  const priceBefore = before.quotedMidPrice;
  const priceNow = after.quotedMidPrice;

  const impact = quantity * (priceNow - priceBefore);

  const portfolioBefore = baseline.quotedTotalValue;

  const projectedPortfolio =
    portfolioBefore === null
      ? null
      : portfolioBefore + impact;

  return {
    symbol,
    quantity,
    priceBefore,
    priceNow,
    impact,
    portfolioBefore,
    projectedPortfolio,
    currency: baseline.currency
  };
}