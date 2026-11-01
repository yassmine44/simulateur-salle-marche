export type GamificationEventKey =
  | '2008'
  | '2020'
  | '2022';


export type GamificationOrderSide =
  | 'BUY'
  | 'SELL';


export type ImpactLevel =
  | 'HIGH'
  | 'MEDIUM'
  | 'LOW';


export interface GamificationChallenge {
  key: GamificationEventKey;
  label: string;
  description: string;
}


export interface GamificationCandle {
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}


export interface GamificationOrder {
  id: number;
  side: GamificationOrderSide;
  quantity: number;
  executionPrice: number;
  candleIndex: number;
  createdAt: string;
}


export interface HistoricalEvent {
  date: string;
  description: string;
  impact: ImpactLevel;
}


export interface AiAnalysisReport {
  rsiValue: number | null;
  smaValue: number | null;
  volatility: number | null;
  learningPhase: boolean;
  trendIndicator: string;
  fundamentalSentiment: string;
  mlConfidenceScore: number | null;
  predictionDirection: string;
  mlConfidenceLabel: string;
  detailedAnalysis: string;
}


export interface WeeklyContext {
  aiAnalysis: AiAnalysisReport | null;
  events: HistoricalEvent[];
}


export interface GamificationPortfolio {
  event: string;
  eventLabel: string;
  cashBalance: number;
  sharesHeld: number;
  averageEntryPrice: number;
  visibleCandleCount: number;
  totalCandles: number;
  currentPrice: number;
  marketValue: number;
  unrealizedPnl: number;
  equity: number;
  totalReturnPct: number;
  completed: boolean;
  startedAt: string;
  updatedAt: string;
  recentOrders: GamificationOrder[];
  currentCandle: GamificationCandle | null;
  weeklyContext: WeeklyContext | null;
}


export interface GamificationOrderRequest {
  side: GamificationOrderSide;
  quantity: number;
}
