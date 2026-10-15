package tn.esprit.simulateurbackend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.CandleResponse;
import tn.esprit.simulateurbackend.dto.CreateOrderRequest;
import tn.esprit.simulateurbackend.dto.MarketOverviewResponse;
import tn.esprit.simulateurbackend.dto.ModifyOrderRequest;
import tn.esprit.simulateurbackend.dto.OrderBookLevelResponse;
import tn.esprit.simulateurbackend.dto.OrderBookResponse;
import tn.esprit.simulateurbackend.dto.OrderResponse;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.Role;
import tn.esprit.simulateurbackend.entity.Trade;
import tn.esprit.simulateurbackend.entity.TriggerType;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.TradeRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;
import tn.esprit.simulateurbackend.service.MarketDataService;
import tn.esprit.simulateurbackend.service.MarketMakerService;
import tn.esprit.simulateurbackend.service.OrderBookService;
import tn.esprit.simulateurbackend.service.OrderService;
import tn.esprit.simulateurbackend.service.TradingRulesService;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Ordres au marché, ordres conditionnels, règles par actif,
 * modification d'ordre, données de marché et teneur de marché.
 *
 * Prix de référence utilisés : AAPL = 215, EURUSD = 1.17.
 */
@SpringBootTest
class TradingFeaturesTests {

    @Autowired
    WebApplicationContext context;

    @Autowired
    UserRepository users;

    @Autowired
    OrderRepository orders;

    @Autowired
    TradeRepository trades;

    @Autowired
    OrderService orderService;

    @Autowired
    OrderBookService orderBookService;

    @Autowired
    MarketDataService marketDataService;

    @Autowired
    MarketMakerService marketMakerService;

    @Autowired
    TradingRulesService tradingRulesService;

    MockMvc mvc;

    User alice;
    User bob;
    User carol;


    @BeforeEach
    void setup() {

        cleanDatabase();

        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .build();

        alice = createUser("alice");
        bob = createUser("bob");
        carol = createUser("carol");

        // Pas de transaction simulée, sauf test dédié.
        setTradeProbability(0);
    }


    @AfterEach
    void cleanDatabase() {

        trades.deleteAll();
        orders.deleteAll();
        users.deleteAll();

        setPriceBand("0");
    }


    // =========================================
    // MARKET ORDERS
    // =========================================

    @Test
    void marketOrderExecutesAtTheBestAvailablePrices() {

        limit(alice, "AAPL", OrderSide.SELL, "5", "101");
        limit(carol, "AAPL", OrderSide.SELL, "5", "100");

        OrderResponse buy =
                market(bob, "AAPL", OrderSide.BUY, "7");

        assertThat(buy.status())
                .isEqualTo(OrderStatus.EXECUTED);

        assertThat(buy.limitPrice()).isNull();

        List<Trade> executed = tradesInOrder();

        assertThat(executed).hasSize(2);

        assertThat(executed.get(0).getExecutionPrice())
                .isEqualByComparingTo("100");

        assertThat(executed.get(0).getQuantity())
                .isEqualByComparingTo("5");

        assertThat(executed.get(1).getExecutionPrice())
                .isEqualByComparingTo("101");

        assertThat(executed.get(1).getQuantity())
                .isEqualByComparingTo("2");

        assertThat(executed)
                .allMatch(trade ->
                        trade.getAggressorSide() == OrderSide.BUY
                );
    }


    @Test
    void unfilledPartOfAMarketOrderIsCancelledAndNeverRests() {

        limit(alice, "AAPL", OrderSide.SELL, "3", "100");

        OrderResponse buy =
                market(bob, "AAPL", OrderSide.BUY, "5");

        assertThat(buy.status())
                .isEqualTo(OrderStatus.CANCELLED);

        assertThat(buy.remainingQuantity())
                .isEqualByComparingTo("2");

        assertThat(trades.count()).isEqualTo(1);

        OrderBookResponse book =
                orderBookService.getOrderBook("AAPL");

        assertThat(book.bids()).isEmpty();
        assertThat(book.asks()).isEmpty();
    }


