package tn.esprit.simulateurbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.simulateurbackend.entity.OrderBookSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;


public interface OrderBookSnapshotRepository
        extends JpaRepository<OrderBookSnapshot, Long> {


    // =========================================
    // LATEST SNAPSHOT
    // =========================================

    Optional<OrderBookSnapshot>
    findFirstByAssetSymbolIgnoreCaseAndMidPriceIsNotNullOrderByCreatedAtDesc(
            String assetSymbol
    );


    // =========================================
    // SNAPSHOT BEFORE OR AT A DATE
    // =========================================

    Optional<OrderBookSnapshot>
    findFirstByAssetSymbolIgnoreCaseAndMidPriceIsNotNullAndCreatedAtLessThanEqualOrderByCreatedAtDesc(

            String assetSymbol,

            Instant createdAt
    );


    // =========================================
    // SNAPSHOTS BETWEEN TWO DATES
    // =========================================

    List<OrderBookSnapshot>
    findByAssetSymbolIgnoreCaseAndMidPriceIsNotNullAndCreatedAtBetweenOrderByCreatedAtAsc(

            String assetSymbol,

            Instant from,

            Instant to
    );
}