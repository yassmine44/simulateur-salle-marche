package tn.esprit.simulateurbackend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.CreateOrderRequest;
import tn.esprit.simulateurbackend.dto.PerformanceResponse;
import tn.esprit.simulateurbackend.dto.RebalanceExecutionResponse;
import tn.esprit.simulateurbackend.dto.RebalancePlanResponse;
import tn.esprit.simulateurbackend.dto.RebalanceRequest;
import tn.esprit.simulateurbackend.dto.StatementResponse;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.Role;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.PortfolioAccountRepository;
import tn.esprit.simulateurbackend.repository.TradeRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;
import tn.esprit.simulateurbackend.service.OrderService;
import tn.esprit.simulateurbackend.service.PortfolioAnalyticsService;
import tn.esprit.simulateurbackend.service.PortfolioService;
import tn.esprit.simulateurbackend.service.RebalanceService;
import tn.esprit.simulateurbackend.service.StatementService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Statistiques de trader, contribution par actif, qualité
 * d'exécution, référence, rééquilibrage et relevé.
 *
 * Contrôle des fonds désactivé : Bob et Carol vendent sans détenir.
 */
@SpringBootTest
class PortfolioAnalyticsTests {

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
    PortfolioAnalyticsService analyticsService;

    @Autowired
    RebalanceService rebalanceService;

    @Autowired
    StatementService statementService;

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

