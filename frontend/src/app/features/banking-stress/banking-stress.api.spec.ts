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

const ANALYZE_ENDPOINT = 'http://localhost:8081/api/banking-stress-tests/analyze';

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
    expect(component.isDemoResult).toBeTrue();
    expect(component.response?.assumptionsAndLimitations.join(' '))
      .toContain('démonstration');
  });
});
