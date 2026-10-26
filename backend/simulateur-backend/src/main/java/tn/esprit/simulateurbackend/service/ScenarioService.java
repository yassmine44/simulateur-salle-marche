
package tn.esprit.simulateurbackend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.StartScenarioRequest;
import tn.esprit.simulateurbackend.dto.ScenarioStatusResponse;
import tn.esprit.simulateurbackend.entity.ScenarioType;
import tn.esprit.simulateurbackend.repository.AssetRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import tn.esprit.simulateurbackend.dto.ScenarioRecoveryStatusResponse;
@Service
public class ScenarioService {

    private final AssetRepository assetRepository;
    private final Clock clock;

    // Un seul scénario actif pour le simulateur.
    // État en mémoire, non persistant après redémarrage.
    private ActiveScenario activeScenario;

    // Stress résiduel après la fin du scénario.
    private RecoveryState recoveryState;

    @Autowired
    public ScenarioService(AssetRepository assetRepository) {
        this(assetRepository, Clock.systemUTC());
    }

    // Constructeur utilisable par les tests déterministes.
    ScenarioService(
            AssetRepository assetRepository,
            Clock clock
    ) {
        this.assetRepository = assetRepository;
        this.clock = clock;
    }

    // =========================================
    // START
    // =========================================

    public synchronized ScenarioStatusResponse start(
            StartScenarioRequest request
    ) {

        Instant now = clock.instant();

        clearIfExpired(now);

        if (activeScenario != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Un scenario est deja en cours."
            );
        }

        String symbol = request.symbol()
                .trim()
                .toUpperCase(Locale.ROOT);

        boolean assetExists = assetRepository
                .findBySymbolIgnoreCaseAndActiveTrue(symbol)
                .isPresent();

