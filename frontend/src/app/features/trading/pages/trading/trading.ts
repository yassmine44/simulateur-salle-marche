import {
  Component,
  inject,
  OnDestroy,
  OnInit
} from '@angular/core';

import { CommonModule } from '@angular/common';

import { HttpErrorResponse } from '@angular/common/http';

import {
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';

import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  finalize,
  forkJoin,
  interval,
  map,
  Observable,
  of,
  Subscription,
  switchMap
} from 'rxjs';

import {
  AssetResponse,
  CandleInterval,
  CreateOrderRequest,
  MarketOverviewResponse,
  OrderBookLevel,
  OrderBookResponse,
  OrderEntryType,
  OrderPreviewResponse,
  OrderResponse,
  OrderSide,
  TimeInForce,
  TradeResponse
} from '../../models/trading.models';

import { TradingService }
  from '../../services/trading.service';

import { ActivatedRoute } from '@angular/router';

import { BuyingPowerResponse }
  from '../../../portfolio/models/portfolio.models';

import { PortfolioService }
  from '../../../portfolio/services/portfolio.service';

import {
  allowedTimeInForce,
  buildOrderRequest,
  cancelReasonLabel,
  linkLabel,
  orderTypeLabel,
  OrderTicketValue,
  TIME_IN_FORCE_LABELS,
  TIME_IN_FORCE_SHORT
} from './order-ticket';

import { CandleChart }
  from '../../components/candle-chart/candle-chart';


interface MarketData {

  orderBook: OrderBookResponse;

  market: MarketOverviewResponse;

  buyingPower: BuyingPowerResponse;

  orders: OrderResponse[];

  trades: TradeResponse[];
}


// Message affiché à côté de l'action qui l'a produit.

interface Feedback {

  kind: 'success' | 'error';

  title: string;

  message: string;
}


// Niveau du carnet enrichi pour l'affichage de la profondeur.

interface BookLevelView
  extends OrderBookLevel {

  // Quantité cumulée depuis le meilleur prix.
  cumulativeQuantity: number;

  // Part de la profondeur maximale affichée, en %.
  depthPercent: number;
}


