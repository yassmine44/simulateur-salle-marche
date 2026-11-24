import { HttpErrorResponse } from '@angular/common/http';

const EUROS = new Intl.NumberFormat('fr-FR', {
  style: 'currency',
  currency: 'EUR',
  maximumFractionDigits: 0
});
const EUROS_PRECISE = new Intl.NumberFormat('fr-FR', {
  style: 'currency',
  currency: 'EUR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});
const COMPACT_EUROS = new Intl.NumberFormat('fr-FR', {
  style: 'currency',
  currency: 'EUR',
  notation: 'compact',
  maximumFractionDigits: 1
});
const DECIMAL = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 });

export function formatEuros(value: number, precise = false): string {
  return (precise ? EUROS_PRECISE : EUROS).format(value);
}

export function formatCompactEuros(value: number): string {
  return COMPACT_EUROS.format(value);
}

const COMPACT_USD = new Intl.NumberFormat('fr-FR', {
  style: 'currency',
  currency: 'USD',
  notation: 'compact',
  maximumFractionDigits: 1
});

/** Montant en dollars, notation compacte (ex. « 17 Md $US »). */
export function formatCompactUsd(value: number): string {
  return COMPACT_USD.format(value);
}

export function formatPercent(ratio: number, digits = 1): string {
  return new Intl.NumberFormat('fr-FR', {
    style: 'percent',
    minimumFractionDigits: digits,
    maximumFractionDigits: digits
  }).format(ratio);
}

export function formatNumber(value: number, digits = 2): string {
  return new Intl.NumberFormat('fr-FR', { maximumFractionDigits: digits }).format(value);
}

/** Multiplicateur de prime : 2.178 → « ×2,18 ». */
export function formatRelativity(relativity: number): string {
  return `×${DECIMAL.format(relativity)}`;
}

/** Variation relative lisible : 2.178 → « +118 % », 0.758 → « −24 % ». */
export function formatRelativityChange(relativity: number): string {
  const change = Math.round((relativity - 1) * 100);
  if (change === 0) {
    return '±0 %';
  }
  return `${change > 0 ? '+' : '−'}${Math.abs(change)} %`;
}

/**
 * Message lisible pour une erreur de l'API.
 * Comprend le format ProblemDetail du backend ({ detail, errors: { champ: message } }).
 */
export function apiErrorMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return 'Une erreur inattendue est survenue.';
  }
  if (error.status === 0) {
    return 'Le serveur est injoignable. Vérifiez que le backend est démarré.';
  }
  if (error.status === 401) {
    return 'Votre session a expiré. Reconnectez-vous.';
  }
  const body = error.error as { detail?: string; errors?: Record<string, string> } | null;
  const fieldErrors = body?.errors ? Object.values(body.errors) : [];
  if (fieldErrors.length > 0) {
    return fieldErrors.join(' ');
  }
  if (body?.detail) {
    return body.detail;
  }
  return `Erreur ${error.status} du serveur.`;
}
