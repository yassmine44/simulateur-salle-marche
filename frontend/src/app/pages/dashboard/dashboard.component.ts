import { Component, inject } from '@angular/core';
import { AsyncPipe, DecimalPipe, PercentPipe } from '@angular/common';

import { AuthStateService } from '../../services/auth-state.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    AsyncPipe,
    DecimalPipe,
    PercentPipe
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent {

  private readonly authState = inject(AuthStateService);

  readonly user$ = this.authState.currentUser$;

  readonly marketIndices = [
    {
      name: 'S&P 500',
      value: 5487.03,
      variation: 0.62
    },
    {
      name: 'NASDAQ',
      value: 17862.23,
      variation: 0.94
    },
    {
      name: 'EUR / USD',
      value: 1.0842,
      variation: -0.13
    },
    {
      name: 'Brent',
      value: 84.31,
      variation: 1.18
    }
  ];

  readonly positions = [
    {
      asset: 'AAPL',
      type: 'Action',
      quantity: 40,
      price: 213.07,
      pnl: 820.50
    },
    {
      asset: 'MSFT',
      type: 'Action',
      quantity: 22,
      price: 447.67,
      pnl: 410.20
    },
    {
      asset: 'EUR/USD',
      type: 'Forex',
      quantity: 10000,
      price: 1.0842,
      pnl: -95.40
    }
  ];

  readonly watchlist = [
    {
      symbol: 'AAPL',
      name: 'Apple',
      price: 213.07,
      change: 1.22
    },
    {
      symbol: 'TSLA',
      name: 'Tesla',
      price: 259.14,
      change: -0.74
    },
    {
      symbol: 'NVDA',
      name: 'NVIDIA',
      price: 128.48,
      change: 2.17
    },
    {
      symbol: 'AMZN',
      name: 'Amazon',
      price: 198.25,
      change: 0.35
    }
  ];
}