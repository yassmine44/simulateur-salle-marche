import { actionLabel, describeAction } from './admin-actions';

const plain = (text: string) => text.replace(/[  ]/g, ' ');

describe('admin actions', () => {
  const base = { id: 1, actorEmail: 'admin@example.test', createdAt: '2026-11-24T10:00:00Z' };

  it('lists only the assumptions that changed', () => {
    const action = {
      ...base,
      type: 'ASSUMPTIONS_UPDATED' as const,
      details: JSON.stringify({
        previous: { expense_ratio: 0.25, profit_ratio: 0.05, safety_loading: 0.03 },
        current: { expense_ratio: 0.2, profit_ratio: 0.05, safety_loading: 0.03 }
      })
    };
    expect(actionLabel(action)).toBe('Hypothèses commerciales modifiées');
    expect(plain(describeAction(action))).toBe('frais 25,0 % → 20,0 %');
  });

  it('describes a reload with the served versions', () => {
    const action = {
      ...base,
      type: 'MODELS_RELOADED' as const,
      details: JSON.stringify({ frequency: 'v2', severity: 'v3' })
    };
    expect(describeAction(action)).toBe('fréquence v2 · sévérité v3');
  });

  it('describes a currency change', () => {
    const action = {
      ...base,
      type: 'CURRENCY_UPDATED' as const,
      details: JSON.stringify({ previous: { code: 'TND', rate_from_model: 3.36 }, current: { code: 'EUR', rate_from_model: 1 } })
    };
    expect(actionLabel(action)).toBe('Devise modifiée');
    expect(describeAction(action)).toBe('TND (×3.36) → EUR (×1)');
  });

  it('survives missing or malformed details', () => {
    expect(describeAction({ ...base, type: 'MODELS_RELOADED', details: null })).toBe('');
    expect(describeAction({ ...base, type: 'MODELS_RELOADED', details: 'pas du json' })).toBe('pas du json');
  });
});
