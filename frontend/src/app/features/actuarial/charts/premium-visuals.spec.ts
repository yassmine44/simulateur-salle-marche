import { TestBed } from '@angular/core/testing';

import { currencyCodeOf, formatCompactMoney, formatMoney } from '../actuarial.format';
import { PremiumBreakdownComponent, premiumParts } from './premium-breakdown.component';
import { gaugePosition, riskLevel, RiskGaugeComponent } from './risk-gauge.component';

const plain = (text: string) => text.replace(/[  ]/g, ' ');

describe('premium parts', () => {
  const assumptions = { expenseRatio: 0.25, profitRatio: 0.05, safetyLoading: 0.03 };
  const pure = 600;
  const commercial = (pure * 1.03) / 0.7;

  it('adds up exactly to the commercial premium', () => {
    const parts = premiumParts(pure, commercial, assumptions);
    const total = parts.reduce((sum, p) => sum + p.amount, 0);
    expect(total).toBeCloseTo(commercial, 8);
    expect(parts.reduce((sum, p) => sum + p.share, 0)).toBeCloseTo(1, 10);
    expect(parts.map(p => p.key)).toEqual(['claims', 'expenses', 'safety', 'profit']);
  });

  it('renders one labelled figure per part', () => {
    const fixture = TestBed.createComponent(PremiumBreakdownComponent);
    fixture.componentRef.setInput('purePremium', pure);
    fixture.componentRef.setInput('commercialPremium', commercial);
    fixture.componentRef.setInput('assumptions', assumptions);
    fixture.componentRef.setInput('currency', 'TND');
    fixture.detectChanges();
    const text = plain(fixture.nativeElement.textContent);
    expect(fixture.nativeElement.querySelectorAll('.segment').length).toBe(4);
    expect(text).toContain('600 DT');
    expect(text).toContain('Bénéfice');
  });
});

describe('risk gauge', () => {
  it('places the average in the middle-left and ×2 / ×0.5 symmetrically around it', () => {
    const average = gaugePosition(1);
    expect(gaugePosition(2) - average).toBeCloseTo(average - gaugePosition(0.5), 10);
    expect(gaugePosition(0.01)).toBe(0);
    expect(gaugePosition(100)).toBe(1);
  });

  it('labels the risk level', () => {
    expect(riskLevel(0.5).tone).toBe('low');
    expect(riskLevel(1).tone).toBe('average');
    expect(riskLevel(2).tone).toBe('high');
    expect(riskLevel(4.4).label).toBe('Risque très élevé');
  });

  it('announces the reading to screen readers', () => {
    const fixture = TestBed.createComponent(RiskGaugeComponent);
    fixture.componentRef.setInput('relativeRisk', 4.44);
    fixture.detectChanges();
    const label = fixture.nativeElement.querySelector('figure').getAttribute('aria-label');
    expect(label).toContain('4,4 fois le risque moyen');
  });
});

describe('money formatting', () => {
  it('uses the Tunisian notation for the dinar', () => {
    expect(plain(formatMoney(2988.39, 'TND'))).toBe('2 988 DT');
    expect(plain(formatMoney(2988.39, 'TND', true))).toBe('2 988,390 DT');
    expect(plain(formatCompactMoney(31_200_000, 'TND'))).toBe('31,2 M DT');
    expect(plain(formatMoney(883.19, 'EUR'))).toBe('883 €');
  });

  it('treats a missing currency as the model currency (euro), never as dinars', () => {
    expect(currencyCodeOf(undefined)).toBe('EUR');
    expect(currencyCodeOf(null)).toBe('EUR');
    expect(currencyCodeOf('')).toBe('EUR');
    expect(currencyCodeOf({ code: 'TND' })).toBe('TND');
    expect(currencyCodeOf('TND')).toBe('TND');
    expect(plain(formatMoney(883.19))).toBe('883 €');
  });
});
