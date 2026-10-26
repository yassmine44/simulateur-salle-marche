package tn.esprit.simulateurbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tn.esprit.simulateurbackend.dto.ScenarioStatusResponse;
import tn.esprit.simulateurbackend.entity.*;
import tn.esprit.simulateurbackend.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MarketMakerScenarioTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final AssetRepository assets = mock(AssetRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MatchingEngineService matching = mock(MatchingEngineService.class);
    private final MarketDataService data = mock(MarketDataService.class);
    private final TradeRepository trades = mock(TradeRepository.class);
    private final ScenarioService scenarios = mock(ScenarioService.class);
    private MarketMakerService maker;
    private List<Order> quotes;
    @BeforeEach void setup() {
        var asset = new Asset();
        asset.setSymbol("AAPL"); asset.setReferencePrice(new BigDecimal("200"));
        asset.setTickSize(new BigDecimal("0.01")); asset.setMinQuantity(BigDecimal.ONE);
        when(assets.findBySymbolIgnoreCaseAndActiveTrue("AAPL")).thenReturn(Optional.of(asset));
        when(data.lastPrice(asset)).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase(MarketMakerService.MAKER_EMAIL)).thenReturn(Optional.of(new User()));
        when(orders.saveAllAndFlush(anyList())).thenAnswer(invocation -> {
            quotes = new ArrayList<>(invocation.getArgument(0)); return quotes;
        });
        maker = new MarketMakerService(orders, assets, users, matching, data, trades,
                mock(ApplicationEventPublisher.class), scenarios);
        ReflectionTestUtils.setField(maker, "levels", 1);
        ReflectionTestUtils.setField(maker, "tradeProbability", 0.0);
    }
    private List<Order> quote(boolean active, String symbol, double intensity) {
        when(scenarios.status()).thenReturn(new ScenarioStatusResponse(active, ScenarioType.CRASH,
                symbol, BigDecimal.valueOf(intensity), Instant.now(), Instant.now().plusSeconds(60), 60));
        ((Map<?, ?>) ReflectionTestUtils.getField(maker, "midPrices")).clear();
        ((Map<?, ?>) ReflectionTestUtils.getField(maker, "lastSeenTradeIds")).clear();
        ((Random) ReflectionTestUtils.getField(maker, "random")).setSeed(42);
        maker.quoteAsset("AAPL");
        return quotes;
    }
    private BigDecimal midpoint(List<Order> quotes) {
        return quotes.get(0).getLimitPrice().add(quotes.get(1).getLimitPrice()).divide(BigDecimal.valueOf(2));
    }
    private BigDecimal spread(List<Order> quotes) {
        return quotes.get(1).getLimitPrice().subtract(quotes.get(0).getLimitPrice());
    }
    @Test void crashLowersQuotesAndWidensSpread() {
        var normal = quote(false, "AAPL", 0);
        var crash = quote(true, "aapl", 1);
        assertThat(midpoint(crash)).isLessThan(midpoint(normal));
        assertThat(spread(crash)).isGreaterThan(spread(normal));
        verify(scenarios, times(2)).status();
        verify(matching, times(2)).triggerConditionalOrders("AAPL");
        verify(matching, times(4)).matchOrder(any());
    }
    @Test void anotherSymbolDoesNotChangeQuotes() {
        var normal = quote(false, "AAPL", 0);
        var other = quote(true, "AMZN", 1);
        assertThat(midpoint(other)).isEqualByComparingTo(midpoint(normal));
        assertThat(spread(other)).isEqualByComparingTo(spread(normal));
    }
    @Test void inactiveScenarioRestoresNormalCoefficients() {
        var normal = quote(false, "AAPL", 0);
        quote(true, "AAPL", 1);
        var stopped = quote(false, "AAPL", 1);
        assertThat(midpoint(stopped)).isEqualByComparingTo(midpoint(normal));
        assertThat(spread(stopped)).isEqualByComparingTo(spread(normal));
    }
    @Test void repeatedCrashCyclesRespectPriceBoundsAndTicks() {
        quote(true, "AAPL", 1);
        for (int i = 0; i < 1000; i++) maker.quoteAsset("AAPL");
        var mids = (Map<?, ?>) ReflectionTestUtils.getField(maker, "midPrices");
        assertThat((BigDecimal) mids.get("AAPL")).isBetween(new BigDecimal("140"), new BigDecimal("260"));
        assertThat(quotes).allSatisfy(order -> {
            assertThat(order.getLimitPrice()).isPositive();
            assertThat(order.getLimitPrice().remainder(new BigDecimal("0.01"))).isEqualByComparingTo(BigDecimal.ZERO);
        });
    }
    @Test void quoteAccessorNormalizesSymbolsAndDoesNotGenerateQuotes() {
        assertThat(maker.currentQuotedMidPrice(null)).isNull();
        assertThat(maker.currentQuotedMidPrice(" ")).isNull();
        assertThat(maker.currentQuotedMidPrice("AAPL")).isNull();
        quote(false, "AAPL", 0);
        var current = maker.currentQuotedMidPrice("AAPL");
        assertThat(current).isNotNull();
        assertThat(maker.currentQuotedMidPrice(" aapl ")).isEqualByComparingTo(current);
        assertThat(maker.currentQuotedMidPrice("UNKNOWN")).isNull();
    }
    @Test void crashReducesActualQuoteQuantitiesAndRestoresThemAfterwards() {
        var normal = quote(false, "AAPL", 0);
        var crash = quote(true, "AAPL", 0.8);
        assertThat(crash.get(0).getRemainingQuantity()).isLessThan(normal.get(0).getRemainingQuantity());
        assertThat(crash.get(1).getRemainingQuantity()).isLessThan(normal.get(1).getRemainingQuantity());
        assertThat(crash.get(0).getRemainingQuantity()).isLessThan(crash.get(1).getRemainingQuantity());
        var other = quote(true, "AMZN", 0.8);
        var stopped = quote(false, "AAPL", 0.8);
        for (int side = 0; side < 2; side++) {
            assertThat(other.get(side).getRemainingQuantity()).isEqualByComparingTo(normal.get(side).getRemainingQuantity());
            assertThat(stopped.get(side).getRemainingQuantity()).isEqualByComparingTo(normal.get(side).getRemainingQuantity());
        }
    }
}