package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.simulateurbackend.dto.OrderBookMetricsResponse;
import tn.esprit.simulateurbackend.entity.OrderBookSnapshot;
import tn.esprit.simulateurbackend.repository.OrderBookSnapshotRepository;


@Service
public class OrderBookSnapshotService {

    private final OrderBookSnapshotRepository snapshotRepository;


    public OrderBookSnapshotService(
            OrderBookSnapshotRepository snapshotRepository
    ) {

        this.snapshotRepository =
                snapshotRepository;
    }


    // =========================================
    // SAVE SNAPSHOT
    // =========================================

    @Transactional
    public void saveSnapshot(
            OrderBookMetricsResponse metrics
    ) {

        /*
         * Sans MidPrice, impossible de calculer
         * les rendements, volatilité et momentum.
         *
         * On ne sauvegarde donc que les états
         * comportant BID + ASK.
         */

        if (
                metrics == null
                        ||
                        metrics.midPrice() == null
        ) {

            return;
        }


        OrderBookSnapshot snapshot =
                new OrderBookSnapshot();


        snapshot.setAssetSymbol(
                metrics.assetSymbol()
        );


        snapshot.setBestBid(
                metrics.bestBid()
        );


        snapshot.setBestAsk(
                metrics.bestAsk()
        );


        snapshot.setMidPrice(
                metrics.midPrice()
        );


        snapshot.setSpread(
                metrics.spread()
        );


        snapshot.setRelativeSpread(
                metrics.relativeSpread()
        );


        snapshot.setTotalBidVolume(
                metrics.totalBidVolume()
        );


        snapshot.setTotalAskVolume(
                metrics.totalAskVolume()
        );


        snapshot.setOrderBookImbalance(
                metrics.orderBookImbalance()
        );


        snapshot.setWeightedBidDepth(
                metrics.weightedBidDepth()
        );


        snapshot.setWeightedAskDepth(
                metrics.weightedAskDepth()
        );


        snapshotRepository.save(
                snapshot
        );
    }
}