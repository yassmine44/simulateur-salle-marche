import { TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { AppComponent } from './app.component';
import { HealthResponse, HealthService } from './services/health.service';

 describe('AppComponent', () => {
  let response: Subject<HealthResponse>;

  beforeEach(async () => {
    response = new Subject<HealthResponse>();
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [{ provide: HealthService, useValue: { checkHealth: () => response.asObservable() } }]
    }).compileComponents();
  });

  it('tracks loading then the backend status', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.healthStatus).toContain('Vérification du backend...');
    response.next({ status: 'UP' });
    fixture.detectChanges();
    expect(fixture.componentInstance.healthStatus).toContain('Connecté (UP)');
    response.complete();
  });

  it('tracks a connection error when the request fails', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    response.error(new Error('Backend unavailable'));
    fixture.detectChanges();
    expect(fixture.componentInstance.healthStatus).toContain('Erreur de connexion au backend');
  });
});
