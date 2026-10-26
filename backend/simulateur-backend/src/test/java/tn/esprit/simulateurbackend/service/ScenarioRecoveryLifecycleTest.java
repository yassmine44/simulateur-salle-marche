
package tn.esprit.simulateurbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tn.esprit.simulateurbackend.dto.StartScenarioRequest;
import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.ScenarioType;
import tn.esprit.simulateurbackend.repository.AssetRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ScenarioRecoveryLifecycleTest {

    private final Instant initialTime =
            Instant.parse("2026-10-25T12:00:00Z");

    private AtomicReference<Instant> currentTime;
    private ScenarioService scenarioService;

    @BeforeEach
    void setUp() {

        currentTime = new AtomicReference<>(initialTime);

        Clock clock = mock(Clock.class);

        when(clock.instant()).thenAnswer(
                invocation -> currentTime.get()
        );

        AssetRepository assetRepository =
                mock(AssetRepository.class);

        when(assetRepository
                .findBySymbolIgnoreCaseAndActiveTrue(anyString()))
                .thenReturn(Optional.of(mock(Asset.class)));

        scenarioService = new ScenarioService(
                assetRepository,
                clock
        );
    }

    private void startCrash(String symbol) {

        scenarioService.start(
                new StartScenarioRequest(
                        ScenarioType.CRASH,
                        symbol,
                        60,
                        new BigDecimal("0.8")
                )
        );
    }

    private void advanceSeconds(long seconds) {

        currentTime.updateAndGet(
                time -> time.plusSeconds(seconds)
        );
    }

    @Test
    void automaticExpiryStartsRecoveryAtExactEndTime() {

        startCrash("AAPL");

        // 90 s après démarrage :
        // CRASH terminé depuis 30 s.
        advanceSeconds(90);

        assertFalse(scenarioService.status().active());

        double residual =
                scenarioService.recoveryIntensity("AAPL");

        assertEquals(
                0.8 * Math.exp(-1.0),
                residual,
                0.000001
        );
    }

    @Test
    void manualStopStartsRecoveryImmediately() {

        startCrash("AAPL");

        advanceSeconds(20);

        scenarioService.stop();

        assertFalse(scenarioService.status().active());

        assertEquals(
                0.8,
                scenarioService.recoveryIntensity("AAPL"),
                0.000001
        );

        advanceSeconds(30);

        assertEquals(
                0.8 * Math.exp(-1.0),
                scenarioService.recoveryIntensity("AAPL"),
                0.000001
        );
    }

    @Test
    void recoveryDoesNotAffectOtherAssets() {

        startCrash("AAPL");

        scenarioService.stop();

        assertTrue(
                scenarioService.recoveryIntensity("AAPL") > 0
        );

        assertEquals(
                0.0,
                scenarioService.recoveryIntensity("MSFT"),
                0.000001
        );
    }

    @Test
    void recoveryEndsAfter120Seconds() {

        startCrash("AAPL");

        scenarioService.stop();

        advanceSeconds(120);

        assertEquals(
                0.0,
                scenarioService.recoveryIntensity("AAPL"),
                0.000001
        );
    }

    @Test
    void newCrashCancelsPreviousRecovery() {

        startCrash("AAPL");

        scenarioService.stop();

        advanceSeconds(10);

        startCrash("MSFT");

        assertTrue(scenarioService.status().active());

        assertEquals(
                0.0,
                scenarioService.recoveryIntensity("AAPL"),
                0.000001
        );

        assertEquals(
                0.0,
                scenarioService.recoveryIntensity("MSFT"),
                0.000001
        );
    }

    @Test
    void repeatedStopDoesNotRestartRecovery() {

        startCrash("AAPL");

        scenarioService.stop();

        advanceSeconds(30);

        scenarioService.stop();

        assertEquals(
                0.8 * Math.exp(-1.0),
                scenarioService.recoveryIntensity("AAPL"),
                0.000001
        );
    }

    @Test
    void recoveryMonitorReturnsNormalInitially() {

        var response = scenarioService.recoveryStatus("AAPL");

        assertEquals("NORMAL", response.phase());
        assertEquals("AAPL", response.symbol());
        assertEquals(0.0, response.residualIntensity(), 0.000001);
        assertEquals(0, response.remainingSeconds());
    }

    @Test
    void recoveryMonitorReturnsCrashDuringScenario() {

        startCrash("AAPL");

        advanceSeconds(20);

        var response = scenarioService.recoveryStatus("AAPL");

        assertEquals("CRASH", response.phase());
        assertEquals(0.8, response.residualIntensity(), 0.000001);
        assertEquals(40, response.remainingSeconds());
    }

    @Test
    void recoveryMonitorReturnsExponentialStressAfterExpiry() {

        startCrash("AAPL");

        // Scénario de 60 secondes :
        // nous sommes 30 secondes après son expiration.
        advanceSeconds(90);

        var response = scenarioService.recoveryStatus("AAPL");

        assertEquals("RECOVERY", response.phase());

        assertEquals(
                0.8 * Math.exp(-1.0),
                response.residualIntensity(),
                0.000001
        );

        assertEquals(90, response.remainingSeconds());
    }

    @Test
    void recoveryMonitorReturnsNormalAfterRecovery() {

        startCrash("AAPL");

        // 60 s de CRASH + 120 s de récupération.
        advanceSeconds(180);

        var response = scenarioService.recoveryStatus("AAPL");

        assertEquals("NORMAL", response.phase());
        assertEquals(0.0, response.residualIntensity(), 0.000001);
        assertEquals(0, response.remainingSeconds());
    }

    @Test
    void recoveryMonitorDoesNotExposeAnotherAssetsCrash() {

        startCrash("AAPL");

        var response = scenarioService.recoveryStatus("MSFT");

        assertEquals("NORMAL", response.phase());
        assertEquals(0.0, response.residualIntensity(), 0.000001);
    }

}
