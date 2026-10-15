// ==========================================
// PORTFOLIO
// ==========================================

// Tout est calculé par le serveur à partir des transactions
// et des ordres ouverts de l'utilisateur.

export type RiskLevel =
  | 'LOW'
  | 'MEDIUM'
  | 'HIGH';


export type HistoryDays =
  | 7
  | 30;


export interface PortfolioPosition {

  assetSymbol: string;

  assetName: string;

  assetType: string | null;

  // > 0 : position acheteuse ; < 0 : position vendeuse
  // (données antérieures au contrôle des ordres).

  quantity: number;

  direction: 'LONG' | 'SHORT';

  // Prix de revient moyen pondéré.

  averagePrice: number;

  lastPrice: number;

  costBasis: number;

  marketValue: number;

  unrealizedPnl: number;

  unrealizedPnlPercent: number;

  realizedPnl: number;

  // Part de la valeur totale du portefeuille.

  weightPercent: number;

  // Engagée dans des ordres de vente en attente.

  reservedQuantity: number;

  availableQuantity: number;

  openedAt: string | null;
}


export interface ClosedPosition {

  assetSymbol: string;

  assetName: string;

  boughtQuantity: number;

  soldQuantity: number;

  realizedPnl: number;
}


export interface AllocationSlice {

  key: string;

  label: string;

  value: number;

  percent: number;
}


export interface PortfolioRisk {

  exposurePercent: number;

  exposureLevel: RiskLevel;

  largestPositionSymbol: string | null;

  largestPositionPercent: number;

  concentrationLevel: RiskLevel;

  diversificationPercent: number;

  effectivePositions: number;

  diversificationLevel: RiskLevel;

  volatilityPercent: number;

  volatilityLevel: RiskLevel;

  maxDrawdownPercent: number;

  drawdownLevel: RiskLevel;

  overallLevel: RiskLevel;
}


export interface EquityPoint {

  time: string;

  value: number;
}


export interface PortfolioResponse {

  currency: string;

  initialCapital: number;

  startedAt: string | null;

  resetCount: number;

  limitsEnforced: boolean;

  cash: number;

  reservedCash: number;

  availableCash: number;

  positionsValue: number;

  totalValue: number;

  totalPnl: number;

  totalPnlPercent: number;

  realizedPnl: number;

  unrealizedPnl: number;

  tradeCount: number;

  openPositions: PortfolioPosition[];

  closedPositions: ClosedPosition[];

  allocation: AllocationSlice[];

  risk: PortfolioRisk;

  historyDays: HistoryDays;

  history: EquityPoint[];

  generatedAt: string;
}


// Ce que l'utilisateur peut engager sur un actif.

export interface BuyingPowerResponse {

  currency: string;

  limitsEnforced: boolean;

  cash: number;

  reservedCash: number;

  availableCash: number;

  assetSymbol: string | null;

  heldQuantity: number;

  reservedQuantity: number;

  availableQuantity: number;
}


// ==========================================
// PERFORMANCE
// ==========================================

export interface TradeHighlight {

  assetSymbol: string;

  realizedPnl: number;

  returnPercent: number;

  closedAt: string;
}


// Statistiques sur les positions fermées (allers-retours).

export interface TraderStats {

  closedTrades: number;

  wins: number;

  losses: number;

  winRatePercent: number | null;

  averageWin: number | null;

  averageLoss: number | null;

  profitFactor: number | null;

  expectancy: number | null;

  averageHoldingMinutes: number | null;

  bestTrade: TradeHighlight | null;

  worstTrade: TradeHighlight | null;
}


export interface Contribution {

  assetSymbol: string;

  assetName: string;

  realizedPnl: number;

  unrealizedPnl: number;

  totalPnl: number;

  contributionPercent: number;

  open: boolean;
}


export interface ExecutionQuality {

  takerFills: number;

  makerFills: number;

  averageTakerSlippageBps: number | null;

  totalSlippageCost: number;
}


export interface Benchmark {

  name: string;

  description: string;

  startedAt: string;

  assetCount: number;

  currentValue: number;

  returnPercent: number;

  portfolioReturnPercent: number;

  outperformancePercent: number;

  history: EquityPoint[];
}


export interface PerformanceResponse {

  currency: string;

  stats: TraderStats;

  contributions: Contribution[];

  execution: ExecutionQuality;

  benchmark: Benchmark;
}


// ==========================================
// REBALANCE
// ==========================================

export interface RebalanceTarget {

  assetSymbol: string;

  targetPercent: number;
}


export interface RebalanceLine {

  assetSymbol: string;

  assetName: string;

  currentQuantity: number;

  currentValue: number;

  currentPercent: number;

  targetPercent: number;

  targetValue: number;

  side: 'BUY' | 'SELL' | null;

  quantity: number;

  estimatedPrice: number;

  estimatedAmount: number;

  note: string | null;
}


export interface RebalancePlanResponse {

  currency: string;

  totalValue: number;

  availableCash: number;

  targetCashPercent: number;

  estimatedCashAfter: number;

  lines: RebalanceLine[];

  warnings: string[];
}


export interface RebalanceResult {

  assetSymbol: string;

  side: 'BUY' | 'SELL';

  requestedQuantity: number;

  executedQuantity: number;

  orderId: number | null;

  status: string | null;

  message: string | null;
}


export interface RebalanceExecutionResponse {

  ordersSent: number;

  ordersFilled: number;

  results: RebalanceResult[];
}


// ==========================================
// STATEMENT
// ==========================================

export interface StatementTrade {

  tradeId: number;

  executedAt: string;

  assetSymbol: string;

  side: 'BUY' | 'SELL';

  quantity: number;

  price: number;

  amount: number;
}


export interface StatementPosition {

  assetSymbol: string;

  assetName: string;

  quantity: number;

  averagePrice: number;

  closingPrice: number;

  value: number;
}


export interface StatementResponse {

  statementNumber: string;

  accountNumber: string;

  holderName: string;

  holderEmail: string;

  currency: string;

  periodStart: string;

  periodEnd: string;

  generatedAt: string;

  portfolioStartedAt: string | null;

  openingValue: number;

  closingValue: number;

  netChange: number;

  netChangePercent: number;

  realizedPnl: number;

  closingCash: number;

  tradeCount: number;

  totalBought: number;

  totalSold: number;

  trades: StatementTrade[];

  positions: StatementPosition[];
}
