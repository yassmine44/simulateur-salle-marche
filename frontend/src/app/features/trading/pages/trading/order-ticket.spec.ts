import {
  allowedTimeInForce,
  buildOrderRequest,
  cancelReasonLabel,
  OrderTicketValue
} from './order-ticket';


function ticket(
  overrides: Partial<OrderTicketValue>
): OrderTicketValue {

  return {
    assetSymbol: ' aapl ',
    side: 'BUY',
    type: 'LIMIT',
    quantity: 10,
    limitPrice: 100,
    triggerPrice: null,
    trailPercent: null,
    timeInForce: 'GTC',
    protect: false,
    stopLossPrice: null,
    takeProfitPrice: null,
    ...overrides
  };
}


describe('buildOrderRequest', () => {

  it('construit un ordre limite normalisé', () => {

    const result = buildOrderRequest(ticket({}));

    expect(result.kind).toBe('order');

    if (result.kind !== 'order') {
      return;
    }

    expect(result.request.assetSymbol).toBe('AAPL');
    expect(result.request.type).toBe('LIMIT');
    expect(result.request.limitPrice).toBe(100);
    expect(result.request.triggerType).toBeNull();
    expect(result.request.stopLossPrice).toBeNull();
  });


  it('envoie un stop suiveur comme stop-loss au marché', () => {

    const result =
      buildOrderRequest(
        ticket({
          side: 'SELL',
          type: 'TRAILING_STOP',
          trailPercent: 5,
          timeInForce: 'DAY'
        })
      );

    expect(result.kind).toBe('order');

    if (result.kind !== 'order') {
      return;
    }

    expect(result.request.type).toBe('MARKET');
    expect(result.request.triggerType).toBe('STOP_LOSS');
    expect(result.request.trailPercent).toBe(5);
    expect(result.request.triggerPrice).toBeNull();
    expect(result.request.limitPrice).toBeNull();
    expect(result.request.timeInForce).toBe('DAY');
  });


  it('attache les protections à un achat (bracket)', () => {

    const result =
      buildOrderRequest(
        ticket({
          protect: true,
          stopLossPrice: 95,
          takeProfitPrice: 120
        })
      );

    expect(result.kind).toBe('order');

    if (result.kind !== 'order') {
      return;
    }

    expect(result.request.stopLossPrice).toBe(95);
    expect(result.request.takeProfitPrice).toBe(120);
  });


  it('ignore les protections sur une vente', () => {

    const result =
      buildOrderRequest(
        ticket({
          side: 'SELL',
          protect: true,
          stopLossPrice: 95
        })
      );

    expect(result.kind === 'order' && result.request.stopLossPrice).toBeNull();
  });


  it('refuse une protection sans aucun prix', () => {

    const result = buildOrderRequest(ticket({ protect: true }));

    expect(result.kind).toBe('invalid');
  });


  it('construit un OCO avec ses deux prix', () => {

    const complete =
      buildOrderRequest(
        ticket({
          side: 'SELL',
          type: 'OCO',
          takeProfitPrice: 120,
          stopLossPrice: 90
        })
      );

    expect(complete.kind).toBe('oco');

    if (complete.kind === 'oco') {
      expect(complete.request.quantity).toBe(10);
      expect(complete.request.takeProfitPrice).toBe(120);
    }

    expect(
      buildOrderRequest(ticket({ type: 'OCO', takeProfitPrice: 120 })).kind
    ).toBe('invalid');
  });

});


describe('allowedTimeInForce', () => {

  it('propose les durées compatibles avec chaque type', () => {

    expect(allowedTimeInForce('LIMIT')).toEqual(['GTC', 'DAY', 'IOC', 'FOK']);

    // Un ordre au marché ne peut pas attendre.
    expect(allowedTimeInForce('MARKET')).toEqual(['IOC', 'FOK']);

    // Un ordre conditionnel attend son seuil.
    expect(allowedTimeInForce('STOP_LOSS')).toEqual(['GTC', 'DAY']);
    expect(allowedTimeInForce('TRAILING_STOP')).toEqual(['GTC', 'DAY']);
  });


  it('traduit les raisons d’annulation', () => {

    expect(cancelReasonLabel('EXPIRED')).toBe('expiré');
    expect(cancelReasonLabel(null)).toBeNull();
  });

});
