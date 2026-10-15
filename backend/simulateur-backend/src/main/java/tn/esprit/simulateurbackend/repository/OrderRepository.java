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

/*
 * Seuls les ordres LIMIT ouverts reposent dans le carnet.
 *
 * Les ordres MARKET s'exécutent immédiatement et les ordres
 * conditionnels (stop-loss / take-profit) attendent hors carnet :
 * toutes les requêtes du carnet filtrent donc sur le type LIMIT.
 */
public interface OrderRepository
        extends JpaRepository<Order, Long> {

    /*
     * Carnet côté ACHAT / BID
     *
     * Priorité :
     * 1. Prix le plus élevé
     * 2. À prix égal : ordre le plus ancien
     */
    @Query("""
        SELECT o
        FROM Order o
        WHERE UPPER(o.assetSymbol) = UPPER(:symbol)
          AND o.side = tn.esprit.simulateurbackend.entity.OrderSide.BUY
          AND o.type = tn.esprit.simulateurbackend.entity.OrderType.LIMIT
          AND o.status IN :statuses
        ORDER BY
          o.limitPrice DESC,
          o.createdAt ASC,
          o.id ASC
        """)
    List<Order> findBookBids(
            @Param("symbol") String symbol,
            @Param("statuses") Collection<OrderStatus> statuses
    );


    /*
     * Carnet côté VENTE / ASK
     *
     * Priorité :
     * 1. Prix le plus faible
     * 2. À prix égal : ordre le plus ancien
     */
    @Query("""
        SELECT o
        FROM Order o
        WHERE UPPER(o.assetSymbol) = UPPER(:symbol)
          AND o.side = tn.esprit.simulateurbackend.entity.OrderSide.SELL
          AND o.type = tn.esprit.simulateurbackend.entity.OrderType.LIMIT
          AND o.status IN :statuses
        ORDER BY
          o.limitPrice ASC,
          o.createdAt ASC,
          o.id ASC
        """)
    List<Order> findBookAsks(
            @Param("symbol") String symbol,
            @Param("statuses") Collection<OrderStatus> statuses
    );


    /*
     * Ordres d'un utilisateur.
     */
    List<Order>
    findByUserIdOrderByCreatedAtDesc(
            Long userId
    );


    /*
     * Branches d'un même OCO (ou d'un même bracket).
     */
    List<Order> findByOcoGroupOrderByIdAsc(String ocoGroup);


    /*
     * Ordres DAY arrivés à échéance.
     */
    @Query("""
        SELECT o
        FROM Order o
        WHERE o.expiresAt IS NOT NULL
          AND o.expiresAt <= :now
          AND o.status IN :statuses
        """)
    List<Order> findExpired(
            @Param("now") java.time.Instant now,
            @Param("statuses") Collection<OrderStatus> statuses
    );


    /*
     * Ordres encore ouverts d'un utilisateur, tous actifs
     * confondus (réservations du portefeuille).
     */
    List<Order>
    findByUserIdAndStatusIn(
            Long userId,
            Collection<OrderStatus> statuses
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
          AND o.type = tn.esprit.simulateurbackend.entity.OrderType.LIMIT
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
          AND o.type = tn.esprit.simulateurbackend.entity.OrderType.LIMIT
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


    /*
     * Ordres LIMIT ouverts d'un utilisateur sur un actif et un sens.
     *
     * Sert à la prévention de l'auto-négociation
     * et au teneur de marché.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM Order o
        WHERE o.user.id = :userId
          AND UPPER(o.assetSymbol) = UPPER(:symbol)
          AND o.side = :side
          AND o.type = tn.esprit.simulateurbackend.entity.OrderType.LIMIT
          AND o.status IN :statuses
        ORDER BY
          o.id ASC
        """)
    List<Order> findOpenLimitOrdersOfUser(
            @Param("userId") Long userId,
            @Param("symbol") String symbol,
            @Param("side") OrderSide side,
            @Param("statuses") Collection<OrderStatus> statuses
    );


    /*
     * Ordres conditionnels (stop-loss / take-profit)
     * en attente de déclenchement sur un actif.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM Order o
        WHERE UPPER(o.assetSymbol) = UPPER(:symbol)
          AND o.triggerType IS NOT NULL
          AND o.triggeredAt IS NULL
          AND o.status = tn.esprit.simulateurbackend.entity.OrderStatus.PENDING
        ORDER BY
          o.createdAt ASC,
          o.id ASC
        """)
    List<Order> findWaitingConditionalOrders(
            @Param("symbol") String symbol
    );
}
