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

  it('shows loading then the backend status', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('p').textContent).toContain('Vérification du backend...');
    response.next({ status: 'UP' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('p').textContent).toContain('Connecté (UP)');
    response.complete();
  });

  it('shows a connection error when the request fails', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    response.error(new Error('Backend unavailable'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('p').textContent).toContain('Erreur de connexion au backend');
  });
});
