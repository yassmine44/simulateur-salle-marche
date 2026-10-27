
import {
  createDrawdownState,
  observePortfolioValue
} from './portfolio-drawdown';

describe('Portfolio Maximum Drawdown', () => {

  it('initializes without losses', () => {
    const state = createDrawdownState(100000);

    expect(state.maxDrawdownAmount).toBe(0);
    expect(state.maxDrawdownPercent).toBe(0);
    expect(state.observationCount).toBe(1);
  });

  it('calculates peak-to-trough maximum drawdown', () => {
    let state = createDrawdownState(100000);

    state = observePortfolioValue(state, 101000);
    state = observePortfolioValue(state, 99000);
    state = observePortfolioValue(state, 97000);

    expect(state.peakValue).toBe(101000);
    expect(state.lowestValue).toBe(97000);
    expect(state.maxDrawdownAmount).toBe(4000);

    expect(state.maxDrawdownPercent)
      .toBeCloseTo(3.9604, 3);

    expect(state.maxBaselineLossAmount).toBe(3000);
    expect(state.maxBaselineLossPercent).toBe(3);
  });

  it('preserves maximum drawdown after recovery', () => {
    let state = createDrawdownState(100000);

    state = observePortfolioValue(state, 90000);
    state = observePortfolioValue(state, 99500);

    expect(state.currentValue).toBe(99500);
    expect(state.maxDrawdownAmount).toBe(10000);
    expect(state.maxDrawdownPercent).toBe(10);
  });

  it('ignores invalid observations', () => {
    const state = createDrawdownState(100000);

    const result = observePortfolioValue(state, NaN);

    expect(result).toBe(state);
    expect(result.observationCount).toBe(1);
  });

});
