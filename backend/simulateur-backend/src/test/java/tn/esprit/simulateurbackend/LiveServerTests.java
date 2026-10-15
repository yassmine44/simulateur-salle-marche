package tn.esprit.simulateurbackend;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Scénario sur un vrai serveur HTTP, avec la chaîne de
 * sécurité complète et le teneur de marché planifié :
 *
 * inscription -> connexion -> jeton CSRF -> carnet alimenté
 * par le teneur de marché -> ordre au marché -> notification
 * temps réel (SSE) -> données de marché.
 *
 * Base H2 dédiée : le teneur de marché tourne en tâche de fond
 * et ne doit pas interférer avec les autres classes de test.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:live-server-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "app.market-maker.enabled=true",
                "app.market-maker.initial-delay-ms=200",
                "app.market-maker.interval-ms=300",
                "app.market-maker.trade-probability=1",
                "app.trading.price-band-percent=20",
                // Petit pool : un flux qui garderait une connexion le bloquerait vite.
                "spring.datasource.hikari.maximum-pool-size=4",
                "spring.datasource.hikari.connection-timeout=3000"
        }
)
@DirtiesContext
class LiveServerTests {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @Value("${local.server.port}")
    int port;

    private final CookieManager cookies =
            new CookieManager();

    private final HttpClient client =
            HttpClient.newBuilder()
                    .cookieHandler(cookies)
                    .build();


    @Test
    void aSingleUserTradesAgainstTheMarketMakerOverRealHttp()
            throws Exception {

        // =====================================
        // 1. Sans session : accès refusé
        // =====================================

        HttpClient anonymous = HttpClient.newHttpClient();

        assertThat(
                anonymous.send(
                        get("/api/order-book/AAPL"),
                        HttpResponse.BodyHandlers.ofString()
                ).statusCode()
        ).isNotEqualTo(200);


        // =====================================
        // 2. Inscription + connexion
        // =====================================

        assertThat(
                post(
                        "/api/auth/register",
                        """
                        {
                          "firstName":"Live",
                          "lastName":"Trader",
                          "countryCode":"TN",
                          "phoneNumber":"22 123 456",
                          "email":"live.trader@example.com",
                          "password":"StrongPassword123!",
                          "recaptchaToken":"test-token"
                        }
                        """,
                        null
                ).statusCode()
        ).isEqualTo(201);

        assertThat(
                post(
                        "/api/auth/login",
                        """
                        {
                          "email":"live.trader@example.com",
                          "password":"StrongPassword123!",
                          "recaptchaToken":"test-token"
                        }
                        """,
                        null
                ).statusCode()
        ).isEqualTo(200);


        // =====================================
        // 3. Le teneur de marché alimente le carnet
        // =====================================

        await(() -> {

            String book = body("/api/order-book/AAPL");

            int bids = JsonPath.read(book, "$.bids.length()");
            int asks = JsonPath.read(book, "$.asks.length()");

            return bids == 5 && asks == 5;
        });

        String book = body("/api/order-book/AAPL");

        BigDecimal bestBid = number(book, "$.bids[0].price");
        BigDecimal bestAsk = number(book, "$.asks[0].price");

        assertThat(bestBid).isLessThan(bestAsk);


        // =====================================
        // 4. Flux temps réel
        // =====================================

        List<String> events = new CopyOnWriteArrayList<>();

        client.sendAsync(
                get("/api/order-book/AAPL/stream"),
                HttpResponse.BodyHandlers.ofLines()
        ).thenAccept(response ->
                response.body().forEach(events::add)
        );

        await(() -> events.contains("event:connected"));

        // Chaque cycle du teneur de marché notifie les abonnés.
        await(() -> events.contains("event:order-book-changed"));


        // =====================================
        // 5. Ordre au marché : CSRF obligatoire
        // =====================================

        String marketOrder = """
                {
                  "assetSymbol":"AAPL",
                  "side":"BUY",
                  "type":"MARKET",
                  "quantity":1
                }
                """;

        assertThat(
                post("/api/orders", marketOrder, null).statusCode()
        ).isEqualTo(403);

        // Comme le frontend Angular : le jeton est lu dans le
        // cookie XSRF-TOKEN et renvoyé dans l'en-tête X-XSRF-TOKEN.
        body("/api/csrf");

        String csrfToken =
                cookies.getCookieStore()
                        .getCookies()
                        .stream()
                        .filter(cookie ->
                                cookie.getName().equals("XSRF-TOKEN")
                        )
                        .map(HttpCookie::getValue)
                        .findFirst()
                        .orElseThrow();

        HttpResponse<String> created =
                post("/api/orders", marketOrder, csrfToken);

        assertThat(created.statusCode()).isEqualTo(201);

        assertThat(
                JsonPath.<String>read(created.body(), "$.status")
        ).isEqualTo("EXECUTED");


        // =====================================
        // 6. Historique + données de marché
        // =====================================

        String myTrades = body("/api/trades/me");

        assertThat(
                JsonPath.<Integer>read(myTrades, "$.length()")
        ).isEqualTo(1);

        assertThat(
                JsonPath.<String>read(myTrades, "$[0].side")
        ).isEqualTo("BUY");

        String market = body("/api/market/AAPL?interval=1m");

        // Prix de référence 215 : le cours reste dans son voisinage.
        assertThat(number(market, "$.summary.lastPrice"))
                .isBetween(
                        new BigDecimal("200"),
                        new BigDecimal("230")
                );

        assertThat(
                JsonPath.<Integer>read(market, "$.recentTrades.length()")
        ).isPositive();

        assertThat(
                JsonPath.<Integer>read(market, "$.candles.length()")
        ).isPositive();
    }


