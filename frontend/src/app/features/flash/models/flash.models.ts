export interface FlashCandle {
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface FlashMlMetrics {
  historicalPredictionSuccessRate: number | null;
  crashDetectionRating: number | null;
  rallyPredictionRating: number | null;
  explainer: string;
  available?: boolean;
}

export interface FlashSession {
  sessionId: string | null;
  symbol: string | null;
  status: 'IDLE' | 'RUNNING' | 'STOPPED' | string;
  serverNow: number;
  startAtEpochMs: number;
  intervalMs: number;
  visibleCandleCount: number;
  totalCandles: number;
  initialVisibleCandles: number;
  candles: FlashCandle[];
  currentCandle: FlashCandle | null;
  mlMetrics: FlashMlMetrics | null;
  messageType: string;
}

export interface FlashStartRequest {
  symbol: string;
}

export interface FlashOrder {
  side: string;
  quantity: number;
  executionPrice: number;
  candleIndex: number;
}

export interface FlashPortfolio {
  sessionId: string;
  symbol: string;
  cashBalance: number;
  sharesHeld: number;
  averageEntryPrice: number;
  currentPrice: number;
  marketValue: number;
  equity: number;
  unrealizedPnl: number;
  totalReturnPct: number;
  visibleCandleCount: number;
  totalCandles: number;
  completed: boolean;
  currentCandle: FlashCandle | null;
  recentOrders: FlashOrder[];
  mlMetrics: FlashMlMetrics | null;
}

export interface FlashVoiceInsightResponse {
  insight: string;
  spokenReady: boolean;
}

export interface FlashOrderBookLevel {
  price: number;
  quantity: number;
  orderCount: number;
}

export interface FlashOrderBookSnapshot {
  sessionId: string | null;
  symbol: string | null;
  serverNow: number;
  bids: FlashOrderBookLevel[];
  asks: FlashOrderBookLevel[];
}
