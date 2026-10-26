
package tn.esprit.simulateurbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tn.esprit.simulateurbackend.dto.PortfolioResponse;
import tn.esprit.simulateurbackend.dto.PortfolioStressResponse;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortfolioStressServiceTest {

    private PortfolioService portfolioService;
    private MarketMakerService marketMakerService;
    private PortfolioStressService stressService;

    private static final String EMAIL = "trader@example.test";

    @BeforeEach
    void setUp() {
        portfolioService = mock(PortfolioService.class);
        marketMakerService = mock(MarketMakerService.class);

        stressService = new PortfolioStressService(
                portfolioService,
                marketMakerService
        );
    }

    private PortfolioResponse portfolio(
            List<PortfolioResponse.Position> positions
    ) {
        PortfolioResponse response =
                mock(PortfolioResponse.class);

        when(response.currency()).thenReturn("USD");
        when(response.totalValue())
                .thenReturn(new BigDecimal("10000"));
        when(response.openPositions())
                .thenReturn(positions);

        return response;
    }

    private PortfolioResponse.Position position(
            String symbol,
            String quantity,
            String lastPrice
    ) {
        PortfolioResponse.Position position =
                mock(PortfolioResponse.Position.class);

        BigDecimal qty = new BigDecimal(quantity);
        BigDecimal price = new BigDecimal(lastPrice);

        when(position.assetSymbol()).thenReturn(symbol);
        when(position.quantity()).thenReturn(qty);
        when(position.lastPrice()).thenReturn(price);
        when(position.marketValue())
                .thenReturn(qty.multiply(price));

        return position;
    }

    @Test
    void crashShouldReduceLongPortfolioValue() {

        var aapl = position("AAPL", "10", "200");
        PortfolioResponse mockPortfolio =
                portfolio(List.of(aapl));

        when(portfolioService.getPortfolio(EMAIL, 7))
                .thenReturn(mockPortfolio);

        when(marketMakerService.currentQuotedMidPrice("AAPL"))
                .thenReturn(new BigDecimal("180"));

        PortfolioStressResponse result =
                stressService.getStress(EMAIL);

        assertTrue(result.quoteCoverageComplete());

        assertEquals(
                0,
                new BigDecimal("9800")
                        .compareTo(result.quotedTotalValue())
        );

        assertEquals(
                0,
                new BigDecimal("-200")
                        .compareTo(result.valuationDifference())
        );

        assertEquals(1, result.positions().size());

        verify(portfolioService).getPortfolio(EMAIL, 7);
        verify(marketMakerService)
                .currentQuotedMidPrice("AAPL");
    }

    @Test
    void missingQuoteShouldNotInventPortfolioValuation() {

        var aapl = position("AAPL", "10", "200");

        PortfolioResponse mockPortfolio = portfolio(List.of(aapl));

        when(portfolioService.getPortfolio(EMAIL, 7))
                .thenReturn(mockPortfolio);

        when(marketMakerService.currentQuotedMidPrice("AAPL"))
                .thenReturn(null);

        PortfolioStressResponse result =
                stressService.getStress(EMAIL);

        assertFalse(result.quoteCoverageComplete());
        assertNull(result.quotedTotalValue());
        assertNull(result.valuationDifference());
        assertNull(result.positions().get(0).quotedMarketValue());
    }

    @Test
    void crashShouldBenefitShortPosition() {

        var aapl = position("AAPL", "-10", "200");

        PortfolioResponse mockPortfolio = portfolio(List.of(aapl));

        when(portfolioService.getPortfolio(EMAIL, 7))
                .thenReturn(mockPortfolio);
        when(marketMakerService.currentQuotedMidPrice("AAPL"))
                .thenReturn(new BigDecimal("180"));

        PortfolioStressResponse result =
                stressService.getStress(EMAIL);

        assertTrue(result.quoteCoverageComplete());

        assertEquals(
                0,
                new BigDecimal("200")
                        .compareTo(result.valuationDifference())
        );

        assertEquals(
                0,
                new BigDecimal("10200")
                        .compareTo(result.quotedTotalValue())
        );
    }

    @Test
    void emptyPortfolioShouldHaveZeroImpact() {
        PortfolioResponse mockPortfolio =
                portfolio(List.of());

        when(portfolioService.getPortfolio(EMAIL, 7))
                .thenReturn(mockPortfolio);

        PortfolioStressResponse result =
                stressService.getStress(EMAIL);

        assertTrue(result.quoteCoverageComplete());
        assertTrue(result.positions().isEmpty());

        assertEquals(
                0,
                BigDecimal.ZERO.compareTo(
                        result.valuationDifference()
                )
        );

        verifyNoInteractions(marketMakerService);
    }
}
