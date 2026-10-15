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

import tn.esprit.simulateurbackend.dto.BuyingPowerResponse;
import tn.esprit.simulateurbackend.dto.CreateOrderRequest;
import tn.esprit.simulateurbackend.dto.ModifyOrderRequest;
import tn.esprit.simulateurbackend.dto.OrderResponse;
import tn.esprit.simulateurbackend.dto.PortfolioResponse;
import tn.esprit.simulateurbackend.dto.PortfolioResponse.Position;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.Role;
import tn.esprit.simulateurbackend.entity.TriggerType;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.PortfolioAccountRepository;
import tn.esprit.simulateurbackend.repository.TradeRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;
import tn.esprit.simulateurbackend.service.MarketMakerService;
import tn.esprit.simulateurbackend.service.OrderService;
import tn.esprit.simulateurbackend.service.PortfolioService;
import tn.esprit.simulateurbackend.service.TradeService;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Portefeuille virtuel : valorisation, indicateurs,
 * contrôle avant ordre et réinitialisation.
 *
 * Capital de départ : 100 000. Prix de référence AAPL : 215.
 */
@SpringBootTest
class PortfolioTests {

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
    PortfolioService portfolioService;

    @Autowired
    TradeService tradeService;

    @Autowired
    MarketMakerService marketMakerService;

    User alice;
    User bob;
    User carol;


