import {
  divergingBar,
  logDomain,
  logScale,
  logTicks,
  extent,
  linearScale,
  linePath,
  nearestIndex,
  niceDomain,
  niceTicks,
  relativityHalfRange
} from './chart-scales';

describe('chart-scales', () => {
  it('maps a domain linearly onto a range, including inverted ranges', () => {
    const y = linearScale([0, 10], [200, 0]);
    expect(y(0)).toBe(200);
    expect(y(10)).toBe(0);
    expect(y(2.5)).toBe(150);
  });

  it('survives a degenerate domain', () => {
    expect(linearScale([3, 3], [0, 100])(3)).toBe(0);
    expect(extent([])).toEqual([0, 1]);
    expect(extent([4, -2, 7])).toEqual([-2, 7]);
  });

  it('produces round ticks covering the interval', () => {
    expect(niceTicks(0, 1, 5)).toEqual([0, 0.2, 0.4, 0.6, 0.8, 1]);
    expect(niceTicks(0.03, 0.38, 5)).toEqual([0.1, 0.2, 0.3]);
    expect(niceTicks(2, 2)).toEqual([2]);
  });

  it('extends a domain to the surrounding ticks', () => {
    expect(niceDomain(0.03, 0.38, 5)).toEqual([0, 0.4]);
  });

  it('builds an SVG path through the points', () => {
    const identity = linearScale([0, 1], [0, 1]);
    expect(linePath([{ x: 0, y: 0 }, { x: 1, y: 1 }], identity, identity)).toBe('M0.00,0.00L1.00,1.00');
  });

  it('finds the nearest abscissa', () => {
    expect(nearestIndex([0, 0.5, 1], 0.7)).toBe(1);
    expect(nearestIndex([0, 0.5, 1], 0.9)).toBe(2);
  });

  describe('diverging relativity bars', () => {
    it('gives ×2 and ×0.5 the same length on opposite sides', () => {
      const half = relativityHalfRange([2, 0.5]);
      const up = divergingBar(2, half);
      const down = divergingBar(0.5, half);

      expect(up.side).toBe('up');
      expect(down.side).toBe('down');
      expect(up.left).toBe(50);
      expect(up.width).toBeCloseTo(down.width, 10);
      expect(down.left + down.width).toBeCloseTo(50, 10);
    });

    it('fills half the track for the largest effect', () => {
      expect(divergingBar(3, relativityHalfRange([3, 1.2])).width).toBeCloseTo(50, 10);
    });

    it('treats effects within 1 % as neutral', () => {
      expect(divergingBar(1.005, relativityHalfRange([1.005])).side).toBe('neutral');
    });

    it('never zooms in below ×1.25, so small effects stay small', () => {
      expect(relativityHalfRange([1.05])).toBeCloseTo(Math.log(1.25), 10);
    });
  });
});

describe('logarithmic scale', () => {
  it('spaces each power of ten equally', () => {
    const y = logScale([1e3, 1e6], [300, 0]);
    expect(y(1e3)).toBeCloseTo(300, 10);
    expect(y(1e4)).toBeCloseTo(200, 10);
    expect(y(1e6)).toBeCloseTo(0, 10);
  });

  it('rounds the domain to the enclosing powers of ten', () => {
    expect(logDomain(9696, 8_878_850)).toEqual([1e3, 1e7]);
  });

  it('keeps the number of ticks readable as the range grows', () => {
    expect(logTicks(1e3, 1e4)).toEqual([1000, 2000, 5000, 10000]);
    expect(logTicks(1e3, 1e7)).toEqual([1e3, 3e3, 1e4, 3e4, 1e5, 3e5, 1e6, 3e6, 1e7]);
    expect(logTicks(1, 1e8).length).toBe(9);
  });
});
