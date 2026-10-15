import {
  Component,
  inject,
  OnDestroy,
  OnInit
} from '@angular/core';

import { CommonModule } from '@angular/common';

import { HttpErrorResponse } from '@angular/common/http';

import { FormsModule } from '@angular/forms';

import { RouterLink } from '@angular/router';

import {
  finalize,
  forkJoin,
  interval,
  Subscription
} from 'rxjs';

import {
  Contribution,
  HistoryDays,
  PerformanceResponse,
  PortfolioPosition,
  PortfolioResponse,
  RebalanceExecutionResponse,
  RebalancePlanResponse,
  RiskLevel
} from '../../models/portfolio.models';

import { AssetResponse }
  from '../../../trading/models/trading.models';

import { TradingService }
  from '../../../trading/services/trading.service';

import { PortfolioService }
  from '../../services/portfolio.service';

import { EquityChart }
  from '../../components/equity-chart/equity-chart';


type SortKey =
  | 'assetSymbol'
  | 'marketValue'
  | 'weightPercent'
  | 'unrealizedPnl'
  | 'unrealizedPnlPercent';


// Ligne de l'assistant de rééquilibrage.

interface TargetRow {

  assetSymbol: string;

  assetName: string;

  currentPercent: number;

  targetPercent: number;
}


interface RiskCard {

  label: string;

  value: string;

  level: RiskLevel;

  // Comment l'indicateur est calculé, en une phrase.
  explanation: string;
}


