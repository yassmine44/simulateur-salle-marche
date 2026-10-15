import {
  Component,
  Input,
  OnChanges
} from '@angular/core';

import { CommonModule } from '@angular/common';

import { EquityPoint } from '../../models/portfolio.models';


interface ChartPoint {

  point: EquityPoint;

  x: number;

  y: number;

  // Variation depuis le capital de départ.
  change: number;

  // Valeur de la référence au même instant, si fournie.
  benchmarkValue: number | null;

  changePercent: number;
}


interface GridLine {

  y: number;

  value: number;
}


/*
 * Évolution de la valeur totale du portefeuille.
 * Une seule série : pas de légende, le titre du panneau la nomme.
 * Une ligne pointillée marque le capital de départ.
 */
@Component({
  selector: 'app-equity-chart',

  standalone: true,

  imports: [
    CommonModule
  ],

  templateUrl: './equity-chart.html',

  styleUrl: './equity-chart.scss'
})
export class EquityChart
  implements OnChanges {

  @Input()
  points: EquityPoint[] = [];

  @Input()
  baseline = 0;

  @Input()
  currency = '';

  // Seconde série facultative, alignée point par point.
  @Input()
  benchmark: EquityPoint[] = [];

  @Input()
  benchmarkLabel = 'Référence';


  // ==========================================
  // GEOMETRY (unités du viewBox)
  // ==========================================

  readonly width = 720;

  readonly height = 240;

  readonly plotLeft = 8;

  readonly plotRight = 640;

  readonly plotTop = 14;

  readonly plotBottom = 212;


  // ==========================================
  // VIEW MODEL
  // ==========================================

  chartPoints: ChartPoint[] = [];

  gridLines: GridLine[] = [];

  linePath = '';

  benchmarkPath = '';

  areaPath = '';

  baselineY = 0;

  hovered: ChartPoint | null = null;


  ngOnChanges(): void {

    this.build();
  }


  private build(): void {

    if (this.points.length === 0) {

      this.chartPoints = [];

      this.gridLines = [];

      this.hovered = null;

      return;
    }


    const benchmark =
      this.benchmark.length === this.points.length
        ? this.benchmark
        : [];

    const values = [
      ...this.points.map(point => point.value),
      ...benchmark.map(point => point.value),
      this.baseline
    ];

    let min = Math.min(...values);

    let max = Math.max(...values);

    // Marge verticale ; une série plate reste lisible.

    const margin =
      max > min
        ? (max - min) * 0.12
        : Math.max(Math.abs(max) * 0.002, 1);

    min -= margin;

    max += margin;


    const toY =
      (value: number) =>
        this.plotTop
        +
        (max - value) / (max - min)
        * (this.plotBottom - this.plotTop);

    const step =
      this.points.length > 1
        ? (this.plotRight - this.plotLeft) / (this.points.length - 1)
        : 0;


    this.chartPoints =
      this.points.map(
        (point, index) => {

          const change =
            point.value - this.baseline;

          return {

            point,

            x:
              this.points.length > 1
                ? this.plotLeft + index * step
                : (this.plotLeft + this.plotRight) / 2,

            y: toY(point.value),

            change,

            benchmarkValue:
              benchmark.length > 0
                ? benchmark[index].value
                : null,

            changePercent:
              this.baseline !== 0
                ? change / this.baseline * 100
                : 0
          };
        }
      );


    this.linePath =
      this.chartPoints
        .map(
          (point, index) =>
            (index === 0 ? 'M' : 'L')
            + point.x.toFixed(1)
            + ' '
            + point.y.toFixed(1)
        )
        .join(' ');

    this.benchmarkPath =
      benchmark.length > 0
        ? this.chartPoints
            .map(
              (point, index) =>
                (index === 0 ? 'M' : 'L')
                + point.x.toFixed(1)
                + ' '
                + toY(benchmark[index].value).toFixed(1)
            )
            .join(' ')
        : '';

    const first = this.chartPoints[0];

    const last = this.chartPoints[this.chartPoints.length - 1];

    this.areaPath =
      `${this.linePath} L${last.x.toFixed(1)} ${this.plotBottom}`
      +
      ` L${first.x.toFixed(1)} ${this.plotBottom} Z`;

    this.baselineY = toY(this.baseline);


    this.gridLines =
      [0.15, 0.5, 0.85].map(
        ratio => {

          const value = max - (max - min) * ratio;

          return {
            value,
            y: toY(value)
          };
        }
      );


    // Le survol suit les données actualisées.

    const hoveredTime = this.hovered?.point.time;

    this.hovered =
      this.chartPoints.find(
        point => point.point.time === hoveredTime
      )
      ??
      null;
  }


  // ==========================================
  // HOVER
  // ==========================================

  hover(
    point: ChartPoint | null
  ): void {

    this.hovered = point;
  }


  get slotWidth(): number {

    return this.chartPoints.length > 1
      ? (this.plotRight - this.plotLeft) / (this.chartPoints.length - 1)
      : this.plotRight - this.plotLeft;
  }


  get tooltipLeft(): number {

    return this.hovered
      ? this.hovered.x / this.width * 100
      : 0;
  }


  get tooltipOnLeft(): boolean {

    return this.tooltipLeft > 60;
  }


  // Le dernier point est la valeur actuelle, les autres
  // des fins de journée : on affiche le jour écoulé.

  pointLabel(
    point: ChartPoint
  ): Date | string {

    return point === this.chartPoints[this.chartPoints.length - 1]
      ? 'Maintenant'
      : new Date(new Date(point.point.time).getTime() - 1);
  }


  isText(
    value: Date | string
  ): value is string {

    return typeof value === 'string';
  }


  trackByTime(
    _: number,
    point: ChartPoint
  ): string {

    return point.point.time;
  }
}
