import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { StressResult } from '../actuarial.models';
import { ActuarialService } from '../actuarial.service';
import { STRESS_DEBOUNCE_MS, StressPanelComponent } from './stress-panel.component';

const RESULT: StressResult = {
  shock: { frequency: 0.2, severity: 0.1, claims_multiplier: 1.32 },
  portfolio: {
    segment: 'portfolio', policies: 3, premiums: 2000, expected_claims: 1360, actual_claims: 1300,
    stressed_claims: 1716, actual_margin: 700, stressed_margin: 284, expected_loss_ratio: 0.68,
    actual_loss_ratio: 0.65, stressed_loss_ratio: 0.858, break_even_shock: 0.5385
  },
  segments: [
    {
      segment: 1, policies: 2, premiums: 1000, expected_claims: 680, actual_claims: 500, stressed_claims: 660,
      actual_margin: 500, stressed_margin: 340, expected_loss_ratio: 0.68, actual_loss_ratio: 0.5,
      stressed_loss_ratio: 0.66, break_even_shock: 1
    },
    {
      segment: 2, policies: 1, premiums: 1000, expected_claims: 680, actual_claims: 800, stressed_claims: 1056,
      actual_margin: 200, stressed_margin: -56, expected_loss_ratio: 0.68, actual_loss_ratio: 0.8,
      stressed_loss_ratio: 1.056, break_even_shock: 0.25
    }
  ],
  segments_in_loss: 1,
  currency: { code: 'TND', label: 'dinar tunisien', rateFromModel: 3.36, modelCurrency: 'EUR', source: 'Cours moyen BCT' }
};

describe('StressPanelComponent', () => {
  let fixture: ComponentFixture<StressPanelComponent>;
  let service: jasmine.SpyObj<ActuarialService>;
  const text = () => (fixture.nativeElement as HTMLElement).textContent!.replace(/[  ]/g, ' ');

  beforeEach(() => {
    service = jasmine.createSpyObj<ActuarialService>('ActuarialService', ['stress']);
    service.stress.and.returnValue(of(RESULT));
    TestBed.configureTestingModule({
      imports: [StressPanelComponent],
      providers: [{ provide: ActuarialService, useValue: service }]
    });
    fixture = TestBed.createComponent(StressPanelComponent);
  });

  it('computes the default scenario once the debounce has elapsed', fakeAsync(() => {
    fixture.detectChanges();
    expect(service.stress).not.toHaveBeenCalled();

    tick(STRESS_DEBOUNCE_MS);
    fixture.detectChanges();

    expect(service.stress).toHaveBeenCalledOnceWith(0.2, 0.1, 'by_risk_class');
    expect(text()).toContain('65 % → 86 %');
    expect(text()).toContain('sinistralité attendue vaut 68 %');
    expect(text()).toContain('1 segment sur 2 passe en perte');
    expect(fixture.nativeElement.querySelectorAll('.status.ko').length).toBe(1);
  }));

  it('coalesces fast slider moves into a single request', fakeAsync(() => {
    fixture.detectChanges();
    tick(STRESS_DEBOUNCE_MS);
    service.stress.calls.reset();

    const slider: HTMLInputElement = fixture.nativeElement.querySelector('input[type=range]');
    for (const value of ['30', '40', '50']) {
      slider.value = value;
      slider.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      tick(50);
    }
    tick(STRESS_DEBOUNCE_MS);

    expect(service.stress).toHaveBeenCalledOnceWith(0.5, 0.1, 'by_risk_class');
  }));

  it('shows the error of the engine', fakeAsync(() => {
    service.stress.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 503, error: { detail: 'Moteur actuariel indisponible.' } }))
    );
    fixture.detectChanges();
    tick(STRESS_DEBOUNCE_MS);
    fixture.detectChanges();

    expect(text()).toContain('Moteur actuariel indisponible.');
  }));
});