    @Test
    void marketOrderWithoutLiquidityIsRejected() {

        OrderResponse sell =
                market(alice, "AAPL", OrderSide.SELL, "5");

        assertThat(sell.status())
                .isEqualTo(OrderStatus.REJECTED);

        assertThat(sell.remainingQuantity())
                .isEqualByComparingTo("5");

        assertThat(trades.count()).isZero();

        assertThat(orderBookService.getOrderBook("AAPL").asks())
                .isEmpty();

        // Un ordre rejeté n'est pas exécuté par un ordre ultérieur.
        OrderResponse buy =
                limit(bob, "AAPL", OrderSide.BUY, "5", "100");

        assertThat(buy.status())
                .isEqualTo(OrderStatus.PENDING);
    }


    // =========================================
    // STOP-LOSS / TAKE-PROFIT
    // =========================================

    @Test
    void stopLossSellTriggersWhenThePriceFallsToTheThreshold() {

        tradeAt("100");

        OrderResponse stop =
                conditional(
                        carol,
                        "AAPL",
                        OrderSide.SELL,
                        "5",
                        TriggerType.STOP_LOSS,
                        "95"
                );

        assertThat(stop.status())
                .isEqualTo(OrderStatus.PENDING);

        assertThat(stop.triggeredAt()).isNull();

        // En attente : absent du carnet.
        assertThat(orderBookService.getOrderBook("AAPL").asks())
                .isEmpty();

        // Le cours reste au-dessus du seuil : rien ne se passe.
        limit(bob, "AAPL", OrderSide.BUY, "1", "97");
        limit(alice, "AAPL", OrderSide.SELL, "1", "97");

        assertThat(order(stop.id()).getTriggeredAt()).isNull();

        // Le cours passe à 94 : l'ordre se déclenche
        // et s'exécute au marché contre l'achat restant.
        limit(bob, "AAPL", OrderSide.BUY, "10", "94");
        limit(alice, "AAPL", OrderSide.SELL, "1", "94");

        Order triggered = order(stop.id());

        assertThat(triggered.getTriggeredAt()).isNotNull();

        assertThat(triggered.getStatus())
                .isEqualTo(OrderStatus.EXECUTED);

        Trade last =
                tradesInOrder().get((int) trades.count() - 1);

        assertThat(last.getExecutionPrice())
                .isEqualByComparingTo("94");

        assertThat(last.getQuantity())
                .isEqualByComparingTo("5");

        assertThat(last.getAggressorSide())
                .isEqualTo(OrderSide.SELL);
    }


    @Test
    void takeProfitSellTriggersWhenThePriceRisesToTheThreshold() {

        tradeAt("100");

        OrderResponse takeProfit =
                conditional(
                        carol,
                        "AAPL",
                        OrderSide.SELL,
                        "5",
                        TriggerType.TAKE_PROFIT,
                        "105"
                );

        limit(bob, "AAPL", OrderSide.BUY, "10", "104");

        assertThat(order(takeProfit.id()).getTriggeredAt()).isNull();

        // Transaction à 106 : le seuil de 105 est atteint.
        limit(alice, "AAPL", OrderSide.SELL, "1", "106");
        limit(bob, "AAPL", OrderSide.BUY, "1", "106");

        Order triggered = order(takeProfit.id());

        assertThat(triggered.getTriggeredAt()).isNotNull();

        assertThat(triggered.getStatus())
                .isEqualTo(OrderStatus.EXECUTED);

        // Exécuté au meilleur achat disponible.
        assertThat(
                tradesInOrder()
                        .get((int) trades.count() - 1)
                        .getExecutionPrice()
        ).isEqualByComparingTo("104");
    }


    @Test
    void stopLossBuyTriggersWhenThePriceRises() {

        tradeAt("100");

        OrderResponse stop =
                conditional(
                        carol,
                        "AAPL",
                        OrderSide.BUY,
                        "2",
                        TriggerType.STOP_LOSS,
                        "105"
                );

        limit(alice, "AAPL", OrderSide.SELL, "3", "105");
        limit(bob, "AAPL", OrderSide.BUY, "1", "105");

        Order triggered = order(stop.id());

        assertThat(triggered.getStatus())
                .isEqualTo(OrderStatus.EXECUTED);
    }


