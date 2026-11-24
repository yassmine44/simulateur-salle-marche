package tn.esprit.simulateurbackend.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import tn.esprit.simulateurbackend.dto.actuarial.AdminActuarialOverview;
import tn.esprit.simulateurbackend.dto.actuarial.PricingAssumptionsDto;
import tn.esprit.simulateurbackend.entity.ActuarialAdminAction;
import tn.esprit.simulateurbackend.entity.ActuarialQuote;
import tn.esprit.simulateurbackend.repository.ActuarialAdminActionRepository;
import tn.esprit.simulateurbackend.repository.ActuarialQuoteRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminActuarialServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ActuarialPricingClient client;
    private ActuarialQuoteRepository quotes;
    private ActuarialAdminActionRepository actions;
    private AdminActuarialService service;

    @BeforeEach
    void setUp() {
        client = mock(ActuarialPricingClient.class);
        quotes = mock(ActuarialQuoteRepository.class);
        actions = mock(ActuarialAdminActionRepository.class);
        service = new AdminActuarialService(client, quotes, actions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static JsonNode json(String text) {
        return JSON.readTree(text);
    }

    @Test
    void shouldCombineEngineStateAndUsageStatistics() {
        when(client.status()).thenReturn(json("{\"status\": \"ok\"}"));
        when(client.modelCards()).thenReturn(json("{\"frequency_model\": {}}"));
        when(quotes.count()).thenReturn(12L);
        when(quotes.countByCreatedAtAfter(NOW.minusSeconds(30L * 24 * 3600))).thenReturn(5L);
        when(quotes.countDistinctUsers()).thenReturn(3L);

        AdminActuarialOverview overview = service.overview();

        assertEquals("ok", overview.engine().path("status").asString());
        assertNull(overview.engineError());
        assertEquals(12L, overview.usage().totalQuotes());
        assertEquals(5L, overview.usage().quotesLast30Days());
        assertEquals(3L, overview.usage().distinctUsers());
    }

    @Test
    void shouldStillShowStatisticsWhenTheEngineIsDown() {
        when(client.status()).thenThrow(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Moteur actuariel injoignable.")
        );
        when(quotes.count()).thenReturn(7L);

        AdminActuarialOverview overview = service.overview();

        assertNull(overview.engine());
        assertEquals("Moteur actuariel injoignable.", overview.engineError());
        assertEquals(7L, overview.usage().totalQuotes());
    }

    @Test
    void shouldTraceAssumptionChangesWithBeforeAndAfter() {
        when(client.updateAssumptions(any())).thenReturn(json("""
                {"previous": {"expense_ratio": 0.25}, "current": {"expense_ratio": 0.2},
                 "status": {"status": "ok"}}
                """));

        service.updateAssumptions(" Admin@Example.TEST ", PricingAssumptionsDto.of(0.2, 0.0, 0.0));

        ArgumentCaptor<ActuarialAdminAction> saved = ArgumentCaptor.forClass(ActuarialAdminAction.class);
        verify(actions).save(saved.capture());
        ActuarialAdminAction action = saved.getValue();
        assertEquals(ActuarialAdminAction.Type.ASSUMPTIONS_UPDATED, action.getType());
        assertEquals("admin@example.test", action.getActorEmail());
        assertEquals(NOW, action.getCreatedAt());
        assertTrue(action.getDetails().contains("\"previous\":{\"expense_ratio\":0.25}"));
        assertTrue(action.getDetails().contains("\"current\":{\"expense_ratio\":0.2}"));
    }

    @Test
    void shouldNotTraceAFailedChange() {
        when(client.updateAssumptions(any())).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "refusé")
        );
        assertThrows(ResponseStatusException.class,
                () -> service.updateAssumptions("admin@example.test", PricingAssumptionsDto.of(0.2, 0.0, 0.0)));
        verifyNoInteractions(actions);
    }

    @Test
    void shouldTraceReloadWithServedVersions() {
        when(client.reload()).thenReturn(json("{\"modelVersions\": {\"frequency\": \"v2\", \"severity\": \"v2\"}}"));

        service.reload("admin@example.test");

        ArgumentCaptor<ActuarialAdminAction> saved = ArgumentCaptor.forClass(ActuarialAdminAction.class);
        verify(actions).save(saved.capture());
        assertEquals(ActuarialAdminAction.Type.MODELS_RELOADED, saved.getValue().getType());
        assertTrue(saved.getValue().getDetails().contains("v2"));
    }

    @Test
    void shouldListRecentQuotesOfAllUsersWithBoundedSize() {
        when(quotes.findAllByOrderByCreatedAtDesc(any())).thenReturn(List.of(
                ActuarialQuote.record("user@example.test", ActuarialFixtures.youngDriver(),
                        ActuarialFixtures.quoteResponse(), NOW)
        ));

        var items = service.recentQuotes(10_000);

        assertEquals("user@example.test", items.get(0).userEmail());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(quotes).findAllByOrderByCreatedAtDesc(page.capture());
        assertEquals(AdminActuarialService.MAX_LIST, page.getValue().getPageSize());
    }

    @Test
    void shouldTraceCurrencyChanges() {
        when(client.updateCurrency(any())).thenReturn(json("""
                {"previous": {"code": "TND"}, "current": {"code": "EUR"}}
                """));

        service.updateCurrency("admin@example.test",
                new tn.esprit.simulateurbackend.dto.actuarial.CurrencyRequest("EUR", "euro", 1.0, null));

        ArgumentCaptor<ActuarialAdminAction> saved = ArgumentCaptor.forClass(ActuarialAdminAction.class);
        verify(actions).save(saved.capture());
        assertEquals(ActuarialAdminAction.Type.CURRENCY_UPDATED, saved.getValue().getType());
        assertTrue(saved.getValue().getDetails().contains("\"current\":{\"code\":\"EUR\"}"));
    }

    @Test
    void typeConverterRoundTrips() {
        var converter = new ActuarialAdminAction.TypeConverter();
        for (ActuarialAdminAction.Type type : ActuarialAdminAction.Type.values()) {
            assertEquals(type, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(type)));
        }
        assertNull(converter.convertToDatabaseColumn(null));
    }
}
