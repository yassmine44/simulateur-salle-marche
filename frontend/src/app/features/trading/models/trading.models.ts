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

  // Règles de trading de l'actif.

  referencePrice: number | null;

  tickSize: number | null;

  minQuantity: number | null;
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


export type TriggerType =
  | 'STOP_LOSS'
  | 'TAKE_PROFIT';


// Choix proposés dans le formulaire.
// TRAILING_STOP = stop-loss suiveur ; OCO = protection de titres détenus.

export type OrderEntryType =
  | OrderType
  | TriggerType
  | 'TRAILING_STOP'
  | 'OCO';


// GTC : jusqu'à annulation ; DAY : journée ;
// IOC : immédiat, le reste est annulé ; FOK : tout ou rien immédiatement.

export type TimeInForce =
  | 'GTC'
  | 'DAY'
  | 'IOC'
  | 'FOK';


export type CancelReason =
  | 'USER'
  | 'REPLACED'
  | 'EXPIRED'
  | 'IOC'
  | 'FOK'
  | 'OCO'
  | 'RESET'
  | 'NO_LIQUIDITY';


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

  // Ordre conditionnel : type MARKET + seuil de déclenchement.

  triggerType?: TriggerType | null;

  triggerPrice?: number | null;

  timeInForce?: TimeInForce | null;

  // Stop suiveur : distance en % entre le cours et le seuil.

  trailPercent?: number | null;

  // Bracket (achat) : protections créées à l'exécution.

  stopLossPrice?: number | null;

  takeProfitPrice?: number | null;
}


// Protection de titres détenus : take-profit + stop-loss liés.

export interface CreateOcoRequest {

  assetSymbol: string;

  quantity: number;

  takeProfitPrice: number;

  stopLossPrice: number;

  trailPercent?: number | null;
}


// Aperçu d'un ordre avant envoi (rien n'est enregistré).

export interface OrderPreviewResponse {

  accepted: boolean;

  refusalReason: string | null;

  warnings: string[];

  currency: string;

  bestBid: number | null;

  bestAsk: number | null;

  midPrice: number | null;

  lastPrice: number | null;

  immediateQuantity: number | null;

  averagePrice: number | null;

  immediateAmount: number | null;

  slippageBps: number | null;

  levelsConsumed: number;

  restingQuantity: number | null;

  cancelledQuantity: number | null;

  triggerPrice: number | null;

  triggerDistancePercent: number | null;

  expiresAt: string | null;

  availableCashBefore: number | null;

  availableCashAfter: number | null;

  heldQuantityBefore: number | null;

  heldQuantityAfter: number | null;

  positionWeightAfterPercent: number | null;

  potentialGain: number | null;

  potentialLoss: number | null;

  rewardRiskRatio: number | null;
}


export interface ModifyOrderRequest {

  quantity: number;

  limitPrice: number;
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

  triggerType: TriggerType | null;

  triggerPrice: number | null;

  triggeredAt: string | null;

  trailPercent: number | null;

  timeInForce: TimeInForce;

  expiresAt: string | null;

  cancelReason: CancelReason | null;

  bracketStopPrice: number | null;

  bracketTakeProfitPrice: number | null;

  parentOrderId: number | null;

  ocoGroup: string | null;

  arrivalMidPrice: number | null;

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

  // Part du niveau appartenant à l'utilisateur connecté.

  myQuantity: number;
}


export interface OrderBookResponse {

  assetSymbol: string;

  bids: OrderBookLevel[];

  asks: OrderBookLevel[];

  generatedAt: string;
}


// ==========================================
// MARKET DATA
// ==========================================

export type CandleInterval =
  | '1m'
  | '5m'
  | '15m';


export interface MarketSummary {

  assetSymbol: string;

  lastPrice: number | null;

  changePercent: number | null;

  high24h: number | null;

  low24h: number | null;

  volume24h: number;

  tradeCount24h: number;
}


// Exécution publique du marché.

export interface MarketTrade {

  id: number;

  price: number;

  quantity: number;

  aggressorSide: OrderSide | null;

  createdAt: string;
}


export interface Candle {

  time: string;

  open: number;

  high: number;

  low: number;

  close: number;

  volume: number;
}


export interface MarketOverviewResponse {

  summary: MarketSummary;

  recentTrades: MarketTrade[];

  candleInterval: CandleInterval;

  candles: Candle[];
}


export type OrderBookStreamEvent =
  | 'connected'
  | 'changed'
  | 'disconnected';


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

  // Qualité d'exécution.
  // TAKER : votre ordre a pris un prix existant ; MAKER : il attendait.

  liquidity: 'TAKER' | 'MAKER' | null;

  arrivalMidPrice: number | null;

  // Écart au prix milieu en points de base (> 0 = coût).

  slippageBps: number | null;

  slippageAmount: number | null;
}
