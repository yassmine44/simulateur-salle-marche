import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  input,
  signal,
  viewChild
} from '@angular/core';

import {
  extent,
  linearScale,
  linePath,
  nearestIndex,
  niceDomain,
  niceTicks,
  Point
} from './chart-scales';

export interface LineSeries {
  key: string;
  name: string;
  points: Point[];
  tone: 'series-1' | 'series-2' | 'reference';
}

export type Formatter = (value: number) => string;

const HEIGHT = 280;
// La marge haute laisse la place au titre de l'axe vertical.
const MARGIN = { top: 26, right: 16, bottom: 40, left: 56 };

/**
 * Graphique en lignes générique (courbe de Lorenz, calibration, dépendance).
 * Une seule échelle verticale ; réticule et info-bulle au survol ; vue tableau.
 */
@Component({
  selector: 'app-line-chart',
  standalone: true,
  templateUrl: './line-chart.component.html',
  styleUrl: './line-chart.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LineChartComponent implements AfterViewInit {
  readonly series = input.required<LineSeries[]>();
  readonly xLabel = input('');
  readonly yLabel = input('');
  readonly xFormat = input<Formatter>(v => String(v));
  readonly yFormat = input<Formatter>(v => String(v));
  readonly xDomain = input<[number, number] | null>(null);
  readonly yDomain = input<[number, number] | null>(null);
  readonly markers = input(false);
  /** Ligne de référence : diagonale y = x, ou horizontale y = valeur. */
  readonly reference = input<'diagonal' | { y: number } | null>(null);
  /** Libellé de la ligne de référence dans la légende (vide = absente de la légende). */
  readonly referenceLabel = input('');
  /** Une ligne sur N dans la vue tableau (les courbes de Lorenz ont 101 points). */
  readonly tableStep = input(1);
  readonly caption = input<string>('');
  readonly ariaLabel = input('Graphique');

  readonly showTable = signal(false);
  readonly hoverIndex = signal<number | null>(null);
  readonly width = signal(640);
  readonly height = HEIGHT;
  readonly margin = MARGIN;

  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);
  private readonly plot = viewChild<ElementRef<SVGSVGElement>>('plot');

  private readonly allPoints = computed(() => this.series().flatMap(s => s.points));

  private readonly xDom = computed<[number, number]>(
    () => this.xDomain() ?? extent(this.allPoints().map(p => p.x))
  );

  private readonly yDom = computed<[number, number]>(() => {
    if (this.yDomain()) {
      return this.yDomain()!;
    }
    const values = this.allPoints().map(p => p.y);
    const ref = this.reference();
    if (ref && ref !== 'diagonal') {
      values.push(ref.y);
    }
    const [min, max] = extent(values);
    const pad = (max - min) * 0.05 || Math.abs(max) * 0.1 || 1;
    return niceDomain(min - pad, max + pad);
  });

  readonly xScale = computed(() =>
    linearScale(this.xDom(), [MARGIN.left, this.width() - MARGIN.right])
  );
  readonly yScale = computed(() => linearScale(this.yDom(), [HEIGHT - MARGIN.bottom, MARGIN.top]));

  readonly xTicks = computed(() =>
    niceTicks(this.xDom()[0], this.xDom()[1], this.width() < 480 ? 4 : 6)
  );
  readonly yTicks = computed(() => niceTicks(this.yDom()[0], this.yDom()[1], 5));

  readonly paths = computed(() =>
    this.series().map(s => ({ ...s, d: linePath(s.points, this.xScale(), this.yScale()) }))
  );

  readonly referencePath = computed(() => {
    const ref = this.reference();
    const [x0, x1] = this.xDom();
    if (!ref) {
      return null;
    }
    const points: Point[] =
      ref === 'diagonal' ? [{ x: x0, y: x0 }, { x: x1, y: x1 }] : [{ x: x0, y: ref.y }, { x: x1, y: ref.y }];
    return linePath(points, this.xScale(), this.yScale());
  });

  /** Abscisses du survol : celles de la première série. */
  private readonly hoverXs = computed(() => this.series()[0]?.points.map(p => p.x) ?? []);

  readonly hover = computed(() => {
    const index = this.hoverIndex();
    if (index === null || this.series().length === 0) {
      return null;
    }
    const x = this.hoverXs()[index];
    const rows = this.series().map(s => {
      const point = s.points[nearestIndex(s.points.map(p => p.x), x)];
      return { key: s.key, name: s.name, tone: s.tone, y: point.y, cy: this.yScale()(point.y) };
    });
    const cx = this.xScale()(x);
    return { x, cx, rows, alignRight: cx > this.width() * 0.6 };
  });

  readonly tableRows = computed(() => {
    const step = Math.max(1, this.tableStep());
    const base = this.series()[0]?.points ?? [];
    return base
      .filter((_, i) => i % step === 0 || i === base.length - 1)
      .map(p => ({
        x: p.x,
        values: this.series().map(s => s.points[nearestIndex(s.points.map(q => q.x), p.x)].y)
      }));
  });

  ngAfterViewInit(): void {
    const element = this.host.nativeElement;
    const observer = new ResizeObserver(entries => {
      const width = Math.round(entries[0].contentRect.width);
      if (width > 0) {
        this.width.set(Math.max(280, width));
      }
    });
    observer.observe(element);
    this.destroyRef.onDestroy(() => observer.disconnect());
  }

  onPointerMove(event: PointerEvent): void {
    const svg = this.plot()?.nativeElement;
    const xs = this.hoverXs();
    if (!svg || xs.length === 0) {
      return;
    }
    const rect = svg.getBoundingClientRect();
    const px = ((event.clientX - rect.left) / rect.width) * this.width();
    const [x0, x1] = this.xDom();
    const value = x0 + ((px - MARGIN.left) / (this.width() - MARGIN.left - MARGIN.right)) * (x1 - x0);
    this.hoverIndex.set(nearestIndex(xs, value));
  }

  onKeydown(event: KeyboardEvent): void {
    const count = this.hoverXs().length;
    if (count === 0) {
      return;
    }
    const current = this.hoverIndex() ?? -1;
    if (event.key === 'ArrowRight') {
      this.hoverIndex.set(Math.min(count - 1, current + 1));
      event.preventDefault();
    } else if (event.key === 'ArrowLeft') {
      this.hoverIndex.set(Math.max(0, current <= 0 ? 0 : current - 1));
      event.preventDefault();
    }
  }

  clearHover(): void {
    this.hoverIndex.set(null);
  }
}
