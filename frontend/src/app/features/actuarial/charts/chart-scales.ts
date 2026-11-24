/** Échelles et géométrie des graphiques du module d'actuariat (fonctions pures). */

export interface Point {
  x: number;
  y: number;
}

export type Scale = (value: number) => number;

export function linearScale(domain: [number, number], range: [number, number]): Scale {
  const [d0, d1] = domain;
  const [r0, r1] = range;
  const span = d1 - d0 || 1;
  return value => r0 + ((value - d0) / span) * (r1 - r0);
}

/** Échelle logarithmique (domaine strictement positif). */
export function logScale(domain: [number, number], range: [number, number]): Scale {
  const [d0, d1] = domain.map(Math.log10) as [number, number];
  const inner = linearScale([d0, d1], range);
  return value => inner(Math.log10(value));
}

/** Domaine logarithmique arrondi aux puissances de 10 qui encadrent [min, max]. */
export function logDomain(min: number, max: number): [number, number] {
  return [10 ** Math.floor(Math.log10(min)), 10 ** Math.ceil(Math.log10(max))];
}

/** Graduations 1, 2, 5 × 10^n comprises dans [min, max] (au plus ~8 traits). */
export function logTicks(min: number, max: number): number[] {
  const decades = Math.ceil(Math.log10(max)) - Math.floor(Math.log10(min));
  const multipliers = decades > 4 ? [1] : decades > 2 ? [1, 3] : [1, 2, 5];
  const ticks: number[] = [];
  for (let exponent = Math.floor(Math.log10(min)); exponent <= Math.ceil(Math.log10(max)); exponent++) {
    for (const m of multipliers) {
      const tick = m * 10 ** exponent;
      if (tick >= min * (1 - 1e-9) && tick <= max * (1 + 1e-9)) {
        ticks.push(tick);
      }
    }
  }
  return ticks;
}

export function extent(values: number[]): [number, number] {
  if (values.length === 0) {
    return [0, 1];
  }
  return [Math.min(...values), Math.max(...values)];
}

/** Graduations « rondes » (1, 2, 2,5 ou 5 × 10^n) couvrant [min, max]. */
export function niceTicks(min: number, max: number, count = 5): number[] {
  if (min === max) {
    return [min];
  }
  const rawStep = (max - min) / Math.max(1, count);
  const magnitude = 10 ** Math.floor(Math.log10(rawStep));
  const step =
    [1, 2, 2.5, 5, 10].map(m => m * magnitude).find(candidate => candidate >= rawStep) ??
    10 * magnitude;
  const start = Math.ceil(min / step - 1e-9) * step;
  const ticks: number[] = [];
  for (let tick = start; tick <= max + step * 1e-9; tick += step) {
    ticks.push(Number(tick.toFixed(10)));
  }
  return ticks;
}

/** Domaine arrondi aux graduations, pour que les extrémités tombent sur un trait. */
export function niceDomain(min: number, max: number, count = 5): [number, number] {
  const ticks = niceTicks(min, max, count);
  if (ticks.length < 2) {
    return [min, max];
  }
  const step = ticks[1] - ticks[0];
  return [
    ticks[0] > min ? ticks[0] - step : ticks[0],
    ticks[ticks.length - 1] < max ? ticks[ticks.length - 1] + step : ticks[ticks.length - 1]
  ];
}

export function linePath(points: Point[], x: Scale, y: Scale): string {
  return points
    .map((p, i) => `${i === 0 ? 'M' : 'L'}${x(p.x).toFixed(2)},${y(p.y).toFixed(2)}`)
    .join('');
}

/** Index du point dont l'abscisse est la plus proche (survol au réticule). */
export function nearestIndex(xs: number[], target: number): number {
  let best = 0;
  for (let i = 1; i < xs.length; i++) {
    if (Math.abs(xs[i] - target) < Math.abs(xs[best] - target)) {
      best = i;
    }
  }
  return best;
}

// ---------------------------------------------------------------------------
// Barres divergentes de relativités (échelle logarithmique centrée sur ×1)
// ---------------------------------------------------------------------------
export interface DivergingBar {
  /** Position du début de la barre, en % de la piste. */
  left: number;
  /** Largeur de la barre, en % de la piste. */
  width: number;
  side: 'up' | 'down' | 'neutral';
}

/** Demi-étendue (en log) commune à toutes les barres, au moins log(1,25). */
export function relativityHalfRange(relativities: number[]): number {
  const largest = Math.max(0, ...relativities.map(r => Math.abs(Math.log(r))));
  return Math.max(largest, Math.log(1.25));
}

/**
 * Barre d'une relativité : ×1 au centre, hausses à droite, baisses à gauche.
 * L'échelle logarithmique rend symétriques ×2 et ×0,5.
 */
export function divergingBar(relativity: number, halfRange: number, neutralTolerance = 0.01): DivergingBar {
  const logValue = Math.log(relativity);
  const halfWidth = (Math.min(Math.abs(logValue), halfRange) / halfRange) * 50;
  if (Math.abs(logValue) <= neutralTolerance) {
    return { left: 50, width: 0, side: 'neutral' };
  }
  return logValue > 0
    ? { left: 50, width: halfWidth, side: 'up' }
    : { left: 50 - halfWidth, width: halfWidth, side: 'down' };
}
