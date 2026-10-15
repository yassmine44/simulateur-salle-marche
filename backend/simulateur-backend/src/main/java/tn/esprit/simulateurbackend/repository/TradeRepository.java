package tn.esprit.simulateurbackend.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.simulateurbackend.dto.TradeTick;
import tn.esprit.simulateurbackend.entity.Trade;
import tn.esprit.simulateurbackend.repository.projection.TradeStatsProjection;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TradeRepository
        extends JpaRepository<Trade, Long> {


    List<Trade>
    findByAssetSymbolIgnoreCaseOrderByCreatedAtDesc(
            String assetSymbol
    );


    @Query("""
            SELECT DISTINCT t
            FROM Trade t

            JOIN FETCH t.buyOrder buyOrder
            JOIN FETCH buyOrder.user buyUser

            JOIN FETCH t.sellOrder sellOrder
            JOIN FETCH sellOrder.user sellUser

            WHERE buyUser.id = :userId
               OR sellUser.id = :userId

            ORDER BY t.createdAt DESC
            """)
    List<Trade> findAllForUser(
            @Param("userId") Long userId
    );


    // =========================================
    // MARKET DATA
    // =========================================

    /*
     * Dernière transaction d'un actif = dernier cours.
     */
    Optional<Trade>
    findFirstByAssetSymbolOrderByCreatedAtDescIdDesc(
            String assetSymbol
    );


    /*
     * Dernière transaction d'un actif avant un instant donné
     * (valorisation historique du portefeuille).
     */
    Optional<Trade>
    findFirstByAssetSymbolAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            String assetSymbol,
            Instant before
    );


    // Transaction la plus récente, tous actifs confondus.
    Optional<Trade> findTopByOrderByIdDesc();


    /*
     * Transactions plus récentes qu'une transaction donnée
     * (flux pris en compte par le teneur de marché).
     */
    List<Trade>
    findByAssetSymbolAndIdGreaterThan(
            String assetSymbol,
            Long id
    );


    Optional<Trade>
    findFirstByAssetSymbolAndCreatedAtGreaterThanEqualOrderByCreatedAtAscIdAsc(
            String assetSymbol,
            Instant since
    );


    /*
     * Flux public des dernières exécutions.
     */
    @Query("""
            SELECT t
            FROM Trade t
            WHERE t.assetSymbol = :symbol
            ORDER BY t.createdAt DESC, t.id DESC
            """)
    List<Trade> findRecent(
            @Param("symbol") String symbol,
            Pageable pageable
    );


    @Query("""
            SELECT
              MIN(t.executionPrice) AS low,
              MAX(t.executionPrice) AS high,
              COALESCE(SUM(t.quantity), 0) AS volume,
              COUNT(t) AS tradeCount
            FROM Trade t
            WHERE t.assetSymbol = :symbol
              AND t.createdAt >= :since
            """)
    TradeStatsProjection statsSince(
            @Param("symbol") String symbol,
            @Param("since") Instant since
    );


    /*
     * Transactions réduites à (date, prix, quantité)
     * pour construire les chandeliers.
     */
    @Query("""
            SELECT new tn.esprit.simulateurbackend.dto.TradeTick(
              t.createdAt,
              t.executionPrice,
              t.quantity
            )
            FROM Trade t
            WHERE t.assetSymbol = :symbol
              AND t.createdAt >= :since
            ORDER BY t.createdAt ASC, t.id ASC
            """)
    List<TradeTick> findTicksSince(
            @Param("symbol") String symbol,
            @Param("since") Instant since
    );
}
