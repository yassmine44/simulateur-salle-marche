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

import tn.esprit.simulateurbackend.dto.CreateOcoRequest;
import tn.esprit.simulateurbackend.dto.CreateOrderRequest;
import tn.esprit.simulateurbackend.dto.ModifyOrderRequest;
import tn.esprit.simulateurbackend.dto.OrderPreviewResponse;
import tn.esprit.simulateurbackend.dto.OrderResponse;
import tn.esprit.simulateurbackend.dto.TradeResponse;
import tn.esprit.simulateurbackend.entity.CancelReason;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.Role;
import tn.esprit.simulateurbackend.entity.TimeInForce;
import tn.esprit.simulateurbackend.entity.TriggerType;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.PortfolioAccountRepository;
import tn.esprit.simulateurbackend.repository.TradeRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;
import tn.esprit.simulateurbackend.service.OrderExpiryService;
import tn.esprit.simulateurbackend.service.OrderPreviewService;
import tn.esprit.simulateurbackend.service.OrderService;
import tn.esprit.simulateurbackend.service.PortfolioService;
import tn.esprit.simulateurbackend.service.TradeService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Durée de validité, qualité d'exécution, stop suiveur,
 * bracket, OCO et aperçu avant ordre.
 */
@SpringBootTest
class AdvancedOrderTests {

    @Autowired
    WebApplicationContext context;

    @Autowired
    UserRepository users;

    @Autowired
    OrderRepository orders;

    @Autowired
    TradeRepository trades;

    @Autowired
    PortfolioAccountRepository accounts;

    @Autowired
    OrderService orderService;

    @Autowired
    OrderPreviewService previewService;

    @Autowired
    OrderExpiryService expiryService;

    @Autowired
    TradeService tradeService;

    @Autowired
    PortfolioService portfolioService;

    User alice;
    User bob;
    User carol;


    @BeforeEach
    void setup() {

        cleanDatabase();

        alice = createUser("alice");
        bob = createUser("bob");
        carol = createUser("carol");
    }


    @AfterEach
    void cleanDatabase() {

        enforceLimits(false);

        trades.deleteAll();
        orders.deleteAll();
        accounts.deleteAll();
        users.deleteAll();
    }


    // =========================================
    // TIME IN FORCE
    // =========================================

