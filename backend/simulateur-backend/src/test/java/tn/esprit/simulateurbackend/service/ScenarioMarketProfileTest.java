package tn.esprit.simulateurbackend.service;

import org.junit.jupiter.api.Test;
import tn.esprit.simulateurbackend.entity.ScenarioType;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioMarketProfileTest {

    @Test
    void zeroIntensityRetainsNormalCoefficients() {
        for (ScenarioType type : ScenarioType.values()) {
            ScenarioMarketProfile effect = ScenarioMarketProfile.active(type, 0, 0.1);
            assertEquals(0, effect.priceDriftPerCycle(), 1e-12, type.name());
            assertEquals(1, effect.volatilityMultiplier(), 1e-12, type.name());
            assertEquals(1, effect.spreadMultiplier(), 1e-12, type.name());
            assertEquals(1, effect.bidLiquidityFactor(), 1e-12, type.name());
            assertEquals(1, effect.askLiquidityFactor(), 1e-12, type.name());
            assertEquals(0.5, effect.buyFlowProbability(), 1e-12, type.name());
        }
    }

    @Test
    void crashPreservesExistingMarketMakerParameters() {
        ScenarioMarketProfile effect = ScenarioMarketProfile.active(ScenarioType.CRASH, 0.8, 0.5);
        assertEquals(-0.0048, effect.priceDriftPerCycle(), 1e-12);
        assertEquals(2.6, effect.volatilityMultiplier(), 1e-12);
        assertEquals(2.6, effect.spreadMultiplier(), 1e-12);
        assertEquals(0.36, effect.bidLiquidityFactor(), 1e-12);
        assertEquals(0.76, effect.askLiquidityFactor(), 1e-12);
        assertEquals(0.5, effect.buyFlowProbability(), 1e-12);
    }

    @Test
    void liquidityCrisisHasTightestBookAndWidestSustainedSpread() {
        ScenarioMarketProfile liquidity = ScenarioMarketProfile.active(ScenarioType.LIQUIDITY_CRISIS, 1, 0.5);
        ScenarioMarketProfile crash = ScenarioMarketProfile.active(ScenarioType.CRASH, 1, 0.5);
        assertTrue(liquidity.spreadMultiplier() > crash.spreadMultiplier());
        assertTrue(liquidity.askLiquidityFactor() < crash.askLiquidityFactor());
        assertTrue(Math.abs(liquidity.priceDriftPerCycle()) < Math.abs(crash.priceDriftPerCycle()));
    }

    @Test
    void panicAndRallyHaveOppositeDirectionAndBookAsymmetry() {
        ScenarioMarketProfile panic = ScenarioMarketProfile.active(ScenarioType.SELLING_PANIC, 1, 0.5);
        ScenarioMarketProfile rally = ScenarioMarketProfile.active(ScenarioType.BULL_RALLY, 1, 0.5);
        assertTrue(panic.priceDriftPerCycle() < 0);
        assertTrue(panic.bidLiquidityFactor() < panic.askLiquidityFactor());
        assertTrue(panic.buyFlowProbability() < 0.5);
        assertTrue(rally.priceDriftPerCycle() > 0);
        assertTrue(rally.askLiquidityFactor() < rally.bidLiquidityFactor());
        assertTrue(rally.buyFlowProbability() > 0.5);
    }

    @Test
    void flashCrashChangesByElapsedPhase() {
        ScenarioMarketProfile plunge = ScenarioMarketProfile.active(ScenarioType.FLASH_CRASH, 1, 0.10);
        ScenarioMarketProfile rebound = ScenarioMarketProfile.active(ScenarioType.FLASH_CRASH, 1, 0.30);
        ScenarioMarketProfile stabilize = ScenarioMarketProfile.active(ScenarioType.FLASH_CRASH, 1, 0.75);
        assertTrue(plunge.priceDriftPerCycle() < 0);
        assertTrue(rebound.priceDriftPerCycle() > 0);
        assertEquals(0, stabilize.priceDriftPerCycle(), 1e-12);
        assertTrue(plunge.spreadMultiplier() > rebound.spreadMultiplier());
        assertTrue(rebound.spreadMultiplier() > stabilize.spreadMultiplier());
        assertTrue(plunge.bidLiquidityFactor() < rebound.bidLiquidityFactor());
        assertTrue(rebound.bidLiquidityFactor() < stabilize.bidLiquidityFactor());
    }

    @Test
    void crashRecoveryNeverAddsNewDirectionalPressure() {
        ScenarioMarketProfile recovery = ScenarioMarketProfile.crashRecovery(0.6);
        assertEquals(0, recovery.priceDriftPerCycle(), 1e-12);
        assertEquals(1, recovery.volatilityMultiplier(), 1e-12);
        assertTrue(recovery.spreadMultiplier() > 1);
        assertTrue(recovery.bidLiquidityFactor() < 1);
    }

    @Test
    void coefficientsAreBoundedAtMaximumIntensity() {
        for (ScenarioType type : ScenarioType.values()) {
            for (double progress : new double[]{0.05, 0.25, 0.75}) {
                ScenarioMarketProfile effect = ScenarioMarketProfile.active(type, 1, progress);
                assertTrue(effect.bidLiquidityFactor() > 0, type.name());
                assertTrue(effect.askLiquidityFactor() > 0, type.name());
                assertTrue(effect.buyFlowProbability() >= 0 && effect.buyFlowProbability() <= 1);
                assertTrue(effect.spreadMultiplier() >= 1);
            }
        }
    }
}