@Component({
  selector: 'app-portfolio',

  standalone: true,

  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    EquityChart
  ],

  templateUrl: './portfolio.html',

  styleUrl: './portfolio.scss'
})
export class Portfolio
  implements OnInit, OnDestroy {

  private readonly portfolioService =
    inject(PortfolioService);

  private readonly tradingService =
    inject(TradingService);


  // ==========================================
  // CONFIGURATION
  // ==========================================

  // Les cours bougent en continu : actualisation périodique.

  readonly refreshIntervalMs =
    10000;

  readonly historyOptions:
    HistoryDays[] = [7, 30];


  // ==========================================
  // STATE
  // ==========================================

  portfolio:
    PortfolioResponse | null = null;

  // Statistiques, contributions, qualité d'exécution, référence.

  performance:
    PerformanceResponse | null = null;


  // ---- Rééquilibrage ----

  rebalanceOpen = false;

  targetRows:
    TargetRow[] = [];

  // Actifs que l'on peut ajouter à la cible.

  assets:
    AssetResponse[] = [];

  assetToAdd = '';

  plan:
    RebalancePlanResponse | null = null;

  planning = false;

  confirmingRebalance = false;

  executing = false;

  execution:
    RebalanceExecutionResponse | null = null;

  rebalanceError = '';

  historyDays:
    HistoryDays = 7;

  loading = false;

  refreshing = false;

  errorMessage = '';

  successMessage = '';

  // Confirmation de la réinitialisation en cours.

  confirmingReset = false;

  resetting = false;

  sortKey:
    SortKey = 'marketValue';

  sortDescending = true;

  riskCards:
    RiskCard[] = [];

  sortedPositions:
    PortfolioPosition[] = [];

  private refreshSubscription?:
    Subscription;

  private loadSubscription?:
    Subscription;


  // ==========================================
  // LIFECYCLE
  // ==========================================

  ngOnInit(): void {

    this.load();

    this.refreshSubscription =
      interval(
        this.refreshIntervalMs
      )
        .subscribe(() => {

          // Pas d'actualisation pendant une confirmation :
          // les chiffres affichés ne changent pas sous les yeux.

          if (
            !this.loading
            &&
            !this.refreshing
            &&
            !this.confirmingReset
            &&
            !this.rebalanceOpen
          ) {

            this.load(true);
          }
        });
  }


  ngOnDestroy(): void {

    this.refreshSubscription
      ?.unsubscribe();

    this.loadSubscription
      ?.unsubscribe();
  }


  // ==========================================
  // LOAD
  // ==========================================

  load(
    silent = false
  ): void {

    if (silent) {
      this.refreshing = true;
    } else {
      this.loading = true;
      this.errorMessage = '';
    }

    this.loadSubscription
      ?.unsubscribe();

    this.loadSubscription =
      forkJoin({

        portfolio:
          this.portfolioService
            .getPortfolio(
              this.historyDays
            ),

        performance:
          this.portfolioService
            .getPerformance(
              this.historyDays
            )
      })
        .pipe(
          finalize(() => {

            this.loading = false;

            this.refreshing = false;
          })
        )
        .subscribe({

          next: ({ portfolio, performance }) => {

            this.apply(portfolio);

            this.performance = performance;

            this.errorMessage = '';
          },

          error: (error) => {

            console.error(
              'Erreur chargement portefeuille',
              error
            );

            // Une actualisation ratée ne masque pas
            // les données déjà affichées.

            if (
              !silent
              ||
              !this.portfolio
            ) {

              this.errorMessage =
                this.extractErrorMessage(
                  error,
                  'Impossible de charger le portefeuille.'
                );
            }
          }
        });
  }


  selectHistory(
    days: HistoryDays
  ): void {

    if (days === this.historyDays) {
      return;
    }

    this.historyDays = days;

    this.load(true);
  }


  private apply(
    portfolio: PortfolioResponse
  ): void {

    this.portfolio = portfolio;

    this.riskCards = this.buildRiskCards(portfolio);

    this.sortPositions();
  }


  // ==========================================
  // POSITIONS SORTING
  // ==========================================

  sortBy(
    key: SortKey
  ): void {

    if (this.sortKey === key) {

      this.sortDescending = !this.sortDescending;

    } else {

      this.sortKey = key;

      // Texte : A → Z ; nombres : du plus grand au plus petit.
      this.sortDescending = key !== 'assetSymbol';
    }

    this.sortPositions();
  }


  sortIndicator(
    key: SortKey
  ): string {

    if (this.sortKey !== key) {
      return '';
    }

    return this.sortDescending ? '▼' : '▲';
  }


  ariaSort(
    key: SortKey
  ): string {

    if (this.sortKey !== key) {
      return 'none';
    }

    return this.sortDescending ? 'descending' : 'ascending';
  }


  private sortPositions(): void {

    const direction = this.sortDescending ? -1 : 1;

    const key = this.sortKey;

    this.sortedPositions =
      [...(this.portfolio?.openPositions ?? [])]
        .sort((first, second) => {

          if (key === 'assetSymbol') {

            return first.assetSymbol.localeCompare(second.assetSymbol)
              * direction;
          }

          return (first[key] - second[key]) * direction;
        });
  }


  // ==========================================
  // RISK
  // ==========================================

  private buildRiskCards(
    portfolio: PortfolioResponse
  ): RiskCard[] {

    const risk = portfolio.risk;

    return [

      {
        label: 'Exposition',

        value: this.percent(risk.exposurePercent),

        level: risk.exposureLevel,

        explanation:
          'Part de la valeur totale investie sur le marché. '
          + 'Le reste est en liquidités.'
      },

      {
        label: 'Concentration',

        value:
          risk.largestPositionSymbol
            ? `${this.percent(risk.largestPositionPercent)} sur ${risk.largestPositionSymbol}`
            : '—',

        level: risk.concentrationLevel,

        explanation:
          'Poids de la plus grosse position dans la valeur totale. '
          + 'Élevé au-delà de 40 %.'
      },

      {
        label: 'Diversification',

        value:
          `${this.percent(risk.diversificationPercent)} · `
          + `${risk.effectivePositions} position(s) équivalente(s)`,

        level: risk.diversificationLevel,

        explanation:
          '0 % si tout est sur un seul actif. Les « positions équivalentes » '
          + 'indiquent combien de positions de même taille donneraient la même répartition.'
      },

      {
        label: 'Volatilité',

        value: this.percent(risk.volatilityPercent, 3),

        level: risk.volatilityLevel,

        explanation:
          'Variation typique, sur 5 minutes, des cours des actifs détenus '
          + '(sur 24 h), pondérée par leur poids.'
      },

      {
        label: 'Baisse maximale',

        value: this.percent(risk.maxDrawdownPercent),

        level: risk.drawdownLevel,

        explanation:
          `Plus forte chute de la valeur depuis un sommet, sur ${portfolio.historyDays} jours.`
      }
    ];
  }


  riskLabel(
    level: RiskLevel
  ): string {

    switch (level) {

      case 'LOW':
        return 'Faible';

      case 'MEDIUM':
        return 'Modéré';

      default:
        return 'Élevé';
    }
  }


  // ==========================================
  // RESET
  // ==========================================

  askReset(): void {

    this.successMessage = '';

    this.confirmingReset = true;
  }


  cancelReset(): void {

    this.confirmingReset = false;
  }


  confirmReset(): void {

    if (this.resetting) {
      return;
    }

    this.resetting = true;

    this.errorMessage = '';

    this.portfolioService
      .reset()
      .pipe(
        finalize(() => {

          this.resetting = false;
        })
      )
      .subscribe({

        next: (portfolio) => {

          this.confirmingReset = false;

          this.apply(portfolio);

          this.load(true);

          this.successMessage =
            'Portefeuille réinitialisé : vous repartez de '
            + `${this.money(portfolio.initialCapital, portfolio.currency)}. `
            + 'Vos ordres ouverts ont été annulés ; l’historique reste consultable dans Trading.';
        },

        error: (error) => {

          this.errorMessage =
            this.extractErrorMessage(
              error,
              'La réinitialisation a échoué.'
            );
        }
      });
  }


  // ==========================================
  // EXPORT
  // ==========================================

  // Positions ouvertes au format CSV (séparateur « ; » pour Excel en français).

  exportCsv(): void {

    const portfolio = this.portfolio;

    if (!portfolio) {
      return;
    }

    const header = [
      'Actif',
      'Nom',
      'Sens',
      'Quantité',
      'Disponible',
      'Prix de revient',
      'Dernier cours',
      'Valeur',
      'Poids %',
      'Résultat latent',
      'Résultat latent %',
      'Résultat réalisé'
    ];

    const rows =
      portfolio.openPositions.map(position => [
        position.assetSymbol,
        position.assetName,
        position.direction === 'LONG' ? 'Achat' : 'Vente',
        position.quantity,
        position.availableQuantity,
        position.averagePrice,
        position.lastPrice,
        position.marketValue,
        position.weightPercent,
        position.unrealizedPnl,
        position.unrealizedPnlPercent,
        position.realizedPnl
      ]);

    rows.push([]);

    rows.push(['Liquidités', '', '', '', '', '', '', portfolio.cash]);

    rows.push(['Valeur totale', '', '', '', '', '', '', portfolio.totalValue]);

    const csv =
      [header, ...rows]
        .map(row =>
          row
            .map(cell => {

              // Nombres au format français (virgule décimale).

              const text =
                typeof cell === 'number'
                  ? String(cell).replace('.', ',')
                  : String(cell ?? '');

              return /[;"\n]/.test(text)
                ? `"${text.replace(/"/g, '""')}"`
                : text;
            })
            .join(';')
        )
        .join('\n');

    // BOM : Excel reconnaît l'UTF-8 (accents).

    const blob =
      new Blob(
        ['﻿' + csv],
        { type: 'text/csv;charset=utf-8' }
      );

    const url = URL.createObjectURL(blob);

    const link = document.createElement('a');

    link.href = url;

    link.download =
      `portefeuille-${new Date().toISOString().slice(0, 10)}.csv`;

    link.click();

    URL.revokeObjectURL(url);
  }


  // ==========================================
  // PERFORMANCE HELPERS
  // ==========================================

  // Largeur de la barre d'une contribution, en % de la demi-largeur :
  // les gains partent vers la droite, les pertes vers la gauche.

  contributionWidth(
    contribution: Contribution
  ): number {

    const max =
      Math.max(
        ...(this.performance?.contributions ?? [])
          .map(item => Math.abs(item.totalPnl)),
        0.01
      );

    return Math.abs(contribution.totalPnl) / max * 100;
  }


  holdingDuration(
    minutes: number | null
  ): string {

    if (minutes === null) {
      return '—';
    }

    if (minutes < 60) {
      return `${minutes} min`;
    }

    if (minutes < 60 * 24) {
      return `${Math.round(minutes / 6) / 10} h`;
    }

    return `${Math.round(minutes / 144) / 10} j`;
  }


  // ==========================================
  // REBALANCE
  // ==========================================

  // Ouvre l'assistant avec la répartition actuelle comme point de départ.

  openRebalance(): void {

    const portfolio = this.portfolio;

    if (!portfolio) {
      return;
    }

    this.targetRows =
      portfolio.openPositions
        .filter(position => position.direction === 'LONG')
        .map(position => ({
          assetSymbol: position.assetSymbol,
          assetName: position.assetName,
          currentPercent: position.weightPercent,
          targetPercent: Math.round(position.weightPercent * 10) / 10
        }));

    this.plan = null;

    this.execution = null;

    this.rebalanceError = '';

    this.rebalanceOpen = true;

    if (this.assets.length === 0) {

      this.tradingService
        .getAssets()
        .subscribe({
          next: (assets) => {
            this.assets = assets;
          }
        });
    }
  }


  closeRebalance(): void {

    this.rebalanceOpen = false;

    this.confirmingRebalance = false;
  }


  get addableAssets(): AssetResponse[] {

    const used =
      new Set(this.targetRows.map(row => row.assetSymbol));

    return this.assets.filter(asset => !used.has(asset.symbol));
  }


  addTarget(): void {

    const asset =
      this.assets.find(item => item.symbol === this.assetToAdd);

    if (!asset) {
      return;
    }

    this.targetRows = [
      ...this.targetRows,
      {
        assetSymbol: asset.symbol,
        assetName: asset.name,
        currentPercent: 0,
        targetPercent: 0
      }
    ];

    this.assetToAdd = '';

    this.plan = null;
  }


  removeTarget(
    row: TargetRow
  ): void {

    this.targetRows =
      this.targetRows.filter(item => item !== row);

    this.plan = null;
  }


  // Une cible modifiée rend le plan affiché obsolète.

  targetChanged(): void {

    this.plan = null;

    this.confirmingRebalance = false;
  }


  get targetTotal(): number {

    return Math.round(
      this.targetRows.reduce(
        (sum, row) => sum + (Number(row.targetPercent) || 0),
        0
      ) * 100
    ) / 100;
  }


  get targetsValid(): boolean {

    return (
      this.targetRows.length > 0
      &&
      this.targetTotal <= 100
      &&
      this.targetRows.every(row =>
        Number(row.targetPercent) >= 0
        &&
        Number(row.targetPercent) <= 100
      )
    );
  }


  computePlan(): void {

    if (!this.targetsValid || this.planning) {
      return;
    }

    this.planning = true;

    this.rebalanceError = '';

    this.execution = null;

    this.portfolioService
      .planRebalance(this.targets())
      .pipe(
        finalize(() => {
          this.planning = false;
        })
      )
      .subscribe({

        next: (plan) => {
          this.plan = plan;
        },

        error: (error) => {

          this.rebalanceError =
            this.extractErrorMessage(
              error,
              'Impossible de calculer le plan.'
            );
        }
      });
  }


  get planOrderCount(): number {

    return this.plan?.lines.filter(line => line.side !== null).length ?? 0;
  }


  askExecuteRebalance(): void {

    this.confirmingRebalance = true;
  }


  executeRebalance(): void {

    if (this.executing) {
      return;
    }

    this.executing = true;

    this.rebalanceError = '';

    this.portfolioService
      .executeRebalance(this.targets())
      .pipe(
        finalize(() => {
          this.executing = false;
          this.confirmingRebalance = false;
        })
      )
      .subscribe({

        next: (execution) => {

          this.execution = execution;

          this.plan = null;

          this.load(true);
        },

        error: (error) => {

          this.rebalanceError =
            this.extractErrorMessage(
              error,
              'Le rééquilibrage a échoué.'
            );
        }
      });
  }


  private targets() {

    return this.targetRows.map(row => ({
      assetSymbol: row.assetSymbol,
      targetPercent: Number(row.targetPercent) || 0
    }));
  }


  // ==========================================
  // DISPLAY HELPERS
  // ==========================================

  get hasPositions(): boolean {

    return (this.portfolio?.openPositions.length ?? 0) > 0;
  }


  get maxAllocation(): number {

    return Math.max(
      ...(this.portfolio?.allocation ?? []).map(slice => slice.percent),
      1
    );
  }


  assetTypeLabel(
    type: string | null
  ): string {

    switch (type) {

      case 'STOCK':
        return 'Action';

      case 'CRYPTO':
        return 'Crypto';

      case 'FOREX':
        return 'Devise';

      case 'INDEX':
        return 'Indice';

      default:
        return '';
    }
  }


  trackBySymbol(
    _: number,
    item: { assetSymbol: string }
  ): string {

    return item.assetSymbol;
  }


  trackByKey(
    _: number,
    item: { key: string }
  ): string {

    return item.key;
  }


  private percent(
    value: number,
    digits = 2
  ): string {

    return value.toLocaleString(
      'fr-FR',
      {
        maximumFractionDigits: digits
      }
    ) + ' %';
  }


  private money(
    value: number,
    currency: string
  ): string {

    return value.toLocaleString(
      'fr-FR',
      {
        maximumFractionDigits: 2
      }
    ) + ' ' + currency;
  }


  private extractErrorMessage(
    error: unknown,
    fallback: string
  ): string {

    if (!(error instanceof HttpErrorResponse)) {
      return fallback;
    }

    if (error.status === 0) {

      return 'Serveur injoignable ou session expirée. '
        + 'Vérifiez votre connexion puis réessayez.';
    }

    const body =
      error.error as { detail?: string } | null;

    return body?.detail || fallback;
  }
}