    @Test
    void iocLimitOrderCancelsWhatCannotBeFilledAtOnce() {

        limit(bob, OrderSide.SELL, "4", "100");

        OrderResponse buy =
                place(alice, OrderSide.BUY, OrderType.LIMIT, "10", "100", TimeInForce.IOC);

        assertThat(buy.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(buy.cancelReason()).isEqualTo(CancelReason.IOC);
        assertThat(buy.remainingQuantity()).isEqualByComparingTo("6");

        assertThat(trades.count()).isEqualTo(1);

        // Le reste n'est pas dans le carnet.
        assertThat(orders.findById(buy.id()).orElseThrow().isOpen()).isFalse();
    }


    @Test
    void fokOrderIsEitherFullyFilledOrNotAtAll() {

        limit(bob, OrderSide.SELL, "4", "100");

        OrderResponse refused =
                place(alice, OrderSide.BUY, OrderType.LIMIT, "10", "100", TimeInForce.FOK);

        assertThat(refused.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(refused.cancelReason()).isEqualTo(CancelReason.FOK);
        assertThat(refused.remainingQuantity()).isEqualByComparingTo("10");

        assertThat(trades.count()).isZero();

        OrderResponse filled =
                place(alice, OrderSide.BUY, OrderType.LIMIT, "4", "100", TimeInForce.FOK);

        assertThat(filled.status()).isEqualTo(OrderStatus.EXECUTED);
    }


    @Test
    void marketOrdersAreImmediateAndConditionalOrdersWait() {

        assertRejected(
                () -> place(alice, OrderSide.BUY, OrderType.MARKET, "1", null, TimeInForce.GTC),
                HttpStatus.BAD_REQUEST
        );

        assertRejected(
                () -> orderService.createOrder(
                        alice.getEmail(),
                        new CreateOrderRequest(
                                "AAPL", OrderSide.SELL, OrderType.MARKET, BigDecimal.ONE, null,
                                TriggerType.STOP_LOSS, new BigDecimal("200"),
                                TimeInForce.IOC, null, null, null
                        )
                ),
                HttpStatus.BAD_REQUEST
        );

        // Sans précision, un ordre au marché est IOC.
        limit(bob, OrderSide.SELL, "1", "100");

        OrderResponse market =
                place(alice, OrderSide.BUY, OrderType.MARKET, "1", null, null);

        assertThat(market.timeInForce()).isEqualTo(TimeInForce.IOC);
    }


    @Test
    void dayOrderExpiresAtMidnight() {

        OrderResponse day =
                place(alice, OrderSide.BUY, OrderType.LIMIT, "1", "100", TimeInForce.DAY);

        OrderResponse gtc =
                place(alice, OrderSide.BUY, OrderType.LIMIT, "1", "99", null);

        assertThat(day.expiresAt()).isAfter(Instant.now());
        assertThat(day.expiresAt()).isBefore(Instant.now().plus(Duration.ofHours(25)));

        assertThat(gtc.timeInForce()).isEqualTo(TimeInForce.GTC);
        assertThat(gtc.expiresAt()).isNull();

        // Avant l'échéance : rien ne change.
        assertThat(expiryService.expireDueOrders(Instant.now())).isZero();

        // Après minuit : seul l'ordre DAY est annulé.
        assertThat(expiryService.expireDueOrders(day.expiresAt().plusSeconds(1))).isEqualTo(1);

        Order expired = orders.findById(day.id()).orElseThrow();

        assertThat(expired.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(expired.getCancelReason()).isEqualTo(CancelReason.EXPIRED);

        assertThat(orders.findById(gtc.id()).orElseThrow().isOpen()).isTrue();
    }


    @Test
    void cancelReasonsAreRecorded() {

        OrderResponse buy = limit(alice, OrderSide.BUY, "1", "100");

        OrderResponse replacement =
                orderService.modifyOrder(
                        buy.id(),
                        alice.getEmail(),
                        new ModifyOrderRequest(BigDecimal.ONE, new BigDecimal("101"))
                );

        assertThat(orders.findById(buy.id()).orElseThrow().getCancelReason())
                .isEqualTo(CancelReason.REPLACED);

        OrderResponse cancelled = orderService.cancelOrder(replacement.id(), alice.getEmail());

        assertThat(cancelled.cancelReason()).isEqualTo(CancelReason.USER);

        OrderResponse market = place(alice, OrderSide.BUY, OrderType.MARKET, "1", null, null);

        assertThat(market.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(market.cancelReason()).isEqualTo(CancelReason.NO_LIQUIDITY);
    }


    // =========================================
    // EXECUTION QUALITY
    // =========================================

    @Test
    void executionQualityComparesThePriceToTheArrivalMid() {

        limit(carol, OrderSide.BUY, "10", "99");
        limit(bob, OrderSide.SELL, "10", "101");

        // Prix milieu à l'arrivée : (99 + 101) / 2 = 100.
        OrderResponse buy = place(alice, OrderSide.BUY, OrderType.MARKET, "5", null, null);

        assertThat(buy.arrivalMidPrice()).isEqualByComparingTo("100");

        TradeResponse fill = tradeService.getMyTrades(alice.getEmail()).get(0);

        assertThat(fill.liquidity()).isEqualTo("TAKER");
        assertThat(fill.arrivalMidPrice()).isEqualByComparingTo("100");

        // (101 - 100) / 100 = 1 % = 100 points de base, 5 x 1 = 5 de coût.
        assertThat(fill.slippageBps()).isEqualByComparingTo("100");
        assertThat(fill.slippageAmount()).isEqualByComparingTo("5");

        assertThat(tradeService.getMyTrades(bob.getEmail()).get(0).liquidity())
                .isEqualTo("MAKER");
    }


    // =========================================
    // TRAILING STOP
    // =========================================

    @Test
    void trailingStopFollowsTheRiseAndTriggersOnTheFall() {

        tradeAt("100");

        OrderResponse trailing =
                orderService.createOrder(
                        carol.getEmail(),
                        new CreateOrderRequest(
                                "AAPL", OrderSide.SELL, OrderType.MARKET, new BigDecimal("5"), null,
                                TriggerType.STOP_LOSS, null,
                                null, new BigDecimal("5"), null, null
                        )
                );

        // 5 % sous 100.
        assertThat(trailing.triggerPrice()).isEqualByComparingTo("95");
        assertThat(trailing.trailPercent()).isEqualByComparingTo("5");

        // Le cours monte à 110 : le seuil remonte à 104,50.
        tradeAt("110");

        assertThat(order(trailing.id()).getTriggerPrice()).isEqualByComparingTo("104.5");

        // Le cours redescend à 108 : le seuil ne recule pas.
        tradeAt("108");

        assertThat(order(trailing.id()).getTriggerPrice()).isEqualByComparingTo("104.5");

        // Le cours passe à 104 : l'ordre se déclenche.
        limit(bob, OrderSide.BUY, "10", "104");
        limit(alice, OrderSide.SELL, "1", "104");

        Order triggered = order(trailing.id());

        assertThat(triggered.getTriggeredAt()).isNotNull();
        assertThat(triggered.getStatus()).isEqualTo(OrderStatus.EXECUTED);
    }


    @Test
    void onlyAStopLossCanTrail() {

        tradeAt("100");

        assertRejected(
                () -> orderService.createOrder(
                        carol.getEmail(),
                        new CreateOrderRequest(
                                "AAPL", OrderSide.SELL, OrderType.MARKET, BigDecimal.ONE, null,
                                TriggerType.TAKE_PROFIT, new BigDecimal("120"),
                                null, new BigDecimal("5"), null, null
                        )
                ),
                HttpStatus.BAD_REQUEST
        );
    }


    // =========================================
    // BRACKET
    // =========================================

    @Test
    void bracketProtectionsFollowTheExecutedQuantity() {

        OrderResponse entry = bracket(alice, "10", "100", "90", "120");

        assertThat(entry.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(legsOf(entry)).isEmpty();

        // 4 achetés : protections pour 4.
        limit(bob, OrderSide.SELL, "4", "100");

        List<Order> legs = legsOf(entry);

        assertThat(legs).hasSize(2);

        assertThat(legs)
                .allMatch(leg -> leg.getSide() == OrderSide.SELL)
                .allMatch(leg -> leg.getRemainingQuantity().compareTo(new BigDecimal("4")) == 0)
                .allMatch(leg -> entry.id().equals(leg.getParentOrderId()));

        Order takeProfit = takeProfitLeg(legs);

        assertThat(takeProfit.getLimitPrice()).isEqualByComparingTo("120");

        assertThat(stopLossLeg(legs).getTriggerPrice()).isEqualByComparingTo("90");

        // 6 de plus : protections pour 10.
        limit(bob, OrderSide.SELL, "6", "100");

        assertThat(legsOf(entry))
                .allMatch(leg -> leg.getRemainingQuantity().compareTo(BigDecimal.TEN) == 0);

        // Le take-profit est exécuté : le stop-loss est annulé.
        limit(carol, OrderSide.BUY, "10", "120");

        legs = legsOf(entry);

        assertThat(takeProfitLeg(legs).getStatus()).isEqualTo(OrderStatus.EXECUTED);

        Order stopLoss = stopLossLeg(legs);

        assertThat(stopLoss.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stopLoss.getCancelReason()).isEqualTo(CancelReason.OCO);
    }


    @Test
    void bracketStopLossCancelsTheTakeProfitWhenPricesFall() {

        limit(bob, OrderSide.SELL, "5", "100");

        OrderResponse entry = bracket(alice, "5", "100", "95", "130");

        assertThat(entry.status()).isEqualTo(OrderStatus.EXECUTED);

        // Le cours tombe à 94 : le stop-loss vend au marché.
        limit(bob, OrderSide.BUY, "10", "94");
        limit(carol, OrderSide.SELL, "1", "94");

        List<Order> legs = legsOf(entry);

        assertThat(stopLossLeg(legs).getStatus()).isEqualTo(OrderStatus.EXECUTED);

        assertThat(takeProfitLeg(legs).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(takeProfitLeg(legs).getCancelReason()).isEqualTo(CancelReason.OCO);
    }


    @Test
    void bracketPricesMustSurroundTheEntry() {

        assertRejected(() -> bracket(alice, "1", "100", "100", "120"), HttpStatus.BAD_REQUEST);
        assertRejected(() -> bracket(alice, "1", "100", "90", "100"), HttpStatus.BAD_REQUEST);

        // Pas de bracket sur une vente.
        assertRejected(
                () -> orderService.createOrder(
                        alice.getEmail(),
                        new CreateOrderRequest(
                                "AAPL", OrderSide.SELL, OrderType.LIMIT, BigDecimal.ONE, new BigDecimal("100"),
                                null, null, null, null, new BigDecimal("110"), new BigDecimal("90")
                        )
                ),
                HttpStatus.BAD_REQUEST
        );
    }


    // =========================================
    // OCO
    // =========================================

    @Test
    void ocoProtectsHeldSharesOnceAndCancelsAsAGroup() {

        enforceLimits(true);

        // Mise en place sans contrôle : Alice achète 10 à Bob.
        enforceLimits(false);
        tradeBetween(bob, alice, "10", "100");
        enforceLimits(true);

        List<OrderResponse> legs = oco(alice, "10", "110", "90");

        assertThat(legs).hasSize(2);
        assertThat(legs.get(0).ocoGroup()).isEqualTo(legs.get(1).ocoGroup());

        // Les deux branches réservent les mêmes 10 titres, une seule fois.
        assertThat(
                portfolioService.getBuyingPower(alice.getEmail(), "AAPL").reservedQuantity()
        ).isEqualByComparingTo("10");

        // Exécution partielle du take-profit : le stop-loss est réduit.
        limit(carol, OrderSide.BUY, "4", "110");

        List<Order> group = orders.findByOcoGroupOrderByIdAsc(legs.get(0).ocoGroup());

        assertThat(takeProfitLeg(group).getRemainingQuantity()).isEqualByComparingTo("6");
        assertThat(stopLossLeg(group).getRemainingQuantity()).isEqualByComparingTo("6");

        // Annuler une branche annule la protection entière.
        orderService.cancelOrder(takeProfitLeg(group).getId(), alice.getEmail());

        assertThat(order(stopLossLeg(group).getId()).getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // On ne protège pas plus que ce que l'on détient.
        // Le cours vaut maintenant 110 : take-profit au-dessus.
        assertRejected(() -> oco(alice, "7", "120", "90"), HttpStatus.CONFLICT);
    }


    @Test
    void ocoPricesMustSurroundTheMarketAndLegsCannotBeModified() {

        tradeAt("100");

        assertRejected(() -> oco(alice, "1", "100", "90"), HttpStatus.BAD_REQUEST);
        assertRejected(() -> oco(alice, "1", "110", "100"), HttpStatus.BAD_REQUEST);

        List<OrderResponse> legs = oco(alice, "1", "110", "90");

        OrderResponse takeProfit =
                legs.stream().filter(leg -> leg.type() == OrderType.LIMIT).findFirst().orElseThrow();

        assertRejected(
                () -> orderService.modifyOrder(
                        takeProfit.id(),
                        alice.getEmail(),
                        new ModifyOrderRequest(BigDecimal.ONE, new BigDecimal("112"))
                ),
                HttpStatus.CONFLICT
        );
    }


    // =========================================
    // PREVIEW
    // =========================================

    @Test
    void previewSimulatesTheFillWithoutPlacingAnything() {

        limit(bob, OrderSide.SELL, "5", "101");
        limit(carol, OrderSide.SELL, "5", "102");

        long ordersBefore = orders.count();

        OrderPreviewResponse preview =
                previewService.preview(
                        alice.getEmail(),
                        new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, new BigDecimal("8"), null)
                );

        assertThat(preview.accepted()).isTrue();
        assertThat(preview.immediateQuantity()).isEqualByComparingTo("8");

        // (5 x 101 + 3 x 102) / 8
        assertThat(preview.averagePrice()).isEqualByComparingTo("101.375");
        assertThat(preview.immediateAmount()).isEqualByComparingTo("811");
        assertThat(preview.levelsConsumed()).isEqualTo(2);
        assertThat(preview.cancelledQuantity()).isEqualByComparingTo("0");

        assertThat(preview.availableCashBefore()).isEqualByComparingTo("100000");
        assertThat(preview.availableCashAfter()).isEqualByComparingTo("99189");
        assertThat(preview.heldQuantityAfter()).isEqualByComparingTo("8");

        assertThat(orders.count()).isEqualTo(ordersBefore);
        assertThat(trades.count()).isZero();
    }


    @Test
    void previewOfARestingLimitOrderShowsTheReservation() {

        limit(bob, OrderSide.SELL, "5", "101");

        OrderPreviewResponse preview =
                previewService.preview(
                        alice.getEmail(),
                        new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, BigDecimal.TEN, new BigDecimal("90"))
                );

        assertThat(preview.accepted()).isTrue();
        assertThat(preview.immediateQuantity()).isEqualByComparingTo("0");
        assertThat(preview.restingQuantity()).isEqualByComparingTo("10");

        // 10 x 90 réservés.
        assertThat(preview.availableCashAfter()).isEqualByComparingTo("99100");

        assertThat(preview.warnings()).anyMatch(warning -> warning.contains("du marché"));
    }


    @Test
    void previewExplainsARefusal() {

        OrderPreviewResponse tick =
                previewService.preview(
                        alice.getEmail(),
                        new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, BigDecimal.ONE, new BigDecimal("100.005"))
                );

        assertThat(tick.accepted()).isFalse();
        assertThat(tick.refusalReason()).contains("pas de");

        enforceLimits(true);

        OrderPreviewResponse funds =
                previewService.preview(
                        alice.getEmail(),
                        new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal("2000"), new BigDecimal("200"))
                );

        assertThat(funds.accepted()).isFalse();
        assertThat(funds.refusalReason()).contains("Fonds insuffisants");

        // La simulation reste affichée pour comprendre le refus.
        assertThat(funds.restingQuantity()).isEqualByComparingTo("2000");
    }


    @Test
    void previewOfABracketShowsTheRewardRiskRatio() {

        OrderPreviewResponse preview =
                previewService.preview(
                        alice.getEmail(),
                        new CreateOrderRequest(
                                "AAPL", OrderSide.BUY, OrderType.LIMIT, BigDecimal.TEN, new BigDecimal("100"),
                                null, null, null, null, new BigDecimal("95"), new BigDecimal("115")
                        )
                );

        assertThat(preview.potentialGain()).isEqualByComparingTo("150");
        assertThat(preview.potentialLoss()).isEqualByComparingTo("50");
        assertThat(preview.rewardRiskRatio()).isEqualByComparingTo("3");
    }


    // =========================================
    // HTTP
    // =========================================

    @Test
    void previewAndOcoEndpoints() throws Exception {

        tradeBetween(bob, alice, "10", "100");

        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

        mvc.perform(
                        post("/api/orders/preview")
                                .principal(as(alice))
                                .contentType("application/json")
                                .content("""
                                    {"assetSymbol":"AAPL","side":"BUY","type":"LIMIT","quantity":1,"limitPrice":99}
                                    """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.restingQuantity").value(1));

        mvc.perform(
                        post("/api/orders/oco")
                                .principal(as(alice))
                                .contentType("application/json")
                                .content("""
                                    {"assetSymbol":"AAPL","quantity":10,"takeProfitPrice":110,"stopLossPrice":90}
                                    """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(
                        post("/api/orders/oco")
                                .principal(as(alice))
                                .contentType("application/json")
                                .content("""
                                    {"assetSymbol":"AAPL","quantity":0,"takeProfitPrice":110,"stopLossPrice":90}
                                    """)
                )
                .andExpect(status().isBadRequest());
    }


    // =========================================
    // HELPERS
    // =========================================

    private OrderResponse limit(User user, OrderSide side, String quantity, String price) {

        return place(user, side, OrderType.LIMIT, quantity, price, null);
    }


    private OrderResponse place(
            User user,
            OrderSide side,
            OrderType type,
            String quantity,
            String price,
            TimeInForce timeInForce
    ) {

        return orderService.createOrder(
                user.getEmail(),
                new CreateOrderRequest(
                        "AAPL", side, type, new BigDecimal(quantity),
                        price == null ? null : new BigDecimal(price),
                        null, null, timeInForce, null, null, null
                )
        );
    }


    private OrderResponse bracket(
            User user,
            String quantity,
            String price,
            String stopLoss,
            String takeProfit
    ) {

        return orderService.createOrder(
                user.getEmail(),
                new CreateOrderRequest(
                        "AAPL", OrderSide.BUY, OrderType.LIMIT, new BigDecimal(quantity), new BigDecimal(price),
                        null, null, null, null, new BigDecimal(stopLoss), new BigDecimal(takeProfit)
                )
        );
    }


    private List<OrderResponse> oco(User user, String quantity, String takeProfit, String stopLoss) {

        return orderService.createOco(
                user.getEmail(),
                new CreateOcoRequest(
                        "AAPL", new BigDecimal(quantity), new BigDecimal(takeProfit), new BigDecimal(stopLoss), null
                )
        );
    }


    // Fixe le dernier cours : Alice vend 1 à Bob.
    private void tradeAt(String price) {

        limit(alice, OrderSide.SELL, "1", price);
        limit(bob, OrderSide.BUY, "1", price);
    }


    private void tradeBetween(User seller, User buyer, String quantity, String price) {

        limit(seller, OrderSide.SELL, quantity, price);
        limit(buyer, OrderSide.BUY, quantity, price);
    }


    private List<Order> legsOf(OrderResponse entry) {

        return orders.findByOcoGroupOrderByIdAsc("BRK-" + entry.id());
    }


    private Order takeProfitLeg(List<Order> legs) {

        return legs.stream().filter(leg -> leg.getType() == OrderType.LIMIT).findFirst().orElseThrow();
    }


    private Order stopLossLeg(List<Order> legs) {

        return legs.stream().filter(leg -> leg.getTriggerType() != null).findFirst().orElseThrow();
    }


    private Order order(Long id) {

        return orders.findById(id).orElseThrow();
    }


    private void enforceLimits(boolean enabled) {

        ReflectionTestUtils.setField(portfolioService, "enforceLimits", enabled);
    }


    private void assertRejected(Runnable action, HttpStatus expected) {

        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(expected)
                );
    }


    private Authentication as(User user) {

        return new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of());
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
