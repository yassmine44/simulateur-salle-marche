import { ComponentFixture, TestBed } from '@angular/core/testing';

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
