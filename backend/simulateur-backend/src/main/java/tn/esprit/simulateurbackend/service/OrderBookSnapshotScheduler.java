package tn.esprit.simulateurbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import tn.esprit.simulateurbackend.dto.OrderBookMetricsResponse;
import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.repository.AssetRepository;

import java.util.List;


@Service
public class OrderBookSnapshotScheduler {


    // =========================================
    // LOGGER
    // =========================================

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    OrderBookSnapshotScheduler.class
            );


    // =========================================
    // DEPENDENCIES
    // =========================================

    private final AssetRepository assetRepository;

    private final OrderBookService orderBookService;

    private final OrderBookSnapshotService snapshotService;


    // =========================================
    // CONSTRUCTOR
    // =========================================

    public OrderBookSnapshotScheduler(

            AssetRepository assetRepository,

            OrderBookService orderBookService,

            OrderBookSnapshotService snapshotService
    ) {

        this.assetRepository =
                assetRepository;

        this.orderBookService =
                orderBookService;

        this.snapshotService =
                snapshotService;
    }


    // =========================================
    // SNAPSHOT EVERY 5 SECONDS
    // =========================================

    /*
     *
     * fixedDelay = 5000 ms
     *
     * Cela signifie :
     *
     * 1. Le scheduler lance une collecte.
     *
     * 2. Il attend que la collecte soit terminée.
     *
     * 3. Il attend ensuite 5 secondes.
     *
     * 4. Il recommence.
     *
     */

    @Scheduled(
            fixedDelay = 5000
    )
    public void captureSnapshots() {


        // =====================================
        // GET ACTIVE ASSETS
        // =====================================

        List<Asset> activeAssets =
                assetRepository
                        .findByActiveTrueOrderBySymbolAsc();


        // =====================================
        // NOTHING TO COLLECT
        // =====================================

        if (
                activeAssets == null
                        ||
                        activeAssets.isEmpty()
        ) {

            LOGGER.debug(
                    "Aucun actif actif à capturer."
            );

            return;
        }


        // =====================================
        // CAPTURE EACH ASSET
        // =====================================

        for (
                Asset asset
                : activeAssets
        ) {


            /*
             * Une erreur sur un actif
             * ne doit jamais empêcher
             * les autres actifs d'être traités.
             */

            try {


                // =================================
                // SAFETY CHECK
                // =================================

                if (
                        asset == null
                                ||
                                asset.getSymbol() == null
                                ||
                                asset.getSymbol().isBlank()
                ) {

                    LOGGER.warn(
                            "Actif ignoré : symbole invalide."
                    );

                    continue;
                }


                String symbol =
                        asset
                                .getSymbol()
                                .trim()
                                .toUpperCase();


                // =================================
                // CALCULATE CURRENT METRICS
                // =================================

                OrderBookMetricsResponse metrics =
                        orderBookService
                                .getOrderBookMetrics(
                                        symbol
                                );


                // =================================
                // NO METRICS
                // =================================

                if (
                        metrics == null
                ) {

                    LOGGER.debug(
                            "Aucune métrique disponible pour {}.",
                            symbol
                    );

                    continue;
                }


                // =================================
                // NO MID PRICE
                // =================================

                /*
                 * Un MidPrice nécessite :
                 *
                 * Best BID
                 * +
                 * Best ASK
                 *
                 * Sans MidPrice, on ne peut pas
                 * construire correctement :
                 *
                 * - les log returns
                 * - la volatilité
                 * - le momentum
                 *
                 * Le service saveSnapshot()
                 * effectue également cette
                 * vérification, mais on la garde
                 * ici pour éviter un appel inutile.
                 */

                if (
                        metrics.midPrice() == null
                ) {

                    LOGGER.debug(
                            "Snapshot {} ignoré : MidPrice indisponible.",
                            symbol
                    );

                    continue;
                }


                // =================================
                // SAVE SNAPSHOT
                // =================================

                snapshotService
                        .saveSnapshot(
                                metrics
                        );


                LOGGER.debug(
                        "Snapshot enregistré : {} | MidPrice={}",
                        symbol,
                        metrics.midPrice()
                );


            } catch (Exception exception) {


                // =================================
                // ERROR FOR ONE ASSET
                // =================================

                LOGGER.error(
                        "Impossible de capturer le snapshot pour {} : {}",
                        asset != null
                                ? asset.getSymbol()
                                : "UNKNOWN",
                        exception.getMessage(),
                        exception
                );
            }
        }
    }
}