    @Test
    void conditionalOrderThatWouldTriggerImmediatelyIsRejected() {

        // Sans transaction, le dernier cours est le prix de référence (215).
        assertRejected(
                () -> conditional(
                        alice,
                        "AAPL",
                        OrderSide.SELL,
                        "1",
                        TriggerType.STOP_LOSS,
                        "220"
                ),
                HttpStatus.BAD_REQUEST
        );

        assertRejected(
                () -> conditional(
                        alice,
                        "AAPL",
                        OrderSide.SELL,
                        "1",
                        TriggerType.TAKE_PROFIT,
                        "210"
                ),
                HttpStatus.BAD_REQUEST
        );

        OrderResponse valid =
                conditional(
                        alice,
                        "AAPL",
                        OrderSide.SELL,
                        "1",
                        TriggerType.STOP_LOSS,
                        "200"
                );

        assertThat(valid.status())
                .isEqualTo(OrderStatus.PENDING);

        assertThat(valid.triggerType())
                .isEqualTo(TriggerType.STOP_LOSS);

        assertThat(valid.triggerPrice())
                .isEqualByComparingTo("200");
    }


    @Test
    void cancelledConditionalOrderNeverTriggers() {

        tradeAt("100");

        OrderResponse stop =
                conditional(
                        carol,
                        "AAPL",
                        OrderSide.SELL,
                        "5",
                        TriggerType.STOP_LOSS,
                        "95"
                );

        orderService.cancelOrder(stop.id(), carol.getEmail());

        limit(bob, "AAPL", OrderSide.BUY, "10", "94");
        limit(alice, "AAPL", OrderSide.SELL, "1", "94");

        Order cancelled = order(stop.id());

        assertThat(cancelled.getStatus())
                .isEqualTo(OrderStatus.CANCELLED);

        assertThat(cancelled.getTriggeredAt()).isNull();
    }


    @Test
    void conditionalOrderRequiresMarketTypeAndTriggerPrice() {

        assertRejected(
                () -> orderService.createOrder(
                        alice.getEmail(),
                        new CreateOrderRequest(
                                "AAPL",
                                OrderSide.SELL,
                                OrderType.LIMIT,
                                new BigDecimal("1"),
                                new BigDecimal("200"),
                                TriggerType.STOP_LOSS,
                                new BigDecimal("190")
                        )
                ),
                HttpStatus.BAD_REQUEST
        );

        assertRejected(
                () -> orderService.createOrder(
                        alice.getEmail(),
                        new CreateOrderRequest(
                                "AAPL",
                                OrderSide.SELL,
                                OrderType.MARKET,
                                new BigDecimal("1"),
                                null,
                                TriggerType.STOP_LOSS,
                                null
                        )
                ),
                HttpStatus.BAD_REQUEST
        );

        assertThat(orders.count()).isZero();
    }


    // =========================================
    // RULES PER ASSET
    // =========================================

    @Test
    void priceMustRespectTheTickSize() {

        assertRejected(
                () -> limit(alice, "AAPL", OrderSide.BUY, "1", "100.005"),
                HttpStatus.BAD_REQUEST
        );

        assertRejected(
                () -> limit(alice, "EURUSD", OrderSide.BUY, "100", "1.17005"),
                HttpStatus.BAD_REQUEST
        );

        assertThat(
                limit(alice, "EURUSD", OrderSide.BUY, "100", "1.1701").status()
        ).isEqualTo(OrderStatus.PENDING);

        assertThat(
                limit(alice, "AAPL", OrderSide.BUY, "1", "100.01").status()
        ).isEqualTo(OrderStatus.PENDING);
    }


    @Test
    void quantityMustReachTheAssetMinimum() {

        assertRejected(
                () -> limit(alice, "AAPL", OrderSide.BUY, "0.5", "100"),
                HttpStatus.BAD_REQUEST
        );

        assertRejected(
                () -> market(alice, "BTCUSD", OrderSide.BUY, "0.00005"),
                HttpStatus.BAD_REQUEST
        );

        assertRejected(
                () -> limit(alice, "EURUSD", OrderSide.BUY, "50", "1.17"),
                HttpStatus.BAD_REQUEST
        );

        assertThat(orders.count()).isZero();
    }


