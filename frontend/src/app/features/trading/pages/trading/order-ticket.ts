import {
  CancelReason,
  CreateOcoRequest,
  CreateOrderRequest,
  OrderEntryType,
  OrderResponse,
  OrderSide,
  TimeInForce
} from '../../models/trading.models';


// ==========================================
// FORM VALUE -> REQUEST
// ==========================================

// Valeurs brutes du formulaire d'ordre.

export interface OrderTicketValue {

  assetSymbol: string;

  side: OrderSide;

  type: OrderEntryType;

  quantity: number;

  limitPrice: number | null;

  triggerPrice: number | null;

  trailPercent: number | null;

  timeInForce: TimeInForce;

  protect: boolean;

  stopLossPrice: number | null;

  takeProfitPrice: number | null;
}


export type OrderTicketRequest =
  | { kind: 'order'; request: CreateOrderRequest }
  | { kind: 'oco'; request: CreateOcoRequest }
  | { kind: 'invalid'; reason: string };


/*
 * Traduit le formulaire en requête pour le serveur.
 *
 * Fonction pure : aucune dépendance Angular, testable seule.
 */
export function buildOrderRequest(
  value: OrderTicketValue
): OrderTicketRequest {

  const assetSymbol =
    value.assetSymbol.trim().toUpperCase();

  const quantity =
    Number(value.quantity);

  if (value.type === 'OCO') {

    if (
      !value.takeProfitPrice
      ||
      !value.stopLossPrice
    ) {

      return {
        kind: 'invalid',
        reason: 'Indiquez le prix du take-profit et celui du stop-loss.'
      };
    }

    return {
      kind: 'oco',
      request: {
        assetSymbol,
        quantity,
        takeProfitPrice: Number(value.takeProfitPrice),
        stopLossPrice: Number(value.stopLossPrice),
        trailPercent: null
      }
    };
  }

  const conditional =
    value.type === 'STOP_LOSS'
    ||
    value.type === 'TAKE_PROFIT'
    ||
    value.type === 'TRAILING_STOP';

  // Protections attachées à un achat (bracket).

  const bracket =
    value.protect
    &&
    value.side === 'BUY'
    &&
    (value.type === 'LIMIT' || value.type === 'MARKET');

  if (
    bracket
    &&
    !value.stopLossPrice
    &&
    !value.takeProfitPrice
  ) {

    return {
      kind: 'invalid',
      reason: 'Indiquez au moins un stop-loss ou un take-profit, ou décochez la protection.'
    };
  }

  return {
    kind: 'order',
    request: {

      assetSymbol,

      side: value.side,

      // Stop-loss, take-profit et stop suiveur s'exécutent
      // au marché une fois leur seuil atteint.

      type: value.type === 'LIMIT' ? 'LIMIT' : 'MARKET',

      quantity,

      limitPrice:
        value.type === 'LIMIT'
          ? Number(value.limitPrice)
          : null,

      triggerType:
        !conditional
          ? null
          : value.type === 'TAKE_PROFIT'
            ? 'TAKE_PROFIT'
            : 'STOP_LOSS',

      triggerPrice:
        value.type === 'STOP_LOSS' || value.type === 'TAKE_PROFIT'
          ? Number(value.triggerPrice)
          : null,

      trailPercent:
        value.type === 'TRAILING_STOP'
          ? Number(value.trailPercent)
          : null,

      timeInForce: value.timeInForce,

      stopLossPrice:
        bracket && value.stopLossPrice
          ? Number(value.stopLossPrice)
          : null,

      takeProfitPrice:
        bracket && value.takeProfitPrice
          ? Number(value.takeProfitPrice)
          : null
    }
  };
}


// ==========================================
// TIME IN FORCE
// ==========================================

// Durées de validité possibles pour chaque type d'ordre.

export function allowedTimeInForce(
  type: OrderEntryType
): TimeInForce[] {

  switch (type) {

    case 'LIMIT':
      return ['GTC', 'DAY', 'IOC', 'FOK'];

    case 'MARKET':
      return ['IOC', 'FOK'];

    case 'OCO':
      return ['GTC'];

    default:
      return ['GTC', 'DAY'];
  }
}


export const TIME_IN_FORCE_LABELS: Record<TimeInForce, string> = {
  GTC: 'Jusqu’à annulation (GTC)',
  DAY: 'Journée (DAY)',
  IOC: 'Immédiat, le reste annulé (IOC)',
  FOK: 'Tout ou rien immédiat (FOK)'
};


export const TIME_IN_FORCE_SHORT: Record<TimeInForce, string> = {
  GTC: 'GTC',
  DAY: 'Jour',
  IOC: 'IOC',
  FOK: 'FOK'
};


// ==========================================
// LABELS
// ==========================================

const CANCEL_REASON_LABELS: Record<CancelReason, string> = {
  USER: 'par vous',
  REPLACED: 'remplacé',
  EXPIRED: 'expiré',
  IOC: 'reste IOC',
  FOK: 'tout ou rien impossible',
  OCO: 'l’autre branche a été exécutée',
  RESET: 'portefeuille réinitialisé',
  NO_LIQUIDITY: 'sans contrepartie'
};


export function cancelReasonLabel(
  reason: CancelReason | null
): string | null {

  return reason ? CANCEL_REASON_LABELS[reason] : null;
}


export function orderTypeLabel(
  order: OrderResponse
): string {

  if (order.triggerType === 'STOP_LOSS') {

    return order.trailPercent !== null
      ? `Stop suiveur ${order.trailPercent} %`
      : 'Stop-loss';
  }

  if (order.triggerType === 'TAKE_PROFIT') {
    return 'Take-profit';
  }

  return order.type === 'MARKET' ? 'Marché' : 'Limite';
}


// Ordre faisant partie d'une protection (bracket ou OCO).

export function linkLabel(
  order: OrderResponse
): string | null {

  if (order.parentOrderId !== null) {
    return `Protection de #${order.parentOrderId}`;
  }

  if (order.ocoGroup !== null) {
    return 'OCO';
  }

  if (
    order.bracketStopPrice !== null
    ||
    order.bracketTakeProfitPrice !== null
  ) {
    return 'Avec protections';
  }

  return null;
}
