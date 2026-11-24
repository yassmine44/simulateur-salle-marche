import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { AdminOverview } from '../actuarial.models';
import { ActuarialAdminComponent } from './actuarial-admin.component';
import { ActuarialAdminService } from './actuarial-admin.service';

const OVERVIEW: AdminOverview = {
  engine: {
    status: 'ok',
    modelVersions: { frequency: '20261124', severity: '20261124' },
    largeClaimLoading: 0.39,
    assumptions: { expenseRatio: 0.25, profitRatio: 0.05, safetyLoading: 0.03, commercialFactor: 1.471429 },
    currency: { code: 'TND', label: 'dinar tunisien', rateFromModel: 3.36, modelCurrency: 'EUR', source: 'Cours moyen BCT' }
  },
  modelCards: null,
  engineError: null,
  usage: { totalQuotes: 12, quotesLast30Days: 5, distinctUsers: 3, byModelVersion: [] },
  recentActions: [
    {
      id: 1, type: 'MODELS_RELOADED', actorEmail: 'admin@example.test', createdAt: '2026-11-24T10:00:00Z',
      details: '{"frequency": "v2", "severity": "v2"}'
    }
  ]
};

describe('ActuarialAdminComponent', () => {
  let fixture: ComponentFixture<ActuarialAdminComponent>;
  let admin: jasmine.SpyObj<ActuarialAdminService>;
  const text = () => (fixture.nativeElement as HTMLElement).textContent!.replace(/[  ]/g, ' ');

  function create(overview: AdminOverview = OVERVIEW): void {
    admin = jasmine.createSpyObj<ActuarialAdminService>('ActuarialAdminService', [
      'overview', 'quotes', 'updateAssumptions', 'updateCurrency', 'reload'
    ]);
    admin.overview.and.returnValue(of(overview));
    admin.updateCurrency.and.returnValue(of({}));
    admin.quotes.and.returnValue(of([]));
    admin.updateAssumptions.and.returnValue(of({}));
    admin.reload.and.returnValue(of({}));
    TestBed.configureTestingModule({
      imports: [ActuarialAdminComponent],
      providers: [{ provide: ActuarialAdminService, useValue: admin }]
    });
    fixture = TestBed.createComponent(ActuarialAdminComponent);
    fixture.detectChanges();
  }

  it('shows the engine state, usage and a readable action log', () => {
    create();
    expect(text()).toContain('✓ En service');
    expect(text()).toContain('12');
    expect(text()).toContain('fréquence v2 · sévérité v2');
  });

  it('prefills the assumptions and previews the commercial factor', () => {
    create();
    const form = fixture.componentInstance.form;
    expect(form.getRawValue()).toEqual({ expensePct: 25, profitPct: 5, safetyPct: 3 });

    form.patchValue({ expensePct: 20, profitPct: 0, safetyPct: 0 });
    expect(fixture.componentInstance.previewFactor()).toBeCloseTo(1.25, 6);
  });

  it('saves the assumptions as ratios and refreshes the overview', () => {
    create();
    const component = fixture.componentInstance;
    component.form.patchValue({ expensePct: 20, profitPct: 2.5, safetyPct: 1 });
    component.form.markAsDirty();

    component.saveAssumptions();

    expect(admin.updateAssumptions).toHaveBeenCalledWith({ expenseRatio: 0.2, profitRatio: 0.025, safetyLoading: 0.01 });
    expect(admin.overview).toHaveBeenCalledTimes(2);
  });

  it('refuses expenses and profit leaving nothing for claims', () => {
    create();
    const component = fixture.componentInstance;
    component.form.patchValue({ expensePct: 70, profitPct: 30 });
    component.saveAssumptions();
    expect(admin.updateAssumptions).not.toHaveBeenCalled();
  });

  it('reports an unavailable engine but keeps the statistics', () => {
    create({ ...OVERVIEW, engine: null, engineError: 'Le moteur actuariel (ml-service) est injoignable.' });
    expect(text()).toContain('✗ Indisponible');
    expect(text()).toContain('injoignable');
    expect(text()).toContain('12');
  });

  it('prefills the currency and saves a new rate', () => {
    create();
    const component = fixture.componentInstance;
    expect(component.currencyForm.getRawValue()).toEqual({
      code: 'TND', label: 'dinar tunisien', rateFromModel: 3.36, source: 'Cours moyen BCT'
    });
    component.currencyForm.patchValue({ rateFromModel: 3.4 });
    component.currencyForm.markAsDirty();
    component.saveCurrency();
    expect(admin.updateCurrency).toHaveBeenCalledWith({
      code: 'TND', label: 'dinar tunisien', rateFromModel: 3.4, source: 'Cours moyen BCT'
    });
  });

  it('reloads the models and reports a failure', () => {
    create();
    admin.reload.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503, error: { detail: 'Modèles absents.' } })));
    fixture.componentInstance.reloadModels();
    fixture.detectChanges();
    expect(text()).toContain('✗ Modèles absents.');
  });
});

describe('ActuarialAdminComponent save button', () => {
  it('is disabled while the form repeats the current assumptions', () => {
    const admin = jasmine.createSpyObj<ActuarialAdminService>('ActuarialAdminService', [
      'overview', 'quotes', 'updateAssumptions', 'reload'
    ]);
    admin.overview.and.returnValue(of(OVERVIEW));
    admin.quotes.and.returnValue(of([]));
    TestBed.configureTestingModule({
      imports: [ActuarialAdminComponent],
      providers: [{ provide: ActuarialAdminService, useValue: admin }]
    });
    const fixture = TestBed.createComponent(ActuarialAdminComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const button = (): HTMLButtonElement => fixture.nativeElement.querySelector('button[type=submit]');

    expect(component.unchanged()).toBeTrue();
    expect(button().disabled).toBeTrue();

    component.form.patchValue({ expensePct: 20 });
    fixture.detectChanges();
    expect(component.unchanged()).toBeFalse();
    expect(button().disabled).toBeFalse();

    component.form.patchValue({ expensePct: 25 }); // back to the current value
    fixture.detectChanges();
    expect(button().disabled).toBeTrue();
  });
});