        trades.deleteAll();
        orders.deleteAll();
        accounts.deleteAll();
        users.deleteAll();
    }


    // =========================================
    // TRADER STATISTICS
    // =========================================

    @Test
    void statisticsSummariseClosedPositions() {

        // Gain : +100.
        aliceBuys("AAPL", "10", "100");
        aliceSells("AAPL", "10", "110");

        // Perte : -50.
        aliceBuys("MSFT", "10", "100");
        aliceSells("MSFT", "10", "95");

        PerformanceResponse.TraderStats stats = performance().stats();

        assertThat(stats.closedTrades()).isEqualTo(2);
        assertThat(stats.wins()).isEqualTo(1);
        assertThat(stats.losses()).isEqualTo(1);
        assertThat(stats.winRatePercent()).isEqualByComparingTo("50");
        assertThat(stats.averageWin()).isEqualByComparingTo("100");
        assertThat(stats.averageLoss()).isEqualByComparingTo("-50");

        // 100 / 50
        assertThat(stats.profitFactor()).isEqualByComparingTo("2");

        // (100 - 50) / 2
        assertThat(stats.expectancy()).isEqualByComparingTo("25");

        assertThat(stats.bestTrade().assetSymbol()).isEqualTo("AAPL");
        assertThat(stats.bestTrade().returnPercent()).isEqualByComparingTo("10");
        assertThat(stats.worstTrade().assetSymbol()).isEqualTo("MSFT");
        assertThat(stats.averageHoldingMinutes()).isNotNull();
    }


    @Test
    void statisticsAreEmptyWithoutClosedPositions() {

        aliceBuys("AAPL", "10", "100");

        PerformanceResponse.TraderStats stats = performance().stats();

        assertThat(stats.closedTrades()).isZero();
        assertThat(stats.winRatePercent()).isNull();
        assertThat(stats.profitFactor()).isNull();
        assertThat(stats.bestTrade()).isNull();
    }


    // =========================================
    // CONTRIBUTIONS
    // =========================================

    @Test
    void contributionsSplitTheResultByAsset() {

        aliceBuys("AAPL", "10", "100");
        aliceSells("AAPL", "5", "120");

        // Le cours d'AAPL passe à 90 : les 5 restants perdent 50.
        trade(carol, bob, "AAPL", "1", "90");

        aliceBuys("MSFT", "10", "100");
        trade(carol, bob, "MSFT", "1", "130");

        List<PerformanceResponse.Contribution> contributions = performance().contributions();

        assertThat(contributions).extracting(PerformanceResponse.Contribution::assetSymbol)
                .containsExactly("MSFT", "AAPL");

        PerformanceResponse.Contribution msft = contributions.get(0);

        // 10 x (130 - 100)
        assertThat(msft.unrealizedPnl()).isEqualByComparingTo("300");
        assertThat(msft.totalPnl()).isEqualByComparingTo("300");

        // 300 / 100 000
        assertThat(msft.contributionPercent()).isEqualByComparingTo("0.3");

        PerformanceResponse.Contribution aapl = contributions.get(1);

        // 5 x (120 - 100) réalisé, 5 x (90 - 100) latent.
        assertThat(aapl.realizedPnl()).isEqualByComparingTo("100");
        assertThat(aapl.unrealizedPnl()).isEqualByComparingTo("-50");
        assertThat(aapl.totalPnl()).isEqualByComparingTo("50");
    }


    // =========================================
    // EXECUTION QUALITY
    // =========================================

    @Test
    void executionQualityAggregatesTakerFills() {

        limit(carol, "AAPL", OrderSide.BUY, "10", "99");
        limit(bob, "AAPL", OrderSide.SELL, "10", "101");

        // Milieu 100, achat à 101 : 100 points de base.
        orderService.createOrder(
                alice.getEmail(),
                new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, new BigDecimal("5"), null)
        );

        PerformanceResponse.ExecutionQuality execution = performance().execution();

        assertThat(execution.takerFills()).isEqualTo(1);
        assertThat(execution.makerFills()).isZero();
        assertThat(execution.averageTakerSlippageBps()).isEqualByComparingTo("100");
        assertThat(execution.totalSlippageCost()).isEqualByComparingTo("5");
    }


    // =========================================
    // BENCHMARK
    // =========================================

    @Test
    void benchmarkIsAnEqualWeightBuyAndHold() {

        PerformanceResponse.Benchmark untouched = performance().benchmark();

        assertThat(untouched.assetCount()).isEqualTo(10);
        assertThat(untouched.currentValue()).isEqualByComparingTo("100000");
        assertThat(untouched.returnPercent()).isEqualByComparingTo("0");
        assertThat(untouched.history()).hasSize(7);

        // AAPL passe de 215 (référence) à 100.
        trade(bob, carol, "AAPL", "1", "100");

        PerformanceResponse.Benchmark benchmark = performance().benchmark();

        // 90 000 + 10 000 / 215 x 100
        assertThat(benchmark.currentValue()).isEqualByComparingTo("94651.16");
        assertThat(benchmark.returnPercent()).isEqualByComparingTo("-5.35");

        // Alice n'a rien fait : elle bat la référence de 5,35 points.
        assertThat(benchmark.portfolioReturnPercent()).isEqualByComparingTo("0");
        assertThat(benchmark.outperformancePercent()).isEqualByComparingTo("5.35");
    }


    // =========================================
    // REBALANCE
    // =========================================

    @Test
    void rebalancePlanAndExecutionReachTheTarget() {

        aliceBuys("AAPL", "10", "100");

        // Liquidité disponible pour les achats.
        limit(bob, "AAPL", OrderSide.SELL, "1000", "100");
        limit(carol, "MSFT", OrderSide.SELL, "1000", "100");

        RebalanceRequest request =
                new RebalanceRequest(
                        List.of(
                                new RebalanceRequest.Target("AAPL", new BigDecimal("20")),
                                new RebalanceRequest.Target("MSFT", new BigDecimal("10"))
                        )
                );

        RebalancePlanResponse plan = rebalanceService.plan(alice.getEmail(), request);

        // 99 000 de liquidités + 10 x 100.
        assertThat(plan.totalValue()).isEqualByComparingTo("100000");
        assertThat(plan.targetCashPercent()).isEqualByComparingTo("70");

        RebalancePlanResponse.Line aapl = plan.lines().get(0);

        assertThat(aapl.side()).isEqualTo(OrderSide.BUY);

        // (20 000 - 1 000) / 100
        assertThat(aapl.quantity()).isEqualByComparingTo("190");

        assertThat(plan.lines().get(1).quantity()).isEqualByComparingTo("100");

        assertThat(plan.estimatedCashAfter()).isEqualByComparingTo("70000");

        // Le plan n'a rien envoyé.
        assertThat(portfolioService.getPortfolio(alice.getEmail(), null).cash())
                .isEqualByComparingTo("99000");

        RebalanceExecutionResponse execution = rebalanceService.execute(alice.getEmail(), request);

        assertThat(execution.ordersSent()).isEqualTo(2);
        assertThat(execution.ordersFilled()).isEqualTo(2);

        assertThat(execution.results())
                .allMatch(result -> result.status() == OrderStatus.EXECUTED);

        var after = portfolioService.getPortfolio(alice.getEmail(), null);

        assertThat(after.cash()).isEqualByComparingTo("70000");

        // Un second passage ne trouve plus rien à faire.
        RebalancePlanResponse again = rebalanceService.plan(alice.getEmail(), request);

        assertThat(again.lines()).allMatch(line -> line.side() == null);
    }


    @Test
    void rebalanceScalesPurchasesToTheAvailableCash() {

        limit(bob, "AAPL", OrderSide.SELL, "2000", "100");

        // 5 000 réservés par un achat en attente.
        limit(alice, "MSFT", OrderSide.BUY, "100", "50");

        RebalancePlanResponse plan =
                rebalanceService.plan(
                        alice.getEmail(),
                        new RebalanceRequest(
                                List.of(new RebalanceRequest.Target("AAPL", new BigDecimal("100")))
                        )
                );

        RebalancePlanResponse.Line aapl = plan.lines().get(0);

        // Cible 1 000 actions (100 000), mais seulement 95 000 disponibles.
        assertThat(aapl.quantity()).isEqualByComparingTo("950");

        assertThat(plan.warnings()).anyMatch(warning -> warning.contains("réduits"));
    }


    @Test
    void rebalanceTargetsAreValidated() {

        assertBadRequest(
                () -> rebalanceService.plan(
                        alice.getEmail(),
                        new RebalanceRequest(
                                List.of(
                                        new RebalanceRequest.Target("AAPL", new BigDecimal("60")),
                                        new RebalanceRequest.Target("MSFT", new BigDecimal("50"))
                                )
                        )
                )
        );

        assertBadRequest(
                () -> rebalanceService.plan(
                        alice.getEmail(),
                        new RebalanceRequest(
                                List.of(
                                        new RebalanceRequest.Target("AAPL", new BigDecimal("10")),
                                        new RebalanceRequest.Target("aapl", new BigDecimal("10"))
                                )
                        )
                )
        );

        assertBadRequest(
                () -> rebalanceService.plan(
                        alice.getEmail(),
                        new RebalanceRequest(
                                List.of(new RebalanceRequest.Target("UNKNOWN", new BigDecimal("10")))
                        )
                )
        );
    }


    // =========================================
    // STATEMENT
    // =========================================

    @Test
    void statementSummarisesThePeriod() {

        aliceBuys("AAPL", "10", "100");
        aliceSells("AAPL", "4", "110");

        StatementResponse statement = statementService.statement(alice.getEmail(), null, null);

        assertThat(statement.holderName()).isEqualTo("alice Trader");
        assertThat(statement.accountNumber()).startsWith("SIM-");
        assertThat(statement.periodStart()).isEqualTo(LocalDate.now().withDayOfMonth(1));
        assertThat(statement.periodEnd()).isEqualTo(LocalDate.now());

        assertThat(statement.openingValue()).isEqualByComparingTo("100000");

        // 100 000 - 1 000 + 440 + 6 x 110
        assertThat(statement.closingValue()).isEqualByComparingTo("100100");
        assertThat(statement.netChange()).isEqualByComparingTo("100");
        assertThat(statement.realizedPnl()).isEqualByComparingTo("40");
        assertThat(statement.closingCash()).isEqualByComparingTo("99440");

        assertThat(statement.tradeCount()).isEqualTo(2);
        assertThat(statement.totalBought()).isEqualByComparingTo("1000");
        assertThat(statement.totalSold()).isEqualByComparingTo("440");

        assertThat(statement.positions()).hasSize(1);
        assertThat(statement.positions().get(0).quantity()).isEqualByComparingTo("6");
    }


    @Test
    void statementPeriodIsValidated() {

        LocalDate today = LocalDate.now();

        assertBadRequest(() -> statementService.statement(alice.getEmail(), today, today.minusDays(1)));

        assertBadRequest(() -> statementService.statement(alice.getEmail(), today.minusYears(2), today));

        // Une fin dans le futur est ramenée à aujourd'hui.
        assertThat(
                statementService.statement(alice.getEmail(), today, today.plusDays(10)).periodEnd()
        ).isEqualTo(today);
    }


    // =========================================
    // HTTP
    // =========================================

    @Test
    void analyticsEndpoints() throws Exception {

        aliceBuys("AAPL", "10", "100");

        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

        mvc.perform(get("/api/portfolio/me/performance?historyDays=30").principal(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.benchmark.history.length()").value(30))
                .andExpect(jsonPath("$.contributions.length()").value(1));

        mvc.perform(
                        post("/api/portfolio/me/rebalance/plan")
                                .principal(as(alice))
                                .contentType("application/json")
                                .content("""
                                    {"targets":[{"assetSymbol":"AAPL","targetPercent":5}]}
                                    """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].side").value("BUY"));

        mvc.perform(
                        post("/api/portfolio/me/rebalance/plan")
                                .principal(as(alice))
                                .contentType("application/json")
                                .content("""
                                    {"targets":[{"assetSymbol":"AAPL","targetPercent":150}]}
                                    """)
                )
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/portfolio/me/statement?from=2026-01-01&to=2026-01-31").principal(as(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tradeCount").value(0));

        mvc.perform(get("/api/portfolio/me/statement?from=pas-une-date").principal(as(alice)))
                .andExpect(status().isBadRequest());
    }


    // =========================================
    // HELPERS
    // =========================================

    private PerformanceResponse performance() {

        return analyticsService.getPerformance(alice.getEmail(), null);
    }


    private void aliceBuys(String symbol, String quantity, String price) {

        trade(bob, alice, symbol, quantity, price);
    }


    private void aliceSells(String symbol, String quantity, String price) {

        trade(alice, bob, symbol, quantity, price);
    }


    // Le vendeur place un ordre limite, l'acheteur l'exécute.
    private void trade(User seller, User buyer, String symbol, String quantity, String price) {

        limit(seller, symbol, OrderSide.SELL, quantity, price);
        limit(buyer, symbol, OrderSide.BUY, quantity, price);
    }


    private void limit(User user, String symbol, OrderSide side, String quantity, String price) {

        orderService.createOrder(
                user.getEmail(),
                new CreateOrderRequest(
                        symbol, side, OrderType.LIMIT, new BigDecimal(quantity), new BigDecimal(price)
                )
        );
    }


    private void assertBadRequest(Runnable action) {

        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
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
