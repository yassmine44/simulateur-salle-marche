package tn.esprit.simulateurbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.StartScenarioRequest;
import tn.esprit.simulateurbackend.dto.ScenarioStatusResponse;
import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.ScenarioType;
import tn.esprit.simulateurbackend.repository.AssetRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScenarioServiceTest {

    private AssetRepository assetRepository;
    private Clock clock;
    private ScenarioService scenarioService;

    private final Instant now =
            Instant.parse("2026-10-22T12:00:00Z");

    private StartScenarioRequest request() {
        return new StartScenarioRequest(
                ScenarioType.CRASH,
                "AAPL",
                60,
                new BigDecimal("0.5")
        );
    }

    @BeforeEach
    void setUp() {
        assetRepository = mock(AssetRepository.class);
        clock = mock(Clock.class);

        when(clock.instant()).thenReturn(now);

        when(assetRepository
                .findBySymbolIgnoreCaseAndActiveTrue("AAPL"))
                .thenReturn(Optional.of(mock(Asset.class)));

        scenarioService =
                new ScenarioService(assetRepository, clock);
    }

    @Test
    void shouldStartCrashScenario() {

        ScenarioStatusResponse result =
                scenarioService.start(request());

        assertTrue(result.active());
        assertEquals(ScenarioType.CRASH, result.type());
        assertEquals("AAPL", result.symbol());
        assertEquals(60, result.remainingSeconds());
    }

    @Test
    void shouldStopScenario() {

        scenarioService.start(request());

        ScenarioStatusResponse result =
                scenarioService.stop();

        assertFalse(result.active());
        assertEquals(0, result.remainingSeconds());
    }

    @Test
    void shouldExpireScenarioAutomatically() {

        when(clock.instant())
                .thenReturn(now, now.plusSeconds(61));

        scenarioService.start(request());

        ScenarioStatusResponse result =
                scenarioService.status();

        assertFalse(result.active());
    }

    @Test
    void shouldRejectSecondActiveScenario() {

        scenarioService.start(request());

        assertThrows(
                ResponseStatusException.class,
                () -> scenarioService.start(request())
        );
    }
}