    @Test
    void priceTooFarFromTheLastPriceIsRejected() {

        setPriceBand("20");

        // Référence 215 : plage autorisée 172 à 258.
        assertRejected(
                () -> limit(alice, "AAPL", OrderSide.BUY, "1", "100"),
                HttpStatus.BAD_REQUEST
        );

        assertRejected(
                () -> limit(alice, "AAPL", OrderSide.SELL, "1", "300"),
                HttpStatus.BAD_REQUEST
        );

        assertThat(
                limit(alice, "AAPL", OrderSide.BUY, "1", "172").status()
        ).isEqualTo(OrderStatus.PENDING);

        assertThat(
                limit(alice, "AAPL", OrderSide.SELL, "1", "258").status()
        ).isEqualTo(OrderStatus.PENDING);
    }


    @Test
    void marketOrderDoesNotExecuteBeyondThePriceBand() {

        limit(alice, "AAPL", OrderSide.SELL, "5", "216");
        limit(carol, "AAPL", OrderSide.SELL, "5", "236");

        // Référence 215, bande de 5 % : achat au marché plafonné à 225.75.
        setPriceBand("5");

        OrderResponse buy =
                market(bob, "AAPL", OrderSide.BUY, "10");

        assertThat(buy.status())
                .isEqualTo(OrderStatus.CANCELLED);

        assertThat(buy.remainingQuantity())
                .isEqualByComparingTo("5");

        List<Trade> executed = tradesInOrder();

        assertThat(executed).hasSize(1);

        assertThat(executed.get(0).getExecutionPrice())
                .isEqualByComparingTo("216");
    }


    // =========================================
    // MODIFY ORDER
    // =========================================

    @Test
    void modifyingAnOrderCancelsAndReplacesIt() {

        OrderResponse original =
                limit(alice, "AAPL", OrderSide.SELL, "10", "100");

        OrderResponse replacement =
                orderService.modifyOrder(
                        original.id(),
                        alice.getEmail(),
                        modification("8", "101")
                );

        assertThat(replacement.id())
                .isNotEqualTo(original.id());

        assertThat(replacement.status())
                .isEqualTo(OrderStatus.PENDING);

        assertThat(replacement.side())
                .isEqualTo(OrderSide.SELL);

        assertThat(order(original.id()).getStatus())
                .isEqualTo(OrderStatus.CANCELLED);

        List<OrderBookLevelResponse> asks =
                orderBookService.getOrderBook("AAPL").asks();

        assertThat(asks).hasSize(1);

        assertThat(asks.get(0).price())
                .isEqualByComparingTo("101");

        assertThat(asks.get(0).totalQuantity())
                .isEqualByComparingTo("8");
    }


