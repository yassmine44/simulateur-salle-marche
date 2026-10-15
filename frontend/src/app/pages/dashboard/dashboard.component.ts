import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  inject,
  OnDestroy,
  OnInit
} from '@angular/core';
import { RouterLink } from '@angular/router';

import {
  finalize,
  forkJoin,
  interval,
  Subscription
} from 'rxjs';

import {
  ActivityEvent,
  DashboardResponse,
  InsightSeverity,
  Protection,
  ProtectionStatus
} from './dashboard.models';

import { DashboardService } from './dashboard.service';

import { Sparkline } from './sparkline';

import { EquityChart }
  from '../../features/portfolio/components/equity-chart/equity-chart';

import {
  PortfolioPosition,
  RiskLevel
} from '../../features/portfolio/models/portfolio.models';

import { TradeResponse }
  from '../../features/trading/models/trading.models';

import { TradingService }
  from '../../features/trading/services/trading.service';


// Une ligne du tableau des positions : la position et sa protection.

interface PositionRow {

  position: PortfolioPosition;

  protection: Protection | null;
}


interface RiskIndicator {

  label: string;

  value: string;

  level: RiskLevel;

  explanation: string;
}


/*
 * Tableau de bord : vue d'ensemble du trading et du portefeuille
 * de l'utilisateur connecté, actualisée automatiquement.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    EquityChart,
    Sparkline
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent
  implements OnInit, OnDestroy {

  private readonly dashboardService =
    inject(DashboardService);

  private readonly tradingService =
    inject(TradingService);


  // ==========================================
  // CONFIGURATION
  // ==========================================

  readonly refreshIntervalMs = 15000;

  private readonly recentTransactionCount = 8;


  // ==========================================
  // STATE
  // ==========================================

  dashboard:
    DashboardResponse | null = null;

  transactions:
    TradeResponse[] = [];

  positionRows:
    PositionRow[] = [];

  riskIndicators:
    RiskIndicator[] = [];

  loading = false;

  refreshing = false;

  errorMessage = '';

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
      interval(this.refreshIntervalMs)
        .subscribe(() => {

          if (!this.loading && !this.refreshing) {
            this.load(true);
          }
        });
  }


  ngOnDestroy(): void {

    this.refreshSubscription?.unsubscribe();

    this.loadSubscription?.unsubscribe();
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

    this.loadSubscription?.unsubscribe();

    this.loadSubscription =
      forkJoin({
        dashboard: this.dashboardService.getDashboard(),
        trades: this.tradingService.getMyTrades()
      })
        .pipe(
          finalize(() => {
            this.loading = false;
            this.refreshing = false;
          })
        )
        .subscribe({

          next: ({ dashboard, trades }) => {

            this.apply(dashboard);

            // Le serveur renvoie les plus récentes en premier.
            this.transactions = trades.slice(0, this.recentTransactionCount);

            this.errorMessage = '';
          },

          error: (error) => {

            console.error('Erreur chargement tableau de bord', error);

            // Une actualisation ratée ne masque pas les données affichées.

            if (!silent || !this.dashboard) {

              this.errorMessage =
                error instanceof HttpErrorResponse && error.status === 0
                  ? 'Serveur injoignable ou session expirée. Vérifiez votre connexion puis réessayez.'
                  : 'Impossible de charger le tableau de bord.';
            }
          }
        });
  }


  private apply(
    dashboard: DashboardResponse
  ): void {

    this.dashboard = dashboard;

    const protections =
      new Map(dashboard.protections.map(protection => [protection.assetSymbol, protection]));

    this.positionRows =
      dashboard.portfolio.openPositions.map(position => ({
        position,
        protection: protections.get(position.assetSymbol) ?? null
      }));

    this.riskIndicators = this.buildRiskIndicators(dashboard);
  }


  private buildRiskIndicators(
    dashboard: DashboardResponse
  ): RiskIndicator[] {

    const risk = dashboard.portfolio.risk;

    return [

      {
        label: 'Exposition globale',
        value: this.percent(risk.exposurePercent),
        level: risk.exposureLevel,
        explanation: 'Part de la valeur investie sur le marché.'
      },

      {
        label: 'Concentration',
        value:
          risk.largestPositionSymbol
            ? `${this.percent(risk.largestPositionPercent)} sur ${risk.largestPositionSymbol}`
            : '—',
        level: risk.concentrationLevel,
        explanation: 'Poids de la plus grosse position.'
      },

      {
        label: 'Diversification',
        value: this.percent(risk.diversificationPercent),
        level: risk.diversificationLevel,
        explanation: '0 % si tout est sur un seul actif.'
      },

      {
        label: 'Volatilité (5 min)',
        value: this.percent(risk.volatilityPercent, 3),
        level: risk.volatilityLevel,
        explanation: 'Variation typique des actifs détenus.'
      },

      {
        label: 'Risque portefeuille',
        value: this.riskLabel(risk.overallLevel),
        level: risk.overallLevel,
        explanation: 'Le plus défavorable des indicateurs.'
      }
    ];
  }


  // ==========================================
  // DISPLAY HELPERS
  // ==========================================

  get currency(): string {

    return this.dashboard?.currency ?? 'USD';
  }


  severityLabel(
    severity: InsightSeverity
  ): string {

    switch (severity) {
      case 'CRITICAL':
        return 'Urgent';
      case 'WARNING':
        return 'À surveiller';
      default:
        return 'Info';
    }
  }


  severityIcon(
    severity: InsightSeverity
  ): string {

    switch (severity) {
      case 'CRITICAL':
        return '!';
      case 'WARNING':
        return '▲';
      default:
        return 'i';
    }
  }


  protectionLabel(
    status: ProtectionStatus
  ): string {

    switch (status) {
      case 'PROTECTED':
        return 'Protégée';
      case 'PARTIAL':
        return 'Partielle';
      default:
        return 'Non protégée';
    }
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


  activityIcon(
    event: ActivityEvent
  ): string {

    switch (event.type) {
      case 'FILLED':
        return '✓';
      case 'TRIGGERED':
        return '⚡';
      case 'CANCELLED':
        return '×';
      case 'REJECTED':
        return '!';
      default:
        return '+';
    }
  }


  trackBySymbol(
    _: number,
    item: { assetSymbol: string }
  ): string {

    return item.assetSymbol;
  }


  trackByRow(
    _: number,
    row: PositionRow
  ): string {

    return row.position.assetSymbol;
  }


  private percent(
    value: number,
    digits = 2
  ): string {

    return value.toLocaleString('fr-FR', { maximumFractionDigits: digits }) + ' %';
  }
}
