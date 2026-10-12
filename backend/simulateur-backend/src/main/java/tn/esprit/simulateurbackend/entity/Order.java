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