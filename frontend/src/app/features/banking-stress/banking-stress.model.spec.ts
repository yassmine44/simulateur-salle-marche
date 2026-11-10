import { validateLossShares } from './banking-stress.model';

describe('validateLossShares', () => {
  it('accepts 0 + 1', () => {
    expect(validateLossShares('0', '1', 'exposure-1')).toEqual({
      bankLossShare: 0,
      investorLossShare: 1
    });
  });

  it('accepts 0.5 + 0.5', () => {
    expect(validateLossShares('0.5', '0.5', 'exposure-1')).toEqual({
      bankLossShare: 0.5,
      investorLossShare: 0.5
    });
  });

  it('accepts complementary shares with eight decimal places', () => {
    expect(validateLossShares('0.33333333', '0.66666667', 'exposure-1')).toEqual({
      bankLossShare: 0.33333333,
      investorLossShare: 0.66666667
    });
  });

  it('rejects complementary shares with more than eight decimal places', () => {
    expect(() => validateLossShares(
      '0.333333333',
      '0.666666667',
      'exposure-1'
    )).toThrowError(/plus de 8 décimales/);
  });

  it('rejects shares whose sum is slightly below one', () => {
    expect(() => validateLossShares(
      '0.33333333',
      '0.66666666',
      'exposure-1'
    )).toThrowError(/totaliser exactement 100 %/);
  });

  it('rejects shares whose sum is slightly above one', () => {
    expect(() => validateLossShares(
      '0.50000001',
      '0.5',
      'exposure-1'
    )).toThrowError(/totaliser exactement 100 %/);
  });

  it('rejects an individual share above one', () => {
    expect(() => validateLossShares('1.00000001', '0', 'exposure-1'))
      .toThrowError(/comprise entre 0 et 1/);
  });
});
