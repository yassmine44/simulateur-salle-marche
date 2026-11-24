package tn.esprit.simulateurbackend.service;

import java.net.ConnectException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.actuarial.ChainLadderRequest;
import tn.esprit.simulateurbackend.dto.actuarial.PricingAssumptionsDto;
import tn.esprit.simulateurbackend.dto.actuarial.QuoteRequest;
import tn.esprit.simulateurbackend.dto.actuarial.QuoteResponse;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ActuarialPricingClientTest {

    private static final String ROOT = "http://ml-service/api/actuarial";

    private MockRestServiceServer server;
    private ActuarialPricingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(ROOT);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ActuarialPricingClient(builder.build());
    }

    @Test
    void shouldMapQuoteResponse() {
        server.expect(requestTo(ROOT + "/quote"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"area\":\"E\"")))
                .andRespond(withSuccess(ActuarialFixtures.QUOTE_JSON, MediaType.APPLICATION_JSON));

        QuoteResponse response = client.quote(ActuarialFixtures.quoteRequest());

        assertEquals(883.19, response.commercialPremium());
        assertEquals("20261124162401", response.modelVersions().frequency());
        assertEquals(2, response.explanation().contributions().size());
        assertEquals("TND", response.currency().code());
        assertEquals(3.36, response.currency().rateFromModel());
        assertEquals("hausse", response.explanation().contributions().get(0).direction());
        assertEquals("B12", response.explanation().contributions().get(1).value());
        server.verify();
    }

    @Test
    void shouldNotForwardTheResponseOnlyCommercialFactor() {
        server.expect(requestTo(ROOT + "/quote"))
                .andExpect(content().string(not(containsString("commercialFactor"))))
                .andExpect(content().string(not(containsString("ratioSumValid"))))
                .andExpect(content().string(containsString("\"expenseRatio\":0.2")))
                .andRespond(withSuccess(ActuarialFixtures.QUOTE_JSON, MediaType.APPLICATION_JSON));

        QuoteRequest request = new QuoteRequest(
                ActuarialFixtures.youngDriver(), 1.0,
                new PricingAssumptionsDto(0.2, 0.05, 0.03, 1.4), true, null
        );
        client.quote(request);
        server.verify();
    }

    @Test
    void shouldTranslateValidationErrorTo400WithReadableDetail() {
        String body = """
                {"detail": [{"loc": ["body", "profile", "area"],
                             "msg": "Value error, valeur inconnue 'Z'"}]}
                """;
        server.expect(requestTo(ROOT + "/quote"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .contentType(MediaType.APPLICATION_JSON).body(body));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class, () -> client.quote(ActuarialFixtures.quoteRequest())
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertTrue(error.getReason().contains("profile.area : Value error, valeur inconnue 'Z'"));
    }

    @Test
    void shouldTranslateUntrainedModelsTo503() {
        server.expect(requestTo(ROOT + "/status"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\": \"Lancez python -m actuarial.train.\"}"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, client::status);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertTrue(error.getReason().contains("python -m actuarial.train"));
    }

    @Test
    void shouldTranslateUnreachableEngineTo503() {
        server.expect(requestTo(ROOT + "/metadata"))
                .andRespond(request -> {
                    throw new ConnectException("Connection refused");
                });

        ResponseStatusException error = assertThrows(ResponseStatusException.class, client::metadata);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertTrue(error.getReason().contains("injoignable"));
    }

    @Test
    void shouldTranslateUnexpectedEngineErrorTo502() {
        server.expect(requestTo(ROOT + "/model-cards"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ResponseStatusException error = assertThrows(ResponseStatusException.class, client::modelCards);

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    }

    @Test
    void shouldFetchReportsByName() {
        server.expect(requestTo(ROOT + "/reports/pricing"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"champion\": \"poisson_gbm\"}", MediaType.APPLICATION_JSON));

        assertEquals("poisson_gbm", client.report("pricing").path("champion").asString());
        server.verify();
    }

    @Test
    void shouldPostTriangleWithNullFutureCells() {
        server.expect(requestTo(ROOT + "/reserving/chain-ladder"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("[[100.0,150.0,160.0],[110.0,170.0,null],[120.0]]")))
                .andExpect(content().string(not(containsString("origins"))))
                .andRespond(withSuccess("{\"total\": {\"reserve\": 123.4}}", MediaType.APPLICATION_JSON));

        var request = new ChainLadderRequest(
                null,
                java.util.Arrays.asList(
                        java.util.List.of(100.0, 150.0, 160.0),
                        java.util.Arrays.asList(110.0, 170.0, null),
                        java.util.List.of(120.0)
                ),
                0.95
        );

        assertEquals(123.4, client.chainLadder(request).path("total").path("reserve").asDouble());
        server.verify();
    }

    @Test
    void shouldExplainARejectedTriangle() {
        server.expect(requestTo(ROOT + "/reserving/chain-ladder"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\": \"Ligne 2 : les valeurs après la diagonale doivent être vides.\"}"));

        var request = new ChainLadderRequest(null, java.util.List.of(), null);
        ResponseStatusException error =
                assertThrows(ResponseStatusException.class, () -> client.chainLadder(request));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertTrue(error.getReason().contains("après la diagonale"));
    }

    @Test
    void shouldCallTheAdministrationRoutes() {
        server.expect(requestTo(ROOT + "/admin/assumptions"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(not(containsString("commercialFactor"))))
                .andRespond(withSuccess("{\"current\": {}}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ROOT + "/admin/reload"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\": \"ok\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ROOT + "/stress"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"frequencyShock\":0.2")))
                .andRespond(withSuccess("{\"segments\": []}", MediaType.APPLICATION_JSON));

        client.updateAssumptions(new PricingAssumptionsDto(0.2, 0.05, 0.02, 1.4));
        assertEquals("ok", client.reload().path("status").asString());
        client.stress(new tn.esprit.simulateurbackend.dto.actuarial.StressRequest(0.2, 0.0, "by_area"));
        server.verify();
    }

    @Test
    void shouldPutTheDisplayCurrency() {
        server.expect(requestTo(ROOT + "/admin/currency"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().string(containsString("\"code\":\"TND\"")))
                .andExpect(content().string(containsString("\"rateFromModel\":3.36")))
                .andRespond(withSuccess("{\"current\": {\"code\": \"TND\"}}", MediaType.APPLICATION_JSON));

        client.updateCurrency(new tn.esprit.simulateurbackend.dto.actuarial.CurrencyRequest(
                "TND", "dinar tunisien", 3.36, "BCT"));
        server.verify();
    }

    @Test
    void shouldExtractDetailFromAnyErrorBody() {
        assertEquals("simple", ActuarialPricingClient.detail("{\"detail\": \"simple\"}"));
        assertEquals("texte brut", ActuarialPricingClient.detail("texte brut"));
        assertEquals("aucun détail", ActuarialPricingClient.detail(""));
    }
}
