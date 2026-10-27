
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting
} from '@angular/common/http/testing';

import { ScenarioHistoryService } from './scenario-history.service';
import {
  ScenarioHistoryResponse,
  ScenarioReportResponse
} from './scenario-history.model';

describe('ScenarioHistoryService', () => {
  let service: ScenarioHistoryService;
  let httpMock: HttpTestingController;

  const baseUrl =
    'http://localhost:8081/api/admin/scenarios/history';

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ScenarioHistoryService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(ScenarioHistoryService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('charge une page de l historique', () => {
    const response: ScenarioHistoryResponse = {
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0
    };

    let received: ScenarioHistoryResponse | undefined;

    service.getHistory(0, 10).subscribe(result => {
      received = result;
    });

    const request = httpMock.expectOne(req =>
      req.method === 'GET' &&
      req.url === baseUrl &&
      req.params.get('page') === '0' &&
      req.params.get('size') === '10'
    );

    expect(request.request.withCredentials).toBeTrue();

    request.flush(response);

    expect(received).toEqual(response);
  });

  it('charge une page spécifique', () => {
    service.getHistory(2, 5).subscribe();

    const request = httpMock.expectOne(req =>
      req.method === 'GET' &&
      req.url === baseUrl &&
      req.params.get('page') === '2' &&
      req.params.get('size') === '5'
    );

    expect(request.request.withCredentials).toBeTrue();

    request.flush({
      content: [],
      page: 2,
      size: 5,
      totalElements: 11,
      totalPages: 3
    });
  });

  it('charge le bilan d un stress test', () => {
    const response: ScenarioReportResponse = {
      id: 1,
      type: 'CRASH',
      symbol: 'AAPL',
      intensity: 0.5,
      status: 'STOPPED',
      startedAt: '2026-10-27T21:24:42Z',
      scheduledEndsAt: '2026-10-27T21:26:42Z',
      actualEndedAt: '2026-10-27T21:25:48Z',
      plannedDurationSeconds: 120,
      actualDurationSeconds: 66,
      completionPercent: 55
    };

    let received: ScenarioReportResponse | undefined;

    service.getReport(1).subscribe(result => {
      received = result;
    });

    const request = httpMock.expectOne(
      `${baseUrl}/1/report`
    );

    expect(request.request.method).toBe('GET');
    expect(request.request.withCredentials).toBeTrue();

    request.flush(response);

    expect(received).toEqual(response);
  });
});