@Component({
  selector: 'app-trading',

  standalone: true,

  imports: [
    CommonModule,
    ReactiveFormsModule,
    CandleChart
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

  private readonly portfolioService =
    inject(PortfolioService);

  private readonly route =
    inject(ActivatedRoute);


  // ==========================================
  // CONFIGURATION
  // ==========================================

  readonly refreshIntervalMs =
    3000;

  // Quand le flux temps réel est actif,
  // l'actualisation périodique ne sert plus
  // que de filet de sécurité.

  private readonly liveSafetyTicks =
    5;


  // ==========================================
  // STATE
  // ==========================================

  // Chargement initial ou changement d'actif.

  loading = false;

  submitting = false;

  // Actualisation manuelle du carnet.

  refreshing = false;

  // Flux temps réel (SSE) connecté.

  liveConnected = false;

  // L'actualisation automatique échoue.

  connectionLost = false;

  // Messages généraux (chargement, connexion) :
  // affichés en haut de la page.

  successMessage = '';

  errorMessage = '';

  // Résultat du dernier ordre saisi :
  // affiché dans le formulaire, sous le bouton.

  orderFeedback:
    Feedback | null = null;

  // Résultat d'une modification ou d'une annulation :
  // affiché dans le panneau « Mes ordres ».

  ordersFeedback:
    Feedback | null = null;


  // Actifs chargés depuis PostgreSQL
  // via GET /api/assets

  assets: AssetResponse[] = [];


  orderBook:
    OrderBookResponse | null = null;


  // Carnet avec quantités cumulées (profondeur).

  bidLevels:
    BookLevelView[] = [];

  askLevels:
    BookLevelView[] = [];


  // Dernier cours, flux des transactions, chandeliers.

  market:
    MarketOverviewResponse | null = null;


  // Liquidités et titres disponibles pour l'actif affiché
  // (contrôle avant ordre du portefeuille).

  buyingPower:
    BuyingPowerResponse | null = null;

  candleInterval:
    CandleInterval = '1m';

  readonly candleIntervals:
    CandleInterval[] = ['1m', '5m', '15m'];


  // Ordre en cours de modification.

  editingOrderId:
    number | null = null;

  savingEdit = false;


  myOrders:
    OrderResponse[] = [];


  myTrades:
    TradeResponse[] = [];


  private readonly cancellingOrderIds =
    new Set<number>();


  private refreshSubscription?:
    Subscription;

  private streamSubscription?:
    Subscription;

  private typeSubscription?:
    Subscription;

  private tickCount = 0;

  private refreshInFlight = false;

  private refreshQueued = false;


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
        'LIMIT' as OrderEntryType,
        Validators.required
      ],

      quantity: [
        1,
        [
          Validators.required,
          Validators.min(0.00000001)
        ]
      ],

      // Requis pour un ordre limite uniquement.

      limitPrice: [
        null as number | null,
        this.priceValidators
      ],

      // Requis pour un stop-loss / take-profit uniquement.

      triggerPrice: [
        null as number | null
      ],

      // Stop suiveur : distance en % entre le cours et le seuil.

      trailPercent: [
        null as number | null
      ],

      timeInForce: [
        'GTC' as TimeInForce
      ],

      // Bracket : protections attachées à un achat.

      protect: [
        false
      ],

      stopLossPrice: [
        null as number | null
      ],

      takeProfitPrice: [
        null as number | null
      ]
    });


  // ==========================================
  // PREVIEW
  // ==========================================

  // Aperçu de l'ordre en cours de saisie (rien n'est envoyé).

  preview:
    OrderPreviewResponse | null = null;

  previewLoading = false;

  private previewSubscription?:
    Subscription;

  readonly timeInForceLabels =
    TIME_IN_FORCE_LABELS;

  readonly timeInForceShort =
    TIME_IN_FORCE_SHORT;


  // Modification d'un ordre limite ouvert.

  readonly editForm =
    this.fb.nonNullable.group({

      quantity: [
        0,
        [
          Validators.required,
          Validators.min(0.00000001)
        ]
      ],

      limitPrice: [
        0,
        this.priceValidators
      ]
    });


  private get priceValidators() {

    return [
      Validators.required,
      Validators.min(0.00000001)
    ];
  }


  // ==========================================
  // LIFECYCLE
  // ==========================================

  ngOnInit(): void {

    // On charge d'abord les actifs
    // depuis le backend.

    this.loadAssets();


    // Les champs requis dépendent du type d'ordre.

    this.typeSubscription =
      this.orderForm
        .controls
        .type
        .valueChanges
        .subscribe(() => {

          this.applyTypeValidators();

        });


    // Aperçu recalculé pendant la saisie, avec un léger délai
    // pour ne pas solliciter le serveur à chaque frappe.

    this.previewSubscription =
      this.orderForm
        .valueChanges
        .pipe(
          debounceTime(350),
          map(() => this.orderForm.getRawValue()),
          distinctUntilChanged(
            (previous, current) =>
              JSON.stringify(previous) === JSON.stringify(current)
          ),
          switchMap(value => this.requestPreview(value))
        )
        .subscribe(preview => {

          this.preview = preview;

          this.previewLoading = false;
        });


    // Actualisation automatique du carnet,
    // des ordres et des transactions.

    this.refreshSubscription =
      interval(
        this.refreshIntervalMs
      )
        .subscribe(() => {

          this.tickCount++;

          if (
            this.liveConnected
            &&
            this.tickCount % this.liveSafetyTicks !== 0
          ) {
            return;
          }

          this.refreshMarketData();

        });
  }


  ngOnDestroy(): void {

    this.refreshSubscription
      ?.unsubscribe();

    this.streamSubscription
      ?.unsubscribe();

    this.typeSubscription
      ?.unsubscribe();

    this.previewSubscription
      ?.unsubscribe();
  }


  // ==========================================
  // LOAD ASSETS
  // ==========================================

  loadAssets(): void {

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


          // Actif demandé dans l'adresse (?symbol=AAPL),
          // par exemple depuis le portefeuille.

          const requestedSymbol =
            this.route
              .snapshot
              .queryParamMap
              .get('symbol')
              ?.trim()
              .toUpperCase();

          if (
            requestedSymbol
            &&
            this.assets.some(
              asset =>
                asset.symbol.toUpperCase() === requestedSymbol
            )
          ) {

            this.orderForm
              .patchValue({
                assetSymbol: requestedSymbol
              });
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


    this.connectStream(
      symbol
    );


    this.fetchMarketData(
      symbol
    )
      .subscribe({

        next: (data) => {

          // Réponse d'un actif qui n'est plus sélectionné.

          if (
            symbol !== this.normalizedSymbol
          ) {
            return;
          }

          this.applyMarketData(
            data
          );

          this.prefillLimitPrice();

          this.loading =
            false;
        },

        error: (error) => {

          if (
            symbol !== this.normalizedSymbol
          ) {
            return;
          }

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


  private fetchMarketData(
    symbol: string
  ): Observable<MarketData> {

    return forkJoin({

      orderBook:
        this.tradingService
          .getOrderBook(
            symbol
          ),

      market:
        this.tradingService
          .getMarketOverview(
            symbol,
            this.candleInterval
          ),

      buyingPower:
        this.portfolioService
          .getBuyingPower(
            symbol
          ),

      orders:
        this.tradingService
          .getMyOrders(),

      trades:
        this.tradingService
          .getMyTrades()

    });
  }


  private applyMarketData(
    data: MarketData
  ): void {

    this.orderBook =
      data.orderBook;

    this.market =
      data.market;

    this.buyingPower =
      data.buyingPower;

    this.myOrders =
      data.orders;

    this.myTrades =
      data.trades;

    this.connectionLost =
      false;


    this.buildDepth();


    // L'ordre en cours de modification a pu
    // être exécuté ou annulé entre-temps.

    const edited =
      this.myOrders.find(
        order =>
          order.id === this.editingOrderId
      );

    if (
      !edited
      ||
      !this.canModify(edited)
    ) {

      this.editingOrderId =
        null;
    }
  }


  // ==========================================
  // MARKET DEPTH
  // ==========================================

  // Quantités cumulées depuis le meilleur prix,
  // rapportées à la plus grande profondeur des deux côtés
  // pour que BID et ASK soient comparables.

  private buildDepth(): void {

    const cumulate =
      (levels: OrderBookLevel[]) => {

        let cumulativeQuantity = 0;

        return levels.map(
          level => {

            cumulativeQuantity +=
              level.totalQuantity;

            return {
              ...level,
              cumulativeQuantity,
              depthPercent: 0
            };
          }
        );
      };


    const bids =
      cumulate(
        this.orderBook?.bids ?? []
      );

    const asks =
      cumulate(
        this.orderBook?.asks ?? []
      );


    const deepest =
      Math.max(
        bids.at(-1)?.cumulativeQuantity ?? 0,
        asks.at(-1)?.cumulativeQuantity ?? 0
      );


    for (const level of [...bids, ...asks]) {

      level.depthPercent =
        deepest > 0
          ? level.cumulativeQuantity / deepest * 100
          : 0;
    }


    this.bidLevels =
      bids;

    this.askLevels =
      asks;
  }


  // ==========================================
  // CANDLE INTERVAL
  // ==========================================

  selectCandleInterval(
    interval: CandleInterval
  ): void {

    if (
      interval === this.candleInterval
    ) {
      return;
    }

    this.candleInterval =
      interval;

    this.refreshMarketData();
  }


  // ==========================================
  // AUTO REFRESH
  // ==========================================

  /*
   * Actualisation silencieuse (polling, temps réel,
   * après une action). Une seule requête à la fois :
   * une demande reçue pendant une actualisation
   * est rejouée dès que celle-ci se termine.
   */

  private refreshMarketData(
    errorFallback?: string
  ): void {

    const symbol =
      this.normalizedSymbol;


    if (
      !symbol
      ||
      this.loading
    ) {
      return;
    }


    if (
      this.refreshInFlight
    ) {

      this.refreshQueued =
        true;

      return;
    }


    this.refreshInFlight =
      true;


    this.fetchMarketData(
      symbol
    )
      .pipe(
        finalize(() => {

          this.refreshInFlight =
            false;

          this.refreshing =
            false;

          if (
            this.refreshQueued
          ) {

            this.refreshQueued =
              false;

            this.refreshMarketData();
          }
        })
      )
      .subscribe({

        next: (data) => {

          if (
            symbol === this.normalizedSymbol
          ) {

            this.applyMarketData(
              data
            );
          }
        },

        error: (error) => {

          console.error(
            'Erreur actualisation trading',
            error
          );


          if (
            errorFallback
          ) {

            this.errorMessage =
              this.extractErrorMessage(
                error,
                errorFallback
              );

            return;
          }


          // Pas de message bloquant pour une
          // actualisation automatique : simple bandeau.

          this.connectionLost =
            true;
        }
      });
  }


  // ==========================================
  // REAL TIME (SSE)
  // ==========================================

  private connectStream(
    symbol: string
  ): void {

    this.streamSubscription
      ?.unsubscribe();

    this.liveConnected =
      false;


    this.streamSubscription =
      this.tradingService
        .watchOrderBook(
          symbol
        )
        .subscribe((event) => {

          if (
            event === 'disconnected'
          ) {

            // Le navigateur se reconnecte tout seul ;
            // en attendant, le polling reprend.

            this.liveConnected =
              false;

            return;
          }


          this.liveConnected =
            true;

          // 'connected' : rattrape ce qui a pu
          // changer pendant une coupure.

          this.refreshMarketData();
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


    // Les données de l'actif précédent
    // ne doivent pas rester affichées.

    this.orderBook =
      null;

    this.market =
      null;

    this.buyingPower =
      null;

    this.buildDepth();


    // Les prix saisis ne valent que pour l'actif précédent.

    this.orderForm
      .controls
      .limitPrice
      .reset(null);

    this.orderForm
      .controls
      .triggerPrice
      .reset(null);


    // Recharge toutes les données
    // lors du changement d'actif.

    this.loadAll();
  }


  // ==========================================
  // REFRESH ORDER BOOK
  // ==========================================

  refreshOrderBook(): void {

    if (
      !this.normalizedSymbol
      ||
      this.loading
      ||
      this.refreshing
    ) {
      return;
    }


    this.errorMessage =
      '';

    this.refreshing =
      true;


    this.refreshMarketData(
      'Impossible de charger le carnet d’ordres.'
    );
  }


  // ==========================================
  // BUY / SELL
  // ==========================================

  selectSide(
    side: OrderSide
  ): void {

    // Un OCO protège des titres détenus : c'est toujours une vente.

    if (
      side === 'BUY'
      &&
      this.entryType === 'OCO'
    ) {

      this.orderForm
        .controls
        .type
        .setValue('LIMIT');
    }

    this.orderForm
      .patchValue({
        side
      });
  }


  // ==========================================
  // CREATE ORDER
  // ==========================================

  submitOrder(): void {

    if (
      this.submitting
    ) {
      return;
    }


    this.clearMessages();


    if (
      this.orderForm.invalid
    ) {

      this.orderForm
        .markAllAsTouched();


      this.orderFeedback = {

        kind: 'error',

        title: 'Ordre incomplet',

        message:
          'Corrigez les champs signalés en rouge ci-dessus.'
      };


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

      this.orderFeedback = {

        kind: 'error',

        title: 'Ordre refusé',

        message:
          'L’actif sélectionné n’existe pas ou n’est plus disponible.'
      };

      return;
    }


    const ticket =
      buildOrderRequest(
        formValue
      );

    if (ticket.kind === 'invalid') {

      this.orderFeedback = {

        kind: 'error',

        title: 'Ordre incomplet',

        message: ticket.reason
      };

      return;
    }


    this.submitting =
      true;


    if (ticket.kind === 'oco') {

      const oco =
        ticket.request;

      this.tradingService
        .createOco(
          oco
        )
        .pipe(
          finalize(() => {

            this.submitting = false;
          })
        )
        .subscribe({

          next: () => {

            this.orderFeedback = {

              kind: 'success',

              title: 'Protection OCO en place',

              message:
                `${oco.quantity} ${oco.assetSymbol} protégés : `
                +
                `vente à ${oco.takeProfitPrice} si le cours monte, `
                +
                `vente au marché sous ${oco.stopLossPrice} s’il baisse. `
                +
                'La première exécutée annule l’autre.'
            };

            this.loadAfterAction();
          },

          error: (error) =>
            this.onOrderFailed(error)
        });

      return;
    }


    this.tradingService
      .createOrder(
        ticket.request
      )
      .pipe(
        finalize(() => {

          this.submitting = false;
        })
      )
      .subscribe({

        next: (order) =>
          this.onOrderPlaced(order),

        error: (error) =>
          this.onOrderFailed(error)
      });
  }


  private onOrderPlaced(
    order: OrderResponse
  ): void {

    // Ordre au marché sans aucune contrepartie.

    if (
      order.status === 'REJECTED'
    ) {

      const counterpart =
        order.side === 'BUY'
          ? 'vendeur'
          : 'acheteur';

      this.orderFeedback = {

        kind: 'error',

        title: `Ordre #${order.id} non exécuté`,

        message:
          `Un ordre au marché s’exécute tout de suite ou pas du tout, `
          +
          `et aucun ${counterpart} n’est présent dans le carnet `
          +
          `à un prix proche du dernier cours. `
          +
          `Utilisez un ordre limite : il restera dans le carnet `
          +
          `en attendant un ${counterpart}.`
      };

    } else if (
      order.status === 'CANCELLED'
      &&
      order.cancelReason === 'FOK'
    ) {

      this.orderFeedback = {

        kind: 'error',

        title: `Ordre #${order.id} annulé`,

        message:
          'Ordre « tout ou rien » : la quantité demandée n’était pas disponible '
          +
          'en totalité à ce prix, rien n’a donc été exécuté.'
      };

    } else {

      this.orderFeedback = {

        kind: 'success',

        title: 'Ordre accepté',

        message:
          this.buildOrderSuccessMessage(
            order
          )
      };
    }

    this.loadAfterAction();
  }


  private onOrderFailed(
    error: unknown
  ): void {

    console.error(
      'Erreur création ordre',
      error
    );

    this.orderFeedback = {

      kind: 'error',

      title: 'Ordre refusé',

      message:
        this.extractErrorMessage(
          error,
          'Impossible de placer l’ordre.'
        )
    };
  }


  // ==========================================
  // PREVIEW
  // ==========================================

  private requestPreview(
    value: OrderTicketValue
  ): Observable<OrderPreviewResponse | null> {

    const ticket =
      buildOrderRequest(
        value
      );

    // Pas d'aperçu tant que l'ordre est incomplet,
    // ni pour un OCO (deux ordres liés).

    if (
      ticket.kind !== 'order'
      ||
      this.orderForm.invalid
      ||
      !(Number(value.quantity) > 0)
    ) {

      return of(null);
    }

    this.previewLoading = true;

    return this.tradingService
      .previewOrder(
        ticket.request
      )
      .pipe(
        catchError(() => of(null))
      );
  }


  // Relance l'aperçu, par exemple après une exécution.

  private refreshPreview(): void {

    this.requestPreview(
      this.orderForm.getRawValue()
    )
      .subscribe(preview => {

        this.preview = preview;

        this.previewLoading = false;
      });
  }


  // ==========================================
  // ORDER TYPE
  // ==========================================

  get entryType():
    OrderEntryType {

    return this.orderForm
      .controls
      .type
      .value;
  }


  // Stop-loss / take-profit à seuil fixe.

  get isConditionalEntry():
    boolean {

    return (
      this.entryType === 'STOP_LOSS'
      ||
      this.entryType === 'TAKE_PROFIT'
    );
  }


  get isTrailingEntry():
    boolean {

    return this.entryType === 'TRAILING_STOP';
  }


  get isOcoEntry():
    boolean {

    return this.entryType === 'OCO';
  }


  // Protections possibles : achat limite ou au marché.

  get canProtect():
    boolean {

    return (
      this.orderForm.controls.side.value === 'BUY'
      &&
      (this.entryType === 'LIMIT' || this.entryType === 'MARKET')
    );
  }


  get allowedTimeInForce():
    TimeInForce[] {

    return allowedTimeInForce(
      this.entryType
    );
  }


  private applyTypeValidators(): void {

    const {
      limitPrice,
      triggerPrice,
      trailPercent,
      timeInForce,
      takeProfitPrice,
      stopLossPrice,
      side
    } = this.orderForm.controls;

    const type = this.entryType;


    limitPrice.setValidators(
      type === 'LIMIT'
        ? this.priceValidators
        : null
    );

    triggerPrice.setValidators(
      this.isConditionalEntry
        ? this.priceValidators
        : null
    );

    trailPercent.setValidators(
      type === 'TRAILING_STOP'
        ? [
            Validators.required,
            Validators.min(0.1),
            Validators.max(50)
          ]
        : null
    );

    // OCO : les deux prix sont obligatoires ;
    // bracket : facultatifs mais positifs.

    const protectionValidators =
      type === 'OCO'
        ? this.priceValidators
        : [Validators.min(0.00000001)];

    takeProfitPrice.setValidators(protectionValidators);

    stopLossPrice.setValidators(protectionValidators);


    // Durée de validité compatible avec le type choisi.

    const allowed =
      allowedTimeInForce(type);

    if (!allowed.includes(timeInForce.value)) {

      timeInForce.setValue(
        allowed[0],
        { emitEvent: false }
      );
    }

    if (type === 'OCO') {

      side.setValue(
        'SELL',
        { emitEvent: false }
      );
    }


    for (const control of [
      limitPrice,
      triggerPrice,
      trailPercent,
      takeProfitPrice,
      stopLossPrice
    ]) {

      control.updateValueAndValidity({ emitEvent: false });
    }

    this.orderForm.updateValueAndValidity();


    if (
      this.entryType === 'LIMIT'
    ) {

      this.prefillLimitPrice();
    }
  }


  // Propose le dernier cours comme prix limite
  // tant que l'utilisateur n'a rien saisi.

  private prefillLimitPrice(): void {

    const control =
      this.orderForm
        .controls
        .limitPrice;

    if (
      control.value === null
      &&
      this.lastPrice !== null
    ) {

      control.setValue(
        this.lastPrice
      );
    }
  }


  // Explique quand l'ordre conditionnel se déclenchera.

  get triggerHint():
    string {

    const selling =
      this.orderForm.controls.side.value === 'SELL';

    if (this.isTrailingEntry) {

      return (
        'Le seuil suit le cours à cette distance et ne recule jamais : '
        +
        (
          selling
            ? 'il monte quand le cours monte, et l’ordre vend au marché si le cours redescend jusqu’à lui.'
            : 'il baisse quand le cours baisse, et l’ordre achète au marché si le cours remonte jusqu’à lui.'
        )
      );
    }

    const fallingPrice =
      (this.entryType === 'STOP_LOSS') === selling;

    return (
      (selling ? 'Vente' : 'Achat')
      +
      ' au marché dès que le cours '
      +
      (
        fallingPrice
          ? 'descend à ce prix ou en dessous.'
          : 'monte à ce prix ou au-dessus.'
      )
    );
  }


  // ==========================================
  // CLICK ON A BOOK LEVEL
  // ==========================================

  // Cliquer un prix de vente prépare un achat à ce prix,
  // cliquer un prix d'achat prépare une vente.

  pickBookPrice(
    bookSide: 'BID' | 'ASK',
    price: number
  ): void {

    this.orderForm
      .patchValue({

        side:
          bookSide === 'ASK'
            ? 'BUY'
            : 'SELL',

        type:
          'LIMIT',

        limitPrice:
          price

      });
  }


  // ==========================================
  // MODIFY ORDER
  // ==========================================

  canModify(
    order: OrderResponse
  ): boolean {

    return (
      order.type === 'LIMIT'
      &&
      this.canCancel(order)
    );
  }


  startEdit(
    order: OrderResponse
  ): void {

    if (
      !this.canModify(order)
      ||
      order.limitPrice === null
    ) {
      return;
    }

    this.clearMessages();

    this.editingOrderId =
      order.id;

    this.editForm
      .setValue({

        quantity:
          order.remainingQuantity,

        limitPrice:
          order.limitPrice

      });
  }


  cancelEdit(): void {

    this.editingOrderId =
      null;
  }


  saveEdit(
    order: OrderResponse
  ): void {

    if (
      this.savingEdit
      ||
      this.editForm.invalid
    ) {
      return;
    }


    this.clearMessages();

    this.savingEdit =
      true;


    const value =
      this.editForm
        .getRawValue();


    this.tradingService
      .modifyOrder(
        order.id,
        {
          quantity:
            Number(value.quantity),

          limitPrice:
            Number(value.limitPrice)
        }
      )
      .pipe(
        finalize(() => {

          this.savingEdit =
            false;
        })
      )
      .subscribe({

        next: (replacement) => {

          this.editingOrderId =
            null;

          this.ordersFeedback = {

            kind: 'success',

            title: 'Ordre modifié',

            message:
              `L’ordre #${order.id} est remplacé par l’ordre #${replacement.id}. `
              +
              this.buildOrderSuccessMessage(
                replacement
              )
          };

          this.loadAfterAction();
        },

        error: (error) => {

          console.error(
            'Erreur modification ordre',
            error
          );

          this.ordersFeedback = {

            kind: 'error',

            title: `Modification de l’ordre #${order.id} refusée`,

            message:
              this.extractErrorMessage(

                error,

                'Impossible de modifier cet ordre.'
              )
              +
              ' L’ordre d’origine est inchangé.'
          };

          this.refreshMarketData();
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
      ||
      this.isCancelling(
        order
      )
    ) {
      return;
    }


    this.clearMessages();


    this.cancellingOrderIds
      .add(
        order.id
      );


    this.tradingService
      .cancelOrder(
        order.id
      )
      .pipe(
        finalize(() => {

          this.cancellingOrderIds
            .delete(
              order.id
            );
        })
      )
      .subscribe({

        next: () => {

          this.ordersFeedback = {

            kind: 'success',

            title: 'Ordre annulé',

            message:
              `L’ordre #${order.id} est retiré du carnet.`
          };


          this.loadAfterAction();
        },

        error: (error) => {

          console.error(
            'Erreur annulation ordre',
            error
          );


          this.ordersFeedback = {

            kind: 'error',

            title: `Annulation de l’ordre #${order.id} refusée`,

            message:
              this.extractErrorMessage(

                error,

                'Impossible d’annuler cet ordre.'
              )
          };


          // L'ordre a pu être exécuté entre-temps :
          // on réaffiche son état réel.

          this.refreshMarketData();
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


  isCancelling(
    order: OrderResponse
  ): boolean {

    return this.cancellingOrderIds
      .has(
        order.id
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


  get currency():
    string {

    return (
      this.selectedAsset?.currency
      ??
      'USD'
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


  get lastPrice():
    number | null {

    return (
      this.market
        ?.summary
        .lastPrice
      ??
      null
    );
  }


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
      (
        this.bestBid
        +
        this.bestAsk
      )
      / 2
    );
  }


  // Valeur estimée de l'ordre en cours de saisie :
  // prix limite, seuil de déclenchement ou dernier cours.

  // ==========================================
  // BUYING POWER
  // ==========================================

  // Quantité maximale : liquidités disponibles / prix (achat),
  // titres disponibles (vente), arrondie à la quantité minimale.

  get maxQuantity():
    number | null {

    const power =
      this.buyingPower;

    if (!power) {
      return null;
    }

    const step =
      this.selectedAsset?.minQuantity
      ||
      1;

    if (
      this.orderForm.controls.side.value === 'SELL'
    ) {

      return power.availableQuantity > 0
        ? this.roundDown(power.availableQuantity, step)
        : 0;
    }

    const {
      limitPrice,
      triggerPrice
    } = this.orderForm.getRawValue();

    // Au marché : estimé au meilleur prix de vente.

    const price =
      this.entryType === 'LIMIT'
        ? limitPrice
        : this.isConditionalEntry
          ? triggerPrice
          : this.bestAsk ?? this.lastPrice;

    if (
      !price
      ||
      price <= 0
      ||
      power.availableCash <= 0
    ) {
      return 0;
    }

    return this.roundDown(
      power.availableCash / price,
      step
    );
  }


  useMaxQuantity(): void {

    const max =
      this.maxQuantity;

    if (
      max !== null
      &&
      max > 0
    ) {

      this.orderForm
        .controls
        .quantity
        .setValue(max);
    }
  }


  private roundDown(
    value: number,
    step: number
  ): number {

    // Arrondi au multiple inférieur, sans erreur de virgule flottante.

    const decimals =
      (String(step).split('.')[1] ?? '').length;

    return Number(
      (Math.floor(value / step + 1e-9) * step).toFixed(decimals)
    );
  }


  get estimatedValue():
    number | null {

    const {
      quantity,
      limitPrice,
      triggerPrice
    } = this.orderForm.getRawValue();


    const price =
      this.entryType === 'LIMIT'
        ? limitPrice
        : this.isConditionalEntry
          ? triggerPrice
          : this.lastPrice;


    // Après un aperçu : prix moyen réellement estimé sur le carnet.

    if (
      this.preview?.averagePrice
      &&
      this.entryType === 'MARKET'
    ) {

      return quantity * this.preview.averagePrice;
    }


    return (
      price !== null
      &&
      quantity > 0
        ? quantity * price
        : null
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

  isWaitingTrigger(
    order: OrderResponse
  ): boolean {

    return (
      order.triggerType !== null
      &&
      order.triggeredAt === null
      &&
      order.status === 'PENDING'
    );
  }


  orderTypeLabel(
    order: OrderResponse
  ): string {

    return orderTypeLabel(order);
  }


  linkLabel(
    order: OrderResponse
  ): string | null {

    return linkLabel(order);
  }


  cancelReasonLabel(
    order: OrderResponse
  ): string | null {

    return order.status === 'CANCELLED' || order.status === 'REJECTED'
      ? cancelReasonLabel(order.cancelReason)
      : null;
  }


  trackById(
    _: number,
    item: { id: number }
  ): number {

    return item.id;
  }


  statusLabel(
    order: OrderResponse
  ): string {

    if (
      this.isWaitingTrigger(order)
    ) {
      return 'En attente de déclenchement';
    }


    switch (order.status) {

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

        return order.status;
    }
  }


  // ==========================================
  // REFRESH AFTER ACTION
  // ==========================================

  private loadAfterAction(): void {

    this.refreshPreview();

    this.refreshMarketData(
      'L’action a été effectuée, mais les données n’ont pas pu être actualisées.'
    );
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
      this.isWaitingTrigger(order)
    ) {

      return (
        `Ordre ${this.orderTypeLabel(order).toLowerCase()} #${order.id} enregistré. `
        +
        `Déclenchement à ${order.triggerPrice}.`
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


    // Ordre au marché : le reste non exécuté est annulé.

    if (
      order.status ===
      'CANCELLED'
    ) {

      return (
        `Ordre #${order.id} exécuté pour `
        +
        `${+(order.quantity - order.remainingQuantity).toFixed(8)} sur ${order.quantity} : `
        +
        'le reste est annulé faute de contrepartie.'
      );
    }


    return (
      `Ordre #${order.id} créé avec le statut ${order.status}.`
    );
  }


  // ==========================================
  // MESSAGES
  // ==========================================

  dismissSuccess(): void {

    this.successMessage =
      '';
  }


  dismissError(): void {

    this.errorMessage =
      '';
  }


  private clearMessages(): void {

    this.successMessage =
      '';

    this.errorMessage =
      '';

    this.orderFeedback =
      null;

    this.ordersFeedback =
      null;
  }


  private extractErrorMessage(
    error: unknown,
    fallback: string
  ): string {

    if (
      !(error instanceof HttpErrorResponse)
    ) {
      return fallback;
    }


    // Aucune réponse : backend arrêté, réseau coupé
    // ou redirection vers la page de connexion.

    if (
      error.status === 0
    ) {

      return (
        'Serveur injoignable ou session expirée. '
        +
        'Vérifiez votre connexion puis réessayez.'
      );
    }


    const body =
      error.error as {

        detail?: string;

        message?: string;

        errors?: Record<string, string>;

      } | null;


    // Erreurs de validation champ par champ.

    const fieldErrors =
      Object.values(
        body?.errors ?? {}
      );

    if (
      fieldErrors.length > 0
    ) {

      return fieldErrors
        .join(' ');
    }


    if (
      body?.detail
      ||
      body?.message
    ) {

      return (
        body.detail
        ||
        body.message
        ||
        fallback
      );
    }


    switch (error.status) {

      case 401:

        return 'Votre session a expiré. Veuillez vous reconnecter.';


      case 403:

        return 'Action non autorisée. Rechargez la page puis réessayez.';


      case 429:

        return 'Trop de requêtes. Patientez quelques instants.';


      default:

        return (
          error.status >= 500
            ? 'Erreur du serveur. Réessayez dans quelques instants.'
            : fallback
        );
    }
  }
}
