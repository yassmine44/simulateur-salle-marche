/**
 * Verified BVMT tickers with a full 2018 trading year (~249 sessions).
 * Admin selects one of these when starting a multiplayer session; the
 * ticker is injected into the AI Context Engine system prompt.
 */
export const VERIFIED_TUNISIAN_STOCKS: readonly string[] = [
  // Banks & Financials
  'BIAT',
  'BT',
  'AMEN BANK',
  'ATB',
  'ATL',
  'ATTIJARI BANK',
  'BH',
  'BNA',
  'STB',
  'UIB',
  'WIFACK INT BANK',
  // Automotive & Leasing
  'ARTES',
  'CITY CARS',
  'ENNAKL AUTOMOBILES',
  'ATTIJARI LEASING',
  'HANNIBAL LEASE',
  // Consumer Goods & Retail
  'SFBT',
  'DELICE HOLDING',
  'POULINA GP HOLDING',
  'MAGASIN GENERAL',
  'MONOPRIX',
  'EURO-CYCLES',
  // Industrial, Tech & Services
  'ASSAD',
  'CELLCOM',
  'ONE TECH HOLDING',
  'SAH',
  'SIAME',
  'TELNET HOLDING',
  'TPR',
  // Airlines
  'TUNISAIR'
] as const;

export const DEFAULT_VERIFIED_STOCK = 'BIAT';
