package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.entity.OrderBookSnapshot;
import tn.esprit.simulateurbackend.entity.ScenarioExecution;
import tn.esprit.simulateurbackend.repository.OrderBookSnapshotRepository;
import tn.esprit.simulateurbackend.repository.ScenarioExecutionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class ScenarioMarketHistoryService {

    private static final Duration MAX_BASELINE_AGE =
            Duration.ofSeconds(30);

    private final ScenarioExecutionRepository executionRepository;
    private final OrderBookSnapshotRepository snapshotRepository;

    public ScenarioMarketHistoryService(
            ScenarioExecutionRepository executionRepository,
            OrderBookSnapshotRepository snapshotRepository
    ) {
        this.executionRepository = executionRepository;
        this.snapshotRepository = snapshotRepository;
    }

    public record MarketObservation(
            Instant observedAt,
            BigDecimal midPrice,
            BigDecimal bestBid,
            BigDecimal bestAsk,
            BigDecimal spread,
            BigDecimal totalBidVolume,
            BigDecimal totalAskVolume,
            BigDecimal weightedBidDepth,
            BigDecimal weightedAskDepth
    ) {}

    public record MarketHistory(
            Long executionId,
            String symbol,
            Instant startedAt,
            Instant observedUntil,
            MarketObservation baseline,
            List<MarketObservation> during,
            MarketObservation lastDuring,
            BigDecimal priceChangePercent
    ) {}

    @Transactional(readOnly = true)
    public MarketHistory getMarketHistory(Long executionId) {

        ScenarioExecution execution = executionRepository
                .findById(executionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Simulation introuvable : " + executionId
                ));

        String symbol = execution.getSymbol();
        Instant start = execution.getStartedAt();

        Instant end = execution.getActualEndedAt();

        if (end == null) {
            Instant now = Instant.now();
            end = now.isBefore(execution.getScheduledEndsAt())
                    ? now
                    : execution.getScheduledEndsAt();
        }

        if (end.isBefore(start)) {
            end = start;
        }

        MarketObservation baseline = snapshotRepository
                .findFirstByAssetSymbolIgnoreCaseAndMidPriceIsNotNullAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                        symbol, start
                )
                .filter(snapshot ->
                        !snapshot.getCreatedAt().isBefore(
                                start.minus(MAX_BASELINE_AGE)
                        )
                )
                .map(this::toObservation)
                .orElse(null);

        List<MarketObservation> observations = snapshotRepository
                .findByAssetSymbolIgnoreCaseAndMidPriceIsNotNullAndCreatedAtBetweenOrderByCreatedAtAsc(
                        symbol, start, end
                )
                .stream()
                .map(this::toObservation)
                .toList();

        MarketObservation lastDuring = observations.isEmpty()
                ? null
                : observations.get(observations.size() - 1);

        BigDecimal priceChangePercent = null;

        if (baseline != null
                && lastDuring != null
                && baseline.midPrice().signum() > 0) {

            priceChangePercent = lastDuring.midPrice()
                    .subtract(baseline.midPrice())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(
                            baseline.midPrice(),
                            4,
                            RoundingMode.HALF_UP
                    );
        }

        return new MarketHistory(
                executionId,
                symbol,
                start,
                end,
                baseline,
                observations,
                lastDuring,
                priceChangePercent
        );
    }

    private MarketObservation toObservation(
            OrderBookSnapshot snapshot
    ) {
        return new MarketObservation(
                snapshot.getCreatedAt(),
                snapshot.getMidPrice(),
                snapshot.getBestBid(),
                snapshot.getBestAsk(),
                snapshot.getSpread(),
                snapshot.getTotalBidVolume(),
                snapshot.getTotalAskVolume(),
                snapshot.getWeightedBidDepth(),
                snapshot.getWeightedAskDepth()
        );
    }
}