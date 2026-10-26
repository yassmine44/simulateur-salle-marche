
package tn.esprit.simulateurbackend.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class MarketRecoveryModelTest {

    @Test
    void recoveryStartsWithOriginalIntensity() {

        double result =
                MarketRecoveryModel.residualIntensity(
                        0.8,
                        Duration.ZERO
                );

        assertEquals(0.8, result, 0.000001);
    }

    @Test
    void stressDecaysExponentially() {

        double result =
                MarketRecoveryModel.residualIntensity(
                        0.8,
                        Duration.ofSeconds(30)
                );

        assertEquals(
                0.8 * Math.exp(-1.0),
                result,
                0.000001
        );
    }

    @Test
    void stressDecreasesOverTime() {

        double at15 =
                MarketRecoveryModel.residualIntensity(
                        0.8,
                        Duration.ofSeconds(15)
                );

        double at60 =
                MarketRecoveryModel.residualIntensity(
                        0.8,
                        Duration.ofSeconds(60)
                );

        assertTrue(at15 > at60);
        assertTrue(at60 > 0.0);
    }

    @Test
    void recoveryEndsAfter120Seconds() {

        double result =
                MarketRecoveryModel.residualIntensity(
                        0.8,
                        Duration.ofSeconds(120)
                );

        assertEquals(0.0, result, 0.000001);
    }

    @Test
    void zeroIntensityRemainsZero() {

        double result =
                MarketRecoveryModel.residualIntensity(
                        0.0,
                        Duration.ofSeconds(30)
                );

        assertEquals(0.0, result, 0.000001);
    }

    @Test
    void intensityIsBounded() {

        double result =
                MarketRecoveryModel.residualIntensity(
                        2.0,
                        Duration.ZERO
                );

        assertEquals(1.0, result, 0.000001);
    }
}
