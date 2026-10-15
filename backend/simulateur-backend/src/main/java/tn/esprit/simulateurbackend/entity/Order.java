package tn.esprit.simulateurbackend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "trade_orders",
        indexes = {

                @Index(
                        name = "idx_trade_orders_user",
                        columnList = "user_id"
                ),

                @Index(
                        name = "idx_trade_orders_symbol",
                        columnList = "asset_symbol"
                ),

                @Index(
                        name = "idx_trade_orders_book",
                        columnList = "asset_symbol, side, status, limit_price, created_at"
                )
        }
)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =========================================
    // USER
    // =========================================

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;


    // =========================================
    // ASSET
    // =========================================

    @Column(
            name = "asset_symbol",
            nullable = false,
            length = 30
    )
    private String assetSymbol;


    // =========================================
    // ORDER INFORMATION
    // =========================================

    @Enumerated(EnumType.STRING)
    @Column(
            name = "side",
            nullable = false,
            length = 10
    )
    private OrderSide side;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "order_type",
            nullable = false,
            length = 20
    )
    private OrderType type;


    @Column(
            name = "limit_price",
            precision = 19,
            scale = 8
    )
    private BigDecimal limitPrice;


    @Column(
            name = "quantity",
            nullable = false,
            precision = 19,
            scale = 8
    )
    private BigDecimal quantity;


    @Column(
            name = "remaining_quantity",
            nullable = false,
            precision = 19,
            scale = 8
    )
    private BigDecimal remainingQuantity;


    // =========================================
    // CONDITIONAL ORDER (STOP-LOSS / TAKE-PROFIT)
    // =========================================

    /*
     * Un ordre conditionnel est un ordre MARKET
     * qui attend hors du carnet (statut PENDING)
     * jusqu'à ce que le dernier cours atteigne
     * triggerPrice.
     */

    @Enumerated(EnumType.STRING)
    @Column(
            name = "trigger_type",
            length = 20
    )
    private TriggerType triggerType;


    @Column(
            name = "trigger_price",
            precision = 19,
            scale = 8
    )
    private BigDecimal triggerPrice;


    @Column(
            name = "triggered_at"
    )
    private Instant triggeredAt;


    /*
     * Stop suiveur : le seuil de déclenchement suit le cours
     * à cette distance (en %) et ne recule jamais.
     */
    @Column(
            name = "trail_percent",
            precision = 9,
            scale = 4
    )
    private BigDecimal trailPercent;


    // =========================================
    // VALIDITY
    // =========================================

    // Null pour les ordres antérieurs : équivalent à GTC.
    @Enumerated(EnumType.STRING)
    @Column(
            name = "time_in_force",
            length = 10
    )
    private TimeInForce timeInForce;


    // Ordres DAY : instant d'annulation automatique.
    @Column(
            name = "expires_at"
    )
    private Instant expiresAt;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "cancel_reason",
            length = 20
    )
    private CancelReason cancelReason;


    // =========================================
    // LINKED ORDERS (BRACKET / OCO)
    // =========================================

    /*
     * Bracket : prix de protection demandés avec l'achat.
     * Les ordres de sortie sont créés au fur et à mesure
     * que l'achat est exécuté.
     */
    @Column(
            name = "bracket_stop_price",
            precision = 19,
            scale = 8
    )
    private BigDecimal bracketStopPrice;


    @Column(
            name = "bracket_take_profit_price",
            precision = 19,
            scale = 8
    )
    private BigDecimal bracketTakeProfitPrice;


    // Ordre d'entrée dont cet ordre de sortie dépend.
    @Column(
            name = "parent_order_id"
    )
    private Long parentOrderId;


    /*
     * Branches d'un même OCO ("l'un annule l'autre") :
     * elles partagent ce groupe et la quantité oco_quantity.
     */
    @Column(
            name = "oco_group",
            length = 36
    )
    private String ocoGroup;


    @Column(
            name = "oco_quantity",
            precision = 19,
            scale = 8
    )
    private BigDecimal ocoQuantity;


    // =========================================
    // EXECUTION QUALITY
    // =========================================

    // Prix milieu du carnet à l'arrivée de l'ordre.
    @Column(
            name = "arrival_mid_price",
            precision = 19,
            scale = 8
    )
    private BigDecimal arrivalMidPrice;


    // =========================================
    // STATUS
    // =========================================

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private OrderStatus status;


    // =========================================
    // DATES
    // =========================================

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;


    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;


    // =========================================
    // JPA CALLBACKS
    // =========================================

    @PrePersist
    protected void onCreate() {

        Instant now = Instant.now();

        this.createdAt = now;
        this.updatedAt = now;

        if (this.status == null) {
            this.status = OrderStatus.PENDING;
        }

        if (this.remainingQuantity == null) {
            this.remainingQuantity = this.quantity;
        }
    }


    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }


    // =========================================
    // GETTERS / SETTERS
    // =========================================

    public Long getId() {
        return id;
    }


    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }


    public String getAssetSymbol() {
        return assetSymbol;
    }

    public void setAssetSymbol(String assetSymbol) {
        this.assetSymbol = assetSymbol;
    }


    public OrderSide getSide() {
        return side;
    }

    public void setSide(OrderSide side) {
        this.side = side;
    }


    public OrderType getType() {
        return type;
    }

    public void setType(OrderType type) {
        this.type = type;
    }


    public BigDecimal getLimitPrice() {
        return limitPrice;
    }

    public void setLimitPrice(BigDecimal limitPrice) {
        this.limitPrice = limitPrice;
    }


    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }


    public BigDecimal getRemainingQuantity() {
        return remainingQuantity;
    }

    public void setRemainingQuantity(
            BigDecimal remainingQuantity
    ) {
        this.remainingQuantity = remainingQuantity;
    }


    public TriggerType getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(TriggerType triggerType) {
        this.triggerType = triggerType;
    }


    public BigDecimal getTriggerPrice() {
        return triggerPrice;
    }

    public void setTriggerPrice(BigDecimal triggerPrice) {
        this.triggerPrice = triggerPrice;
    }


    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Instant triggeredAt) {
        this.triggeredAt = triggeredAt;
    }


    public BigDecimal getTrailPercent() {
        return trailPercent;
    }

    public void setTrailPercent(BigDecimal trailPercent) {
        this.trailPercent = trailPercent;
    }


    public TimeInForce getTimeInForce() {
        return timeInForce;
    }

    public void setTimeInForce(TimeInForce timeInForce) {
        this.timeInForce = timeInForce;
    }

    // Les ordres antérieurs à cette option sont valables jusqu'à annulation.
    public TimeInForce effectiveTimeInForce() {
        return timeInForce == null ? TimeInForce.GTC : timeInForce;
    }


    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }


    public CancelReason getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(CancelReason cancelReason) {
        this.cancelReason = cancelReason;
    }


    // Annule l'ordre en conservant sa raison.
    public void cancel(CancelReason reason) {
        this.status = OrderStatus.CANCELLED;
        this.cancelReason = reason;
    }


    public BigDecimal getBracketStopPrice() {
        return bracketStopPrice;
    }

    public void setBracketStopPrice(BigDecimal bracketStopPrice) {
        this.bracketStopPrice = bracketStopPrice;
    }


    public BigDecimal getBracketTakeProfitPrice() {
        return bracketTakeProfitPrice;
    }

    public void setBracketTakeProfitPrice(BigDecimal bracketTakeProfitPrice) {
        this.bracketTakeProfitPrice = bracketTakeProfitPrice;
    }


    public boolean hasBracket() {
        return bracketStopPrice != null || bracketTakeProfitPrice != null;
    }


    public Long getParentOrderId() {
        return parentOrderId;
    }

    public void setParentOrderId(Long parentOrderId) {
        this.parentOrderId = parentOrderId;
    }


    public String getOcoGroup() {
        return ocoGroup;
    }

    public void setOcoGroup(String ocoGroup) {
        this.ocoGroup = ocoGroup;
    }


    public BigDecimal getOcoQuantity() {
        return ocoQuantity;
    }

    public void setOcoQuantity(BigDecimal ocoQuantity) {
        this.ocoQuantity = ocoQuantity;
    }


    public BigDecimal getArrivalMidPrice() {
        return arrivalMidPrice;
    }

    public void setArrivalMidPrice(BigDecimal arrivalMidPrice) {
        this.arrivalMidPrice = arrivalMidPrice;
    }


    // Quantité déjà exécutée.
    public BigDecimal executedQuantity() {
        return quantity.subtract(remainingQuantity);
    }


    public boolean isOpen() {
        return status == OrderStatus.PENDING
                || status == OrderStatus.PARTIALLY_FILLED;
    }


    public boolean isWaitingTrigger() {

        return triggerType != null
                && triggeredAt == null
                && status == OrderStatus.PENDING;
    }


    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }


    public Instant getCreatedAt() {
        return createdAt;
    }


    public Instant getUpdatedAt() {
        return updatedAt;
    }
}