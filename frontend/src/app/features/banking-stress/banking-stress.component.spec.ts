import { ComponentFixture, TestBed } from '@angular/core/testing';
import { isDevMode } from '@angular/core';
import { of } from 'rxjs';

import { BankingStressComponent } from './banking-stress.component';
import { BankingStressService } from './banking-stress.service';
import {
  createDemoComparisonRequest,
  createDemoResponse
} from './banking-stress.demo';

describe('BankingStressComponent', () => {
  let fixture: ComponentFixture<BankingStressComponent>;
  let service: jasmine.SpyObj<BankingStressService>;

  beforeEach(async () => {
    service = jasmine.createSpyObj<BankingStressService>(
      'BankingStressService',
      ['analyze']
    );
    service.analyze.and.returnValue(
      of(createDemoResponse(createDemoComparisonRequest()))
    );
    await TestBed.configureTestingModule({
      imports: [BankingStressComponent],
      providers: [{ provide: BankingStressService, useValue: service }]
    }).compileComponents();
    fixture = TestBed.createComponent(BankingStressComponent);
    fixture.detectChanges();
  });

  it('exposes the local demo only when Angular is in development mode', () => {
    expect(fixture.componentInstance.demoAvailable).toBe(isDevMode());
    if (isDevMode()) {
      expect(fixture.nativeElement.textContent)
        .toContain('Démo locale de développement uniquement');
    }
  });

  it('renders the BIAT and Banque Zitouna preset with published KDT references', () => {
    const component = fixture.componentInstance;
    component.loadDemoComparison();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(component.banks.map(bank => bank.bankName)).toEqual(['BIAT', 'Banque Zitouna']);
    expect(text).toContain('Données financières publiées — 2025');
    expect(text).toContain('KDT');
    expect(text).toContain('1 KDT = 1 000 TND');
    expect(text).toContain('Capital proxy (TND (DT))');
    expect(text).toContain('EAD (TND (DT))');
    expect(text).toContain('Valeur actuelle (TND (DT))');
    expect(text).toContain('Valeur de marché (TND (DT))');
    expect(text).toContain('Expositions de crédit');
    expect(text).toContain('Expositions de taux');
    expect(text).toContain('Expositions actions / marché');
    expect(text).toContain('Scénario combiné');
    expect(text).toContain('Source et limites d’extraction');
  });

  it('runs the local fixture without calling the real HTTP service', () => {
    const component = fixture.componentInstance;
    component.loadDemoComparison();
    component.runDemo();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(component.isDemoResult).toBeTrue();
    expect(text).toContain('Résultats simulés — démonstration');
    expect(text).toContain('Ratios indicatifs non réglementaires');
    expect(text).toContain('TND (DT)');
    expect(text).toContain('ne reflètent pas les paramètres saisis');
    expect(service.analyze).not.toHaveBeenCalled();
  });

  it('clears demo results when the user changes a form value', () => {
    const component = fixture.componentInstance;
    component.loadDemoComparison();
    component.runDemo();
    fixture.detectChanges();

    const horizonInput: HTMLInputElement =
      fixture.nativeElement.querySelector('[name="creditHorizonDays"]');
    horizonInput.value = '180';
    horizonInput.dispatchEvent(new Event('input', { bubbles: true }));
    fixture.detectChanges();

    expect(component.creditHorizonDays).toBe(180);
    expect(component.response).toBeNull();
    expect(fixture.nativeElement.textContent)
      .not.toContain('Résultats simulés — démonstration');
  });

  it('keeps the normal submit path connected to the HTTP service', () => {
    const component = fixture.componentInstance;
    component.loadDemoComparison();
    component.submit();

    expect(service.analyze).toHaveBeenCalledTimes(1);
    expect(service.analyze).toHaveBeenCalledWith(
      createDemoComparisonRequest()
    );
    expect(component.isDemoResult).toBeFalse();
  });

  it('keeps validation active for the demo path', () => {
    const component = fixture.componentInstance;
    component.loadDemoComparison();
    component.banks[0].creditExposures[0].terms.investorLossShare = '0.2';
    component.runDemo();

    expect(component.response).toBeNull();
    expect(component.errorMessage).toContain('totaliser exactement 100 %');
    expect(service.analyze).not.toHaveBeenCalled();
  });
});
