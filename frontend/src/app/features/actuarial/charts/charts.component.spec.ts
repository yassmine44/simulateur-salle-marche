import { ComponentFixture, TestBed } from '@angular/core/testing';

import { IntervalChartComponent, IntervalRow } from './interval-chart.component';
import { LineChartComponent, LineSeries } from './line-chart.component';
import { RelativityChartComponent, RelativityRow } from './relativity-chart.component';

describe('RelativityChartComponent', () => {
  let fixture: ComponentFixture<RelativityChartComponent>;
  const rows: RelativityRow[] = [
    { key: 'bonusMalus', label: 'Coefficient bonus-malus', detail: '100', relativity: 2.18 },
    { key: 'vehicleBrand', label: 'Marque du véhicule', detail: 'B12', relativity: 0.76 },
    { key: 'area', label: 'Zone', detail: 'E', relativity: 1.002 }
  ];

  beforeEach(() => {
    fixture = TestBed.createComponent(RelativityChartComponent);
    fixture.componentRef.setInput('rows', rows);
    fixture.detectChanges();
  });

  it('draws one bar per factor, on the side of its effect', () => {
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelectorAll('.row').length).toBe(3);
    expect(element.querySelector('.bar.up')).not.toBeNull();
    expect(element.querySelector('.bar.down')).not.toBeNull();
    expect(element.querySelector('.bar.neutral')).not.toBeNull();
    expect(element.querySelector('.value')?.textContent).toContain('×2,18');
  });

  it('describes each row for screen readers', () => {
    const label = fixture.nativeElement.querySelector('.row').getAttribute('aria-label');
    expect(label).toContain('Coefficient bonus-malus (100)');
    expect(label).toContain('×2,18');
  });

  it('offers a table view', () => {
    fixture.nativeElement.querySelector('.table-toggle').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(3);
  });
});

describe('LineChartComponent', () => {
  let fixture: ComponentFixture<LineChartComponent>;
  const series: LineSeries[] = [
    { key: 'model', name: 'Modèle', tone: 'series-1', points: [{ x: 0, y: 0 }, { x: 0.5, y: 0.2 }, { x: 1, y: 1 }] },
    { key: 'oracle', name: 'Oracle', tone: 'series-2', points: [{ x: 0, y: 0 }, { x: 0.5, y: 0.05 }, { x: 1, y: 1 }] }
  ];

  beforeEach(() => {
    fixture = TestBed.createComponent(LineChartComponent);
    fixture.componentRef.setInput('series', series);
    fixture.componentRef.setInput('reference', 'diagonal');
    fixture.componentRef.setInput('referenceLabel', 'Hasard');
    fixture.detectChanges();
  });

  it('draws each series, the reference line and a legend', () => {
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelectorAll('path.line').length).toBe(2);
    expect(element.querySelector('path.reference')).not.toBeNull();
    expect(element.querySelectorAll('.legend-item').length).toBe(3);
  });

  it('shows a tooltip with every series when hovering a point by keyboard', () => {
    const svg: SVGElement = fixture.nativeElement.querySelector('svg');
    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('.tooltip-row');
    expect(rows.length).toBe(2);
    expect(fixture.componentInstance.hover()?.x).toBe(0.5);
  });

  it('offers a table view with one column per series', () => {
    fixture.nativeElement.querySelector('.table-toggle').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('thead th').length).toBe(3);
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(3);
  });
});

describe('IntervalChartComponent', () => {
  let fixture: ComponentFixture<IntervalChartComponent>;
  const rows: IntervalRow[] = [
    { key: '2020', label: '2020', estimate: 16.5, low: 11, high: 23, actual: 15 },
    { key: '2021', label: '2021', estimate: 78, low: 57, high: 103, actual: 50 },
    { key: '2022', label: '2022', estimate: 90, low: 60, high: 130 }
  ];

  beforeEach(() => {
    fixture = TestBed.createComponent(IntervalChartComponent);
    fixture.componentRef.setInput('rows', rows);
    fixture.detectChanges();
  });

  it('draws an interval and an estimate per row, and the actual value only when known', () => {
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelectorAll('rect.range').length).toBe(3);
    expect(element.querySelectorAll('circle.estimate').length).toBe(3);
    expect(element.querySelectorAll('path.actual').length).toBe(2);
  });

  it('says whether the actual value fell inside the interval', () => {
    const svg: SVGElement = fixture.nativeElement.querySelector('svg');
    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    svg.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.tooltip-note').textContent).toContain('hors de l\'intervalle');
  });
});

describe('IntervalChartComponent scale', () => {
  it('switches to a logarithmic axis when values span several orders of magnitude', () => {
    const fixture = TestBed.createComponent(IntervalChartComponent);
    fixture.componentRef.setInput('rows', [
      { key: 'a', label: '1989', estimate: 9_696, low: 7_000, high: 13_000, actual: 11_320 },
      { key: 'b', label: '1997', estimate: 8_878_850, low: 7_900_000, high: 9_900_000, actual: 7_812_194 }
    ]);
    fixture.detectChanges();

    expect(fixture.componentInstance.logarithmic()).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Échelle logarithmique');
    // Both small and large intervals stay visible (non-zero height).
    const heights = Array.from(fixture.nativeElement.querySelectorAll('rect.range'))
      .map(r => Number((r as SVGRectElement).getAttribute('height')));
    expect(Math.min(...heights)).toBeGreaterThan(5);
  });
});

describe('IntervalChartComponent relative mode', () => {
  it('plots each row relative to its own estimate, with signed percent ticks', () => {
    const fixture = TestBed.createComponent(IntervalChartComponent);
    fixture.componentRef.setInput('relative', true);
    fixture.componentRef.setInput('rows', [
      { key: 'a', label: '1989', estimate: 10_000, low: 7_000, high: 13_000, actual: 11_000 },
      { key: 'b', label: '1997', estimate: 9_000_000, low: 8_100_000, high: 9_900_000, actual: 7_800_000 }
    ]);
    fixture.detectChanges();

    const component = fixture.componentInstance;
    const [small, large] = component.marks();
    // Both estimates sit on the same 0 % line whatever their size.
    expect(small.yEstimate).toBeCloseTo(large.yEstimate, 6);
    expect(component.zeroLine()).toBeCloseTo(small.yEstimate, 6);
    expect(large.deviation).toBeCloseTo(-0.1333, 3);
    expect(large.inside).toBeFalse();
    expect(component.logarithmic()).toBeFalse();
    expect(fixture.nativeElement.textContent).toMatch(/\+\d+\s?%/);
  });
});
