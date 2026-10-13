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
  OrderType,
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


  // ==========================================
  // ASSETS
  // ==========================================

  assets: AssetResponse[] = [];


  // ==========================================
  // ORDER BOOK
  // ==========================================

  orderBook:
    OrderBookResponse | null = null;


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

    // Configure correctement le champ prix
    // selon LIMIT / MARKET.

    this.onOrderTypeChange();


    // ========================================
    // LOAD ASSETS
    // ========================================

    this.loadAssets();


    // ========================================
    // AUTO REFRESH
    // ========================================

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


          // ==================================
          // NO ASSET
          // ==================================

          if (
            this.assets.length === 0
          ) {

            this.errorMessage =
              'Aucun actif disponible.';


            this.loading =
              false;


            return;
          }


          // ==================================
          // CURRENT SYMBOL
          // ==================================

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


          // ==================================
          // DEFAULT ASSET
          // ==================================

          /*
           * Si AAPL n'existe pas,
           * on sélectionne automatiquement
           * le premier actif disponible.
           */

          if (
            !currentAssetExists
          ) {

            this.orderForm
              .patchValue({

                assetSymbol:
                  this.assets[0].symbol

              });
          }


          // ==================================
          // LOAD TRADING
          // ==================================

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

    /*
     * Pendant la création d'un ordre,
     * on évite une actualisation parallèle.
     */

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


    // ========================================
    // ASSET VALIDATION
    // ========================================

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


    // ========================================
    // NORMALIZE SYMBOL
    // ========================================

    this.orderForm
      .patchValue({

        assetSymbol:
          symbol

      });


    // ========================================
    // RELOAD
    // ========================================

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


    // ========================================
    // MARKET
    // ========================================

    /*
     * Un MARKET n'a pas besoin
     * de prix limite.
     *
     * Le prix sera obtenu en consommant
     * les meilleurs niveaux disponibles
     * dans le carnet.
     */

    if (
      type === 'MARKET'
    ) {

      limitPriceControl
        .clearValidators();


      limitPriceControl
        .updateValueAndValidity({
          emitEvent: false
        });


      return;
    }


    // ========================================
    // LIMIT
    // ========================================

    /*
     * Un LIMIT nécessite obligatoirement
     * un prix strictement positif.
     */

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
  }


  // ==========================================
  // CREATE ORDER
  // ==========================================

  submitOrder(): void {

    this.clearMessages();


    // ========================================
    // FORM VALIDATION
    // ========================================

    if (
      this.orderForm.invalid
    ) {

      this.orderForm
        .markAllAsTouched();


      this.errorMessage =
        'Vérifiez les informations de l’ordre.';


      return;
    }


    // ========================================
    // FORM VALUES
    // ========================================

    const formValue =
      this.orderForm
        .getRawValue();


    const symbol =
      formValue
        .assetSymbol
        .trim()
        .toUpperCase();


    // ========================================
    // ASSET VALIDATION
    // ========================================

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


    // ========================================
    // CREATE REQUEST
    // ========================================

    /*
     * LIMIT :
     *
     * limitPrice = valeur saisie
     *
     *
     * MARKET :
     *
     * limitPrice = null
     */

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


    // ========================================
    // SUBMIT
    // ========================================

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

    /*
     * Seulement les LIMIT encore ouverts
     * sont normalement annulables.
     *
     * Un MARKET est exécuté ou annulé
     * immédiatement côté backend.
     */

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
  // CURRENT ORDER TYPE
  // ==========================================

  get selectedOrderType():
    OrderType {

    return this.orderForm
      .controls
      .type
      .value;
  }


  // ==========================================
  // IS LIMIT
  // ==========================================

  get isLimitOrder():
    boolean {

    return (
      this.selectedOrderType
      ===
      'LIMIT'
    );
  }


  // ==========================================
  // IS MARKET
  // ==========================================

  get isMarketOrder():
    boolean {

    return (
      this.selectedOrderType
      ===
      'MARKET'
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


  // ==========================================
  // BEST ASK
  // ==========================================

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


  // ==========================================
  // SPREAD
  // ==========================================

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


  // ==========================================
  // MID PRICE
  // ==========================================

  /*
   * On prépare déjà cette information
   * pour la future couche IA.
   *
   * MidPrice =
   *
   * (Best Bid + Best Ask) / 2
   */

  get midPrice():
    number | null {

    if (
      this.bestBid === null
      ||
      this.bestAsk === null
    ) {

      return null;
    }


    return (
      this.bestBid
      +
      this.bestAsk
    ) / 2;
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
  // REFRESH AFTER ACTION
  // ==========================================

  private loadAfterAction(): void {

    /*
     * On conserve le message de succès
     * pendant l'actualisation.
     */

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

    // ========================================
    // MARKET FULL EXECUTION
    // ========================================

    if (
      order.type === 'MARKET'
      &&
      order.status === 'EXECUTED'
    ) {

      return (
        `Ordre MARKET #${order.id} exécuté immédiatement.`
      );
    }


    // ========================================
    // MARKET PARTIAL EXECUTION
    // ========================================

    /*
     * Notre backend traite la partie restante
     * d'un MARKET comme annulée.
     *
     * Exemple :
     *
     * demandé = 10
     * exécuté = 6
     * restant = 4
     * statut = CANCELLED
     */

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


      // MARKET partiellement exécuté

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


      // MARKET sans aucune liquidité

      return (
        `Ordre MARKET #${order.id} non exécuté : `
        +
        `aucune liquidité disponible dans le carnet.`
      );
    }


    // ========================================
    // LIMIT EXECUTED
    // ========================================

    if (
      order.status ===
      'EXECUTED'
    ) {

      return (
        `Ordre #${order.id} exécuté immédiatement.`
      );
    }


    // ========================================
    // LIMIT PARTIALLY FILLED
    // ========================================

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


    // ========================================
    // LIMIT PENDING
    // ========================================

    if (
      order.status ===
      'PENDING'
    ) {

      return (
        `Ordre #${order.id} placé dans le carnet.`
      );
    }


    // ========================================
    // DEFAULT
    // ========================================

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


  // ==========================================
  // ERROR MESSAGE
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