package tn.esprit.simulateurbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;

import java.util.Collection;
import java.util.List;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
public interface OrderRepository
        extends JpaRepository<Order, Long> {

    /*
     * Carnet côté ACHAT / BID
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
     * Carnet côté VENTE / ASK
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
     * Ordres d'un utilisateur.
     */
    List<Order>
    findByUserIdOrderByCreatedAtDesc(
            Long userId
    );
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM Order o
        WHERE o.id = :id
        """)
    Optional<Order> findByIdForUpdate(
            @Param("id") Long id
    );


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM Order o
        WHERE UPPER(o.assetSymbol) = UPPER(:symbol)
          AND o.side = :side
          AND o.status IN :statuses
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


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM Order o
        WHERE UPPER(o.assetSymbol) = UPPER(:symbol)
          AND o.side = :side
          AND o.status IN :statuses
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
}