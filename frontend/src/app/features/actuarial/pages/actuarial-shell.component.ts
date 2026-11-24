import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, map, of } from 'rxjs';

import { formatNumber } from '../actuarial.format';
import { Currency } from '../actuarial.models';
import { ActuarialService } from '../actuarial.service';
import { IconComponent, IconName } from '../ui/icon.component';

interface Tab {
  path: string;
  label: string;
  icon: IconName;
}

/** En-tête, devise d'affichage et onglets du module d'actuariat. */
@Component({
  selector: 'app-actuarial-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './actuarial-shell.component.html',
  styleUrl: './actuarial-shell.component.scss'
})
export class ActuarialShellComponent {
  private readonly service = inject(ActuarialService);

  readonly tabs: readonly Tab[] = [
    { path: 'tarification', label: 'Tarification', icon: 'receipt' },
    { path: 'comparaison', label: 'Comparaison', icon: 'scale' },
    { path: 'provisionnement', label: 'Provisionnement', icon: 'staircase' },
    { path: 'portefeuille', label: 'Portefeuille et modèles', icon: 'chart' }
  ];

  /** Devise d'affichage ; l'en-tête reste utilisable si le moteur ne répond pas. */
  readonly currency = toSignal(
    this.service.metadata().pipe(
      map(metadata => metadata.currency as Currency | null),
      catchError(() => of(null))
    ),
    { initialValue: null }
  );

  rate(currency: Currency): string {
    return formatNumber(currency.rateFromModel, 3);
  }
}
