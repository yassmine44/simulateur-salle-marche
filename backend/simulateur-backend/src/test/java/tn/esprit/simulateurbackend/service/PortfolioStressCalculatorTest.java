
package tn.esprit.simulateurbackend.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioStressCalculatorTest {

    @Test
    void crashShouldReduceLongPositionAndPortfolioValue() {

        var result = PortfolioStressCalculator.calculate(
                "AAPL",
                new BigDecimal("10"),
                new BigDecimal("200"),
                new BigDecimal("180"),
                new BigDecimal("10000")
        );

        assertEquals(
                new BigDecimal("-200.00"),
                result.pnlImpact()
        );

        assertEquals(
                new BigDecimal("9800.00"),
                result.stressedPortfolioValue()
        );

        assertEquals(
                new BigDecimal("-2.00"),
                result.portfolioImpactPercent()
        );
    }

    @Test
    void crashShouldBenefitShortPosition() {

        var result = PortfolioStressCalculator.calculate(
                "AAPL",
                new BigDecimal("-10"),
                new BigDecimal("200"),
                new BigDecimal("180"),
                new BigDecimal("10000")
        );

        assertEquals(
                new BigDecimal("200.00"),
                result.pnlImpact()
        );

        assertEquals(
                new BigDecimal("10200.00"),
                result.stressedPortfolioValue()
        );
    }

    @Test
    void unchangedPriceShouldProduceNoImpact() {

        var result = PortfolioStressCalculator.calculate(
                "AAPL",
                new BigDecimal("10"),
                new BigDecimal("200"),
                new BigDecimal("200"),
                new BigDecimal("10000")
        );

        assertEquals(
                new BigDecimal("0.00"),
                result.pnlImpact()
        );

        assertEquals(
                new BigDecimal("10000.00"),
                result.stressedPortfolioValue()
        );
    }

    @Test
    void missingOrInvalidPriceShouldBeRejected() {

        assertThrows(
                IllegalArgumentException.class,
                () -> PortfolioStressCalculator.calculate(
                        "AAPL",
                        BigDecimal.TEN,
                        new BigDecimal("200"),
                        BigDecimal.ZERO,
                        new BigDecimal("10000")
                )
        );
    }
}
