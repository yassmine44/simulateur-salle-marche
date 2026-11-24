import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { QuoteRequest } from '../actuarial.models';
import { ActuarialService } from '../actuarial.service';
import { METADATA, QUOTE } from '../testing/actuarial-fixtures';
import { QuotePageComponent } from './quote-page.component';

describe('QuotePageComponent', () => {
  let fixture: ComponentFixture<QuotePageComponent>;
  let service: jasmine.SpyObj<ActuarialService>;

  const text = () => (fixture.nativeElement as HTMLElement).textContent!.replace(/[  ]/g, ' ');

  beforeEach(() => {
    service = jasmine.createSpyObj<ActuarialService>('ActuarialService', ['metadata', 'quote', 'history']);
    service.metadata.and.returnValue(of(METADATA));
    service.history.and.returnValue(of([]));
    service.quote.and.returnValue(of(QUOTE));

    TestBed.configureTestingModule({
      imports: [QuotePageComponent],
      providers: [{ provide: ActuarialService, useValue: service }]
    });
    fixture = TestBed.createComponent(QuotePageComponent);
    fixture.detectChanges();
  });

  it('builds the form from the engine metadata, prefilled with a preset', () => {
    expect(fixture.nativeElement.querySelector('#quote-driverAge').value).toBe('20');
    expect(fixture.nativeElement.querySelectorAll('select#quote-region option').length).toBe(3);
  });

  it('submits through the real button without reloading the page', () => {
    const form: HTMLFormElement = fixture.nativeElement.querySelector('form');
    let nativeSubmitPrevented = false;
    form.addEventListener('submit', event => (nativeSubmitPrevented = event.defaultPrevented));

    fixture.nativeElement.querySelector('button[type=submit]').click();

    expect(service.quote).toHaveBeenCalledTimes(1);
    expect(nativeSubmitPrevented).toBeTrue();
  });

  it('sends the profile and converts the assumptions from percentages to ratios', () => {
    fixture.componentInstance.submit();

    const request: QuoteRequest = service.quote.calls.mostRecent().args[0];
    expect(request.profile.driverAge).toBe(20);
    expect(request.assumptions).toEqual({ expenseRatio: 0.25, profitRatio: 0.05, safetyLoading: 0.03 });
    expect(request.explain).toBeTrue();
  });

  it('shows the premium, its explanation and refreshes the history', () => {
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(text()).toContain('883 €');
    expect(text()).toContain('Coefficient bonus-malus (100) augmente la prime : ×2,18');
    expect(fixture.nativeElement.querySelectorAll('app-relativity-chart .row').length).toBe(3);
    expect(service.history).toHaveBeenCalledTimes(2);
  });

  it('does not call the engine while the profile is invalid', () => {
    fixture.componentInstance.profileForm!.controls.driverAge.setValue(12);
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(service.quote).not.toHaveBeenCalled();
    expect(text()).toContain('Entre 18 et 100.');
  });

  it('rejects expenses and profit leaving nothing for claims', () => {
    fixture.componentInstance.assumptionsForm.patchValue({ expensePct: 70, profitPct: 30 });
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(service.quote).not.toHaveBeenCalled();
    expect(text()).toContain('Frais + bénéfice doivent rester sous 100 %');
  });

  it('shows the error returned by the backend', () => {
    service.quote.and.returnValue(
      throwError(() => new HttpErrorResponse({ status: 503, error: { detail: 'Moteur actuariel indisponible.' } }))
    );
    fixture.componentInstance.submit();
    fixture.detectChanges();

    expect(text()).toContain('Moteur actuariel indisponible.');
  });
});