    /*
     * Régression : les flux temps réel ne doivent pas immobiliser
     * de connexion à la base. Plus de flux ouverts que de connexions
     * dans le pool, et l'application répond toujours.
     */
    @Test
    void openStreamsDoNotExhaustTheConnectionPool()
            throws Exception {

        assertThat(
                post(
                        "/api/auth/register",
                        """
                        {
                          "firstName":"Stream",
                          "lastName":"Watcher",
                          "countryCode":"TN",
                          "phoneNumber":"22 654 321",
                          "email":"stream.watcher@example.com",
                          "password":"StrongPassword123!",
                          "recaptchaToken":"test-token"
                        }
                        """,
                        null
                ).statusCode()
        ).isEqualTo(201);

        assertThat(
                post(
                        "/api/auth/login",
                        """
                        {
                          "email":"stream.watcher@example.com",
                          "password":"StrongPassword123!",
                          "recaptchaToken":"test-token"
                        }
                        """,
                        null
                ).statusCode()
        ).isEqualTo(200);

        List<String> events = new CopyOnWriteArrayList<>();

        // 6 flux pour un pool de 4 connexions.
        for (String symbol : List.of("AAPL", "MSFT", "NVDA", "TSLA", "AMZN", "META")) {

            client.sendAsync(
                    get("/api/order-book/" + symbol + "/stream"),
                    HttpResponse.BodyHandlers.ofLines()
            ).thenAccept(response ->
                    response.body().forEach(events::add)
            );
        }

        await(() ->
                events.stream().filter("event:connected"::equals).count() == 6
        );

        // Requêtes ordinaires : elles obtiennent toujours une connexion.
        for (int attempt = 0; attempt < 5; attempt++) {

            long started = System.nanoTime();

            body("/api/order-book/AAPL");

            assertThat(Duration.ofNanos(System.nanoTime() - started))
                    .isLessThan(Duration.ofSeconds(2));
        }
    }


    // =========================================
    // HELPERS
    // =========================================

    private HttpRequest get(String path) {

        return HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofMinutes(1))
                .GET()
                .build();
    }


    private String body(String path) throws Exception {

        HttpResponse<String> response =
                client.send(
                        get(path),
                        HttpResponse.BodyHandlers.ofString()
                );

        assertThat(response.statusCode()).isEqualTo(200);

        return response.body();
    }


    private HttpResponse<String> post(
            String path,
            String json,
            String csrfToken
    ) throws Exception {

        HttpRequest.Builder request =
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json));

        if (csrfToken != null) {
            request.header("X-XSRF-TOKEN", csrfToken);
        }

        return client.send(
                request.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }


    private URI uri(String path) {

        return URI.create("http://localhost:" + port + path);
    }


    private BigDecimal number(String json, String path) {

        Object value = JsonPath.read(json, path);

        return new BigDecimal(value.toString());
    }


    private void await(
            ThrowingCondition condition
    ) throws Exception {

        long deadline =
                System.nanoTime() + TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {

            if (condition.isMet()) {
                return;
            }

            Thread.sleep(100);
        }

        throw new AssertionError(
                "Condition non atteinte après " + TIMEOUT.toSeconds() + " s."
        );
    }


    @FunctionalInterface
    private interface ThrowingCondition {

        boolean isMet() throws Exception;
    }
}
