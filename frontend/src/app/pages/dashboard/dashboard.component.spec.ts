import { ComponentFixture, TestBed } from '@angular/core/testing';

import { provideRouter } from '@angular/router';

import { of, throwError } from 'rxjs';

import { DashboardComponent } from './dashboard.component';

import { DashboardResponse } from './dashboard.models';

import { DashboardService } from './dashboard.service';

import { TradingService }
  from '../../features/trading/services/trading.service';


const DASHBOARD: DashboardResponse = {

  currency: 'USD',

  headline: {
    text: 'Votre portefeuille gagne 0,10 % aujourd’hui (+100,00 USD), porté par AAPL (+100,00).',
    tone: 'POSITIVE',
    dayChange: 100,
    dayChangePercent: 0.1
  },

  portfolio: {
    currency: 'USD',
    initialCapital: 100000,
    startedAt: null,
    resetCount: 0,
    limitsEnforced: true,
    cash: 99000,
    reservedCash: 0,
    availableCash: 99000,
    positionsValue: 1100,
    totalValue: 100100,
    totalPnl: 100,
    totalPnlPercent: 0.1,
    realizedPnl: 0,
    unrealizedPnl: 100,
    tradeCount: 1,
    openPositions: [
      {
        assetSymbol: 'AAPL', assetName: 'Apple', assetType: 'STOCK', quantity: 10, direction: 'LONG',
        averagePrice: 100, lastPrice: 110, costBasis: 1000, marketValue: 1100, unrealizedPnl: 100,
        unrealizedPnlPercent: 10, realizedPnl: 0, weightPercent: 1.1, reservedQuantity: 0,
        availableQuantity: 10, openedAt: '2026-10-14T09:00:00Z'
      }
    ],
    closedPositions: [],
    allocation: [],
    risk: {
      exposurePercent: 1.1, exposureLevel: 'LOW', largestPositionSymbol: 'AAPL', largestPositionPercent: 1.1,
      concentrationLevel: 'LOW', diversificationPercent: 0, effectivePositions: 1, diversificationLevel: 'HIGH',
      volatilityPercent: 0, volatilityLevel: 'LOW', maxDrawdownPercent: 0, drawdownLevel: 'LOW', overallLevel: 'LOW'
    },
    historyDays: 7,
    history: [
      { time: '2026-10-13T23:00:00Z', value: 100000 },
      { time: '2026-10-14T10:00:00Z', value: 100100 }
    ],
    generatedAt: '2026-10-14T10:00:00Z'
  },

  performance: {
    currency: 'USD',
    stats: {
      closedTrades: 0, wins: 0, losses: 0, winRatePercent: null, averageWin: null, averageLoss: null,
      profitFactor: null, expectancy: null, averageHoldingMinutes: null, bestTrade: null, worstTrade: null
    },
    contributions: [],
    execution: { takerFills: 1, makerFills: 0, averageTakerSlippageBps: 100, totalSlippageCost: 10 },
    benchmark: {
      name: 'Acheter et conserver', description: '', startedAt: '2026-10-13T09:00:00Z', assetCount: 10,
      currentValue: 99000, returnPercent: -1, portfolioReturnPercent: 0.1, outperformancePercent: 1.1,
      history: [
        { time: '2026-10-13T23:00:00Z', value: 100000 },
        { time: '2026-10-14T10:00:00Z', value: 99000 }
      ]
    }
  },

  insights: [
    {
      severity: 'WARNING', category: 'UNPROTECTED', title: 'AAPL sans stop-loss',
      message: 'Aucune vente automatique ne limite la perte.', assetSymbol: 'AAPL', orderId: null
    }
  ],

  watchlist: [
    {
      assetSymbol: 'AAPL', assetName: 'Apple', lastPrice: 110, changePercent: 2.5,
      sparkline: [100, 104, 110], positionValue: 1100, unrealizedPnl: 100
    }
  ],

  protections: [
    {
      assetSymbol: 'AAPL', quantity: 10, lastPrice: 110, status: 'UNPROTECTED', protectedQuantity: 0,
      stopPrice: null, stopDistancePercent: null, trailing: false, takeProfitPrice: null,
      takeProfitDistancePercent: null, lossAtStop: null, weightPercent: 1.1
    }
  ],

  habits: {
    ordersPlaced: 1, limitOrderPercent: 0, marketOrderPercent: 100, conditionalOrderPercent: 0,
    protectedEntryPercent: 0, closedTrades: 0, winRatePercent: null, profitFactor: null,
    averageTakerSlippageBps: 100, advice: ['Fermez une première position.']
  },

  activity: [
    {
      time: '2026-10-14T09:00:00Z', type: 'FILLED', title: 'Achat exécuté',
      detail: '10 AAPL à 100,00.', assetSymbol: 'AAPL', orderId: 1, tone: 'POSITIVE'
    }
  ],

  generatedAt: '2026-10-14T10:00:00Z'
};


describe('DashboardComponent', () => {

  let dashboardService: jasmine.SpyObj<DashboardService>;

  let fixture: ComponentFixture<DashboardComponent>;

  let component: DashboardComponent;


  beforeEach(async () => {

    dashboardService =
      jasmine.createSpyObj<DashboardService>('DashboardService', ['getDashboard']);

    dashboardService.getDashboard.and.returnValue(of(DASHBOARD));

    const tradingService =
      jasmine.createSpyObj<TradingService>('TradingService', ['getMyTrades']);

    tradingService.getMyTrades.and.returnValue(of([]));

    await TestBed.configureTestingModule({

      imports: [DashboardComponent],

      providers: [
        provideRouter([]),
        { provide: DashboardService, useValue: dashboardService },
        { provide: TradingService, useValue: tradingService }
      ]

    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);

    component = fixture.componentInstance;
  });


  afterEach(() => {

    fixture.destroy();
  });


  it('affiche les données réelles au lieu des valeurs fictives', () => {

    fixture.detectChanges();

    const text =
      (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('porté par AAPL');

    expect(text).toContain('Ce qui demande votre attention');

    expect(text).toContain('AAPL sans stop-loss');

    expect(text).toContain('Non protégée');

    expect(text).toContain('Achat exécuté');

    // Plus aucune valeur de démonstration.
    expect(text).not.toContain('DT');

    expect(text).not.toContain('103 450');
  });


  it('associe chaque position à sa protection', () => {

    fixture.detectChanges();

    expect(component.positionRows.length).toBe(1);

    expect(component.positionRows[0].protection?.status).toBe('UNPROTECTED');

    expect(component.riskIndicators.length).toBe(5);
  });


  it('affiche une erreur et permet de réessayer', () => {

    dashboardService.getDashboard.and.returnValue(
      throwError(() => new Error('réseau'))
    );

    fixture.detectChanges();

    expect(component.errorMessage).not.toBe('');

    dashboardService.getDashboard.and.returnValue(of(DASHBOARD));

    component.load();

    expect(component.errorMessage).toBe('');

    expect(component.dashboard).not.toBeNull();
  });

});
