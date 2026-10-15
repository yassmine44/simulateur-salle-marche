package tn.esprit.simulateurbackend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tn.esprit.simulateurbackend.dto.CreateOcoRequest;
import tn.esprit.simulateurbackend.dto.CreateOrderRequest;
import tn.esprit.simulateurbackend.dto.DashboardResponse;
import tn.esprit.simulateurbackend.dto.DashboardResponse.Insight;
import tn.esprit.simulateurbackend.dto.DashboardResponse.Protection;
import tn.esprit.simulateurbackend.dto.OrderResponse;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.Role;
import tn.esprit.simulateurbackend.entity.TimeInForce;
import tn.esprit.simulateurbackend.entity.TriggerType;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.PortfolioAccountRepository;
import tn.esprit.simulateurbackend.repository.TradeRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;
import tn.esprit.simulateurbackend.service.DashboardService;
import tn.esprit.simulateurbackend.service.OrderService;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Tableau de bord : phrase du jour, points d'attention,
 * protections, actifs suivis, habitudes et activité.
 *
 * Contrôle des fonds désactivé : Bob et Carol vendent sans détenir.
 */
@SpringBootTest
class DashboardTests {

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
    DashboardService dashboardService;

    @Autowired
    JdbcTemplate jdbc;

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
    // NEW USER
    // =========================================

    @Test
    void newUserSeesAWelcomeAndNothingToDo() {

        DashboardResponse dashboard = dashboardOf(alice);

        assertThat(dashboard.headline().text()).startsWith("Bienvenue");
        assertThat(dashboard.headline().tone()).isEqualTo("NEUTRAL");

        assertThat(dashboard.portfolio().totalValue()).isEqualByComparingTo("100000");
        assertThat(dashboard.performance()).isNotNull();

        assertThat(dashboard.insights()).isEmpty();
        assertThat(dashboard.protections()).isEmpty();
        assertThat(dashboard.watchlist()).isEmpty();
        assertThat(dashboard.activity()).isEmpty();

        assertThat(dashboard.habits().ordersPlaced()).isZero();
        assertThat(dashboard.habits().advice()).singleElement().asString().contains("premiers ordres");
    }


    // =========================================
    // HEADLINE
    // =========================================

    @Test
    void headlineDescribesTheDayAndItsDriver() {

        aliceBuys("10", "100");

        // Le cours passe à 110 : +100 pour Alice.
        trade(carol, bob, "AAPL", "1", "110");

        DashboardResponse.Headline headline = dashboardOf(alice).headline();

        assertThat(headline.dayChange()).isEqualByComparingTo("100");
        assertThat(headline.dayChangePercent()).isEqualByComparingTo("0.1");
        assertThat(headline.tone()).isEqualTo("POSITIVE");

        assertThat(headline.text())
                .contains("gagne 0,10 % aujourd'hui")
                .contains("porté par AAPL (+100,00)")
                .contains("mieux que la référence");
    }


    @Test
    void headlineForAPortfolioBackToCash() {

        aliceBuys("10", "100");
        trade(alice, bob, "AAPL", "10", "110");

        assertThat(dashboardOf(alice).headline().text())
                .startsWith("Vous êtes entièrement en liquidités")
                .contains("+100,00 USD");
    }


    // =========================================
    // INSIGHTS
    // =========================================

    @Test
    void insightsFlagConcentrationAndMissingProtection() {

        // 50 000 sur AAPL, soit la moitié du portefeuille.
        aliceBuys("500", "100");

        List<Insight> insights = dashboardOf(alice).insights();

        assertThat(insights).extracting(Insight::category)
                .contains("CONCENTRATION", "UNPROTECTED");

        Insight concentration =
                insights.stream().filter(insight -> insight.category().equals("CONCENTRATION")).findFirst().orElseThrow();

        assertThat(concentration.severity()).isEqualTo("WARNING");
        assertThat(concentration.title()).contains("AAPL");
        assertThat(concentration.message()).contains("rééquilibrage");
    }


    @Test
    void insightsFlagANearStopAndAnExpiringDayOrder() {

        aliceBuys("10", "100");

        // Stop à 99, cours 100 : 1 % d'écart.
        orderService.createOrder(
                alice.getEmail(),
                new CreateOrderRequest(
                        "AAPL", OrderSide.SELL, OrderType.MARKET, new BigDecimal("10"), null,
                        TriggerType.STOP_LOSS, new BigDecimal("99")
                )
        );

        orderService.createOrder(
                alice.getEmail(),
                new CreateOrderRequest(
                        "MSFT", OrderSide.BUY, OrderType.LIMIT, BigDecimal.ONE, new BigDecimal("400")
                ).withTimeInForce(TimeInForce.DAY)
        );

        List<Insight> insights = dashboardOf(alice).insights();

        assertThat(insights).extracting(Insight::category).contains("STOP_NEAR", "EXPIRING");

        Insight stop =
                insights.stream().filter(insight -> insight.category().equals("STOP_NEAR")).findFirst().orElseThrow();

        assertThat(stop.title()).contains("1,00 %");
        assertThat(stop.orderId()).isNotNull();
    }