        if (!assetExists) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Actif introuvable ou inactif : " + symbol
            );
        }

        activeScenario = new ActiveScenario(
                request.type(),
                symbol,
                request.intensity(),
                now,
                now.plusSeconds(request.durationSeconds())
        );

        // Le nouveau scénario remplace
        // toute récupération précédente.
        recoveryState = null;

        return statusAt(now);
    }

    // =========================================
    // STATUS
    // =========================================

    public synchronized ScenarioStatusResponse status() {
        return statusAt(clock.instant());
    }

    // =========================================
    // STOP
    // =========================================

    public synchronized ScenarioStatusResponse stop() {

        Instant now = clock.instant();

        // Si le scénario a déjà expiré,
        // la récupération démarre à endsAt,
        // et non à l'heure de cette requête.
        clearIfExpired(now);

        if (activeScenario != null) {

            if (activeScenario.type() == ScenarioType.CRASH) {
                beginRecovery(
                        activeScenario.symbol(),
                        activeScenario.intensity(),
                        now
                );
            }

            activeScenario = null;
        }

        // Un deuxième arrêt ne redémarre pas
        // le chronomètre de récupération.
        return statusAt(now);
    }

    // =========================================
    // STATUS RESPONSE
    // =========================================

    private ScenarioStatusResponse statusAt(
            Instant now
    ) {

        clearIfExpired(now);

        if (activeScenario == null) {

            return new ScenarioStatusResponse(
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0
            );
        }

        long remainingSeconds = Math.max(
                0,
                Duration.between(
                        now,
                        activeScenario.endsAt()
                ).getSeconds()
        );

        return new ScenarioStatusResponse(
                true,
                activeScenario.type(),
                activeScenario.symbol(),
                activeScenario.intensity(),
                activeScenario.startedAt(),
                activeScenario.endsAt(),
                remainingSeconds
        );
    }

    // =========================================
    // AUTOMATIC EXPIRATION
    // =========================================

    private void clearIfExpired(
            Instant now
    ) {

        if (activeScenario == null) {
            return;
        }

        if (activeScenario.endsAt().isAfter(now)) {
            return;
        }

        // Début de la récupération à l'instant
        // exact d'expiration du scénario.
        if (activeScenario.type() == ScenarioType.CRASH) {

            beginRecovery(
                    activeScenario.symbol(),
                    activeScenario.intensity(),
                    activeScenario.endsAt()
            );
        }

        activeScenario = null;
    }

    // =========================================
    // RECOVERY
    // =========================================

    private void beginRecovery(
            String symbol,
            BigDecimal intensity,
            Instant endedAt
    ) {

        recoveryState = new RecoveryState(
                symbol,
                intensity.doubleValue(),
                endedAt
        );
    }

    /**
     * Retourne le stress résiduel pour un actif.
     *
     * Ce stress doit agir sur la liquidité
     * et le spread, jamais sur le biais baissier
     * du modèle de prix.
     */
    public synchronized double recoveryIntensity(
            String symbol
    ) {

        Instant now = clock.instant();

        // Détecter une expiration même si status()
        // n'a pas été appelé depuis la fin du CRASH.
        clearIfExpired(now);

        if (symbol == null || symbol.isBlank()) {
            return 0.0;
        }

        // Pendant un scénario actif, le Market Maker
        // utilisera directement son intensité CRASH.
        if (activeScenario != null) {
            return 0.0;
        }

        if (recoveryState == null) {
            return 0.0;
        }

        if (!recoveryState.symbol()
                .equalsIgnoreCase(symbol)) {
            return 0.0;
        }

        Duration elapsed = Duration.between(
                recoveryState.endedAt(),
                now
        );

        double residual =
                MarketRecoveryModel.residualIntensity(
                        recoveryState.initialIntensity(),
                        elapsed
                );

        if (residual == 0.0) {
            recoveryState = null;
        }

        return residual;
    }

    // =========================================
    // INTERNAL STATES
    // =========================================

    private record ActiveScenario(
            ScenarioType type,
            String symbol,
            BigDecimal intensity,
            Instant startedAt,
            Instant endsAt
    ) {}

    private record RecoveryState(
            String symbol,
            double initialIntensity,
            Instant endedAt
    ) {}

    public synchronized ScenarioRecoveryStatusResponse recoveryStatus(
            String requestedSymbol
    ) {

        if (requestedSymbol == null
                || requestedSymbol.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Symbole obligatoire"
            );
        }

        String symbol = requestedSymbol
                .trim()
                .toUpperCase(Locale.ROOT);

        boolean assetExists = assetRepository
                .findBySymbolIgnoreCaseAndActiveTrue(symbol)
                .isPresent();

        if (!assetExists) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Actif introuvable ou inactif : " + symbol
            );
        }

        Instant now = clock.instant();

        // Détecte une expiration automatique,
        // même si aucun appel à status() n'a eu lieu.
        clearIfExpired(now);

        // =========================================
        // PHASE 1 : CRASH
        // =========================================

        if (activeScenario != null
                && activeScenario.type() == ScenarioType.CRASH
                && activeScenario.symbol().equals(symbol)) {

            long remainingMillis = Math.max(
                    0,
                    Duration.between(
                            now,
                            activeScenario.endsAt()
                    ).toMillis()
            );

            long remainingSeconds =
                    (remainingMillis + 999) / 1000;

            return new ScenarioRecoveryStatusResponse(
                    symbol,
                    "CRASH",
                    activeScenario.intensity().doubleValue(),
                    remainingSeconds
            );
        }

        // =========================================
        // PHASE 2 : RECOVERY
        // =========================================

        if (recoveryState != null
                && recoveryState.symbol().equals(symbol)) {

            double residual =
                    MarketRecoveryModel.residualIntensity(
                            recoveryState.initialIntensity(),
                            Duration.between(
                                    recoveryState.endedAt(),
                                    now
                            )
                    );

            if (residual > 0.0) {

                Instant recoveryEndsAt =
                        recoveryState.endedAt().plus(
                                MarketRecoveryModel.RECOVERY_DURATION
                        );

                long remainingMillis = Math.max(
                        0,
                        Duration.between(
                                now,
                                recoveryEndsAt
                        ).toMillis()
                );

                long remainingSeconds =
                        (remainingMillis + 999) / 1000;

                return new ScenarioRecoveryStatusResponse(
                        symbol,
                        "RECOVERY",
                        residual,
                        remainingSeconds
                );
            }

            recoveryState = null;
        }

        // =========================================
        // PHASE 3 : NORMAL
        // =========================================

        return new ScenarioRecoveryStatusResponse(
                symbol,
                "NORMAL",
                0.0,
                0
        );
    }

}
