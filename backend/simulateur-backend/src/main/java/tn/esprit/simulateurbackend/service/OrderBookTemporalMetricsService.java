package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.OrderBookTemporalMetricsResponse;
import tn.esprit.simulateurbackend.entity.OrderBookSnapshot;
import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.repository.OrderBookSnapshotRepository;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;


@Service
public class OrderBookTemporalMetricsService {


    private static final MathContext MC =
            MathContext.DECIMAL128;


    private static final int VOLATILITY_WINDOW_SECONDS =
            60;


    private static final int MOMENTUM_WINDOW_SECONDS =
            30;


    private final OrderBookSnapshotRepository snapshotRepository;

    private final AssetRepository assetRepository;


    public OrderBookTemporalMetricsService(

            OrderBookSnapshotRepository snapshotRepository,

            AssetRepository assetRepository
    ) {

        this.snapshotRepository =
                snapshotRepository;

        this.assetRepository =
                assetRepository;
    }


    // =========================================
    // TEMPORAL METRICS
    // =========================================

    public OrderBookTemporalMetricsResponse
    getTemporalMetrics(
            String rawSymbol
    ) {


        String symbol =
                validateAndNormalizeSymbol(
                        rawSymbol
                );


        Instant now =
                Instant.now();


        // =====================================
        // LATEST SNAPSHOT
        // =====================================

        Optional<OrderBookSnapshot> latestOptional =
                snapshotRepository
                        .findFirstByAssetSymbolIgnoreCaseAndMidPriceIsNotNullOrderByCreatedAtDesc(
                                symbol
                        );


        if (
                latestOptional.isEmpty()
        ) {

            return new OrderBookTemporalMetricsResponse(

                    symbol,

                    null,

                    null,

                    null,

                    null,

                    null,

                    0,

                    now
            );
        }


        OrderBookSnapshot latest =
                latestOptional.get();


        BigDecimal currentMidPrice =
                latest.getMidPrice();


        // =====================================
        // VOLATILITY HISTORY
        // =====================================

        Instant volatilityStart =
                latest
                        .getCreatedAt()
                        .minus(
                                VOLATILITY_WINDOW_SECONDS,
                                ChronoUnit.SECONDS
                        );


        List<OrderBookSnapshot> volatilitySnapshots =
                snapshotRepository
                        .findByAssetSymbolIgnoreCaseAndMidPriceIsNotNullAndCreatedAtBetweenOrderByCreatedAtAsc(

                                symbol,

                                volatilityStart,

                                latest.getCreatedAt()
                        );


        // =====================================
        // VOLATILITY
        // =====================================

        BigDecimal volatility =
                calculateVolatility(
                        volatilitySnapshots
                );


        BigDecimal volatilityPercent =
                volatility == null

                        ? null

                        : volatility.multiply(
                        BigDecimal.valueOf(100),
                        MC
                );


        // =====================================
        // MOMENTUM REFERENCE
        // =====================================

        Instant momentumTarget =
                latest
                        .getCreatedAt()
                        .minus(
                                MOMENTUM_WINDOW_SECONDS,
                                ChronoUnit.SECONDS
                        );


        Optional<OrderBookSnapshot> momentumReference =
                snapshotRepository
                        .findFirstByAssetSymbolIgnoreCaseAndMidPriceIsNotNullAndCreatedAtLessThanEqualOrderByCreatedAtDesc(

                                symbol,

                                momentumTarget
                        );


        // =====================================
        // MOMENTUM
        // =====================================

        BigDecimal momentum =
                null;


        if (
                momentumReference.isPresent()
                        &&
                        momentumReference
                                .get()
                                .getMidPrice()
                                .compareTo(
                                        BigDecimal.ZERO
                                ) > 0
        ) {


            BigDecimal oldMidPrice =
                    momentumReference
                            .get()
                            .getMidPrice();


            momentum =
                    currentMidPrice
                            .subtract(
                                    oldMidPrice,
                                    MC
                            )
                            .divide(
                                    oldMidPrice,
                                    MC
                            );
        }


        BigDecimal momentumPercent =
                momentum == null

                        ? null

                        : momentum.multiply(
                        BigDecimal.valueOf(100),
                        MC
                );


        // =====================================
        // RESPONSE
        // =====================================

        return new OrderBookTemporalMetricsResponse(

                symbol,

                currentMidPrice,

                volatility,

                volatilityPercent,

                momentum,

                momentumPercent,

                volatilitySnapshots.size(),

                now
        );
    }


    // =========================================
    // VOLATILITY
    // =========================================

    private BigDecimal calculateVolatility(
            List<OrderBookSnapshot> snapshots
    ) {


        /*
         * Au moins deux prix sont nécessaires
         * pour obtenir un rendement.
         */

        if (
                snapshots == null
                        ||
                        snapshots.size() < 2
        ) {

            return null;
        }


        List<Double> returns =
                new ArrayList<>();


        for (
                int i = 1;
                i < snapshots.size();
                i++
        ) {


            BigDecimal previousPrice =
                    snapshots
                            .get(i - 1)
                            .getMidPrice();


            BigDecimal currentPrice =
                    snapshots
                            .get(i)
                            .getMidPrice();


            if (
                    previousPrice == null
                            ||
                            currentPrice == null
                            ||
                            previousPrice.compareTo(
                                    BigDecimal.ZERO
                            ) <= 0
                            ||
                            currentPrice.compareTo(
                                    BigDecimal.ZERO
                            ) <= 0
            ) {

                continue;
            }


            // =================================
            // LOG RETURN
            // =================================

            double logReturn =
                    Math.log(

                            currentPrice.doubleValue()

                                    /

                                    previousPrice.doubleValue()
                    );


            returns.add(
                    logReturn
            );
        }


        if (
                returns.isEmpty()
        ) {

            return null;
        }


        // =====================================
        // MEAN RETURN
        // =====================================

        double mean =
                returns
                        .stream()
                        .mapToDouble(
                                Double::doubleValue
                        )
                        .average()
                        .orElse(
                                0.0
                        );


        // =====================================
        // VARIANCE
        // =====================================

        double variance =
                0.0;


        for (
                double value
                : returns
        ) {


            double difference =
                    value - mean;


            variance +=
                    difference
                            *
                            difference;
        }


        /*
         * Volatilité population sur la fenêtre.
         */

        variance =
                variance
                        /
                        returns.size();


        // =====================================
        // STANDARD DEVIATION
        // =====================================

        double volatility =
                Math.sqrt(
                        variance
                );


        return BigDecimal.valueOf(
                volatility
        );
    }


    // =========================================
    // SYMBOL VALIDATION
    // =========================================

    private String validateAndNormalizeSymbol(
            String rawSymbol
    ) {


        if (
                rawSymbol == null
                        ||
                        rawSymbol.isBlank()
        ) {

            throw new ResponseStatusException(

                    HttpStatus.BAD_REQUEST,

                    "Le symbole de l'actif est obligatoire."
            );
        }


        String symbol =
                rawSymbol
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );


        assetRepository
                .findBySymbolIgnoreCaseAndActiveTrue(
                        symbol
                )
                .orElseThrow(
                        () ->
                                new ResponseStatusException(

                                        HttpStatus.NOT_FOUND,

                                        "Actif inconnu ou désactivé : "
                                                +
                                                symbol
                                )
                );


        return symbol;
    }
}