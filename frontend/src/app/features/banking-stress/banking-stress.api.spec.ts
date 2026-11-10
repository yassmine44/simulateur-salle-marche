import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BankingStressComponent } from './banking-stress.component';
import {
  createDemoComparisonRequest,
  createDemoResponse
} from './banking-stress.demo';
import {
  BankingScenarioProposal,
  BankingScenarioProposalRequest
} from './banking-scenario-proposal.model';

const ANALYZE_ENDPOINT = 'http://localhost:8081/api/banking-stress-tests/analyze';
const PROPOSAL_ENDPOINT =
  'http://localhost:8081/api/banking-stress-tests/scenario-proposals';

describe('BankingStressComponent API integration', () => {
  let fixture: ComponentFixture<BankingStressComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BankingStressComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(BankingStressComponent);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('posts the bank and combined-scenario request and displays the HTTP response', () => {
    const component = fixture.componentInstance;
    const expectedRequest = createDemoComparisonRequest();
    component.loadDemoComparison();
    component.submit();

    const request = httpMock.expectOne(ANALYZE_ENDPOINT);
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBeTrue();
    expect(request.request.body).toEqual(expectedRequest);

    const response = createDemoResponse(expectedRequest);
    response.assumptionsAndLimitations = ['Réponse HTTP simulée du test.'];
    request.flush(response);
    fixture.detectChanges();

    expect(component.response).toEqual(response);
    expect(component.isDemoResult).toBeFalse();
    expect(fixture.nativeElement.textContent).toContain('BIAT');
    expect(fixture.nativeElement.textContent).toContain('Banque Zitouna');
    expect(fixture.nativeElement.textContent).toContain('Réponse HTTP simulée du test.');
  });

  it('displays the backend ProblemDetail for an HTTP 400 response', () => {
    const component = fixture.componentInstance;
    component.loadDemoComparison();
    component.submit();

    httpMock.expectOne(ANALYZE_ENDPOINT).flush({
      type: 'about:blank',
      title: 'Bad Request',
      status: 400,
      detail: 'Vérifiez les champs du formulaire.'
    }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(component.errorMessage).toBe('Vérifiez les champs du formulaire.');
    expect(fixture.nativeElement.textContent).toContain(
      'Vérifiez les champs du formulaire.'
    );
    expect(component.response).toBeNull();
  });

  it('displays an HTTP 401 authentication response', () => {
    const component = fixture.componentInstance;
    component.loadDemoComparison();
    component.submit();

    httpMock.expectOne(ANALYZE_ENDPOINT).flush({
      type: 'about:blank',
      title: 'Unauthorized',
      status: 401,
      detail: 'Authentification requise.'
    }, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    expect(component.errorMessage).toBe('Authentification requise.');
    expect(fixture.nativeElement.textContent).toContain('Authentification requise.');
  });

  it('reports a network failure without presenting a success response', () => {
    const component = fixture.componentInstance;
    component.loadDemoComparison();
    component.submit();

    httpMock.expectOne(ANALYZE_ENDPOINT).error(new ProgressEvent('error'));
    fixture.detectChanges();

    expect(component.errorMessage).toContain('service bancaire est injoignable');
    expect(component.response).toBeNull();
    expect(component.loading).toBeFalse();
  });

  it('does not make an HTTP request while running the local demo', () => {
    const component = fixture.componentInstance;
    component.loadDemoComparison();
    component.runDemo();

    httpMock.expectNone(ANALYZE_ENDPOINT);
    httpMock.expectNone(PROPOSAL_ENDPOINT);
    expect(component.isDemoResult).toBeTrue();
    expect(component.response?.assumptionsAndLimitations.join(' '))
      .toContain('démonstration');
  });

  it('sends proposal generation to the authenticated Spring API and displays the structured result', () => {
    const component = fixture.componentInstance;
    component.proposalSeverity = 'SEVERE';
    component.proposalHorizonDays = 540;
    component.proposalContext = 'Contexte fictif de validation';
    component.generateScenarioProposal();

    const request = httpMock.expectOne(PROPOSAL_ENDPOINT);
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBeTrue();
    expect(request.request.body).toEqual({
      severity: 'SEVERE',
      horizonDays: 540,
      context: 'Contexte fictif de validation'
    } satisfies BankingScenarioProposalRequest);

    request.flush(createProposal());
    fixture.detectChanges();

    expect(component.scenarioProposal?.scenarioName)
      .toBe('Proposition locale structurée');
    expect(fixture.nativeElement.textContent)
      .toContain('Proposition IA — à vérifier');
    expect(component.scenarios).toHaveSize(0);
  });

  function createProposal(): BankingScenarioProposal {
    return {
      scenarioName: 'Proposition locale structurée',
      severity: 'SEVERE',
      horizonDays: 540,
      factors: [
        {
          type: 'UNEMPLOYMENT_INCREASE',
          value: 1.5,
          unit: 'PERCENTAGE_POINTS',
          rationale: 'Valeur de test, non économique.'
        },
        {
          type: 'GDP_CONTRACTION',
          value: 2,
          unit: 'PERCENT',
          rationale: 'Valeur de test, non économique.'
        },
        {
          type: 'BANK_REVENUE_DECLINE',
          value: 3,
          unit: 'PERCENT',
          rationale: 'Valeur de test, non économique.'
        },
        {
          type: 'ASSET_QUALITY_DETERIORATION',
          value: 4,
          unit: 'PERCENT',
          rationale: 'Valeur de test, non économique.'
        },
        {
          type: 'DEFAULT_PROBABILITY_INCREASE',
          value: 1,
          unit: 'PERCENTAGE_POINTS',
          rationale: 'Valeur de test, non économique.'
        }
      ],
      engineParameters: {
        rateShock: null,
        recessionProbabilityMultiplier: null,
        recessionLossGivenDefaultAddOn: null,
        equityReturnShock: null
      },
      justifications: ['Proposition de fixture.'],
      assumptions: ['Les valeurs sont exclusivement des données de test.']
    };
  }
});
