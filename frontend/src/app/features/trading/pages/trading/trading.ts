import {
  Component,
  inject,
  OnDestroy,
  OnInit
} from '@angular/core';

import { CommonModule } from '@angular/common';

import {
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';

import {
  forkJoin,
  interval,
  Subscription
} from 'rxjs';

import {
  AssetResponse,
  CreateOrderRequest,
  OrderBookMetricsResponse,
  OrderBookResponse,
  OrderBookTemporalMetricsResponse,
  OrderResponse,
  OrderSide,
  OrderType,
  SlippageEstimateResponse,
  TradeResponse
} from '../../models/trading.models';

import {
  TradingService
} from '../../services/trading.service';


@Component({
  selector: 'app-trading',
  standalone: true,

  imports: [
    CommonModule,
    ReactiveFormsModule
  ],

  templateUrl: './trading.html',
  styleUrl: './trading.scss'
})
export class Trading
  implements OnInit, OnDestroy {


  // ==========================================
  // DEPENDENCIES
  // ==========================================

  private readonly fb =
    inject(FormBuilder);

  private readonly tradingService =
    inject(TradingService);


  // ==========================================
  // CONFIGURATION
  // ==========================================

  readonly refreshIntervalMs =
    3000;


  // ==========================================
  // GENERAL STATE
  // ==========================================

  loading =
    false;

  submitting =
    false;

  successMessage =
    '';

  errorMessage =
    '';


  // ==========================================
  // ASSETS
  // ==========================================

  assets:
    AssetResponse[] = [];


  // ==========================================
  // ORDER BOOK
  // ==========================================

  orderBook:
    OrderBookResponse | null = null;


  // ==========================================
  // MICROSTRUCTURE METRICS
  // ==========================================

  metrics:
    OrderBookMetricsResponse | null = null;


  // ==========================================
  // TEMPORAL METRICS
  // ==========================================

  temporalMetrics:
    OrderBookTemporalMetricsResponse | null = null;


  // ==========================================
  // SLIPPAGE
  // ==========================================

  slippageEstimate:
    SlippageEstimateResponse | null = null;

  slippageLoading =
    false;

  slippageErrorMessage =
    '';

  private slippageRequestSequence =
    0;


  // ==========================================
  // USER ORDERS
  // ==========================================

  myOrders:
    OrderResponse[] = [];


  // ==========================================
  // USER TRADES
  // ==========================================

  myTrades:
    TradeResponse[] = [];


  // ==========================================
  // AUTO REFRESH
  // ==========================================

  private refreshSubscription?:
    Subscription;


  // ==========================================
  // ORDER FORM
  // ==========================================

  readonly orderForm =
    this.fb.nonNullable.group({

      assetSymbol: [
        'AAPL',
        [
          Validators.required,
          Validators.maxLength(30)
        ]
      ],

      side: [
        'BUY' as OrderSide,
        Validators.required
      ],

      type: [
        'LIMIT' as OrderType,
        Validators.required
      ],

      quantity: [
        1,
        [
          Validators.required,
          Validators.min(0.00000001)
        ]
      ],

      limitPrice: [
        200,
        [
          Validators.required,
          Validators.min(0.00000001)
        ]
      ]
    });


  // ==========================================
  // LIFECYCLE
  // ==========================================

  ngOnInit(): void {

    this.onOrderTypeChange();

    this.loadAssets();

    this.refreshSubscription =
      interval(
        this.refreshIntervalMs
      )
        .subscribe(() => {

          this.refreshMarketData();

        });
  }


  ngOnDestroy(): void {

    this.refreshSubscription
      ?.unsubscribe();
  }


  // ==========================================
  // LOAD ASSETS
  // ==========================================

  private loadAssets(): void {

    this.loading =
      true;

    this.clearMessages();


    this.tradingService
      .getAssets()
      .subscribe({

        next: (assets) => {

          this.assets =
            assets;


          if (
            this.assets.length === 0
          ) {

            this.errorMessage =
              'Aucun actif disponible.';

            this.loading =
              false;

            return;
          }


          const currentSymbol =
            this.normalizedSymbol;


          const currentAssetExists =
            this.assets.some(

              asset =>

                asset.symbol
                  .toUpperCase()

                ===

                currentSymbol
            );


          if (
            !currentAssetExists
          ) {

            this.orderForm
              .patchValue({

                assetSymbol:
                  this.assets[0].symbol

              });
          }


          this.loadAll();
        },


        error: (error) => {

          console.error(
            'Erreur chargement actifs',
            error
          );


          this.errorMessage =
            this.extractErrorMessage(
              error,
              'Impossible de charger la liste des actifs.'
            );


          this.loading =
            false;
        }
      });
  }


  // ==========================================
  // LOAD ALL
  // ==========================================

  loadAll(): void {

    const symbol =
      this.normalizedSymbol;


    if (!symbol) {

      this.loading =
        false;

      return;
    }


    this.loading =
      true;


    forkJoin({

      orderBook:
        this.tradingService
          .getOrderBook(
            symbol
          ),

      metrics:
        this.tradingService
          .getOrderBookMetrics(
            symbol
          ),

      temporalMetrics:
        this.tradingService
          .getTemporalMetrics(
            symbol
          ),

      orders:
        this.tradingService
          .getMyOrders(),

      trades:
        this.tradingService
          .getMyTrades()

    })
      .subscribe({

        next: ({
          orderBook,
          metrics,
          temporalMetrics,
          orders,
          trades
        }) => {

          this.orderBook =
            orderBook;

          this.metrics =
            metrics;

          this.temporalMetrics =
            temporalMetrics;

          this.myOrders =
            orders;

          this.myTrades =
            trades;

          this.loading =
            false;


          this.refreshSlippageEstimate();
        },


        error: (error) => {

          console.error(
            'Erreur chargement trading',
            error
          );


          this.errorMessage =
            this.extractErrorMessage(
              error,
              'Impossible de charger les données de trading.'
            );


          this.loading =
            false;
        }
      });
  }


  // ==========================================
  // AUTO REFRESH
  // ==========================================

  private refreshMarketData(): void {

    if (
      this.submitting
    ) {

      return;
    }


    const symbol =
      this.normalizedSymbol;


    if (!symbol) {

      return;
    }


    forkJoin({

      orderBook:
        this.tradingService
          .getOrderBook(
            symbol
          ),

      metrics:
        this.tradingService
          .getOrderBookMetrics(
            symbol
          ),

      temporalMetrics:
        this.tradingService
          .getTemporalMetrics(
            symbol
          ),

      orders:
        this.tradingService
          .getMyOrders(),

      trades:
        this.tradingService
          .getMyTrades()

    })
      .subscribe({

        next: ({
          orderBook,
          metrics,
          temporalMetrics,
          orders,
          trades
        }) => {

          this.orderBook =
            orderBook;

          this.metrics =
            metrics;

          this.temporalMetrics =
            temporalMetrics;

          this.myOrders =
            orders;

          this.myTrades =
            trades;


          this.refreshSlippageEstimate();
        },


        error: (error) => {

          console.error(
            'Erreur actualisation trading',
            error
          );
        }
      });
  }


  // ==========================================
  // CHANGE ASSET
  // ==========================================

  loadSymbol(): void {

    this.clearMessages();

    this.slippageEstimate =
      null;

    this.slippageErrorMessage =
      '';


    const symbol =
      this.normalizedSymbol;


    if (!symbol) {

      this.errorMessage =
        'Le symbole de l’actif est obligatoire.';

      return;
    }


    const exists =
      this.assets.some(

        asset =>

          asset.symbol
            .toUpperCase()

          ===

          symbol
      );


    if (!exists) {

      this.errorMessage =
        'Cet actif n’est pas disponible.';

      return;
    }


    this.orderForm
      .patchValue({

        assetSymbol:
          symbol

      });


    this.loadAll();
  }


  // ==========================================
  // MANUAL REFRESH
  // ==========================================

  refreshOrderBook(): void {

    const symbol =
      this.normalizedSymbol;


    if (!symbol) {

      return;
    }


    forkJoin({

      orderBook:
        this.tradingService
          .getOrderBook(
            symbol
          ),

      metrics:
        this.tradingService
          .getOrderBookMetrics(
            symbol
          ),

      temporalMetrics:
        this.tradingService
          .getTemporalMetrics(
            symbol
          )

    })
      .subscribe({

        next: ({
          orderBook,
          metrics,
          temporalMetrics
        }) => {

          this.orderBook =
            orderBook;

          this.metrics =
            metrics;

          this.temporalMetrics =
            temporalMetrics;


          this.refreshSlippageEstimate();
        },


        error: (error) => {

          console.error(
            'Erreur chargement carnet',
            error
          );


          this.errorMessage =
            this.extractErrorMessage(
              error,
              'Impossible de charger le carnet d’ordres.'
            );
        }
      });
  }


  // ==========================================
  // BUY / SELL
  // ==========================================

  selectSide(
    side: OrderSide
  ): void {

    this.orderForm
      .patchValue({
        side
      });


    this.refreshSlippageEstimate();
  }


  // ==========================================
  // LIMIT / MARKET
  // ==========================================

  onOrderTypeChange(): void {

    const type =
      this.orderForm
        .controls
        .type
        .value;


    const limitPriceControl =
      this.orderForm
        .controls
        .limitPrice;


    if (
      type === 'MARKET'
    ) {

      limitPriceControl
        .clearValidators();


      limitPriceControl
        .updateValueAndValidity({
          emitEvent: false
        });


      this.refreshSlippageEstimate();

      return;
    }


    limitPriceControl
      .setValidators([

        Validators.required,

        Validators.min(
          0.00000001
        )

      ]);


    limitPriceControl
      .updateValueAndValidity({
        emitEvent: false
      });


    this.slippageEstimate =
      null;

    this.slippageErrorMessage =
      '';

    this.slippageLoading =
      false;
  }


  // ==========================================
  // REFRESH SLIPPAGE
  // ==========================================

  refreshSlippageEstimate(): void {

    const requestSequence =
      ++this.slippageRequestSequence;


    this.slippageErrorMessage =
      '';


    if (
      !this.isMarketOrder
    ) {

      this.slippageEstimate =
        null;

      this.slippageLoading =
        false;

      return;
    }


    const symbol =
      this.normalizedSymbol;


    const quantity =
      Number(
        this.orderForm
          .controls
          .quantity
          .value
      );


    const side =
      this.orderForm
        .controls
        .side
        .value;


    if (
      !symbol
      ||
      !Number.isFinite(quantity)
      ||
      quantity <= 0
    ) {

      this.slippageEstimate =
        null;

      this.slippageLoading =
        false;

      return;
    }


    this.slippageLoading =
      true;


    this.tradingService
      .estimateSlippage(
        symbol,
        side,
        quantity
      )
      .subscribe({

        next: (estimate) => {

          if (
            requestSequence
            !==
            this.slippageRequestSequence
          ) {

            return;
          }


          this.slippageEstimate =
            estimate;

          this.slippageLoading =
            false;
        },


        error: (error) => {

          if (
            requestSequence
            !==
            this.slippageRequestSequence
          ) {

            return;
          }


          console.error(
            'Erreur estimation slippage',
            error
          );


          this.slippageEstimate =
            null;

          this.slippageLoading =
            false;


          this.slippageErrorMessage =
            this.extractErrorMessage(
              error,
              'Impossible d’estimer le slippage.'
            );
        }
      });
  }


  // ==========================================
  // CREATE ORDER
  // ==========================================

  submitOrder(): void {

    this.clearMessages();


    if (
      this.orderForm.invalid
    ) {

      this.orderForm
        .markAllAsTouched();


      this.errorMessage =
        'Vérifiez les informations de l’ordre.';

      return;
    }


    const formValue =
      this.orderForm
        .getRawValue();


    const symbol =
      formValue
        .assetSymbol
        .trim()
        .toUpperCase();


    const assetExists =
      this.assets.some(

        asset =>

          asset.symbol
            .toUpperCase()

          ===

          symbol
      );


    if (!assetExists) {

      this.errorMessage =
        'L’actif sélectionné n’existe pas.';

      return;
    }


    const request:
      CreateOrderRequest = {

        assetSymbol:
          symbol,

        side:
          formValue.side,

        type:
          formValue.type,

        quantity:
          Number(
            formValue.quantity
          ),

        limitPrice:
          formValue.type === 'LIMIT'

            ?

            Number(
              formValue.limitPrice
            )

            :

            null
      };


    this.submitting =
      true;


    this.tradingService
      .createOrder(
        request
      )
      .subscribe({

        next: (order) => {

          this.successMessage =
            this.buildOrderSuccessMessage(
              order
            );


          this.submitting =
            false;


          this.loadAfterAction();
        },


        error: (error) => {

          console.error(
            'Erreur création ordre',
            error
          );


          this.errorMessage =
            this.extractErrorMessage(
              error,
              'Impossible de placer l’ordre.'
            );


          this.submitting =
            false;
        }
      });
  }


  // ==========================================
  // CANCEL ORDER
  // ==========================================

  cancelOrder(
    order: OrderResponse
  ): void {

    if (
      !this.canCancel(
        order
      )
    ) {

      return;
    }


    this.clearMessages();


    this.tradingService
      .cancelOrder(
        order.id
      )
      .subscribe({

        next: () => {

          this.successMessage =
            `Ordre #${order.id} annulé avec succès.`;


          this.loadAfterAction();
        },


        error: (error) => {

          console.error(
            'Erreur annulation ordre',
            error
          );


          this.errorMessage =
            this.extractErrorMessage(
              error,
              'Impossible d’annuler cet ordre.'
            );
        }
      });
  }


  // ==========================================
  // CAN CANCEL
  // ==========================================

  canCancel(
    order: OrderResponse
  ): boolean {

    return (

      order.type === 'LIMIT'

      &&

      (
        order.status === 'PENDING'

        ||

        order.status === 'PARTIALLY_FILLED'
      )
    );
  }


  // ==========================================
  // SELECTED ASSET
  // ==========================================

  get selectedAsset():
    AssetResponse | null {

    const symbol =
      this.normalizedSymbol;


    return (

      this.assets.find(

        asset =>

          asset.symbol
            .toUpperCase()

          ===

          symbol
      )

      ??

      null
    );
  }


  // ==========================================
  // ORDER TYPE
  // ==========================================

  get selectedOrderType():
    OrderType {

    return this.orderForm
      .controls
      .type
      .value;
  }


  get isLimitOrder():
    boolean {

    return (
      this.selectedOrderType
      ===
      'LIMIT'
    );
  }


  get isMarketOrder():
    boolean {

    return (
      this.selectedOrderType
      ===
      'MARKET'
    );
  }


  // ==========================================
  // MARKET METRICS
  // ==========================================

  get bestBid():
    number | null {

    return this.metrics?.bestBid
      ?? null;
  }


  get bestAsk():
    number | null {

    return this.metrics?.bestAsk
      ?? null;
  }


  get midPrice():
    number | null {

    return this.metrics?.midPrice
      ?? null;
  }


  get spread():
    number | null {

    return this.metrics?.spread
      ?? null;
  }


  get relativeSpread():
    number | null {

    return this.metrics?.relativeSpread
      ?? null;
  }


  get relativeSpreadPercent():
    number | null {

    return this.metrics?.relativeSpreadPercent
      ?? null;
  }


  get orderBookImbalance():
    number | null {

    return this.metrics?.orderBookImbalance
      ?? null;
  }


  get totalBidVolume():
    number {

    return this.metrics?.totalBidVolume
      ?? 0;
  }


  get totalAskVolume():
    number {

    return this.metrics?.totalAskVolume
      ?? 0;
  }


  // ==========================================
  // WEIGHTED DEPTH
  // ==========================================

  get weightedBidDepth():
    number | null {

    return this.metrics?.weightedBidDepth
      ?? null;
  }


  get weightedAskDepth():
    number | null {

    return this.metrics?.weightedAskDepth
      ?? null;
  }


  // ==========================================
  // TEMPORAL METRICS
  // ==========================================

  get volatility60s():
    number | null {

    return this.temporalMetrics
      ?.volatility60s
      ?? null;
  }


  get volatility60sPercent():
    number | null {

    return this.temporalMetrics
      ?.volatility60sPercent
      ?? null;
  }


  get momentum30s():
    number | null {

    return this.temporalMetrics
      ?.momentum30s
      ?? null;
  }


  get momentum30sPercent():
    number | null {

    return this.temporalMetrics
      ?.momentum30sPercent
      ?? null;
  }


  get temporalObservationCount():
    number {

    return this.temporalMetrics
      ?.observationCount
      ?? 0;
  }


  // ==========================================
  // SLIPPAGE GETTERS
  // ==========================================

  get estimatedExecutionPrice():
    number | null {

    return this.slippageEstimate
      ?.expectedExecutionPrice
      ?? null;
  }


  get estimatedSlippagePercent():
    number | null {

    return this.slippageEstimate
      ?.slippagePercent
      ?? null;
  }


  get executableQuantity():
    number {

    return this.slippageEstimate
      ?.executableQuantity
      ?? 0;
  }


  get remainingMarketQuantity():
    number {

    return this.slippageEstimate
      ?.remainingQuantity
      ?? 0;
  }


  get marketFullyExecutable():
    boolean {

    return this.slippageEstimate
      ?.fullyExecutable
      ?? false;
  }


  get slippageLevelsConsumed():
    number {

    return this.slippageEstimate
      ?.levelsConsumed
      ?? 0;
  }


  // ==========================================
  // NORMALIZED SYMBOL
  // ==========================================

  get normalizedSymbol():
    string {

    return this.orderForm
      .controls
      .assetSymbol
      .value
      .trim()
      .toUpperCase();
  }


  // ==========================================
  // STATUS LABEL
  // ==========================================

  statusLabel(
    status: string
  ): string {

    switch (status) {

      case 'PENDING':
        return 'En attente';

      case 'PARTIALLY_FILLED':
        return 'Partiellement exécuté';

      case 'EXECUTED':
        return 'Exécuté';

      case 'CANCELLED':
        return 'Annulé';

      case 'REJECTED':
        return 'Rejeté';

      default:
        return status;
    }
  }


  // ==========================================
  // ORDER TYPE LABEL
  // ==========================================

  orderTypeLabel(
    type: OrderType
  ): string {

    switch (type) {

      case 'LIMIT':
        return 'Limite';

      case 'MARKET':
        return 'Marché';

      default:
        return type;
    }
  }


  // ==========================================
  // LOAD AFTER ACTION
  // ==========================================

  private loadAfterAction(): void {

    const successMessage =
      this.successMessage;


    const symbol =
      this.normalizedSymbol;


    if (!symbol) {

      return;
    }


    forkJoin({

      orderBook:
        this.tradingService
          .getOrderBook(
            symbol
          ),

      metrics:
        this.tradingService
          .getOrderBookMetrics(
            symbol
          ),

      temporalMetrics:
        this.tradingService
          .getTemporalMetrics(
            symbol
          ),

      orders:
        this.tradingService
          .getMyOrders(),

      trades:
        this.tradingService
          .getMyTrades()

    })
      .subscribe({

        next: ({
          orderBook,
          metrics,
          temporalMetrics,
          orders,
          trades
        }) => {

          this.orderBook =
            orderBook;

          this.metrics =
            metrics;

          this.temporalMetrics =
            temporalMetrics;

          this.myOrders =
            orders;

          this.myTrades =
            trades;

          this.successMessage =
            successMessage;


          this.refreshSlippageEstimate();
        },


        error: (error) => {

          console.error(
            'Erreur actualisation après action',
            error
          );


          this.errorMessage =
            this.extractErrorMessage(
              error,
              'L’action a été effectuée, mais les données n’ont pas pu être actualisées.'
            );
        }
      });
  }


  // ==========================================
  // SUCCESS MESSAGE
  // ==========================================

  private buildOrderSuccessMessage(
    order: OrderResponse
  ): string {


    if (
      order.type === 'MARKET'
      &&
      order.status === 'EXECUTED'
    ) {

      return (
        `Ordre MARKET #${order.id} exécuté immédiatement.`
      );
    }


    if (
      order.type === 'MARKET'
      &&
      order.status === 'CANCELLED'
    ) {

      const executedQuantity =
        Number(
          order.quantity
        )
        -
        Number(
          order.remainingQuantity
        );


      if (
        executedQuantity > 0
      ) {

        return (
          `Ordre MARKET #${order.id} partiellement exécuté. `
          +
          `${executedQuantity} exécuté(s), `
          +
          `${order.remainingQuantity} non exécuté(s) puis annulé(s).`
        );
      }


      return (
        `Ordre MARKET #${order.id} non exécuté : `
        +
        `aucune liquidité disponible dans le carnet.`
      );
    }


    if (
      order.status ===
      'EXECUTED'
    ) {

      return (
        `Ordre #${order.id} exécuté immédiatement.`
      );
    }


    if (
      order.status ===
      'PARTIALLY_FILLED'
    ) {

      return (
        `Ordre #${order.id} partiellement exécuté. `
        +
        `Quantité restante : ${order.remainingQuantity}.`
      );
    }


    if (
      order.status ===
      'PENDING'
    ) {

      return (
        `Ordre #${order.id} placé dans le carnet.`
      );
    }


    return (
      `Ordre #${order.id} créé avec le statut ${order.status}.`
    );
  }


  // ==========================================
  // CLEAR MESSAGES
  // ==========================================

  private clearMessages(): void {

    this.successMessage =
      '';

    this.errorMessage =
      '';
  }


  // ==========================================
  // EXTRACT ERROR
  // ==========================================

  private extractErrorMessage(
    error: unknown,
    fallback: string
  ): string {

    if (
      typeof error === 'object'
      &&
      error !== null
      &&
      'error' in error
    ) {

      const httpError =
        error as {

          error?: {

            message?: string;

            detail?: string;

            error?: string;
          };
        };


      return (
        httpError.error?.message
        ||
        httpError.error?.detail
        ||
        httpError.error?.error
        ||
        fallback
      );
    }


    return fallback;
  }
}