package tn.esprit.simulateurbackend.service;

import java.math.BigDecimal;
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

import tn.esprit.simulateurbackend.dto.actuarial.QuoteHistoryItem;
import tn.esprit.simulateurbackend.dto.actuarial.QuoteResponse;
import tn.esprit.simulateurbackend.entity.ActuarialQuote;
import tn.esprit.simulateurbackend.repository.ActuarialQuoteRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ActuarialServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

    private ActuarialPricingClient client;
    private ActuarialQuoteRepository repository;
    private ActuarialService service;

    @BeforeEach
    void setUp() {
        client = mock(ActuarialPricingClient.class);
        repository = mock(ActuarialQuoteRepository.class);
        service = new ActuarialService(client, repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldReturnQuoteAndTraceIt() {
        QuoteResponse response = ActuarialFixtures.quoteResponse();
        when(client.quote(any())).thenReturn(response);

        QuoteResponse result = service.quote("  Trader@Example.TEST ", ActuarialFixtures.quoteRequest());

        assertSame(response, result);
        ArgumentCaptor<ActuarialQuote> saved = ArgumentCaptor.forClass(ActuarialQuote.class);
        verify(repository).save(saved.capture());
        ActuarialQuote quote = saved.getValue();

        assertEquals("trader@example.test", quote.getUserEmail());
        assertEquals(NOW, quote.getCreatedAt());
        assertEquals(20, quote.getDriverAge());
        assertEquals("E", quote.getArea());
        assertEquals(new BigDecimal("600.23"), quote.getPurePremium());
        assertEquals(new BigDecimal("883.19"), quote.getCommercialPremium());
        assertEquals(new BigDecimal("282.96"), quote.getPremiumMargin());
        assertEquals(0.25, quote.getExpenseRatio());
        assertEquals("20261124162401", quote.getFrequencyModelVersion());
        assertEquals("20261124162402", quote.getSeverityModelVersion());
    }

    @Test
    void shouldNotTraceWhenTheEngineFails() {
        when(client.quote(any())).thenThrow(
                new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "indisponible")
        );

        assertThrows(ResponseStatusException.class,
                () -> service.quote("trader@example.test", ActuarialFixtures.quoteRequest()));
        verifyNoInteractions(repository);
    }

    @Test
    void shouldReturnHistoryOfTheUserWithBoundedSize() {
        ActuarialQuote quote = ActuarialQuote.record(
                "trader@example.test", ActuarialFixtures.youngDriver(), ActuarialFixtures.quoteResponse(), NOW
        );
        when(repository.findByUserEmailOrderByCreatedAtDesc(eq("trader@example.test"), any()))
                .thenReturn(List.of(quote));

        List<QuoteHistoryItem> history = service.history("TRADER@example.test", 1_000);

        assertEquals(1, history.size());
        assertEquals(new BigDecimal("883.19"), history.get(0).commercialPremium());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserEmailOrderByCreatedAtDesc(eq("trader@example.test"), page.capture());
        assertEquals(ActuarialService.MAX_HISTORY, page.getValue().getPageSize());
    }

    @Test
    void shouldClampHistorySizeToAtLeastOne() {
        service.history("trader@example.test", 0);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserEmailOrderByCreatedAtDesc(any(), page.capture());
        assertEquals(1, page.getValue().getPageSize());
    }

    @Test
    void shouldRejectUnknownReport() {
        ResponseStatusException error =
                assertThrows(ResponseStatusException.class, () -> service.report("secret"));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(client);
    }

    @Test
    void shouldNormalizeReportName() {
        service.report(" Pricing ");
        verify(client).report("pricing");
    }
}
