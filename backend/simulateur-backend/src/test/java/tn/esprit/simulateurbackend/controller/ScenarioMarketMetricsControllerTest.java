package tn.esprit.simulateurbackend.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.service.MarketMakerService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ScenarioMarketMetricsControllerTest {
    private final AssetRepository assets = mock(AssetRepository.class);
    private final MarketMakerService maker = mock(MarketMakerService.class);
    private final ScenarioMarketMetricsController controller = new ScenarioMarketMetricsController(maker, assets);
    private Asset asset;
    @BeforeEach void setup() {
        asset = new Asset(); asset.setSymbol("AAPL"); asset.setReferencePrice(new BigDecimal("200"));
        when(assets.findBySymbolIgnoreCaseAndActiveTrue("AAPL")).thenReturn(Optional.of(asset));
    }
    @Test void reportsRealQuoteAndPercentageRelativeToReference() {
        when(maker.currentQuotedMidPrice("AAPL")).thenReturn(new BigDecimal("197.50"));
        Instant before = Instant.now();
        var result = controller.market(" AAPL ");
        assertThat(result.symbol()).isEqualTo("AAPL");
        assertThat(result.quotedMidPrice()).isEqualByComparingTo("197.50");
        assertThat(result.referencePrice()).isEqualByComparingTo("200");
        assertThat(result.variationPercent()).isEqualByComparingTo("-1.25");
        assertThat(result.capturedAt()).isBetween(before, Instant.now());
        verify(maker, never()).quoteAsset(anyString());
    }
    @Test void absentQuoteIsNullRatherThanFabricated() {
        var result = controller.market("AAPL");
        assertThat(result.quotedMidPrice()).isNull();
        assertThat(result.variationPercent()).isNull();
    }
    @Test void missingOrZeroReferenceDoesNotDivideByZero() {
        when(maker.currentQuotedMidPrice("AAPL")).thenReturn(BigDecimal.TEN);
        asset.setReferencePrice(BigDecimal.ZERO);
        assertThat(controller.market("AAPL").variationPercent()).isNull();
        asset.setReferencePrice(null);
        assertThat(controller.market("AAPL").variationPercent()).isNull();
    }
    @Test void inactiveOrUnknownSymbolIsNotFound() {
        when(assets.findBySymbolIgnoreCaseAndActiveTrue("AAPL")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.market("AAPL")).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(404));
        verifyNoInteractions(maker);
    }
}