    @BeforeEach
    void setup() {

        cleanDatabase();

        alice = createUser("alice");
        bob = createUser("bob");
        carol = createUser("carol");

        ReflectionTestUtils.setField(
                marketMakerService,
                "tradeProbability",
                0.0
        );
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
    // VALUATION
    // =========================================

    @Test
    void newPortfolioIsTheInitialCapitalInCash() {

        PortfolioResponse portfolio = portfolioOf(alice, null);

        assertThat(portfolio.currency()).isEqualTo("USD");
        assertThat(portfolio.initialCapital()).isEqualByComparingTo("100000");
        assertThat(portfolio.cash()).isEqualByComparingTo("100000");
        assertThat(portfolio.availableCash()).isEqualByComparingTo("100000");
        assertThat(portfolio.totalValue()).isEqualByComparingTo("100000");
        assertThat(portfolio.totalPnl()).isEqualByComparingTo("0");
        assertThat(portfolio.tradeCount()).isZero();
        assertThat(portfolio.startedAt()).isNull();

        assertThat(portfolio.openPositions()).isEmpty();
        assertThat(portfolio.closedPositions()).isEmpty();

        assertThat(portfolio.allocation()).hasSize(1);
        assertThat(portfolio.allocation().get(0).key()).isEqualTo("CASH");
        assertThat(portfolio.allocation().get(0).percent()).isEqualByComparingTo("100");

        assertThat(portfolio.risk().overallLevel()).isEqualTo("LOW");

        assertThat(portfolio.historyDays()).isEqualTo(7);
        assertThat(portfolio.history()).hasSize(7);

        assertThat(portfolioOf(alice, 30).history()).hasSize(30);

        // Valeur non prévue : 7 jours par défaut.
        assertThat(portfolioOf(alice, 12).historyDays()).isEqualTo(7);
    }


    @Test
    void positionIsValuedAtTheLastMarketPrice() {

        // Mise en place sans contrôle : Bob vend sans détenir.
        trade(bob, alice, "AAPL", "10", "100");
        trade(carol, bob, "AAPL", "1", "110");

        PortfolioResponse portfolio = portfolioOf(alice, null);

        assertThat(portfolio.cash()).isEqualByComparingTo("99000");

        Position position = portfolio.openPositions().get(0);

        assertThat(position.assetSymbol()).isEqualTo("AAPL");
        assertThat(position.assetName()).isEqualTo("Apple");
        assertThat(position.assetType()).isEqualTo("STOCK");
        assertThat(position.direction()).isEqualTo("LONG");
        assertThat(position.quantity()).isEqualByComparingTo("10");
        assertThat(position.averagePrice()).isEqualByComparingTo("100");
        assertThat(position.lastPrice()).isEqualByComparingTo("110");
        assertThat(position.costBasis()).isEqualByComparingTo("1000");
        assertThat(position.marketValue()).isEqualByComparingTo("1100");
        assertThat(position.unrealizedPnl()).isEqualByComparingTo("100");
        assertThat(position.unrealizedPnlPercent()).isEqualByComparingTo("10");
        assertThat(position.openedAt()).isNotNull();

        // 1100 / 100100
        assertThat(position.weightPercent()).isEqualByComparingTo("1.10");

        assertThat(portfolio.totalValue()).isEqualByComparingTo("100100");
        assertThat(portfolio.totalPnl()).isEqualByComparingTo("100");
        assertThat(portfolio.totalPnlPercent()).isEqualByComparingTo("0.10");
        assertThat(portfolio.unrealizedPnl()).isEqualByComparingTo("100");

        // Répartition : actions + liquidités.
        assertThat(portfolio.allocation())
                .extracting(slice -> slice.key())
                .containsExactly("CASH", "STOCK");

        // Le dernier point de l'historique est la valeur actuelle.
        assertThat(portfolio.history().get(6).value())
                .isEqualByComparingTo("100100");
    }


    @Test
    void closedPositionKeepsItsRealizedResult() {

        trade(bob, alice, "AAPL", "10", "100");
        trade(alice, carol, "AAPL", "10", "90");

        PortfolioResponse portfolio = portfolioOf(alice, null);

        assertThat(portfolio.openPositions()).isEmpty();

        assertThat(portfolio.closedPositions()).hasSize(1);

        assertThat(portfolio.closedPositions().get(0).realizedPnl())
                .isEqualByComparingTo("-100");

        assertThat(portfolio.realizedPnl()).isEqualByComparingTo("-100");
        assertThat(portfolio.totalValue()).isEqualByComparingTo("99900");
        assertThat(portfolio.tradeCount()).isEqualTo(2);
    }


    @Test
    void riskIndicatorsDescribeExposureAndConcentration() {

        // 20 000 sur AAPL.
        trade(bob, alice, "AAPL", "100", "200");

        PortfolioResponse.Risk single = portfolioOf(alice, null).risk();

        assertThat(single.exposurePercent()).isEqualByComparingTo("20");
        assertThat(single.exposureLevel()).isEqualTo("LOW");

        assertThat(single.largestPositionSymbol()).isEqualTo("AAPL");
        assertThat(single.largestPositionPercent()).isEqualByComparingTo("20");
        assertThat(single.concentrationLevel()).isEqualTo("MEDIUM");

        assertThat(single.diversificationPercent()).isEqualByComparingTo("0");
        assertThat(single.effectivePositions()).isEqualByComparingTo("1");

        // + 20 000 sur MSFT : deux positions de même poids.
        trade(bob, alice, "MSFT", "50", "400");

        PortfolioResponse.Risk balanced = portfolioOf(alice, null).risk();

        assertThat(balanced.exposurePercent()).isEqualByComparingTo("40");
        assertThat(balanced.diversificationPercent()).isEqualByComparingTo("50");
        assertThat(balanced.effectivePositions()).isEqualByComparingTo("2");
        assertThat(balanced.volatilityPercent()).isNotNegative();
        assertThat(balanced.maxDrawdownPercent()).isNotNegative();
    }


    // =========================================
    // PRE-TRADE CHECK
    // =========================================

    @Test
    void purchaseBeyondTheAvailableCashIsRefused() {

        enforceLimits(true);

        // 500 x 215 = 107 500 > 100 000
        assertRefused(
                () -> limit(alice, "AAPL", OrderSide.BUY, "500", "215"),
                "Fonds insuffisants"
        );

        assertThat(orders.count()).isZero();

        OrderResponse buy =
                limit(alice, "AAPL", OrderSide.BUY, "400", "215");

        assertThat(buy.status()).isEqualTo(OrderStatus.PENDING);
    }


    @Test
    void restingBuyOrdersReserveCashUntilCancelled() {

        enforceLimits(true);

        // 400 x 200 = 80 000 réservés.
        OrderResponse first =
                limit(alice, "AAPL", OrderSide.BUY, "400", "200");

        BuyingPowerResponse power =
                portfolioService.getBuyingPower(alice.getEmail(), "AAPL");

        assertThat(power.cash()).isEqualByComparingTo("100000");
        assertThat(power.reservedCash()).isEqualByComparingTo("80000");
        assertThat(power.availableCash()).isEqualByComparingTo("20000");

        // 200 x 200 = 40 000 > 20 000 disponibles.
        assertRefused(
                () -> limit(alice, "MSFT", OrderSide.BUY, "200", "200"),
                "déjà réservés"
        );

        orderService.cancelOrder(first.id(), alice.getEmail());

        assertThat(
                limit(alice, "MSFT", OrderSide.BUY, "200", "200").status()
        ).isEqualTo(OrderStatus.PENDING);
    }


    @Test
    void sellingWithoutHoldingIsRefused() {

        enforceLimits(true);

        assertRefused(
                () -> limit(alice, "AAPL", OrderSide.SELL, "1", "215"),
                "ne détenez pas"
        );

        assertRefused(
                () -> market(alice, "AAPL", OrderSide.SELL, "1"),
                "ne détenez pas"
        );

        assertRefused(
                () -> conditional(
                        alice,
                        OrderSide.SELL,
                        TriggerType.STOP_LOSS,
                        "200"
                ),
                "ne détenez pas"
        );
    }


    @Test
    void sharesBoughtFromTheMarketMakerCanBeSoldButNotTwice() {

        enforceLimits(true);

        marketMakerService.quoteAsset("AAPL");

        OrderResponse buy = market(alice, "AAPL", OrderSide.BUY, "10");

        assertThat(buy.status()).isEqualTo(OrderStatus.EXECUTED);

        // 6 engagés dans une vente en attente (au-dessus du marché).
        limit(alice, "AAPL", OrderSide.SELL, "6", "250");

        BuyingPowerResponse power =
                portfolioService.getBuyingPower(alice.getEmail(), "aapl");

        assertThat(power.heldQuantity()).isEqualByComparingTo("10");
        assertThat(power.reservedQuantity()).isEqualByComparingTo("6");
        assertThat(power.availableQuantity()).isEqualByComparingTo("4");

        assertRefused(
                () -> limit(alice, "AAPL", OrderSide.SELL, "5", "250"),
                "au plus 4"
        );

        assertThat(
                limit(alice, "AAPL", OrderSide.SELL, "4", "251").status()
        ).isEqualTo(OrderStatus.PENDING);

        Position position = portfolioOf(alice, null).openPositions().get(0);

        assertThat(position.reservedQuantity()).isEqualByComparingTo("10");
        assertThat(position.availableQuantity()).isEqualByComparingTo("0");
    }


    @Test
    void marketPurchaseIsCheckedAgainstTheBookItWouldConsume() {

        enforceLimits(true);

        marketMakerService.quoteAsset("AAPL");

        // 460 x 200 = 92 000 réservés : il reste 8 000.
        limit(alice, "AAPL", OrderSide.BUY, "460", "200");

        // 100 actions vers 215 coûtent environ 21 500.
        assertRefused(
                () -> market(alice, "AAPL", OrderSide.BUY, "100"),
                "Fonds insuffisants"
        );

        assertThat(
                market(alice, "AAPL", OrderSide.BUY, "10").status()
        ).isEqualTo(OrderStatus.EXECUTED);
    }


    @Test
    void modificationIsCheckedWithoutCountingTheReplacedOrder() {

        enforceLimits(true);

        OrderResponse buy =
                limit(alice, "AAPL", OrderSide.BUY, "400", "200");

        // 450 x 200 = 90 000 : possible, l'ancien ordre est libéré.
        OrderResponse replacement =
                orderService.modifyOrder(
                        buy.id(),
                        alice.getEmail(),
                        new ModifyOrderRequest(
                                new BigDecimal("450"),
                                new BigDecimal("200")
                        )
                );

        assertThat(replacement.status()).isEqualTo(OrderStatus.PENDING);

        // 600 x 200 = 120 000 : refusé, l'ordre en place est intact.
        assertRefused(
                () -> orderService.modifyOrder(
                        replacement.id(),
                        alice.getEmail(),
                        new ModifyOrderRequest(
                                new BigDecimal("600"),
                                new BigDecimal("200")
                        )
                ),
                "Fonds insuffisants"
        );

        assertThat(orders.findById(replacement.id()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING);
    }


    // =========================================
    // RESET
    // =========================================

    @Test
    void resetRestoresTheCapitalAndCancelsOpenOrders() {

        trade(bob, alice, "AAPL", "10", "100");

        OrderResponse resting =
                limit(alice, "MSFT", OrderSide.BUY, "5", "300");

        PortfolioResponse reset = portfolioService.reset(alice.getEmail());

        assertThat(reset.cash()).isEqualByComparingTo("100000");
        assertThat(reset.totalValue()).isEqualByComparingTo("100000");
        assertThat(reset.openPositions()).isEmpty();
        assertThat(reset.tradeCount()).isZero();
        assertThat(reset.resetCount()).isEqualTo(1);
        assertThat(reset.startedAt()).isNotNull();

        assertThat(orders.findById(resting.id()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);

        // L'historique des transactions reste consultable.
        assertThat(tradeService.getMyTrades(alice.getEmail())).hasSize(1);

        // Seules les nouvelles transactions comptent.
        trade(bob, alice, "AAPL", "2", "100");

        PortfolioResponse after = portfolioOf(alice, null);

        assertThat(after.tradeCount()).isEqualTo(1);
        assertThat(after.openPositions().get(0).quantity()).isEqualByComparingTo("2");
        assertThat(after.cash()).isEqualByComparingTo("99800");

        // Le portefeuille des autres utilisateurs n'est pas touché.
        assertThat(portfolioOf(bob, null).tradeCount()).isEqualTo(2);
    }


    // =========================================
    // HTTP
    // =========================================

    @Test
    void portfolioEndpointsServeOnlyTheCallersData() throws Exception {

        trade(bob, alice, "AAPL", "10", "100");

        MockMvc mvc =
                MockMvcBuilders
                        .webAppContextSetup(context)
                        .build();

        mvc.perform(
                        get("/api/portfolio/me?historyDays=30")
                                .principal(as(alice))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cash").value(99000.0))
                .andExpect(jsonPath("$.openPositions.length()").value(1))
                .andExpect(jsonPath("$.openPositions[0].quantity").value(10))
                .andExpect(jsonPath("$.historyDays").value(30))
                .andExpect(jsonPath("$.history.length()").value(30));

        mvc.perform(
                        get("/api/portfolio/me/buying-power?symbol=AAPL")
                                .principal(as(alice))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCash").value(99000.0))
                .andExpect(jsonPath("$.heldQuantity").value(10))
                .andExpect(jsonPath("$.availableQuantity").value(10));

        mvc.perform(
                        get("/api/portfolio/me")
                                .principal(as(carol))
                )
                .andExpect(jsonPath("$.cash").value(100000.0))
                .andExpect(jsonPath("$.openPositions.length()").value(0));

        mvc.perform(
                        post("/api/portfolio/me/reset")
                                .principal(as(alice))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cash").value(100000.0))
                .andExpect(jsonPath("$.resetCount").value(1));
    }


    // =========================================
    // HELPERS
    // =========================================

    // Le vendeur place un ordre limite, l'acheteur l'exécute.
    private void trade(
            User seller,
            User buyer,
            String symbol,
            String quantity,
            String price
    ) {

        limit(seller, symbol, OrderSide.SELL, quantity, price);
        limit(buyer, symbol, OrderSide.BUY, quantity, price);
    }


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
            OrderSide side,
            TriggerType triggerType,
            String triggerPrice
    ) {

        return orderService.createOrder(
                user.getEmail(),
                new CreateOrderRequest(
                        "AAPL",
                        side,
                        OrderType.MARKET,
                        BigDecimal.ONE,
                        null,
                        triggerType,
                        new BigDecimal(triggerPrice)
                )
        );
    }


    private PortfolioResponse portfolioOf(
            User user,
            Integer historyDays
    ) {

        return portfolioService.getPortfolio(
                user.getEmail(),
                historyDays
        );
    }


    private void enforceLimits(boolean enabled) {

        ReflectionTestUtils.setField(
                portfolioService,
                "enforceLimits",
                enabled
        );
    }


    private void assertRefused(
            Runnable action,
            String expectedMessagePart
    ) {

        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> {

                            assertThat(exception.getStatusCode())
                                    .isEqualTo(HttpStatus.CONFLICT);

                            assertThat(exception.getReason())
                                    .contains(expectedMessagePart);
                        }
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
