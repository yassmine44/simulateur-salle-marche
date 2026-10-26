package tn.esprit.simulateurbackend.service;

import org.junit.jupiter.api.Test;

import tn.esprit.simulateurbackend.entity.OrderSide;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketMakerLiquidityTest {

    @Test
    void noCrashKeepsNormalLiquidity() {

        assertEquals(
                1.0,
                MarketMakerService.liquidityFactor(
                        OrderSide.BUY, 0.0
                ),
                0.000001
        );

        assertEquals(
                1.0,
                MarketMakerService.liquidityFactor(
                        OrderSide.SELL, 0.0
                ),
                0.000001
        );
    }

    @Test
    void crashReducesBidLiquidityMoreThanAskLiquidity() {

        double bid = MarketMakerService.liquidityFactor(
                OrderSide.BUY, 0.8
        );

        double ask = MarketMakerService.liquidityFactor(
                OrderSide.SELL, 0.8
        );

        assertEquals(0.36, bid, 0.000001);
        assertEquals(0.76, ask, 0.000001);
    }

    @Test
    void maximumCrashKeepsMinimumLiquidityFraction() {

        assertEquals(
                0.20,
                MarketMakerService.liquidityFactor(
                        OrderSide.BUY, 1.0
                ),
                0.000001
        );

        assertEquals(
                0.70,
                MarketMakerService.liquidityFactor(
                        OrderSide.SELL, 1.0
                ),
                0.000001
        );
    }

    @Test
    void intensityIsBounded() {

        assertEquals(
                1.0,
                MarketMakerService.liquidityFactor(
                        OrderSide.BUY, -1.0
                ),
                0.000001
        );

        assertEquals(
                0.20,
                MarketMakerService.liquidityFactor(
                        OrderSide.BUY, 2.0
                ),
                0.000001
        );
    }
    @Test
    void quantitiesRespectExactFactorsAndMinimumStep() {
        var maker = org.mockito.Mockito.mock(MarketMakerService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        var asset = new tn.esprit.simulateurbackend.entity.Asset();
        asset.setMinQuantity(java.math.BigDecimal.ONE);
        java.math.BigDecimal bid = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                maker, "stressedQuoteQuantity", asset, new java.math.BigDecimal("100"), OrderSide.BUY, 0.8);
        java.math.BigDecimal ask = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                maker, "stressedQuoteQuantity", asset, new java.math.BigDecimal("100"), OrderSide.SELL, 0.8);
        org.assertj.core.api.Assertions.assertThat(bid).isEqualByComparingTo("36");
        org.assertj.core.api.Assertions.assertThat(ask).isEqualByComparingTo("76");
        asset.setMinQuantity(new java.math.BigDecimal("0.1"));
        java.math.BigDecimal minimum = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                maker, "stressedQuoteQuantity", asset, new java.math.BigDecimal("0.1"), OrderSide.BUY, 1.0);
        org.assertj.core.api.Assertions.assertThat(minimum).isEqualByComparingTo("0.1");
    }
}