    @Test
    void negativeCashIsTheMostUrgentInsight() {

        // Achat de 200 000 sans contrôle des fonds : liquidités négatives.
        trade(bob, carol, "AAPL", "2000", "100");

        List<Insight> insights = dashboardOf(carol).insights();

        assertThat(insights.get(0).severity()).isEqualTo("CRITICAL");
        assertThat(insights.get(0).category()).isEqualTo("CASH");

        assertThat(insights).hasSizeLessThanOrEqualTo(5);
    }


    @Test
    void anOldOrderFarFromTheMarketIsFlagged() {

        tradeAt("100");

        OrderResponse farBuy =
                orderService.createOrder(
                        alice.getEmail(),
                        new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.LIMIT, BigDecimal.ONE, new BigDecimal("80"))
                );

        // Placé il y a deux jours.
        jdbc.update(
                "UPDATE trade_orders SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(2))),
                farBuy.id()
        );

        Insight stale =
                dashboardOf(alice).insights().stream()
                        .filter(insight -> insight.category().equals("STALE_ORDER"))
                        .findFirst()
                        .orElseThrow();

        assertThat(stale.title()).contains("#" + farBuy.id()).contains("2 jour(s)");
        assertThat(stale.message()).contains("20,00 %");
    }


    // =========================================
    // PROTECTIONS
    // =========================================

    @Test
    void protectionShowsCoverageStopAndTarget() {

        aliceBuys("10", "100");

        orderService.createOco(
                alice.getEmail(),
                new CreateOcoRequest("AAPL", BigDecimal.TEN, new BigDecimal("120"), new BigDecimal("90"), null)
        );

        Protection protection = dashboardOf(alice).protections().get(0);

        assertThat(protection.status()).isEqualTo("PROTECTED");

        // Les deux branches de l'OCO protègent les mêmes 10 titres.
        assertThat(protection.protectedQuantity()).isEqualByComparingTo("10");
        assertThat(protection.stopPrice()).isEqualByComparingTo("90");
        assertThat(protection.stopDistancePercent()).isEqualByComparingTo("10");
        assertThat(protection.takeProfitPrice()).isEqualByComparingTo("120");
        assertThat(protection.takeProfitDistancePercent()).isEqualByComparingTo("20");

        // (100 - 90) x 10
        assertThat(protection.lossAtStop()).isEqualByComparingTo("100");
        assertThat(protection.trailing()).isFalse();
    }


    @Test
    void partialAndTrailingProtection() {

        aliceBuys("10", "100");

        orderService.createOrder(
                alice.getEmail(),
                new CreateOrderRequest(
                        "AAPL", OrderSide.SELL, OrderType.MARKET, new BigDecimal("4"), null,
                        TriggerType.STOP_LOSS, null, null, new BigDecimal("5"), null, null
                )
        );

        Protection protection = dashboardOf(alice).protections().get(0);

        assertThat(protection.status()).isEqualTo("PARTIAL");
        assertThat(protection.protectedQuantity()).isEqualByComparingTo("4");
        assertThat(protection.trailing()).isTrue();
        assertThat(protection.stopPrice()).isEqualByComparingTo("95");
    }


    // =========================================
    // WATCHLIST
    // =========================================

    @Test
    void watchlistShowsHeldAssetsThenAssetsWithOrders() {

        aliceBuys("10", "100");

        orderService.createOrder(
                alice.getEmail(),
                new CreateOrderRequest("MSFT", OrderSide.BUY, OrderType.LIMIT, BigDecimal.ONE, new BigDecimal("400"))
        );

        List<DashboardResponse.WatchedAsset> watchlist = dashboardOf(alice).watchlist();

        assertThat(watchlist).extracting(DashboardResponse.WatchedAsset::assetSymbol)
                .containsExactly("AAPL", "MSFT");

        DashboardResponse.WatchedAsset aapl = watchlist.get(0);

        assertThat(aapl.lastPrice()).isEqualByComparingTo("100");
        assertThat(aapl.sparkline()).isNotEmpty();
        assertThat(aapl.positionValue()).isEqualByComparingTo("1000");

        // Pas de position sur MSFT : seulement le marché.
        assertThat(watchlist.get(1).positionValue()).isNull();
    }


    // =========================================
    // HABITS
    // =========================================

    @Test
    void habitsTurnOrderStatisticsIntoAdvice() {

        limit(carol, "AAPL", OrderSide.BUY, "1", "99");
        limit(bob, "AAPL", OrderSide.SELL, "100", "101");

        // 3 achats au marché à 101 pour un milieu à 100, et un achat limite.
        for (int index = 0; index < 3; index++) {
            orderService.createOrder(
                    alice.getEmail(),
                    new CreateOrderRequest("AAPL", OrderSide.BUY, OrderType.MARKET, BigDecimal.ONE, null)
            );
        }

        limit(alice, "MSFT", OrderSide.BUY, "1", "400");

        DashboardResponse.Habits habits = dashboardOf(alice).habits();

        assertThat(habits.ordersPlaced()).isEqualTo(4);
        assertThat(habits.marketOrderPercent()).isEqualByComparingTo("75");
        assertThat(habits.limitOrderPercent()).isEqualByComparingTo("25");
        assertThat(habits.protectedEntryPercent()).isEqualByComparingTo("0");
        assertThat(habits.averageTakerSlippageBps()).isEqualByComparingTo("100");

        assertThat(habits.advice())
                .anyMatch(advice -> advice.contains("ordres au marché vous coûtent"))
                .anyMatch(advice -> advice.contains("Aucun de vos achats n'est protégé"));
    }


    // =========================================
    // ACTIVITY
    // =========================================

    @Test
    void activityTellsWhatHappenedNewestFirst() {

        aliceBuys("10", "100");

        OrderResponse cancelled =
                orderService.createOrder(
                        alice.getEmail(),
                        new CreateOrderRequest("MSFT", OrderSide.BUY, OrderType.LIMIT, BigDecimal.ONE, new BigDecimal("400"))
                );

        orderService.cancelOrder(cancelled.id(), alice.getEmail());

        orderService.createOco(
                alice.getEmail(),
                new CreateOcoRequest("AAPL", BigDecimal.TEN, new BigDecimal("120"), new BigDecimal("90"), null)
        );

        List<DashboardResponse.ActivityEvent> activity = dashboardOf(alice).activity();

        assertThat(activity).extracting(DashboardResponse.ActivityEvent::type)
                .contains("PLACED", "FILLED", "CANCELLED");

        // La protection OCO (deux ordres) n'est annoncée qu'une fois.
        assertThat(activity).filteredOn(event -> event.title().startsWith("Protection OCO")).hasSize(1);

        assertThat(activity)
                .filteredOn(event -> event.type().equals("CANCELLED"))
                .singleElement()
                .satisfies(event -> assertThat(event.detail()).isEqualTo("Annulé par vous."));

        assertThat(activity)
                .isSortedAccordingTo(
                        java.util.Comparator.comparing(DashboardResponse.ActivityEvent::time).reversed()
                );
    }


    // =========================================
    // HTTP
    // =========================================

    @Test
    void dashboardEndpointServesTheCaller() throws Exception {

        aliceBuys("10", "100");

        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

        mvc.perform(
                        get("/api/dashboard/me")
                                .principal(new UsernamePasswordAuthenticationToken(alice.getEmail(), null, List.of()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headline.text").isString())
                .andExpect(jsonPath("$.portfolio.openPositions.length()").value(1))
                .andExpect(jsonPath("$.protections[0].status").value("UNPROTECTED"))
                .andExpect(jsonPath("$.performance.benchmark.history.length()").value(7));
    }


    // =========================================
    // HELPERS
    // =========================================

    private DashboardResponse dashboardOf(User user) {

        return dashboardService.getDashboard(user.getEmail());
    }


    private void aliceBuys(String quantity, String price) {

        trade(bob, alice, "AAPL", quantity, price);
    }


    // Fixe le dernier cours d'AAPL entre Bob et Carol.
    private void tradeAt(String price) {

        trade(bob, carol, "AAPL", "1", price);
    }


    private void trade(User seller, User buyer, String symbol, String quantity, String price) {

        limit(seller, symbol, OrderSide.SELL, quantity, price);
        limit(buyer, symbol, OrderSide.BUY, quantity, price);
    }


    private void limit(User user, String symbol, OrderSide side, String quantity, String price) {

        orderService.createOrder(
                user.getEmail(),
                new CreateOrderRequest(symbol, side, OrderType.LIMIT, new BigDecimal(quantity), new BigDecimal(price))
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
