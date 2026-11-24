import { HttpErrorResponse } from '@angular/common/http';

import {
  apiErrorMessage,
  formatEuros,
  formatPercent,
  formatRelativity,
  formatRelativityChange
} from './actuarial.format';

/** Intl insère des espaces insécables fines : on les normalise pour comparer. */
const plain = (text: string) => text.replace(/[  ]/g, ' ');

describe('actuarial.format', () => {
  it('formats euros and percentages in French', () => {
    expect(plain(formatEuros(883.19))).toBe('883 €');
    expect(plain(formatEuros(883.19, true))).toBe('883,19 €');
    expect(plain(formatPercent(0.2253))).toBe('22,5 %');
  });

  it('formats relativities as multipliers and changes', () => {
    expect(formatRelativity(2.178169)).toBe('×2,18');
    expect(plain(formatRelativityChange(2.178169))).toBe('+118 %');
    expect(plain(formatRelativityChange(0.758))).toBe('−24 %');
    expect(plain(formatRelativityChange(1.001))).toBe('±0 %');
  });

  describe('apiErrorMessage', () => {
    const error = (status: number, body: unknown = null) =>
      new HttpErrorResponse({ status, error: body });

    it('explains an unreachable server and an expired session', () => {
      expect(apiErrorMessage(error(0))).toContain('injoignable');
      expect(apiErrorMessage(error(401))).toContain('session');
    });

    it('lists field errors of a ProblemDetail', () => {
      const body = { detail: 'Vérifiez les champs.', errors: { 'profile.area': 'Zone invalide.' } };
      expect(apiErrorMessage(error(400, body))).toBe('Zone invalide.');
    });

    it('falls back to the detail, then to the status', () => {
      expect(apiErrorMessage(error(503, { detail: 'Moteur indisponible.' }))).toBe('Moteur indisponible.');
      expect(apiErrorMessage(error(500))).toBe('Erreur 500 du serveur.');
      expect(apiErrorMessage(new Error('x'))).toContain('inattendue');
    });
  });
});
