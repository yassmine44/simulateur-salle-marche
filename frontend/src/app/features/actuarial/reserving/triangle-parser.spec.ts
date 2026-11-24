import { parseTriangle, TAYLOR_ASHE_EXAMPLE, TriangleParseError } from './triangle-parser';

describe('parseTriangle', () => {
  it('reads a tab-separated triangle pasted from a spreadsheet', () => {
    const parsed = parseTriangle(TAYLOR_ASHE_EXAMPLE);
    expect(parsed.triangle.length).toBe(10);
    expect(parsed.triangle[0].length).toBe(10);
    expect(parsed.triangle[9]).toEqual([344014]);
    expect(parsed.origins).toBeUndefined();
  });

  it('detects a leading column of years', () => {
    const parsed = parseTriangle('2019;100;150;160\n2020;110;170\n2021;120');
    expect(parsed.origins).toEqual([2019, 2020, 2021]);
    expect(parsed.triangle).toEqual([[100, 150, 160], [110, 170], [120]]);
  });

  it('accepts French decimals, thousands separators and empty future cells', () => {
    const parsed = parseTriangle('1 000,5\t1 500\t1 600\t\n1.100,25\t1 700\t\t\n1 200\t\t\t');
    expect(parsed.triangle).toEqual([[1000.5, 1500, 1600], [1100.25, 1700], [1200]]);
  });

  it('keeps a single-value last row intact (no separator on that line)', () => {
    const parsed = parseTriangle('1 000\t1 500\t1 600\n1 100\t1 700\n1 200');
    expect(parsed.triangle[2]).toEqual([1200]);
  });

  it('accepts space-separated values and ignores blank lines', () => {
    expect(parseTriangle('\n100 150 160\n\n110 170\n120\n').triangle).toEqual([[100, 150, 160], [110, 170], [120]]);
  });

  it('explains what is wrong', () => {
    expect(() => parseTriangle('100\n200')).toThrowError(TriangleParseError, /au moins 3 lignes/);
    expect(() => parseTriangle('100;abc\n110\n120')).toThrowError(TriangleParseError, /Ligne 1 : « abc »/);
    expect(() => parseTriangle('100\n;;\n120')).toThrowError(TriangleParseError, /Ligne 2 : aucune valeur/);
  });
});
