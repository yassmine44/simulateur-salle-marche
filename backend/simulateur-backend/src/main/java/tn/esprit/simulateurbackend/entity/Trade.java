package tn.esprit.simulateurbackend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "trades",
        indexes = {
                @Index(
                        name = "idx_trades_symbol",
                        columnList = "asset_symbol"
                ),
                @Index(
                        name = "idx_trades_buy_order",
                        columnList = "buy_order_id"
                ),
                @Index(
                        name = "idx_trades_sell_order",
                        columnList = "sell_order_id"
                ),
                @Index(
                        name = "idx_trades_created",
                        columnList = "created_at"
                )
        }
)
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    // =========================================
    // ORDERS
    // =========================================

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "buy_order_id",
            nullable = false
    )
    private Order buyOrder;


    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "sell_order_id",
            nullable = false
    )
    private Order sellOrder;


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
    // EXECUTION
    // =========================================

    @Column(
            name = "execution_price",
            nullable = false,
            precision = 19,
            scale = 8
    )
    private BigDecimal executionPrice;


    @Column(
            name = "quantity",
            nullable = false,
            precision = 19,
            scale = 8
    )
    private BigDecimal quantity;


    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;


    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }


    public Long getId() {
        return id;
    }


    public Order getBuyOrder() {
        return buyOrder;
    }

    public void setBuyOrder(Order buyOrder) {
        this.buyOrder = buyOrder;
    }


    public Order getSellOrder() {
        return sellOrder;
    }

    public void setSellOrder(Order sellOrder) {
        this.sellOrder = sellOrder;
    }


    public String getAssetSymbol() {
        return assetSymbol;
    }

    public void setAssetSymbol(String assetSymbol) {
        this.assetSymbol = assetSymbol;
    }


    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }

    public void setExecutionPrice(
            BigDecimal executionPrice
    ) {
        this.executionPrice = executionPrice;
    }


    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }


    public Instant getCreatedAt() {
        return createdAt;
    }
}