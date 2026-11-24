import { formatPercent } from '../actuarial.format';
import { AdminOverview } from '../actuarial.models';

type AdminAction = AdminOverview['recentActions'][number];

const ACTION_LABELS: Record<AdminAction['type'], string> = {
  ASSUMPTIONS_UPDATED: 'Hypothèses commerciales modifiées',
  CURRENCY_UPDATED: 'Devise modifiée',
  MODELS_RELOADED: 'Modèles rechargés'
};

const ASSUMPTION_LABELS: Record<string, string> = {
  expense_ratio: 'frais',
  profit_ratio: 'bénéfice',
  safety_loading: 'sécurité'
};

export function actionLabel(action: AdminAction): string {
  return ACTION_LABELS[action.type] ?? action.type;
}

/**
 * Détail lisible d'une action du journal.
 * ASSUMPTIONS_UPDATED → « frais 25 % → 20 % · bénéfice 5 % → 3 % » (seuls les changements) ;
 * MODELS_RELOADED     → « fréquence v1 · sévérité v1 ».
 */
export function describeAction(action: AdminAction): string {
  if (!action.details) {
    return '';
  }
  let details: Record<string, unknown>;
  try {
    details = JSON.parse(action.details);
  } catch {
    return action.details;
  }

  if (action.type === 'ASSUMPTIONS_UPDATED') {
    const previous = (details['previous'] ?? {}) as Record<string, number>;
    const current = (details['current'] ?? {}) as Record<string, number>;
    const changes = Object.entries(ASSUMPTION_LABELS)
      .filter(([key]) => previous[key] !== current[key])
      .map(([key, label]) => `${label} ${formatPercent(previous[key] ?? 0, 1)} → ${formatPercent(current[key] ?? 0, 1)}`);
    return changes.length > 0 ? changes.join(' · ') : 'aucun changement';
  }
  if (action.type === 'CURRENCY_UPDATED') {
    const previous = (details['previous'] ?? {}) as Record<string, string | number>;
    const current = (details['current'] ?? {}) as Record<string, string | number>;
    return `${previous['code'] ?? '?'} (×${previous['rate_from_model'] ?? '?'}) → ${current['code'] ?? '?'} (×${current['rate_from_model'] ?? '?'})`;
  }
  if (action.type === 'MODELS_RELOADED') {
    return `fréquence ${details['frequency'] ?? '?'} · sévérité ${details['severity'] ?? '?'}`;
  }
  return action.details;
}
