// ==========================================
// ASSETS
// ==========================================

export type AssetType =
  | 'STOCK'
  | 'FOREX'
  | 'CRYPTO'
  | 'INDEX';


export interface AssetResponse {
  id: number;

  symbol: string;

  name: string;

  type: AssetType;

  currency: string;

  active: boolean;
}


// ==========================================
// ORDER ENUMS / TYPES
// ==========================================

export type OrderSide =
  | 'BUY'
  | 'SELL';


export type OrderType =
  | 'LIMIT'
  | 'MARKET';


export type OrderStatus =
  | 'PENDING'
  | 'PARTIALLY_FILLED'
  | 'EXECUTED'
  | 'CANCELLED'
  | 'REJECTED';


// ==========================================
// CREATE ORDER
// ==========================================

export interface CreateOrderRequest {

  assetSymbol: string;

  side: OrderSide;

  type: OrderType;

  quantity: number;

  limitPrice: number | null;
}


// ==========================================
// ORDER RESPONSE
// ==========================================

export interface OrderResponse {

  id: number;

  userId: number;

  assetSymbol: string;

  side: OrderSide;

  type: OrderType;

  limitPrice: number | null;

  quantity: number;

  remainingQuantity: number;

  status: OrderStatus;

  createdAt: string;

  updatedAt: string;
}


// ==========================================
// ORDER BOOK
// ==========================================

export interface OrderBookLevel {

  price: number;

  totalQuantity: number;

  orderCount: number;
}


export interface OrderBookResponse {

  assetSymbol: string;

  bids: OrderBookLevel[];

  asks: OrderBookLevel[];

  generatedAt: string;
}


// ==========================================
// TRADE / EXECUTION
// ==========================================

export interface TradeResponse {

  id: number;

  assetSymbol: string;

  side: OrderSide;

  orderId: number;

  executionPrice: number;

  quantity: number;

  createdAt: string;
}
// ==========================================
// ORDER BOOK METRICS
// ==========================================

export interface OrderBookMetricsResponse {

  assetSymbol: string;

  bestBid: number | null;

  bestAsk: number | null;

  midPrice: number | null;

  spread: number | null;

  relativeSpread: number | null;

  relativeSpreadPercent: number | null;

  totalBidVolume: number;

  totalAskVolume: number;

  orderBookImbalance: number | null;

  weightedBidDepth: number | null;

  weightedAskDepth: number | null;

  generatedAt: string;
}
// ==========================================
// TEMPORAL METRICS
// ==========================================

export interface OrderBookTemporalMetricsResponse {

  assetSymbol: string;

  currentMidPrice: number | null;

  volatility60s: number | null;

  volatility60sPercent: number | null;

  momentum30s: number | null;

  momentum30sPercent: number | null;

  observationCount: number;

  generatedAt: string;
}


// ==========================================
// SLIPPAGE ESTIMATE
// ==========================================

export interface SlippageEstimateResponse {

  assetSymbol: string;

  side: OrderSide;

  requestedQuantity: number;

  executableQuantity: number;

  remainingQuantity: number;

  fullyExecutable: boolean;

  bestPrice: number | null;

  expectedExecutionPrice: number | null;

  absoluteSlippage: number | null;

  relativeSlippage: number | null;

  slippagePercent: number | null;

  levelsConsumed: number;

  generatedAt: string;
}