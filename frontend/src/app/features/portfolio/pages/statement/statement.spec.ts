import { ComponentFixture, TestBed } from '@angular/core/testing';

import { provideRouter } from '@angular/router';

import { of } from 'rxjs';

import { StatementResponse } from '../../models/portfolio.models';

import { PortfolioService }
  from '../../services/portfolio.service';

import { Statement } from './statement';


const STATEMENT: StatementResponse = {

  statementNumber: 'REL-1-20260201-20260228',
  accountNumber: 'SIM-000001',
  holderName: 'Alice Trader',
  holderEmail: 'alice@example.com',
  currency: 'USD',
  periodStart: '2026-02-01',
  periodEnd: '2026-02-28',
  generatedAt: '2026-10-14T10:00:00Z',
  portfolioStartedAt: null,

  openingValue: 100000,
  closingValue: 100100,
  netChange: 100,
  netChangePercent: 0.1,
  realizedPnl: 40,
  closingCash: 99440,

  tradeCount: 1,
  totalBought: 1000,
  totalSold: 0,

  trades: [
    {
      tradeId: 7,
      executedAt: '2026-02-10T10:00:00Z',
      assetSymbol: 'AAPL',
      side: 'BUY',
      quantity: 10,
      price: 100,
      amount: 1000
    }
  ],

  positions: []
};


describe('Statement', () => {

  let service: jasmine.SpyObj<PortfolioService>;

  let fixture: ComponentFixture<Statement>;

  let component: Statement;


  beforeEach(async () => {

    service =
      jasmine.createSpyObj<PortfolioService>('PortfolioService', ['getStatement']);

    service.getStatement.and.returnValue(of(STATEMENT));

    await TestBed.configureTestingModule({

      imports: [Statement],

      providers: [
        provideRouter([]),
        { provide: PortfolioService, useValue: service }
      ]

    }).compileComponents();

    fixture = TestBed.createComponent(Statement);

    component = fixture.componentInstance;
  });


  it('demande le mois entier, du premier au dernier jour', () => {

    component.month = '2026-02';

    component.load();

    expect(service.getStatement).toHaveBeenCalledWith('2026-02-01', '2026-02-28');
  });


  it('affiche le relevé et ses opérations', () => {

    fixture.detectChanges();

    const text =
      (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('Relevé de compte');

    expect(text).toContain('SIM-000001');

    expect(text).toContain('AAPL');
  });


  it('imprime via le navigateur', () => {

    const print = spyOn(window, 'print');

    component.print();

    expect(print).toHaveBeenCalled();
  });

});
