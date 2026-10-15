import {
  Component,
  Input,
  OnChanges
} from '@angular/core';

import { CommonModule } from '@angular/common';

import { Candle } from '../../models/trading.models';


interface CandleBar {

  candle: Candle;

  // Centre du chandelier.
  x: number;

  // Mèche : du plus haut au plus bas.
  highY: number;

  lowY: number;

  // Corps : entre ouverture et clôture.
  bodyY: number;

  bodyHeight: number;

  rising: boolean;
}


interface GridLine {

  y: number;

  price: number;
}


@Component({
  selector: 'app-candle-chart',

  standalone: true,

  imports: [
    CommonModule
  ],

  templateUrl: './candle-chart.html',

  styleUrl: './candle-chart.scss'
})
export class CandleChart
  implements OnChanges {

  @Input()
  candles: Candle[] = [];


  // ==========================================
  // GEOMETRY (unités du viewBox)
  // ==========================================

  readonly width = 720;

  readonly height = 260;

  readonly plotLeft = 8;

  readonly plotRight = 652;

  readonly plotTop = 12;

  readonly plotBottom = 232;

  // Nombre minimal d'emplacements : quelques chandeliers
  // ne s'étirent pas sur toute la largeur.

  private readonly minSlots = 30;

  private readonly gridLineCount = 4;


  // ==========================================
  // VIEW MODEL
  // ==========================================

  bars: CandleBar[] = [];

  gridLines: GridLine[] = [];

  slotWidth = 0;

  bodyWidth = 0;

  hovered: CandleBar | null = null;


  ngOnChanges(): void {

    this.build();
  }


  private build(): void {

    const hoveredTime =
      this.hovered?.candle.time;

    if (
      this.candles.length === 0
    ) {

      this.bars = [];

      this.gridLines = [];

      this.hovered = null;

      return;
    }


    let min =
      Math.min(
        ...this.candles.map(candle => candle.low)
      );

    let max =
      Math.max(
        ...this.candles.map(candle => candle.high)
      );

    // Marge verticale ; évite aussi une échelle
    // nulle quand tous les prix sont identiques.

    const margin =
      max > min
        ? (max - min) * 0.08
        : max * 0.005 || 1;

    min -= margin;

    max += margin;


    const plotHeight =
      this.plotBottom - this.plotTop;

    const toY =
      (price: number) =>
        this.plotTop
        +
        (max - price) / (max - min) * plotHeight;


    this.slotWidth =
      (this.plotRight - this.plotLeft)
      /
      Math.max(
        this.candles.length,
        this.minSlots
      );

    this.bodyWidth =
      Math.min(
        this.slotWidth * 0.62,
        14
      );


    this.bars =
      this.candles.map(
        (candle, index) => {

          const openY =
            toY(candle.open);

          const closeY =
            toY(candle.close);

          return {

            candle,

            x:
              this.plotLeft
              +
              (index + 0.5) * this.slotWidth,

            highY:
              toY(candle.high),

            lowY:
              toY(candle.low),

            bodyY:
              Math.min(openY, closeY),

            // Un corps reste visible même
            // si ouverture = clôture.

            bodyHeight:
              Math.max(
                Math.abs(closeY - openY),
                1.5
              ),

            rising:
              candle.close >= candle.open
          };
        }
      );


    this.gridLines =
      Array.from(
        { length: this.gridLineCount },
        (_, index) => {

          const price =
            min
            +
            (max - min)
            * (index + 0.5)
            / this.gridLineCount;

          return {
            price,
            y: toY(price)
          };
        }
      );


    // Le survol est conservé quand
    // les données sont actualisées.

    this.hovered =
      this.bars.find(
        bar =>
          bar.candle.time === hoveredTime
      )
      ??
      null;
  }


  // ==========================================
  // HOVER
  // ==========================================

  hover(
    bar: CandleBar | null
  ): void {

    this.hovered = bar;
  }


  // Position de l'infobulle, en % de la largeur.
  // Elle passe à gauche du chandelier près du bord droit.

  get tooltipLeft(): number {

    return this.hovered
      ? this.hovered.x / this.width * 100
      : 0;
  }


  get tooltipOnLeft(): boolean {

    return this.tooltipLeft > 60;
  }


  get latest(): CandleBar | null {

    return this.bars.length > 0
      ? this.bars[this.bars.length - 1]
      : null;
  }


  trackByTime(
    _: number,
    bar: CandleBar
  ): string {

    return bar.candle.time;
  }
}
