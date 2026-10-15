package tn.esprit.simulateurbackend;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.Filter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.CreateOrderRequest;
import tn.esprit.simulateurbackend.dto.OrderBookResponse;
import tn.esprit.simulateurbackend.dto.OrderResponse;
import tn.esprit.simulateurbackend.dto.TradeResponse;
import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.AssetType;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.Role;
import tn.esprit.simulateurbackend.entity.Trade;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.TradeRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;
import tn.esprit.simulateurbackend.service.OrderBookService;
import tn.esprit.simulateurbackend.service.OrderService;
import tn.esprit.simulateurbackend.service.TradeService;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class OrderBookTests {

    private static final String INACTIVE_SYMBOL = "DELISTED";

    @Autowired
    WebApplicationContext context;

    @Autowired
    UserRepository users;

    @Autowired
    OrderRepository orders;

    @Autowired
    TradeRepository trades;

    @Autowired
    AssetRepository assets;

    @Autowired
    OrderService orderService;

    @Autowired
    OrderBookService orderBookService;

    @Autowired
    TradeService tradeService;

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
    }


    /*
     * Les autres classes de test font users.deleteAll() :
     * il ne doit rester aucun ordre référençant un utilisateur.
     */
    @AfterEach
    void cleanDatabase() {

        trades.deleteAll();
        orders.deleteAll();
        users.deleteAll();

        assets.findBySymbolIgnoreCase(INACTIVE_SYMBOL)
                .ifPresent(assets::delete);
    }


    // =========================================
    // MATCHING
    // =========================================

    @Test
    void crossingOrdersAreFullyExecuted() {

        OrderResponse sell =
                place(alice, "AAPL", OrderSide.SELL, "10", "100");

        assertThat(sell.status())
                .isEqualTo(OrderStatus.PENDING);

        OrderResponse buy =
                place(bob, "AAPL", OrderSide.BUY, "10", "100");

        assertThat(buy.status())
                .isEqualTo(OrderStatus.EXECUTED);

        assertThat(buy.remainingQuantity())
                .isEqualByComparingTo("0");

        assertOrder(sell.id(), OrderStatus.EXECUTED, "0");

        List<Trade> executed = tradesInOrder();

        assertThat(executed).hasSize(1);

        assertThat(executed.get(0).getQuantity())
                .isEqualByComparingTo("10");

        assertThat(executed.get(0).getExecutionPrice())
                .isEqualByComparingTo("100");

        OrderBookResponse book =
                orderBookService.getOrderBook("AAPL");

        assertThat(book.bids()).isEmpty();
        assertThat(book.asks()).isEmpty();
    }


    @Test
    void ordersThatDoNotCrossStayInTheBook() {

        OrderResponse sell =
                place(alice, "AAPL", OrderSide.SELL, "10", "101");

        OrderResponse buy =
                place(bob, "AAPL", OrderSide.BUY, "10", "100");

        assertThat(sell.status())
                .isEqualTo(OrderStatus.PENDING);

        assertThat(buy.status())
                .isEqualTo(OrderStatus.PENDING);

        assertThat(trades.count()).isZero();

        OrderBookResponse book =
                orderBookService.getOrderBook("AAPL");

        assertThat(book.bids()).hasSize(1);

        assertThat(book.bids().get(0).price())
                .isEqualByComparingTo("100");

        assertThat(book.asks()).hasSize(1);

        assertThat(book.asks().get(0).price())
                .isEqualByComparingTo("101");
    }


    @Test
    void tradeIsExecutedAtTheRestingOrderPrice() {

        // Vente au repos à 100, achat agressif à 105 : exécution à 100.
        place(alice, "AAPL", OrderSide.SELL, "5", "100");
        place(bob, "AAPL", OrderSide.BUY, "5", "105");

        // Achat au repos à 105, vente agressive à 100 : exécution à 105.
        place(alice, "MSFT", OrderSide.BUY, "5", "105");
        place(bob, "MSFT", OrderSide.SELL, "5", "100");

        List<Trade> executed = tradesInOrder();

        assertThat(executed).hasSize(2);

        assertThat(executed.get(0).getExecutionPrice())
                .isEqualByComparingTo("100");

        assertThat(executed.get(1).getExecutionPrice())
                .isEqualByComparingTo("105");
    }


    // =========================================
    // PRICE PRIORITY
    // =========================================

    @Test
    void buyOrderMatchesTheLowestAskFirst() {

        OrderResponse ask102 =
                place(alice, "AAPL", OrderSide.SELL, "5", "102");

        OrderResponse ask100 =
                place(carol, "AAPL", OrderSide.SELL, "5", "100");

        OrderResponse ask101 =
                place(alice, "AAPL", OrderSide.SELL, "5", "101");

        // Ne consomme que le meilleur prix, pourtant arrivé en 2e.
        place(bob, "AAPL", OrderSide.BUY, "5", "102");

        assertOrder(ask100.id(), OrderStatus.EXECUTED, "0");
        assertOrder(ask101.id(), OrderStatus.PENDING, "5");
        assertOrder(ask102.id(), OrderStatus.PENDING, "5");

        // Balaye ensuite le carnet du moins cher au plus cher.
        place(bob, "AAPL", OrderSide.BUY, "10", "102");

        assertThat(tradesInOrder())
                .extracting(trade ->
                        trade.getExecutionPrice().intValueExact()
                )
                .containsExactly(100, 101, 102);
    }


    @Test
    void sellOrderMatchesTheHighestBidFirst() {

        OrderResponse bid98 =
                place(alice, "AAPL", OrderSide.BUY, "5", "98");

        OrderResponse bid100 =
                place(carol, "AAPL", OrderSide.BUY, "5", "100");

        OrderResponse bid99 =
                place(alice, "AAPL", OrderSide.BUY, "5", "99");

        place(bob, "AAPL", OrderSide.SELL, "5", "98");

        assertOrder(bid100.id(), OrderStatus.EXECUTED, "0");
        assertOrder(bid99.id(), OrderStatus.PENDING, "5");
        assertOrder(bid98.id(), OrderStatus.PENDING, "5");

        place(bob, "AAPL", OrderSide.SELL, "10", "98");

        assertThat(tradesInOrder())
                .extracting(trade ->
                        trade.getExecutionPrice().intValueExact()
                )
                .containsExactly(100, 99, 98);
    }


    @Test
    void orderOutsideTheLimitPriceIsNeverMatched() {

        OrderResponse ask100 =
                place(alice, "AAPL", OrderSide.SELL, "5", "100");

        OrderResponse ask103 =
                place(carol, "AAPL", OrderSide.SELL, "5", "103");

        OrderResponse buy =
                place(bob, "AAPL", OrderSide.BUY, "10", "101");

        assertOrder(ask100.id(), OrderStatus.EXECUTED, "0");
        assertOrder(ask103.id(), OrderStatus.PENDING, "5");

        assertThat(buy.status())
                .isEqualTo(OrderStatus.PARTIALLY_FILLED);

        assertThat(buy.remainingQuantity())
                .isEqualByComparingTo("5");
    }


    // =========================================
    // TIME PRIORITY
    // =========================================

    @Test
    void atEqualPriceTheOldestAskIsMatchedFirst() {

        OrderResponse first =
                place(alice, "AAPL", OrderSide.SELL, "5", "100");

        OrderResponse second =
                place(carol, "AAPL", OrderSide.SELL, "5", "100");

        place(bob, "AAPL", OrderSide.BUY, "5", "100");

        assertOrder(first.id(), OrderStatus.EXECUTED, "0");
        assertOrder(second.id(), OrderStatus.PENDING, "5");

        // Le reliquat suit toujours l'ordre d'arrivée.
        place(bob, "AAPL", OrderSide.BUY, "2", "100");

        assertOrder(second.id(), OrderStatus.PARTIALLY_FILLED, "3");
    }


    @Test
    void atEqualPriceTheOldestBidIsMatchedFirst() {

        OrderResponse first =
                place(carol, "AAPL", OrderSide.BUY, "5", "100");

        OrderResponse second =
                place(alice, "AAPL", OrderSide.BUY, "5", "100");

        place(bob, "AAPL", OrderSide.SELL, "7", "100");

        assertOrder(first.id(), OrderStatus.EXECUTED, "0");
        assertOrder(second.id(), OrderStatus.PARTIALLY_FILLED, "3");
    }


    @Test
    void betterPriceBeatsAnOlderOrder() {

        OrderResponse olderButWorse =
                place(alice, "AAPL", OrderSide.SELL, "5", "101");

        OrderResponse newerButBetter =
                place(carol, "AAPL", OrderSide.SELL, "5", "100");

        place(bob, "AAPL", OrderSide.BUY, "5", "101");

        assertOrder(newerButBetter.id(), OrderStatus.EXECUTED, "0");
        assertOrder(olderButWorse.id(), OrderStatus.PENDING, "5");
    }


    // =========================================
    // PARTIAL EXECUTION
    // =========================================

    @Test
    void smallerIncomingOrderPartiallyFillsTheRestingOrder() {

        OrderResponse sell =
                place(alice, "AAPL", OrderSide.SELL, "10", "100");

        OrderResponse buy =
                place(bob, "AAPL", OrderSide.BUY, "4", "100");

        assertThat(buy.status())
                .isEqualTo(OrderStatus.EXECUTED);

        assertOrder(sell.id(), OrderStatus.PARTIALLY_FILLED, "6");

        OrderBookResponse book =
                orderBookService.getOrderBook("AAPL");

        assertThat(book.asks()).hasSize(1);

        assertThat(book.asks().get(0).totalQuantity())
                .isEqualByComparingTo("6");
    }


    @Test
    void largerIncomingOrderRestsInTheBookWithItsRemainder() {

        OrderResponse sell =
                place(alice, "AAPL", OrderSide.SELL, "4", "100");

        OrderResponse buy =
                place(bob, "AAPL", OrderSide.BUY, "10", "100");

        assertOrder(sell.id(), OrderStatus.EXECUTED, "0");

        assertThat(buy.status())
                .isEqualTo(OrderStatus.PARTIALLY_FILLED);

        assertThat(buy.remainingQuantity())
                .isEqualByComparingTo("6");

        OrderBookResponse book =
                orderBookService.getOrderBook("AAPL");

        assertThat(book.asks()).isEmpty();
        assertThat(book.bids()).hasSize(1);

        assertThat(book.bids().get(0).totalQuantity())
                .isEqualByComparingTo("6");

        // Le reliquat reste exécutable.
        place(carol, "AAPL", OrderSide.SELL, "6", "100");

        assertOrder(buy.id(), OrderStatus.EXECUTED, "0");
    }


    @Test
    void incomingOrderIsFilledAcrossSeveralRestingOrders() {

        place(alice, "AAPL", OrderSide.SELL, "3", "100");
        place(carol, "AAPL", OrderSide.SELL, "3", "100.5");
        place(alice, "AAPL", OrderSide.SELL, "3", "101");

        OrderResponse buy =
                place(bob, "AAPL", OrderSide.BUY, "7.5", "101");

        assertThat(buy.status())
                .isEqualTo(OrderStatus.EXECUTED);

        List<Trade> executed = tradesInOrder();

        assertThat(executed).hasSize(3);

        assertThat(executed.get(2).getQuantity())
                .isEqualByComparingTo("1.5");

        BigDecimal total =
                executed.stream()
                        .map(Trade::getQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(total).isEqualByComparingTo("7.5");
    }


    // =========================================
    // CANCELLATION
    // =========================================

    @Test
    void cancelledOrderLeavesTheBookAndIsNoLongerMatched() {

        OrderResponse sell =
                place(alice, "AAPL", OrderSide.SELL, "10", "100");

        OrderResponse cancelled =
                orderService.cancelOrder(
                        sell.id(),
                        alice.getEmail()
                );

        assertThat(cancelled.status())
                .isEqualTo(OrderStatus.CANCELLED);

        assertThat(orderBookService.getOrderBook("AAPL").asks())
                .isEmpty();

        OrderResponse buy =
                place(bob, "AAPL", OrderSide.BUY, "10", "100");

        assertThat(buy.status())
                .isEqualTo(OrderStatus.PENDING);

        assertThat(trades.count()).isZero();
    }


    @Test
    void cancellingAPartiallyFilledOrderKeepsItsTrades() {

        OrderResponse sell =
                place(alice, "AAPL", OrderSide.SELL, "10", "100");

        place(bob, "AAPL", OrderSide.BUY, "4", "100");

        OrderResponse cancelled =
                orderService.cancelOrder(
                        sell.id(),
                        alice.getEmail()
                );

        assertThat(cancelled.status())
                .isEqualTo(OrderStatus.CANCELLED);

        assertThat(cancelled.remainingQuantity())
                .isEqualByComparingTo("6");

        assertThat(trades.count()).isEqualTo(1);

        // Les 6 restants ne sont plus disponibles.
        OrderResponse buy =
                place(bob, "AAPL", OrderSide.BUY, "6", "100");

        assertThat(buy.status())
                .isEqualTo(OrderStatus.PENDING);
    }


    @Test
    void executedOrCancelledOrdersCannotBeCancelled() {

        OrderResponse sell =
                place(alice, "AAPL", OrderSide.SELL, "5", "100");

        place(bob, "AAPL", OrderSide.BUY, "5", "100");

        assertRejected(
                () -> orderService.cancelOrder(
                        sell.id(),
                        alice.getEmail()
                ),
                HttpStatus.CONFLICT
        );

        OrderResponse pending =
                place(alice, "AAPL", OrderSide.SELL, "5", "100");

        orderService.cancelOrder(
                pending.id(),
                alice.getEmail()
        );

        assertRejected(
                () -> orderService.cancelOrder(
                        pending.id(),
                        alice.getEmail()
                ),
                HttpStatus.CONFLICT
        );
    }


    @Test
    void userCannotCancelSomeoneElsesOrder() {

        OrderResponse sell =
                place(alice, "AAPL", OrderSide.SELL, "5", "100");

        assertRejected(
                () -> orderService.cancelOrder(
                        sell.id(),
                        bob.getEmail()
                ),
                HttpStatus.FORBIDDEN
        );

        assertOrder(sell.id(), OrderStatus.PENDING, "5");
    }


    @Test
    void cancellingAnUnknownOrderReturnsNotFound() {

        assertRejected(
                () -> orderService.cancelOrder(
                        999_999L,
                        alice.getEmail()
                ),
                HttpStatus.NOT_FOUND
        );
    }


    // =========================================
    // TWO USERS
    // =========================================

    @Test
    void userNeverTradesAgainstTheirOwnOrders() {

        OrderResponse sell =
                place(alice, "AAPL", OrderSide.SELL, "5", "100");

        // Un ordre qui croiserait le sien est refusé :
        // il laisserait le carnet croisé sans jamais s'exécuter.
        assertRejected(
                () -> place(alice, "AAPL", OrderSide.BUY, "5", "100"),
                HttpStatus.CONFLICT
        );

        assertRejected(
                () -> place(alice, "AAPL", OrderSide.BUY, "5", "101"),
                HttpStatus.CONFLICT
        );

        // Sans croisement, ses deux ordres coexistent.
        OrderResponse buy =
                place(alice, "AAPL", OrderSide.BUY, "5", "99");

        assertThat(buy.status())
                .isEqualTo(OrderStatus.PENDING);

        assertOrder(sell.id(), OrderStatus.PENDING, "5");

        assertThat(trades.count()).isZero();

        // Un autre utilisateur peut exécuter l'ordre.
        OrderResponse bobBuy =
                place(bob, "AAPL", OrderSide.BUY, "5", "100");

        assertThat(bobBuy.status())
                .isEqualTo(OrderStatus.EXECUTED);

        assertOrder(sell.id(), OrderStatus.EXECUTED, "0");
    }


    @Test
    void eachUserSeesTheirOwnSideOfTheTrade() {

        OrderResponse sell =
                place(alice, "AAPL", OrderSide.SELL, "5", "100");

        OrderResponse buy =
                place(bob, "AAPL", OrderSide.BUY, "5", "100");

        List<TradeResponse> aliceTrades =
                tradeService.getMyTrades(alice.getEmail());

        List<TradeResponse> bobTrades =
                tradeService.getMyTrades(bob.getEmail());

        assertThat(aliceTrades).hasSize(1);

        assertThat(aliceTrades.get(0).side())
                .isEqualTo(OrderSide.SELL);

        assertThat(aliceTrades.get(0).orderId())
                .isEqualTo(sell.id());

        assertThat(bobTrades).hasSize(1);

        assertThat(bobTrades.get(0).side())
                .isEqualTo(OrderSide.BUY);

        assertThat(bobTrades.get(0).orderId())
                .isEqualTo(buy.id());

        assertThat(tradeService.getMyTrades(carol.getEmail()))
                .isEmpty();
    }


    @Test
    void orderHistoryOnlyContainsTheUsersOwnOrders() {

        place(alice, "AAPL", OrderSide.SELL, "5", "100");
        place(alice, "MSFT", OrderSide.SELL, "5", "300");
        place(bob, "AAPL", OrderSide.BUY, "5", "90");

        assertThat(orderService.getMyOrders(alice.getEmail()))
                .hasSize(2)
                .allMatch(order ->
                        order.userId().equals(alice.getId())
                );

        assertThat(orderService.getMyOrders(bob.getEmail()))
                .hasSize(1);

        assertThat(orderService.getMyOrders(carol.getEmail()))
                .isEmpty();
    }


    // =========================================
    // MULTI-ASSETS
    // =========================================

    @Test
    void ordersOnDifferentAssetsNeverMatch() {

        OrderResponse sell =
                place(alice, "AAPL", OrderSide.SELL, "5", "100");

        OrderResponse buy =
                place(bob, "MSFT", OrderSide.BUY, "5", "100");

        assertThat(sell.status())
                .isEqualTo(OrderStatus.PENDING);

        assertThat(buy.status())
                .isEqualTo(OrderStatus.PENDING);

        assertThat(trades.count()).isZero();

        OrderBookResponse aapl =
                orderBookService.getOrderBook("AAPL");

        OrderBookResponse msft =
                orderBookService.getOrderBook("MSFT");

        assertThat(aapl.asks()).hasSize(1);
        assertThat(aapl.bids()).isEmpty();

        assertThat(msft.bids()).hasSize(1);
        assertThat(msft.asks()).isEmpty();
    }


    @Test
    void eachAssetIsMatchedInItsOwnBook() {

        place(alice, "AAPL", OrderSide.SELL, "5", "100");
        place(alice, "BTCUSD", OrderSide.SELL, "0.5", "60000");

        place(bob, "BTCUSD", OrderSide.BUY, "0.5", "60000");

        List<Trade> executed = tradesInOrder();

        assertThat(executed).hasSize(1);

        assertThat(executed.get(0).getAssetSymbol())
                .isEqualTo("BTCUSD");

        assertThat(orderBookService.getOrderBook("AAPL").asks())
                .hasSize(1);

        assertThat(orderBookService.getOrderBook("BTCUSD").asks())
                .isEmpty();
    }


    @Test
    void assetSymbolIsCaseInsensitive() {

        OrderResponse sell =
                place(alice, " aapl ", OrderSide.SELL, "5", "100");

        assertThat(sell.assetSymbol()).isEqualTo("AAPL");

        OrderResponse buy =
                place(bob, "AAPL", OrderSide.BUY, "5", "100");

        assertThat(buy.status())
                .isEqualTo(OrderStatus.EXECUTED);

        assertThat(orderBookService.getOrderBook("aapl").assetSymbol())
                .isEqualTo("AAPL");
    }


    // =========================================
    // ASSET VALIDATION
    // =========================================

    @Test
    void orderBookOfUnknownAssetReturnsNotFound() throws Exception {

        mvc.perform(
                        get("/api/order-book/UNKNOWN")
                                .principal(as(alice))
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.detail")
                                .value("Actif inconnu ou désactivé : UNKNOWN")
                );
    }


    @Test
    void orderBookOfInactiveAssetReturnsNotFound() throws Exception {

        createInactiveAsset();

        mvc.perform(
                        get("/api/order-book/" + INACTIVE_SYMBOL)
                                .principal(as(alice))
                )
                .andExpect(status().isNotFound());

        mvc.perform(
                        get("/api/order-book/" + INACTIVE_SYMBOL + "/stream")
                                .principal(as(alice))
                )
                .andExpect(status().isNotFound());
    }


    @Test
    void orderOnUnknownOrInactiveAssetIsRejected() {

        createInactiveAsset();

        assertRejected(
                () -> place(alice, "UNKNOWN", OrderSide.BUY, "1", "100"),
                HttpStatus.BAD_REQUEST
        );

        assertRejected(
                () -> place(alice, INACTIVE_SYMBOL, OrderSide.BUY, "1", "100"),
                HttpStatus.BAD_REQUEST
        );

        assertThat(orders.count()).isZero();
    }


    @Test
    void orderBookAggregatesOrdersByPriceLevel() {

        place(alice, "AAPL", OrderSide.BUY, "5", "100");
        place(carol, "AAPL", OrderSide.BUY, "2.5", "100.00");
        place(alice, "AAPL", OrderSide.BUY, "1", "99");
        place(bob, "AAPL", OrderSide.SELL, "4", "102");
        place(bob, "AAPL", OrderSide.SELL, "4", "101");

        OrderBookResponse book =
                orderBookService.getOrderBook("AAPL");

        assertThat(book.bids()).hasSize(2);

        assertThat(book.bids().get(0).price())
                .isEqualByComparingTo("100");

        assertThat(book.bids().get(0).totalQuantity())
                .isEqualByComparingTo("7.5");

        assertThat(book.bids().get(0).orderCount())
                .isEqualTo(2);

        assertThat(book.bids().get(1).price())
                .isEqualByComparingTo("99");

        // Meilleur ASK (le moins cher) en premier.
        assertThat(book.asks().get(0).price())
                .isEqualByComparingTo("101");

        assertThat(book.asks().get(1).price())
                .isEqualByComparingTo("102");
    }


    // =========================================
    // REAL TIME (SSE)
    // =========================================

    @Test
    void streamNotifiesSubscribersWhenTheBookChanges() throws Exception {

        MvcResult aaplStream =
                mvc.perform(
                                get("/api/order-book/AAPL/stream")
                                        .principal(as(alice))
                        )
                        .andExpect(request().asyncStarted())
                        .andReturn();

        MvcResult msftStream =
                mvc.perform(
                                get("/api/order-book/MSFT/stream")
                                        .principal(as(alice))
                        )
                        .andExpect(request().asyncStarted())
                        .andReturn();

        assertThat(aaplStream.getResponse().getContentAsString())
                .contains("event:connected")
                .doesNotContain("order-book-changed");

        OrderResponse sell =
                place(bob, "AAPL", OrderSide.SELL, "5", "100");

        assertThat(aaplStream.getResponse().getContentAsString())
                .containsOnlyOnce("event:order-book-changed");

        orderService.cancelOrder(sell.id(), bob.getEmail());

        assertThat(aaplStream.getResponse().getContentAsString())
                .satisfies(content ->
                        assertThat(
                                content.split("event:order-book-changed", -1)
                        ).hasSize(3)
                );

        // Un abonné d'un autre actif n'est pas notifié.
        assertThat(msftStream.getResponse().getContentAsString())
                .doesNotContain("order-book-changed");
    }


    /*
     * EventSource (navigateur) : requête cross-origin
     * authentifiée par le cookie de session.
     */
    @Test
    void streamRequiresAnAuthenticatedSessionAndAllowsTheFrontendOrigin()
            throws Exception {

        MockMvc securedMvc =
                MockMvcBuilders
                        .webAppContextSetup(context)
                        .addFilters(
                                context.getBean(
                                        "springSecurityFilterChain",
                                        Filter.class
                                )
                        )
                        .build();

        MvcResult anonymous =
                securedMvc.perform(
                                get("/api/order-book/AAPL/stream")
                                        .header("Origin", "http://localhost:4200")
                        )
                        .andReturn();

        assertThat(anonymous.getRequest().isAsyncStarted())
                .isFalse();

        assertThat(anonymous.getResponse().getStatus())
                .isNotEqualTo(200);

        SecurityContext securityContext =
                SecurityContextHolder.createEmptyContext();

        securityContext.setAuthentication(as(alice));

        MockHttpSession session = new MockHttpSession();

        session.setAttribute(
                HttpSessionSecurityContextRepository
                        .SPRING_SECURITY_CONTEXT_KEY,
                securityContext
        );

        securedMvc.perform(
                        get("/api/order-book/AAPL/stream")
                                .session(session)
                                .header("Origin", "http://localhost:4200")
                )
                .andExpect(request().asyncStarted())
                .andExpect(
                        header().string(
                                "Access-Control-Allow-Origin",
                                "http://localhost:4200"
                        )
                )
                .andExpect(
                        header().string(
                                "Access-Control-Allow-Credentials",
                                "true"
                        )
                );
    }


    // =========================================
    // END TO END (HTTP)
    // =========================================

    @Test
    void completeTradingScenarioThroughTheHttpApi() throws Exception {

        // 1. Les deux utilisateurs voient les actifs disponibles.
        mvc.perform(
                        get("/api/assets")
                                .principal(as(alice))
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[?(@.symbol == 'AAPL')]").exists()
                );


        // 2. Alice place deux ventes, le carnet les affiche.
        long aliceAsk100 =
                postOrder(alice, "AAPL", "SELL", "10", "100", "PENDING");

        long aliceAsk101 =
                postOrder(alice, "AAPL", "SELL", "5", "101", "PENDING");

        mvc.perform(
                        get("/api/order-book/AAPL")
                                .principal(as(bob))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assetSymbol").value("AAPL"))
                .andExpect(jsonPath("$.bids.length()").value(0))
                .andExpect(jsonPath("$.asks.length()").value(2))
                .andExpect(jsonPath("$.asks[0].price").value(100.0))
                .andExpect(jsonPath("$.asks[0].totalQuantity").value(10.0))
                .andExpect(jsonPath("$.asks[0].orderCount").value(1))
                .andExpect(jsonPath("$.asks[1].price").value(101.0));


        // 3. Bob achète 4 : exécution partielle de la vente d'Alice.
        long bobBuy1 =
                postOrder(bob, "AAPL", "BUY", "4", "100", "EXECUTED");

        mvc.perform(
                        get("/api/order-book/AAPL")
                                .principal(as(alice))
                )
                .andExpect(jsonPath("$.asks[0].totalQuantity").value(6.0));


        // 4. Bob achète 10 à 100 : 6 exécutés, 4 restent au carnet.
        long bobBuy2 =
                postOrder(
                        bob,
                        "AAPL",
                        "BUY",
                        "10",
                        "100",
                        "PARTIALLY_FILLED"
                );

        mvc.perform(
                        get("/api/order-book/AAPL")
                                .principal(as(alice))
                )
                .andExpect(jsonPath("$.bids.length()").value(1))
                .andExpect(jsonPath("$.bids[0].price").value(100.0))
                .andExpect(jsonPath("$.bids[0].totalQuantity").value(4.0))
                .andExpect(jsonPath("$.asks.length()").value(1))
                .andExpect(jsonPath("$.asks[0].price").value(101.0));


        // 5. Alice ne peut pas annuler l'ordre de Bob.
        mvc.perform(
                        patch("/api/orders/" + bobBuy2 + "/cancel")
                                .principal(as(alice))
                )
                .andExpect(status().isForbidden());


        // 6. Bob annule son reliquat, Alice annule sa vente à 101.
        mvc.perform(
                        patch("/api/orders/" + bobBuy2 + "/cancel")
                                .principal(as(bob))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.remainingQuantity").value(4.0));

        mvc.perform(
                        patch("/api/orders/" + aliceAsk101 + "/cancel")
                                .principal(as(alice))
                )
                .andExpect(status().isOk());

        // Un ordre exécuté n'est plus annulable.
        mvc.perform(
                        patch("/api/orders/" + aliceAsk100 + "/cancel")
                                .principal(as(alice))
                )
                .andExpect(status().isConflict());

        mvc.perform(
                        get("/api/order-book/AAPL")
                                .principal(as(alice))
                )
                .andExpect(jsonPath("$.bids.length()").value(0))
                .andExpect(jsonPath("$.asks.length()").value(0));


        // 7. Historique des ordres.
        mvc.perform(
                        get("/api/orders/me")
                                .principal(as(alice))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(
                        jsonPath(
                                "$[?(@.id == " + aliceAsk100 + ")].status"
                        ).value("EXECUTED")
                )
                .andExpect(
                        jsonPath(
                                "$[?(@.id == " + aliceAsk101 + ")].status"
                        ).value("CANCELLED")
                );

        mvc.perform(
                        get("/api/orders/me")
                                .principal(as(bob))
                )
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(
                        jsonPath(
                                "$[?(@.id == " + bobBuy1 + ")].status"
                        ).value("EXECUTED")
                )
                .andExpect(
                        jsonPath(
                                "$[?(@.id == " + bobBuy2 + ")].status"
                        ).value("CANCELLED")
                );


        // 8. Historique des transactions : 4 + 6, chacun de son côté.
        mvc.perform(
                        get("/api/trades/me")
                                .principal(as(alice))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].side").value("SELL"))
                .andExpect(jsonPath("$[1].side").value("SELL"))
                .andExpect(jsonPath("$[0].executionPrice").value(100.0));

        mvc.perform(
                        get("/api/trades/me")
                                .principal(as(bob))
                )
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].side").value("BUY"))
                .andExpect(jsonPath("$[1].side").value("BUY"));


        // 9. Requêtes invalides.
        mvc.perform(
                        post("/api/orders")
                                .principal(as(alice))
                                .contentType("application/json")
                                .content(orderJson("UNKNOWN", "BUY", "1", "100"))
                )
                .andExpect(status().isBadRequest());

        mvc.perform(
                        post("/api/orders")
                                .principal(as(alice))
                                .contentType("application/json")
                                .content(orderJson("AAPL", "BUY", "0", "100"))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").exists());

        mvc.perform(
                        post("/api/orders")
                                .principal(as(alice))
                                .contentType("application/json")
                                .content("""
                                    {
                                      "assetSymbol":"AAPL",
                                      "side":"BUY",
                                      "type":"LIMIT",
                                      "quantity":1
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest());

        // Ordre au marché sans contrepartie : enregistré mais rejeté.
        mvc.perform(
                        post("/api/orders")
                                .principal(as(alice))
                                .contentType("application/json")
                                .content("""
                                    {
                                      "assetSymbol":"AAPL",
                                      "side":"BUY",
                                      "type":"MARKET",
                                      "quantity":1
                                    }
                                    """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mvc.perform(
                        get("/api/order-book/UNKNOWN")
                                .principal(as(alice))
                )
                .andExpect(status().isNotFound());
    }


    // =========================================
    // HELPERS
    // =========================================

    private OrderResponse place(
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


    private long postOrder(
            User user,
            String symbol,
            String side,
            String quantity,
            String price,
            String expectedStatus
    ) throws Exception {

        MvcResult result =
                mvc.perform(
                                post("/api/orders")
                                        .principal(as(user))
                                        .contentType("application/json")
                                        .content(
                                                orderJson(
                                                        symbol,
                                                        side,
                                                        quantity,
                                                        price
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andExpect(
                                jsonPath("$.status")
                                        .value(expectedStatus)
                        )
                        .andReturn();

        Number id =
                JsonPath.read(
                        result.getResponse().getContentAsString(),
                        "$.id"
                );

        return id.longValue();
    }


    private String orderJson(
            String symbol,
            String side,
            String quantity,
            String price
    ) {

        return """
                {
                  "assetSymbol":"%s",
                  "side":"%s",
                  "type":"LIMIT",
                  "quantity":%s,
                  "limitPrice":%s
                }
                """.formatted(symbol, side, quantity, price);
    }


    private Authentication as(User user) {

        return new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                List.of()
        );
    }


    private void assertOrder(
            Long orderId,
            OrderStatus expectedStatus,
            String expectedRemaining
    ) {

        Order order =
                orders.findById(orderId).orElseThrow();

        assertThat(order.getStatus())
                .isEqualTo(expectedStatus);

        assertThat(order.getRemainingQuantity())
                .isEqualByComparingTo(expectedRemaining);
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


    private List<Trade> tradesInOrder() {

        return trades.findAll()
                .stream()
                .sorted(Comparator.comparing(Trade::getId))
                .toList();
    }


    private void createInactiveAsset() {

        Asset asset = new Asset();

        asset.setSymbol(INACTIVE_SYMBOL);
        asset.setName("Delisted asset");
        asset.setType(AssetType.STOCK);
        asset.setCurrency("USD");
        asset.setActive(false);

        assets.saveAndFlush(asset);
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
