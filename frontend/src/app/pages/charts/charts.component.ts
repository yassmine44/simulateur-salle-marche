import {
  AfterViewInit,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  inject
} from '@angular/core';

import { Router } from '@angular/router';


interface ChartAsset {
  label: string;
  description: string;
  tvSymbol: string;
  tradeSymbol: string;
}

interface TradingViewWidgetOptions {
  autosize: boolean;
  symbol: string;
  interval: string;
  timezone: string;
  theme: 'light' | 'dark';
  style: string;
  locale: string;
  enable_publishing: boolean;
  allow_symbol_change: boolean;
  hide_side_toolbar: boolean;
  container_id: string;
}

interface TradingViewApi {
  widget: new (
    options: TradingViewWidgetOptions
  ) => object;
}

declare global {
  interface Window {
    TradingView?: TradingViewApi;
  }
}


@Component({
  selector: 'app-charts',
  standalone: true,
  imports: [],
  templateUrl: './charts.component.html',
  styleUrl: './charts.component.scss'
})
export class ChartsComponent
  implements AfterViewInit, OnDestroy {

  private readonly router =
    inject(Router);

  private readonly scriptId =
    'tradingview-widget-script';

  private readonly scriptSrc =
    'https://s3.tradingview.com/tv.js';

  private readonly containerId =
    'charts-tradingview';

  @ViewChild('chartHost')
  private chartHost?: ElementRef<HTMLDivElement>;

  private tradingViewReady = false;

  private destroyed = false;

  loadError = false;


  readonly assets: ChartAsset[] = [
    {
      label: 'NASDAQ',
      description: 'Nasdaq Composite',
      tvSymbol: 'NASDAQ:IXIC',
      tradeSymbol: 'IXIC'
    },
    {
      label: 'S&P 500',
      description: 'S&P 500',
      tvSymbol: 'SP:SPX',
      tradeSymbol: 'SPX'
    },
    {
      label: 'Or',
      description: 'Gold / US Dollar',
      tvSymbol: 'OANDA:XAUUSD',
      tradeSymbol: 'XAUUSD'
    },
    {
      label: 'NVIDIA',
      description: 'NVIDIA Corporation',
      tvSymbol: 'NASDAQ:NVDA',
      tradeSymbol: 'NVDA'
    }
  ];

  selectedAsset: ChartAsset = this.assets[0];


  ngAfterViewInit(): void {

    this.loadTradingViewScript()
      .then(() => {

        if (this.destroyed) {
          return;
        }

        this.tradingViewReady = true;
        this.renderWidget();
      })
      .catch(() => {

        if (this.destroyed) {
          return;
        }

        this.loadError = true;
      });
  }


  ngOnDestroy(): void {

    this.destroyed = true;
    this.destroyWidget();
  }


  selectAsset(
    asset: ChartAsset
  ): void {

    if (
      asset.tradeSymbol
      ===
      this.selectedAsset.tradeSymbol
    ) {
      return;
    }

    this.selectedAsset = asset;
    this.renderWidget();
  }


  openTrading(): void {

    this.router.navigate(
      ['/app/trading'],
      {
        queryParams: {
          symbol: this.selectedAsset.tradeSymbol
        }
      }
    );
  }


  private loadTradingViewScript(): Promise<void> {

    if (window.TradingView) {
      return Promise.resolve();
    }

    const existing =
      document.getElementById(this.scriptId);


    if (existing) {

      return new Promise((resolve, reject) => {

        existing.addEventListener(
          'load',
          () => resolve(),
          { once: true }
        );

        existing.addEventListener(
          'error',
          () => reject(
            new Error('TradingView script failed')
          ),
          { once: true }
        );
      });
    }


    return new Promise((resolve, reject) => {

      const script =
        document.createElement('script');

      script.id = this.scriptId;
      script.src = this.scriptSrc;
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => reject(
        new Error('TradingView script failed')
      );

      document.head.appendChild(script);
    });
  }


  private renderWidget(): void {

    const host =
      this.chartHost?.nativeElement;

    const TradingView =
      window.TradingView;


    if (
      !this.tradingViewReady
      ||
      !host
      ||
      !TradingView
    ) {
      return;
    }


    host.replaceChildren();


    new TradingView.widget({
      autosize: true,
      symbol: this.selectedAsset.tvSymbol,
      interval: 'D',
      timezone: 'Europe/Paris',
      theme: 'light',
      style: '1',
      locale: 'fr',
      enable_publishing: false,
      allow_symbol_change: false,
      hide_side_toolbar: false,
      container_id: this.containerId
    });
  }


  private destroyWidget(): void {

    this.chartHost
      ?.nativeElement
      .replaceChildren();
  }
}
