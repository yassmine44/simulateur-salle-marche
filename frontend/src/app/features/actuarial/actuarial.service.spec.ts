import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PROFILE_PRESETS } from './actuarial.models';
import { ActuarialService } from './actuarial.service';

const API = 'http://localhost:8081/api/actuarial';

describe('ActuarialService', () => {
  let service: ActuarialService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(ActuarialService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts a quote with the session cookie', () => {
    const profile = PROFILE_PRESETS[0].profile;
    service.quote({ profile, explain: true }).subscribe();

    const request = http.expectOne(`${API}/quotes`);
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBeTrue();
    expect(request.request.body.profile).toEqual(profile);
    request.flush({});
  });

  it('requests the metadata only once per session', () => {
    const received: unknown[] = [];
    service.metadata().subscribe(m => received.push(m));
    service.metadata().subscribe(m => received.push(m));

    http.expectOne(`${API}/metadata`).flush({ fields: [], defaultAssumptions: {} });
    expect(received.length).toBe(2);
    service.metadata().subscribe(m => received.push(m));
    expect(received.length).toBe(3); // servi depuis le cache, sans nouvelle requête
  });

  it('caches each report separately', () => {
    const received: string[] = [];
    service.report<{ name: string }>('pricing').subscribe(r => received.push(r.name));
    service.report<{ name: string }>('pricing').subscribe(r => received.push(r.name));
    service.report<{ name: string }>('frequency').subscribe(r => received.push(r.name));

    http.expectOne(`${API}/reports/pricing`).flush({ name: 'pricing' });
    http.expectOne(`${API}/reports/frequency`).flush({ name: 'frequency' });
    expect(received).toEqual(['pricing', 'pricing', 'frequency']);
  });

  it('asks for the latest quotes of the user', () => {
    service.history(5).subscribe();

    const request = http.expectOne(r => r.url === `${API}/quotes/me`);
    expect(request.request.params.get('limit')).toBe('5');
    request.flush([]);
  });

  it('posts both profiles to compare', () => {
    const [a, b] = PROFILE_PRESETS;
    service.compare(a.profile, b.profile).subscribe();

    const request = http.expectOne(`${API}/comparisons`);
    expect(request.request.body).toEqual({ profileA: a.profile, profileB: b.profile });
    request.flush({});
  });
});
