import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';

interface DashboardMetric {
  label: string;
  value: string;
  variation?: string;
  positive?: boolean;
}

interface Position {
  asset: string;
  type: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  pnl: number;
}

interface Transaction {
  asset: string;
  type: 'BUY' | 'SELL';
  quantity: number;
  price: number;
  date: string;
}

interface RiskIndicator {
  label: string;
  value: string;
  level: 'LOW' | 'MEDIUM' | 'HIGH';
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent {

  // =========================================
  // TEMPORARY DEMO DATA
  // =========================================

  readonly metrics: DashboardMetric[] = [

    {
      label: 'Capital disponible',
      value: '100 000 DT'
    },

    {
      label: 'Valeur du portefeuille',
      value: '103 450 DT',
      variation: '+3,45%',
      positive: true
    },

    {
      label: 'Performance globale',
      value: '+3 450 DT',
      variation: '+3,45%',
      positive: true
    },

    {
      label: 'Positions ouvertes',
      value: '4'
    }
  ];


  readonly portfolioHistory = [
    100000,
    100800,
    100300,
    101500,
    102200,
    101900,
    103450
  ];


  readonly positions: Position[] = [

    {
      asset: 'AAPL',
      type: 'BUY',
      quantity: 10,
      price: 215.40,
      pnl: 420
    },

    {
      asset: 'EUR/USD',
      type: 'BUY',
      quantity: 2000,
      price: 1.1712,
      pnl: 180
    },

    {
      asset: 'TSLA',
      type: 'SELL',
      quantity: 4,
      price: 438.25,
      pnl: -95
    },

    {
      asset: 'BTC/USD',
      type: 'BUY',
      quantity: 0.02,
      price: 68420,
      pnl: 260
    }
  ];


  readonly transactions: Transaction[] = [

    {
      asset: 'AAPL',
      type: 'BUY',
      quantity: 10,
      price: 215.40,
      date: '02/10/2026 10:22'
    },

    {
      asset: 'EUR/USD',
      type: 'BUY',
      quantity: 2000,
      price: 1.1712,
      date: '01/10/2026 15:40'
    },

    {
      asset: 'TSLA',
      type: 'SELL',
      quantity: 4,
      price: 438.25,
      date: '30/09/2026 11:15'
    }
  ];


  readonly riskIndicators: RiskIndicator[] = [

    {
      label: 'Exposition globale',
      value: '38%',
      level: 'LOW'
    },

    {
      label: 'Diversification',
      value: '72%',
      level: 'LOW'
    },

    {
      label: 'Volatilité',
      value: '16%',
      level: 'MEDIUM'
    },

    {
      label: 'Risque portefeuille',
      value: 'Modéré',
      level: 'MEDIUM'
    }
  ];


  // =========================================
  // CHART HELPERS
  // =========================================

  get chartMin(): number {

    return Math.min(
      ...this.portfolioHistory
    );
  }


  get chartMax(): number {

    return Math.max(
      ...this.portfolioHistory
    );
  }


  pointHeight(
    value: number
  ): number {

    const range =
      this.chartMax -
      this.chartMin;


    if (
      range === 0
    ) {

      return 50;
    }


    return (
      (
        value -
        this.chartMin
      ) /
      range
    ) * 100;
  }


  riskClass(
    level: RiskIndicator['level']
  ): string {

    return level.toLowerCase();
  }
}