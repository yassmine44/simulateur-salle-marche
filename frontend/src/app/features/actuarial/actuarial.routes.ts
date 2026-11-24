import { Routes } from '@angular/router';

/** Module d'actuariat : /app/actuarial/{tarification|comparaison|portefeuille}. */
export const ACTUARIAL_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/actuarial-shell.component').then(m => m.ActuarialShellComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tarification' },
      {
        path: 'tarification',
        title: 'Tarification — Actuariat',
        loadComponent: () => import('./pages/quote-page.component').then(m => m.QuotePageComponent)
      },
      {
        path: 'comparaison',
        title: 'Comparaison — Actuariat',
        loadComponent: () => import('./pages/compare-page.component').then(m => m.ComparePageComponent)
      },
      {
        path: 'provisionnement',
        title: 'Provisionnement — Actuariat',
        loadComponent: () => import('./pages/reserving-page.component').then(m => m.ReservingPageComponent)
      },
      {
        path: 'portefeuille',
        title: 'Portefeuille et modèles — Actuariat',
        loadComponent: () => import('./pages/insights-page.component').then(m => m.InsightsPageComponent)
      }
    ]
  }
];
