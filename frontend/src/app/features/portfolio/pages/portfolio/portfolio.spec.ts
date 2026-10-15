import { ComponentFixture, TestBed } from '@angular/core/testing';

import { provideRouter } from '@angular/router';

import { of } from 'rxjs';

import {
  PerformanceResponse,
  PortfolioPosition,
  PortfolioResponse
} from '../../models/portfolio.models';

import { TradingService }
  from '../../../trading/services/trading.service';

import { PortfolioService }
  from '../../services/portfolio.service';

import { Portfolio } from './portfolio';


function position(
  symbol: string,
  marketValue: number,
  unrealizedPnl: number
): PortfolioPosition {

  return {
    assetSymbol: symbol,
    assetName: symbol,
    assetType: 'STOCK',
    quantity: 10,
    direction: 'LONG',
    averagePrice: 100,
    lastPrice: marketValue / 10,
    costBasis: 1000,
    marketValue,
    unrealizedPnl,
    unrealizedPnlPercent: unrealizedPnl / 10,
    realizedPnl: 0,
    weightPercent: marketValue / 1000,
    reservedQuantity: 0,
    availableQuantity: 10,
    openedAt: '2026-10-13T09:00:00Z'
  };
}


const PORTFOLIO: PortfolioResponse = {

  currency: 'USD',
  initialCapital: 100000,
  startedAt: null,
  resetCount: 0,
  limitsEnforced: true,

  cash: 97000,
  reservedCash: 2000,
  availableCash: 95000,

  positionsValue: 3200,
  totalValue: 100200,
  totalPnl: 200,
  totalPnlPercent: 0.2,
  realizedPnl: 0,
  unrealizedPnl: 200,
  tradeCount: 2,

  openPositions: [
    position('MSFT', 1200, 200),
    position('AAPL', 2000, 0)
  ],

  closedPositions: [],

  allocation: [
    { key: 'CASH', label: 'Liquidités', value: 97000, percent: 96.81 },
    { key: 'STOCK', label: 'Actions', value: 3200, percent: 3.19 }
  ],

  risk: {
    exposurePercent: 3.19,
    exposureLevel: 'LOW',
    largestPositionSymbol: 'AAPL',
    largestPositionPercent: 2,
    concentrationLevel: 'LOW',
    diversificationPercent: 46.88,
    effectivePositions: 1.9,
    diversificationLevel: 'MEDIUM',
    volatilityPercent: 0.05,
    volatilityLevel: 'LOW',
    maxDrawdownPercent: 0,
    drawdownLevel: 'LOW',
    overallLevel: 'LOW'
  },

  historyDays: 7,

  history: [
    { time: '2026-10-13T23:00:00Z', value: 100000 },
    { time: '2026-10-14T10:00:00Z', value: 100200 }
  ],

  generatedAt: '2026-10-14T10:00:00Z'
};


const PERFORMANCE: PerformanceResponse = {

  currency: 'USD',

  stats: {
    closedTrades: 2,
    wins: 1,
    losses: 1,
    winRatePercent: 50,
    averageWin: 100,
    averageLoss: -50,
    profitFactor: 2,
    expectancy: 25,
    averageHoldingMinutes: 90,
    bestTrade: { assetSymbol: 'AAPL', realizedPnl: 100, returnPercent: 10, closedAt: '2026-10-14T09:00:00Z' },
    worstTrade: { assetSymbol: 'MSFT', realizedPnl: -50, returnPercent: -5, closedAt: '2026-10-14T09:30:00Z' }
  },

  contributions: [
    { assetSymbol: 'MSFT', assetName: 'Microsoft', realizedPnl: 0, unrealizedPnl: 200, totalPnl: 200, contributionPercent: 0.2, open: true },
    { assetSymbol: 'AAPL', assetName: 'Apple', realizedPnl: -100, unrealizedPnl: 0, totalPnl: -100, contributionPercent: -0.1, open: false }
  ],

  execution: {
    takerFills: 3,
    makerFills: 1,
    averageTakerSlippageBps: 12.5,
    totalSlippageCost: 4.2
  },

  benchmark: {
    name: 'Acheter et conserver',
    description: 'Capital réparti à parts égales.',
    startedAt: '2026-10-13T09:00:00Z',
    assetCount: 10,
    currentValue: 99500,
    returnPercent: -0.5,
    portfolioReturnPercent: 0.2,
    outperformancePercent: 0.7,
    history: [
      { time: '2026-10-13T23:00:00Z', value: 100000 },
      { time: '2026-10-14T10:00:00Z', value: 99500 }
    ]
  }
};


