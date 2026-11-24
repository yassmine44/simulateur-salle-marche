import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { ActuarialService } from '../actuarial.service';
import { CHAIN_LADDER, RESERVING_REPORT } from '../testing/reserving-fixtures';
import { ReservingPageComponent } from './reserving-page.component';

describe('ReservingPageComponent', () => {
  let fixture: ComponentFixture<ReservingPageComponent>;
  let service: jasmine.SpyObj<ActuarialService>;
  const text = () => (fixture.nativeElement as HTMLElement).textContent!.replace(/[  ]/g, ' ');

  beforeEach(() => {
    service = jasmine.createSpyObj<ActuarialService>('ActuarialService', ['report', 'chainLadder']);
    service.report.and.returnValue(of(RESERVING_REPORT));
    service.chainLadder.and.returnValue(of(CHAIN_LADDER));

    TestBed.configureTestingModule({
      imports: [ReservingPageComponent],
      providers: [{ provide: ActuarialService, useValue: service }]
    });
    fixture = TestBed.createComponent(ReservingPageComponent);
    fixture.detectChanges();
  });

  it('shows the market portfolio with its backtest', () => {
    expect(service.report).toHaveBeenCalledWith('reserving');
    expect(text()).toContain('Marché (2 assureurs)');
    expect(text()).toContain('Réellement payé ensuite');
    expect(text()).toContain('✗ hors de l\'intervalle');
    // Thousands of dollars: 94.5 k$ → 94,5 k $US
    expect(text()).toContain('94,5 k $US');
  });

  it('draws the completed triangle with projected cells', () => {
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelectorAll('.triangle tbody tr').length).toBe(3);
    expect(element.querySelectorAll('.triangle td.projected').length).toBe(3);
    expect(element.querySelectorAll('.triangle td.diagonal').length).toBe(3);
  });

  it('switches portfolio', () => {
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');
    select.value = '42';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(text()).toContain('Assureur 42');
  });

  it('derives the backtest lesson from the data', () => {
    expect(text()).toContain('Chain-Ladder a surestimé la provision de 2 portefeuilles sur 2');
  });

  describe('simulation', () => {
    beforeEach(() => {
      fixture.componentInstance.mode.set('simulation');
      fixture.detectChanges();
    });

    it('sends the pasted triangle and the chosen confidence level', () => {
      fixture.componentInstance.triangleText.setValue('2019;100;150;165\n2020;110;165\n2021;120');
      fixture.componentInstance.confidence.setValue(0.99);
      fixture.componentInstance.simulate();
      fixture.detectChanges();

      expect(service.chainLadder).toHaveBeenCalledWith(
        [[100, 150, 165], [110, 165], [120]],
        0.99,
        [2019, 2020, 2021]
      );
      expect(fixture.nativeElement.querySelector('app-reserving-result')).not.toBeNull();
      expect(text()).not.toContain('Réellement payé ensuite'); // no backtest for a user triangle
    });

    it('reports a parsing error without calling the engine', () => {
      fixture.componentInstance.triangleText.setValue('100\n200');
      fixture.componentInstance.simulate();
      fixture.detectChanges();

      expect(service.chainLadder).not.toHaveBeenCalled();
      expect(text()).toContain('au moins 3 lignes');
    });

    it('reports the engine rejection', () => {
      service.chainLadder.and.returnValue(
        throwError(() => new HttpErrorResponse({ status: 400, error: { detail: 'Ligne 2 : valeurs après la diagonale.' } }))
      );
      fixture.componentInstance.simulate();
      fixture.detectChanges();

      expect(text()).toContain('Ligne 2 : valeurs après la diagonale.');
    });
  });
});
