import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/** En-tête et onglets du module d'actuariat. */
@Component({
  selector: 'app-actuarial-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="actuarial">
      <header class="page-heading">
        <div>
          <span class="eyebrow">Module d'actuariat</span>
          <h1>Assurance automobile : tarification et provisionnement</h1>
          <p>
            Modèles fréquence × coût entraînés sur 678 013 contrats réels (freMTPL2), avec l'explication
            de chaque prime facteur par facteur, et provisionnement Chain-Ladder sur des triangles réels.
          </p>
        </div>
      </header>

      <nav class="tabs" aria-label="Sections du module d'actuariat">
        <a routerLink="tarification" routerLinkActive="active" ariaCurrentWhenActive="page">Tarification</a>
        <a routerLink="comparaison" routerLinkActive="active" ariaCurrentWhenActive="page">Comparaison</a>
        <a routerLink="provisionnement" routerLinkActive="active" ariaCurrentWhenActive="page">Provisionnement</a>
        <a routerLink="portefeuille" routerLinkActive="active" ariaCurrentWhenActive="page">Portefeuille et modèles</a>
      </nav>

      <router-outlet />
    </section>
  `,
  styleUrl: './actuarial-shell.component.scss'
})
export class ActuarialShellComponent {}
