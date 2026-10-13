package tn.esprit.simulateurbackend.repository;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


public interface OrderRepository
        extends JpaRepository<Order, Long> {


    /*
     * =========================================
     * CARNET BID
     * =========================================
     *
     * Priorité :
     * 1. Prix le plus élevé
     * 2. À prix égal : ordre le plus ancien
     */

    List<Order>
    findByAssetSymbolIgnoreCaseAndSideAndStatusInOrderByLimitPriceDescCreatedAtAsc(
            String assetSymbol,
            OrderSide side,
            Collection<OrderStatus> statuses
    );


    /*
     * =========================================
     * CARNET ASK
     * =========================================
     *
     * Priorité :
     * 1. Prix le plus faible
     * 2. À prix égal : ordre le plus ancien
     */

    List<Order>
    findByAssetSymbolIgnoreCaseAndSideAndStatusInOrderByLimitPriceAscCreatedAtAsc(
            String assetSymbol,
            OrderSide side,
            Collection<OrderStatus> statuses
    );


    /*
     * =========================================
     * ORDRES D'UN UTILISATEUR
     * =========================================
     */

    List<Order> findByUserIdOrderByCreatedAtDesc(
            Long userId
    );


    /*
     * =========================================
     * LOCK D'UN ORDRE
     * =========================================
     */

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM Order o
        WHERE o.id = :id
        """)
    Optional<Order> findByIdForUpdate(
            @Param("id") Long id
    );


    /*
     * =========================================
     * MATCHING LIMIT BUY
     * =========================================
     *
     * Un BUY LIMIT peut rencontrer uniquement
     * des ASK <= son prix limite.
     *
     * Meilleur ASK d'abord.
     */

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM Order o
        WHERE UPPER(o.assetSymbol) = UPPER(:symbol)
          AND o.side = :side
          AND o.status IN :statuses
          AND o.limitPrice IS NOT NULL
          AND o.limitPrice <= :maxPrice
          AND o.user.id <> :userId
        ORDER BY
          o.limitPrice ASC,
          o.createdAt ASC,
          o.id ASC
        """)
    List<Order> findMatchingAsks(
            @Param("symbol") String symbol,
            @Param("side") OrderSide side,
            @Param("statuses") Collection<OrderStatus> statuses,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("userId") Long userId
    );


    /*
     * =========================================
     * MATCHING LIMIT SELL
     * =========================================
     *
     * Un SELL LIMIT peut rencontrer uniquement
     * des BID >= son prix limite.
     *
     * Meilleur BID d'abord.
     */

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM Order o
        WHERE UPPER(o.assetSymbol) = UPPER(:symbol)
          AND o.side = :side
          AND o.status IN :statuses
          AND o.limitPrice IS NOT NULL
          AND o.limitPrice >= :minPrice
          AND o.user.id <> :userId
        ORDER BY
          o.limitPrice DESC,
          o.createdAt ASC,
          o.id ASC
        """)
    List<Order> findMatchingBids(
            @Param("symbol") String symbol,
            @Param("side") OrderSide side,
            @Param("statuses") Collection<OrderStatus> statuses,
            @Param("minPrice") BigDecimal minPrice,
            @Param("userId") Long userId
    );


    /*
     * =========================================
     * MARKET BUY
     * =========================================
     *
     * Aucun prix maximum.
     *
     * On consomme les ASK :
     *
     * prix le plus faible
     * puis ordre le plus ancien.
     */

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM Order o
        WHERE UPPER(o.assetSymbol) = UPPER(:symbol)
          AND o.side = :side
          AND o.status IN :statuses
          AND o.limitPrice IS NOT NULL
          AND o.user.id <> :userId
        ORDER BY
          o.limitPrice ASC,
          o.createdAt ASC,
          o.id ASC
        """)
    List<Order> findMarketAsks(
            @Param("symbol") String symbol,
            @Param("side") OrderSide side,
            @Param("statuses") Collection<OrderStatus> statuses,
            @Param("userId") Long userId
    );


    /*
     * =========================================
     * MARKET SELL
     * =========================================
     *
     * Aucun prix minimum.
     *
     * On consomme les BID :
     *
     * prix le plus élevé
     * puis ordre le plus ancien.
     */

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM Order o
        WHERE UPPER(o.assetSymbol) = UPPER(:symbol)
          AND o.side = :side
          AND o.status IN :statuses
          AND o.limitPrice IS NOT NULL
          AND o.user.id <> :userId
        ORDER BY
          o.limitPrice DESC,
          o.createdAt ASC,
          o.id ASC
        """)
    List<Order> findMarketBids(
            @Param("symbol") String symbol,
            @Param("side") OrderSide side,
            @Param("statuses") Collection<OrderStatus> statuses,
            @Param("userId") Long userId
    );
}