describe('Portfolio', () => {

  let service: jasmine.SpyObj<PortfolioService>;

  let fixture: ComponentFixture<Portfolio>;

  let component: Portfolio;


  beforeEach(async () => {

    service =
      jasmine.createSpyObj<PortfolioService>(
        'PortfolioService',
        ['getPortfolio', 'getPerformance', 'reset', 'planRebalance', 'executeRebalance']
      );

    service.getPortfolio.and.returnValue(of(PORTFOLIO));

    service.getPerformance.and.returnValue(of(PERFORMANCE));

    service.planRebalance.and.returnValue(
      of({
        currency: 'USD',
        totalValue: 100200,
        availableCash: 95000,
        targetCashPercent: 80,
        estimatedCashAfter: 80000,
        lines: [
          {
            assetSymbol: 'AAPL', assetName: 'Apple', currentQuantity: 10, currentValue: 2000,
            currentPercent: 2, targetPercent: 20, targetValue: 20040, side: 'BUY',
            quantity: 90, estimatedPrice: 200, estimatedAmount: 18000, note: null
          }
        ],
        warnings: []
      })
    );

    service.executeRebalance.and.returnValue(
      of({
        ordersSent: 1,
        ordersFilled: 1,
        results: [
          {
            assetSymbol: 'AAPL', side: 'BUY', requestedQuantity: 90, executedQuantity: 90,
            orderId: 42, status: 'EXECUTED', message: null
          }
        ]
      })
    );

    const trading =
      jasmine.createSpyObj<TradingService>('TradingService', ['getAssets']);

    trading.getAssets.and.returnValue(of([]));

    service.reset.and.returnValue(
      of({
        ...PORTFOLIO,
        cash: 100000,
        availableCash: 100000,
        openPositions: [],
        resetCount: 1
      })
    );

    await TestBed.configureTestingModule({

      imports: [Portfolio],

      providers: [
        provideRouter([]),
        { provide: PortfolioService, useValue: service },
        { provide: TradingService, useValue: trading }
      ]

    }).compileComponents();

    fixture = TestBed.createComponent(Portfolio);

    component = fixture.componentInstance;

    fixture.detectChanges();
  });


  afterEach(() => {

    fixture.destroy();
  });


  it('affiche les positions, triées par valeur décroissante', () => {

    expect(service.getPortfolio).toHaveBeenCalledWith(7);

    expect(component.sortedPositions.map(p => p.assetSymbol))
      .toEqual(['AAPL', 'MSFT']);

    const text =
      (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('Positions ouvertes');

    expect(text).toContain('MSFT');

    expect(text).toContain('Risque global : Faible');
  });


  it('trie par résultat latent puis inverse le sens', () => {

    component.sortBy('unrealizedPnl');

    expect(component.sortedPositions[0].assetSymbol).toBe('MSFT');

    component.sortBy('unrealizedPnl');

    expect(component.sortedPositions[0].assetSymbol).toBe('AAPL');
  });


  it('explique chaque indicateur de risque', () => {

    expect(component.riskCards.length).toBe(5);

    expect(component.riskCards.every(card => card.explanation.length > 0))
      .toBeTrue();

    expect(component.riskCards[1].value).toContain('AAPL');
  });


  it('demande confirmation avant de réinitialiser', () => {

    component.askReset();

    expect(component.confirmingReset).toBeTrue();

    expect(service.reset).not.toHaveBeenCalled();

    // Après la réinitialisation, le serveur renvoie l'état remis à zéro.
    service.getPortfolio.and.returnValue(
      of({ ...PORTFOLIO, openPositions: [], resetCount: 1 })
    );

    component.confirmReset();

    expect(service.reset).toHaveBeenCalled();

    expect(component.confirmingReset).toBeFalse();

    expect(component.portfolio?.resetCount).toBe(1);

    expect(component.successMessage).toContain('réinitialisé');
  });


  it('recharge l’historique sur 30 jours', () => {

    component.selectHistory(30);

    expect(service.getPortfolio).toHaveBeenCalledWith(30);

    expect(service.getPerformance).toHaveBeenCalledWith(30);
  });


  it('affiche les statistiques et les contributions', () => {

    const text =
      (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('Vos statistiques de trader');

    expect(text).toContain('vient votre résultat');

    // Gain à droite, perte à gauche, proportionnels au plus grand.
    expect(component.contributionWidth(PERFORMANCE.contributions[0])).toBe(100);
    expect(component.contributionWidth(PERFORMANCE.contributions[1])).toBe(50);

    expect(component.holdingDuration(90)).toBe('1.5 h');
  });


  it('prépare un rééquilibrage puis l’exécute après confirmation', () => {

    component.openRebalance();

    // Point de départ : la répartition actuelle.
    expect(component.targetRows.map(row => row.assetSymbol)).toEqual(['MSFT', 'AAPL']);

    component.targetRows[1].targetPercent = 20;

    component.targetChanged();

    expect(component.targetTotal).toBeCloseTo(21.2, 1);

    component.computePlan();

    expect(service.planRebalance).toHaveBeenCalled();

    expect(component.planOrderCount).toBe(1);

    expect(service.executeRebalance).not.toHaveBeenCalled();

    component.askExecuteRebalance();

    component.executeRebalance();

    expect(service.executeRebalance).toHaveBeenCalled();

    expect(component.execution?.ordersFilled).toBe(1);
  });


  it('refuse une cible au-delà de 100 %', () => {

    component.openRebalance();

    component.targetRows[0].targetPercent = 80;
    component.targetRows[1].targetPercent = 30;

    expect(component.targetsValid).toBeFalse();

    component.computePlan();

    expect(service.planRebalance).not.toHaveBeenCalled();
  });

});
