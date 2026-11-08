export type ScenarioExecutionStatus =
  | 'RUNNING'
  | 'STOPPED'
  | 'EXPIRED'
  | 'INTERRUPTED';

export interface ScenarioHistoryItem {
  id: number;
  type: string;
  symbol: string;
  intensity: number;
  status: ScenarioExecutionStatus;
  startedAt: string;
  scheduledEndsAt: string;
  actualEndedAt: string | null;
}

export interface ScenarioMarketRiskMetrics {
  minMidPrice: number | null;
  maxMidPrice: number | null;
  maxPriceDrawdownPercent: number | null;
  maxSpread: number | null;
  maxBidDepthContractionPercent: number | null;
}




export interface ScenarioHistoryResponse {
  content: ScenarioHistoryItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ScenarioReportResponse {
  id: number;
  type: string;
  symbol: string;
  intensity: number;
  status: ScenarioExecutionStatus;
  startedAt: string;
  scheduledEndsAt: string;
  actualEndedAt: string | null;
  plannedDurationSeconds: number;
  actualDurationSeconds: number | null;
  completionPercent: number | null;
}

export interface ScenarioMarketObservation {
  observedAt: string;
  midPrice: number | null;
  bestBid: number | null;
  bestAsk: number | null;
  spread: number | null;
  totalBidVolume: number | null;
  totalAskVolume: number | null;
  weightedBidDepth: number | null;
  weightedAskDepth: number | null;
}


export interface ScenarioMarketHistory {
  executionId: number;
  symbol: string;
  startedAt: string;
  observedUntil: string;
  baseline: ScenarioMarketObservation | null;
  during: ScenarioMarketObservation[];
  lastDuring: ScenarioMarketObservation | null;
  priceChangePercent: number | null;
  riskMetrics: ScenarioMarketRiskMetrics | null;
}


