import { Component, OnInit } from '@angular/core';
import { HealthService } from './services/health.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {
  healthStatus = 'Vérification du backend...';

  constructor(private readonly healthService: HealthService) {}

  ngOnInit(): void {
    this.healthService.checkHealth().subscribe({
      next: (response) => {
        this.healthStatus = `Connecté (${response.status})`;
      },
      error: () => {
        this.healthStatus = 'Erreur de connexion au backend';
      }
    });
  }
}
