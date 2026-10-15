import {
  PerformanceResponse,
  PortfolioResponse
} from '../../features/portfolio/models/portfolio.models';


// ==========================================
// DASHBOARD
// ==========================================

// Tout est calculé par le serveur à partir des données
// de l'utilisateur connecté.

export type Tone =
  | 'POSITIVE'
  | 'NEGATIVE'
  | 'NEUTRAL';


export type InsightSeverity =
  | 'CRITICAL'
  | 'WARNING'
  | 'INFO';


export interface Headline {

  text: string;

  tone: Tone;

  dayChange: number;

  dayChangePercent: number;
}


// Point qui mérite une action.

export interface Insight {

  severity: InsightSeverity;

  category: string;

  title: string;

  message: string;

  assetSymbol: string | null;

  orderId: number | null;
}


export interface WatchedAsset {

  assetSymbol: string;

  assetName: string;

  lastPrice: number | null;

  changePercent: number | null;

  sparkline: number[];

  positionValue: number | null;

  unrealizedPnl: number | null;
}


export type ProtectionStatus =
  | 'PROTECTED'
  | 'PARTIAL'
  | 'UNPROTECTED';


export interface Protection {

  assetSymbol: string;

  quantity: number;

  lastPrice: number;

  status: ProtectionStatus;

  protectedQuantity: number;

  stopPrice: number | null;

  stopDistancePercent: number | null;

  trailing: boolean;

  takeProfitPrice: number | null;

  takeProfitDistancePercent: number | null;

  lossAtStop: number | null;

  weightPercent: number;
}


export interface Habits {

  ordersPlaced: number;

  limitOrderPercent: number | null;

  marketOrderPercent: number | null;

  conditionalOrderPercent: number | null;

  protectedEntryPercent: number | null;

  closedTrades: number;

  winRatePercent: number | null;

  profitFactor: number | null;

  averageTakerSlippageBps: number | null;

  advice: string[];
}


export type ActivityType =
  | 'PLACED'
  | 'FILLED'
  | 'TRIGGERED'
  | 'CANCELLED'
  | 'REJECTED';


export interface ActivityEvent {

  time: string;

  type: ActivityType;

  title: string;

  detail: string;

  assetSymbol: string | null;

  orderId: number | null;

  tone: Tone;
}


export interface DashboardResponse {

  currency: string;

  headline: Headline;

  portfolio: PortfolioResponse;

  performance: PerformanceResponse;

  insights: Insight[];

  watchlist: WatchedAsset[];

  protections: Protection[];

  habits: Habits;

  activity: ActivityEvent[];

  generatedAt: string;
}
