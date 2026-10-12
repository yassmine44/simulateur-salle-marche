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
  OrderBookResponse,
  OrderResponse,
  OrderSide,
  TradeResponse
} from '../../models/trading.models';

import { TradingService }
  from '../../services/trading.service';


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
  // STATE
  // ==========================================

  loading = false;

  submitting = false;

  successMessage = '';

  errorMessage = '';


  // Actifs chargés depuis PostgreSQL
  // via GET /api/assets

  assets: AssetResponse[] = [];


  orderBook:
    OrderBookResponse | null = null;


  myOrders:
    OrderResponse[] = [];


  myTrades:
    TradeResponse[] = [];


  private refreshSubscription?:
    Subscription;


  // ==========================================
  // FORM
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
        'LIMIT' as const,
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

    // On charge d'abord les actifs
    // depuis le backend.

    this.loadAssets();


    // Actualisation automatique du carnet,
    // des ordres et des transactions.

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

    this.loading = true;

    this.clearMessages();


    this.tradingService
      .getAssets()
      .subscribe({

        next: (assets) => {

          this.assets =
            assets;


          // Aucun actif disponible

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


          // Si AAPL n'existe pas en BDD,
          // on sélectionne le premier actif.

          if (
            !currentAssetExists
          ) {

            this.orderForm
              .patchValue({

                assetSymbol:
                  this.assets[0].symbol

              });
          }


          // Une fois les actifs chargés,
          // on charge le carnet + ordres + trades.

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
  // LOAD ALL TRADING DATA
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
          orders,
          trades
        }) => {

          this.orderBook =
            orderBook;

          this.myOrders =
            orders;

          this.myTrades =
            trades;

          this.loading =
            false;
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
          orders,
          trades
        }) => {

          this.orderBook =
            orderBook;

          this.myOrders =
            orders;

          this.myTrades =
            trades;
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


    // Recharge toutes les données
    // lors du changement d'actif.

    this.loadAll();
  }


  // ==========================================
  // REFRESH ORDER BOOK
  // ==========================================

  refreshOrderBook(): void {

    const symbol =
      this.normalizedSymbol;


    if (!symbol) {
      return;
    }


    this.tradingService
      .getOrderBook(
        symbol
      )
      .subscribe({

        next: (book) => {

          this.orderBook =
            book;
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


    // Vérification frontend supplémentaire.

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
          'LIMIT',

        quantity:
          Number(
            formValue.quantity
          ),

        limitPrice:
          Number(
            formValue.limitPrice
          )
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


  canCancel(
    order: OrderResponse
  ): boolean {

    return (
      order.status === 'PENDING'
      ||
      order.status === 'PARTIALLY_FILLED'
    );
  }


  // ==========================================
  // CURRENT ASSET
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
  // MARKET INFORMATION
  // ==========================================

  get bestBid():
    number | null {

    if (
      !this.orderBook
      ||
      this.orderBook
        .bids
        .length === 0
    ) {

      return null;
    }


    return this.orderBook
      .bids[0]
      .price;
  }


  get bestAsk():
    number | null {

    if (
      !this.orderBook
      ||
      this.orderBook
        .asks
        .length === 0
    ) {

      return null;
    }


    return this.orderBook
      .asks[0]
      .price;
  }


  get spread():
    number | null {

    if (
      this.bestBid === null
      ||
      this.bestAsk === null
    ) {

      return null;
    }


    return (
      this.bestAsk
      -
      this.bestBid
    );
  }


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
  // DISPLAY HELPERS
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
  // REFRESH AFTER ACTION
  // ==========================================

  private loadAfterAction(): void {

    const successMessage =
      this.successMessage;


    const symbol =
      this.normalizedSymbol;


    forkJoin({

      orderBook:
        this.tradingService
          .getOrderBook(
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
          orders,
          trades
        }) => {

          this.orderBook =
            orderBook;

          this.myOrders =
            orders;

          this.myTrades =
            trades;


          this.successMessage =
            successMessage;
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
  // MESSAGES
  // ==========================================

  private clearMessages(): void {

    this.successMessage =
      '';

    this.errorMessage =
      '';
  }


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