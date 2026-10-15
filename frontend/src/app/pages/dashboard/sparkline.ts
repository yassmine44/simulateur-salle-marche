import {
  Component,
  Input,
  OnChanges
} from '@angular/core';


/*
 * Mini-courbe d'un cours, sans axe : seule la forme compte.
 * Le sens (hausse / baisse) est aussi écrit à côté par le parent,
 * la couleur n'est donc jamais la seule information.
 */
@Component({
  selector: 'app-sparkline',

  standalone: true,

  template: `
    @if (path) {
      <svg
        viewBox="0 0 100 32"
        preserveAspectRatio="none"
        role="img"
        [attr.aria-label]="label"
      >
        <path
          class="line"
          [class.rising]="rising"
          [class.falling]="!rising"
          [attr.d]="path"
        />
      </svg>
    } @else {
      <span class="empty">Pas encore d'historique</span>
    }
  `,

  styles: [`
    :host {
      display: block;
      height: 32px;
    }

    svg {
      display: block;
      width: 100%;
      height: 100%;
      overflow: visible;
    }

    .line {
      fill: none;
      stroke-width: 2;
      stroke-linejoin: round;
      stroke-linecap: round;
      vector-effect: non-scaling-stroke;
    }

    .rising {
      stroke: #059669;
    }

    .falling {
      stroke: #dc2626;
    }

    .empty {
      color: #94a3b8;
      font-size: 11px;
    }
  `]
})
export class Sparkline
  implements OnChanges {

  @Input()
  points: number[] = [];

  @Input()
  label = 'Évolution récente du cours';

  path = '';

  rising = true;


  ngOnChanges(): void {

    if (this.points.length < 2) {

      this.path = '';

      return;
    }

    const min = Math.min(...this.points);

    const max = Math.max(...this.points);

    const range = max - min || 1;

    const step = 100 / (this.points.length - 1);

    // 2 unités de marge en haut et en bas pour le trait.

    this.path =
      this.points
        .map(
          (value, index) =>
            (index === 0 ? 'M' : 'L')
            + (index * step).toFixed(2)
            + ' '
            + (30 - (value - min) / range * 28).toFixed(2)
        )
        .join(' ');

    this.rising =
      this.points[this.points.length - 1] >= this.points[0];
  }
}