    @Test
    void modifiedOrderLosesItsTimePriority() {

        OrderResponse first =
                limit(alice, "AAPL", OrderSide.SELL, "5", "100");

        OrderResponse second =
                limit(carol, "AAPL", OrderSide.SELL, "5", "100");

        OrderResponse replacement =
                orderService.modifyOrder(
                        first.id(),
                        alice.getEmail(),
                        modification("5", "100")
                );

        limit(bob, "AAPL", OrderSide.BUY, "5", "100");

        assertThat(order(second.id()).getStatus())
                .isEqualTo(OrderStatus.EXECUTED);

        assertThat(order(replacement.id()).getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }


    @Test
    void modifiedOrderIsMatchedIfItsNewPriceCrosses() {

        limit(alice, "AAPL", OrderSide.SELL, "5", "100");

        OrderResponse buy =
                limit(bob, "AAPL", OrderSide.BUY, "5", "99");

        OrderResponse replacement =
                orderService.modifyOrder(
                        buy.id(),
                        bob.getEmail(),
                        modification("5", "100")
                );

        assertThat(replacement.status())
                .isEqualTo(OrderStatus.EXECUTED);
    }


    @Test
    void refusedModificationLeavesTheOriginalOrderIntact() {

        limit(alice, "AAPL", OrderSide.SELL, "5", "100");

        OrderResponse buy =
                limit(alice, "AAPL", OrderSide.BUY, "5", "99");

        // Le nouveau prix croiserait sa propre vente.
        assertRejected(
                () -> orderService.modifyOrder(
                        buy.id(),
                        alice.getEmail(),
                        modification("5", "100")
                ),
                HttpStatus.CONFLICT
        );

        Order untouched = order(buy.id());

        assertThat(untouched.getStatus())
                .isEqualTo(OrderStatus.PENDING);

        assertThat(untouched.getLimitPrice())
                .isEqualByComparingTo("99");

        assertThat(orders.count()).isEqualTo(2);
    }


    @Test
    void onlyTheOwnerCanModifyAnOpenLimitOrder() throws Exception {

        OrderResponse sell =
                limit(alice, "AAPL", OrderSide.SELL, "5", "100");

        mvc.perform(
                        put("/api/orders/" + sell.id())
                                .principal(as(bob))
                                .contentType("application/json")
                                .content("""
                                    {"quantity":5,"limitPrice":101}
                                    """)
                )
                .andExpect(status().isForbidden());

        mvc.perform(
                        put("/api/orders/" + sell.id())
                                .principal(as(alice))
                                .contentType("application/json")
                                .content("""
                                    {"quantity":0,"limitPrice":101}
                                    """)
                )
                .andExpect(status().isBadRequest());

        mvc.perform(
                        put("/api/orders/" + sell.id())
                                .principal(as(alice))
                                .contentType("application/json")
                                .content("""
                                    {"quantity":4,"limitPrice":101}
                                    """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.quantity").value(4.0))
                .andExpect(jsonPath("$.limitPrice").value(101.0));

        // L'ordre d'origine est annulé : il n'est plus modifiable.
        mvc.perform(
                        put("/api/orders/" + sell.id())
                                .principal(as(alice))
                                .contentType("application/json")
                                .content("""
                                    {"quantity":4,"limitPrice":102}
                                    """)
                )
                .andExpect(status().isConflict());

        mvc.perform(
                        put("/api/orders/999999")
                                .principal(as(alice))
                                .contentType("application/json")
                                .content("""
                                    {"quantity":4,"limitPrice":102}
                                    """)
                )
                .andExpect(status().isNotFound());
    }


    // =========================================
    // MY ORDERS IN THE BOOK
    // =========================================

    @Test
    void orderBookShowsTheViewersOwnQuantityPerLevel() throws Exception {

        limit(alice, "AAPL", OrderSide.BUY, "5", "100");
        limit(carol, "AAPL", OrderSide.BUY, "3", "100");
        limit(carol, "AAPL", OrderSide.BUY, "2", "99");

        List<OrderBookLevelResponse> aliceView =
                orderBookService
                        .getOrderBook("AAPL", alice.getEmail())
                        .bids();

        assertThat(aliceView.get(0).totalQuantity())
                .isEqualByComparingTo("8");

        assertThat(aliceView.get(0).myQuantity())
                .isEqualByComparingTo("5");

        assertThat(aliceView.get(1).myQuantity())
                .isEqualByComparingTo("0");

        mvc.perform(
                        get("/api/order-book/AAPL")
                                .principal(as(bob))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bids[0].myQuantity").value(0))
                .andExpect(jsonPath("$.bids[0].totalQuantity").value(8.0));

        mvc.perform(
                        get("/api/order-book/AAPL")
                                .principal(as(carol))
                )
                .andExpect(jsonPath("$.bids[0].myQuantity").value(3.0))
                .andExpect(jsonPath("$.bids[1].myQuantity").value(2.0));
    }


    // =========================================
    // MARKET DATA
    // =========================================

    @Test
    void overviewFallsBackToTheReferencePriceWithoutTrades() {

        MarketOverviewResponse overview =
                marketDataService.getOverview("aapl", null);

        assertThat(overview.summary().assetSymbol())
                .isEqualTo("AAPL");

        assertThat(overview.summary().lastPrice())
                .isEqualByComparingTo("215");

        assertThat(overview.summary().changePercent())
                .isEqualByComparingTo("0");

        assertThat(overview.summary().tradeCount24h()).isZero();

        assertThat(overview.summary().volume24h())
                .isEqualByComparingTo("0");

        assertThat(overview.recentTrades()).isEmpty();
        assertThat(overview.candles()).isEmpty();

        assertThat(overview.candleInterval()).isEqualTo("1m");
    }


    @Test
    void overviewReflectsTheExecutedTrades() throws Exception {

        limit(alice, "AAPL", OrderSide.SELL, "2", "100");
        limit(bob, "AAPL", OrderSide.BUY, "2", "100");

        limit(alice, "AAPL", OrderSide.SELL, "1", "102");
        market(bob, "AAPL", OrderSide.BUY, "1");

        // Les transactions d'un autre actif ne comptent pas.
        limit(alice, "MSFT", OrderSide.SELL, "1", "400");
        limit(bob, "MSFT", OrderSide.BUY, "1", "400");

        MarketOverviewResponse overview =
                marketDataService.getOverview("AAPL", "5m");

        assertThat(overview.summary().lastPrice())
                .isEqualByComparingTo("102");

        assertThat(overview.summary().high24h())
                .isEqualByComparingTo("102");

        assertThat(overview.summary().low24h())
                .isEqualByComparingTo("100");

        assertThat(overview.summary().volume24h())
                .isEqualByComparingTo("3");

        assertThat(overview.summary().tradeCount24h())
                .isEqualTo(2);

        // (102 - 100) / 100
        assertThat(overview.summary().changePercent())
                .isEqualByComparingTo("2");

        // Flux public : la plus récente en premier, sans identité.
        assertThat(overview.recentTrades()).hasSize(2);

        assertThat(overview.recentTrades().get(0).price())
                .isEqualByComparingTo("102");

        assertThat(overview.recentTrades().get(0).aggressorSide())
                .isEqualTo(OrderSide.BUY);

        // Chandeliers : 1 en général, 2 si le test chevauche deux intervalles.
        List<CandleResponse> candles = overview.candles();

        assertThat(candles).isNotEmpty();

        assertThat(overview.candleInterval()).isEqualTo("5m");

        assertThat(candles.get(0).open())
                .isEqualByComparingTo("100");

        assertThat(candles.get(candles.size() - 1).close())
                .isEqualByComparingTo("102");

        assertThat(
                candles.stream()
                        .map(CandleResponse::volume)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        ).isEqualByComparingTo("3");

        mvc.perform(
                        get("/api/market/AAPL")
                                .principal(as(carol))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.lastPrice").value(102.0))
                .andExpect(jsonPath("$.recentTrades.length()").value(2))
                .andExpect(jsonPath("$.recentTrades[0].buyOrder").doesNotExist());

        mvc.perform(
                        get("/api/market/UNKNOWN")
                                .principal(as(carol))
                )
                .andExpect(status().isNotFound());
    }


    // =========================================
    // MARKET MAKER
    // =========================================

    @Test
    void marketMakerQuotesBothSidesAroundTheReferencePrice() {

        marketMakerService.quoteAsset("AAPL");

        OrderBookResponse book =
                orderBookService.getOrderBook("AAPL");

        assertThat(book.bids()).hasSize(5);
        assertThat(book.asks()).hasSize(5);

        BigDecimal bestBid = book.bids().get(0).price();
        BigDecimal bestAsk = book.asks().get(0).price();

        // Carnet jamais croisé, proche du prix de référence (215).
        assertThat(bestBid).isLessThan(bestAsk);

        assertThat(bestBid)
                .isBetween(
                        new BigDecimal("205"),
                        new BigDecimal("225")
                );

        assertThat(bestAsk)
                .isBetween(
                        new BigDecimal("205"),
                        new BigDecimal("225")
                );

        // Respect du pas de cotation (0.01) et de la quantité minimale (1).
        assertThat(orders.findAll())
                .allMatch(order ->
                        order.getLimitPrice()
                                .remainder(new BigDecimal("0.01"))
                                .signum() == 0
                )
                .allMatch(order ->
                        order.getQuantity()
                                .compareTo(BigDecimal.ONE) >= 0
                );

        // Comptes techniques : connexion impossible.
        assertThat(
                users.findByEmailIgnoreCase(
                        MarketMakerService.MAKER_EMAIL
                )
        ).hasValueSatisfying(maker ->
                assertThat(maker.isEnabled()).isFalse()
        );
    }


    @Test
    void marketMakerReusesItsOrdersFromOneCycleToTheNext() {

        marketMakerService.quoteAsset("AAPL");

        long afterFirstCycle = orders.count();

        marketMakerService.quoteAsset("AAPL");
        marketMakerService.quoteAsset("AAPL");

        assertThat(afterFirstCycle).isEqualTo(10);
        assertThat(orders.count()).isEqualTo(10);

        OrderBookResponse book =
                orderBookService.getOrderBook("AAPL");

        assertThat(book.bids().get(0).price())
                .isLessThan(book.asks().get(0).price());
    }


    @Test
    void aSingleUserCanTradeAgainstTheMarketMaker() {

        marketMakerService.quoteAsset("AAPL");

        BigDecimal bestAsk =
                orderBookService
                        .getOrderBook("AAPL")
                        .asks()
                        .get(0)
                        .price();

        OrderResponse buy =
                market(alice, "AAPL", OrderSide.BUY, "1");

        assertThat(buy.status())
                .isEqualTo(OrderStatus.EXECUTED);

        assertThat(tradesInOrder().get(0).getExecutionPrice())
                .isEqualByComparingTo(bestAsk);

        // Le cycle suivant reconstitue la liquidité consommée.
        marketMakerService.quoteAsset("AAPL");

        assertThat(orderBookService.getOrderBook("AAPL").asks())
                .hasSize(5);
    }


    @Test
    void marketMakerExecutesRestingUserOrdersItsQuotesCross() {

        // Achat au repos très au-dessus du marché (215).
        OrderResponse buy =
                limit(alice, "AAPL", OrderSide.BUY, "1", "230");

        assertThat(buy.status())
                .isEqualTo(OrderStatus.PENDING);

        marketMakerService.quoteAsset("AAPL");

        assertThat(order(buy.id()).getStatus())
                .isEqualTo(OrderStatus.EXECUTED);

        // Exécution au prix de l'ordre au repos.
        assertThat(tradesInOrder().get(0).getExecutionPrice())
                .isEqualByComparingTo("230");
    }


    @Test
    void simulatedMarketFlowProducesTrades() {

        setTradeProbability(1);

        marketMakerService.quoteAsset("AAPL");

        assertThat(trades.count()).isEqualTo(1);

        MarketOverviewResponse overview =
                marketDataService.getOverview("AAPL", "1m");

        assertThat(overview.recentTrades()).hasSize(1);
        assertThat(overview.candles()).hasSize(1);

        assertThat(overview.summary().lastPrice())
                .isBetween(
                        new BigDecimal("205"),
                        new BigDecimal("225")
                );
    }


    // =========================================
    // MARKET IMPACT
    // =========================================

    @Test
    void heavySellingPushesThePriceDownAndWidensTheSpread() {

        marketMakerService.quoteAsset("AAPL");

        BigDecimal midBefore = midPrice("AAPL");
        BigDecimal spreadBefore = spread("AAPL");

        // Vente massive : tous les achats du carnet sont consommés.
        OrderResponse sell =
                market(alice, "AAPL", OrderSide.SELL, "100000");

        assertThat(sell.status())
                .isEqualTo(OrderStatus.CANCELLED);

        assertThat(orderBookService.getOrderBook("AAPL").bids())
                .isEmpty();

        marketMakerService.quoteAsset("AAPL");

        // Le teneur de marché recote plus bas (bien au-delà
        // du simple bruit aléatoire) et avec un écart plus large.
        assertThat(midPrice("AAPL"))
                .isLessThan(
                        midBefore.multiply(new BigDecimal("0.998"))
                );

        assertThat(spread("AAPL"))
                .isGreaterThan(spreadBefore);
    }


    @Test
    void heavyBuyingPushesThePriceUp() {

        marketMakerService.quoteAsset("AAPL");

        BigDecimal midBefore = midPrice("AAPL");

        market(alice, "AAPL", OrderSide.BUY, "100000");

        marketMakerService.quoteAsset("AAPL");

        assertThat(midPrice("AAPL"))
                .isGreaterThan(
                        midBefore.multiply(new BigDecimal("1.002"))
                );
    }


    @Test
    void marketMakerIgnoresUnknownAssets() {

        marketMakerService.quoteAsset("UNKNOWN");

        assertThat(orders.count()).isZero();

        assertThat(marketMakerService.quotableSymbols())
                .contains("AAPL", "BTCUSD", "EURUSD");
    }


    // =========================================
    // HELPERS
    // =========================================

    private OrderResponse limit(
            User user,
            String symbol,
            OrderSide side,
            String quantity,
            String price
    ) {

        return orderService.createOrder(
                user.getEmail(),
                new CreateOrderRequest(
                        symbol,
                        side,
                        OrderType.LIMIT,
                        new BigDecimal(quantity),
                        new BigDecimal(price)
                )
        );
    }


    private OrderResponse market(
            User user,
            String symbol,
            OrderSide side,
            String quantity
    ) {

        return orderService.createOrder(
                user.getEmail(),
                new CreateOrderRequest(
                        symbol,
                        side,
                        OrderType.MARKET,
                        new BigDecimal(quantity),
                        null
                )
        );
    }


    private OrderResponse conditional(
            User user,
            String symbol,
            OrderSide side,
            String quantity,
            TriggerType triggerType,
            String triggerPrice
    ) {

        return orderService.createOrder(
                user.getEmail(),
                new CreateOrderRequest(
                        symbol,
                        side,
                        OrderType.MARKET,
                        new BigDecimal(quantity),
                        null,
                        triggerType,
                        new BigDecimal(triggerPrice)
                )
        );
    }


    // Fixe le dernier cours d'AAPL par une transaction alice -> bob.
    private void tradeAt(String price) {

        limit(alice, "AAPL", OrderSide.SELL, "1", price);
        limit(bob, "AAPL", OrderSide.BUY, "1", price);
    }


    private BigDecimal midPrice(String symbol) {

        OrderBookResponse book =
                orderBookService.getOrderBook(symbol);

        return book.bids().get(0).price()
                .add(book.asks().get(0).price())
                .divide(new BigDecimal("2"));
    }


    private BigDecimal spread(String symbol) {

        OrderBookResponse book =
                orderBookService.getOrderBook(symbol);

        return book.asks().get(0).price()
                .subtract(book.bids().get(0).price());
    }


    private ModifyOrderRequest modification(
            String quantity,
            String price
    ) {

        return new ModifyOrderRequest(
                new BigDecimal(quantity),
                new BigDecimal(price)
        );
    }


    private Order order(Long id) {

        return orders.findById(id).orElseThrow();
    }


    private List<Trade> tradesInOrder() {

        return trades.findAll()
                .stream()
                .sorted(Comparator.comparing(Trade::getId))
                .toList();
    }


    private void setPriceBand(String percent) {

        ReflectionTestUtils.setField(
                tradingRulesService,
                "priceBandPercent",
                new BigDecimal(percent)
        );
    }


    private void setTradeProbability(double probability) {

        ReflectionTestUtils.setField(
                marketMakerService,
                "tradeProbability",
                probability
        );
    }


    private void assertRejected(
            Runnable action,
            HttpStatus expectedStatus
    ) {

        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception ->
                                assertThat(exception.getStatusCode())
                                        .isEqualTo(expectedStatus)
                );
    }


    private Authentication as(User user) {

        return new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                List.of()
        );
    }


    private User createUser(String name) {

        User user = new User();

        user.setFirstName(name);
        user.setLastName("Trader");
        user.setEmail(name + ".trader@example.com");
        user.setCountryCode("TN");
        user.setRole(Role.USER);
        user.setEnabled(true);

        return users.saveAndFlush(user);
    }
}
