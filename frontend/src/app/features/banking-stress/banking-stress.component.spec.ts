import { ComponentFixture, TestBed } from '@angular/core/testing';
import { isDevMode } from '@angular/core';
import { of } from 'rxjs';

import { BankingStressComponent } from './banking-stress.component';
import { BankingStressService } from './banking-stress.service';
import {
  createDemoComparisonRequest,
  createDemoResponse
} from './banking-stress.demo';
import { BankingScenarioProposal } from './banking-scenario-proposal.model';

describe('BankingStressComponent', () => {
  let fixture: ComponentFixture<BankingStressComponent>;
  let service: jasmine.SpyObj<BankingStressService>;

  beforeEach(async () => {
    service = jasmine.createSpyObj<BankingStressService>(
      'BankingStressService',
      ['analyze', 'proposeScenario']
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

  it('shows normalized fictional ratios side by side without ranking the banks', () => {
    const component = fixture.componentInstance;
    component.loadDemoComparison();
    component.runDemo();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Comparaison normalisée');
    expect(text).toContain('BIAT');
    expect(text).toContain('Banque Zitouna');
    expect(text).toContain('Sévérité globale du choc / capital initial');
    expect(text).toContain('Impact supporté par la banque / capital initial');
    expect(text).toContain('Les deux parts sont additionnées une seule fois');
    expect(text).toContain('ce n’est pas une perte intégralement supportée par la banque');
    expect(text).toContain('positif = perte, négatif = gain');
    expect(text).toContain('En DEMO, les pertes proviennent de fixtures fictives fixes');
    expect(text).toContain('Ratios indicatifs non réglementaires');
    expect(text).not.toContain('classement officiel');
    expect(component.normalizedComparisons[0].banks[0]?.shockSeverityToInitialCapitalPercent)
      .toBeCloseTo(60_500 / 320_000 * 100, 8);
    expect(component.normalizedComparisons[0].banks[0]?.bankCapitalImpactPercent)
      .toBeCloseTo(39_325 / 320_000 * 100, 8);
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
    fixture.detectChanges();

    expect(service.analyze).toHaveBeenCalledTimes(1);
    expect(service.analyze).toHaveBeenCalledWith(
      createDemoComparisonRequest()
    );
    expect(component.isDemoResult).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('Comparaison normalisée');
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

  it('requests a scenario proposal from the backend and requires explicit confirmation', () => {
    const component = fixture.componentInstance;
    const proposal = createScenarioProposal();
    service.proposeScenario.and.returnValue(of(proposal));
    component.proposalSeverity = 'HIGH';
    component.proposalHorizonDays = 180;

    component.generateScenarioProposal();
    fixture.detectChanges();

    expect(service.proposeScenario).toHaveBeenCalledWith({
      severity: 'HIGH',
      horizonDays: 180,
      context: null
    });
    expect(component.scenarioProposal).toEqual(proposal);
    expect(component.scenarios).toHaveSize(0);
    expect(fixture.nativeElement.textContent).toContain('Proposition IA — à vérifier');

    component.confirmScenarioProposal();

    expect(component.scenarios).toHaveSize(1);
    expect(component.scenarios[0].type).toBe('COMBINED');
    expect(component.scenarios[0].defaultProbabilityAddOn).toBe(0.02);
    expect(component.proposalConfirmed).toBeTrue();
  });

  it('rejects invalid proposal values before confirmation', () => {
    const component = fixture.componentInstance;
    const proposal = createScenarioProposal();
    proposal.factors[0].value = 101;
    component.proposalSeverity = proposal.severity;
    component.proposalHorizonDays = proposal.horizonDays;
    service.proposeScenario.and.returnValue(of(proposal));

    component.generateScenarioProposal();

    expect(component.scenarioProposal).toBeNull();
    expect(component.proposalError).toContain('hors limites');
    expect(component.scenarios).toHaveSize(0);
  });

  it('does not request AI proposals for displayed demo results and keeps manual scenarios available', () => {
    const component = fixture.componentInstance;
    component.loadDemoComparison();
    component.runDemo();

    component.generateScenarioProposal();
    component.addScenario();

    expect(service.proposeScenario).not.toHaveBeenCalled();
    expect(component.scenarios).toHaveSize(2);
    expect(component.scenarios[1].type).toBe('COMBINED');
    expect(component.proposalError).toContain('désactivée pendant');
  });

  function createScenarioProposal(): BankingScenarioProposal {
    return {
      scenarioName: 'Proposition fictive de test',
      severity: 'HIGH',
      horizonDays: 180,
      factors: [
        {
          type: 'UNEMPLOYMENT_INCREASE',
          value: 2,
          unit: 'PERCENTAGE_POINTS',
          rationale: 'Facteur contextuel de test.'
        },
        {
          type: 'GDP_CONTRACTION',
          value: 3,
          unit: 'PERCENT',
          rationale: 'Facteur contextuel de test.'
        },
        {
          type: 'BANK_REVENUE_DECLINE',
          value: 4,
          unit: 'PERCENT',
          rationale: 'Facteur contextuel de test.'
        },
        {
          type: 'ASSET_QUALITY_DETERIORATION',
          value: 5,
          unit: 'PERCENT',
          rationale: 'Facteur contextuel de test.'
        },
        {
          type: 'DEFAULT_PROBABILITY_INCREASE',
          value: 2,
          unit: 'PERCENTAGE_POINTS',
          rationale: 'Incrément PD explicite de test.'
        }
      ],
      engineParameters: {
        rateShock: 0.01,
        recessionProbabilityMultiplier: 1.5,
        recessionLossGivenDefaultAddOn: 0.05,
        equityReturnShock: -0.1
      },
      justifications: ['Justification fictive de test.'],
      assumptions: ['Hypothèse fictive de test.']
    };
  }
});
