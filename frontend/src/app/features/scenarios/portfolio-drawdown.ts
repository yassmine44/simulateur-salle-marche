
export interface PortfolioDrawdownState {
  baselineValue: number;
  currentValue: number;
  peakValue: number;
  lowestValue: number;

  maxDrawdownAmount: number;
  maxDrawdownPercent: number;

  maxBaselineLossAmount: number;
  maxBaselineLossPercent: number;

  observationCount: number;
}

export function createDrawdownState(
  baselineValue: number
): PortfolioDrawdownState {
  if (!Number.isFinite(baselineValue) || baselineValue <= 0) {
    throw new Error('Baseline portfolio value must be positive.');
  }

  return {
    baselineValue,
    currentValue: baselineValue,
    peakValue: baselineValue,
    lowestValue: baselineValue,

    maxDrawdownAmount: 0,
    maxDrawdownPercent: 0,

    maxBaselineLossAmount: 0,
    maxBaselineLossPercent: 0,

    observationCount: 1
  };
}

export function observePortfolioValue(
  state: PortfolioDrawdownState,
  value: number
): PortfolioDrawdownState {
  if (!Number.isFinite(value) || value <= 0) {
    return state;
  }

  const peakValue = Math.max(state.peakValue, value);
  const lowestValue = Math.min(state.lowestValue, value);

  const drawdownAmount = Math.max(0, peakValue - value);
  const drawdownPercent =
    (drawdownAmount / peakValue) * 100;

  const baselineLossAmount = Math.max(
    0,
    state.baselineValue - value
  );

  const baselineLossPercent =
    (baselineLossAmount / state.baselineValue) * 100;

  return {
    ...state,
    currentValue: value,
    peakValue,
    lowestValue,

    maxDrawdownAmount: Math.max(
      state.maxDrawdownAmount,
      drawdownAmount
    ),

    maxDrawdownPercent: Math.max(
      state.maxDrawdownPercent,
      drawdownPercent
    ),

    maxBaselineLossAmount: Math.max(
      state.maxBaselineLossAmount,
      baselineLossAmount
    ),

    maxBaselineLossPercent: Math.max(
      state.maxBaselineLossPercent,
      baselineLossPercent
    ),

    observationCount: state.observationCount + 1
  };
}
