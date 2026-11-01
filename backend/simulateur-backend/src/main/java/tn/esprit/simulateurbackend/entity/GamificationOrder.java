package tn.esprit.simulateurbackend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/*
 * Ordre marché exécuté dans un défi gamification.
 * Isolé des ordres live (trade_orders).
 */
@Entity
@Table(
        name = "gamification_orders",
        indexes = {
                @Index(
                        name = "idx_gamification_orders_portfolio",
                        columnList = "portfolio_id"
                )
        }
)
public class GamificationOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderSide side;


    @Column(
            nullable = false,
            precision = 19,
            scale = 8
    )
    private BigDecimal quantity;


    @Column(
            name = "execution_price",
            nullable = false,
            precision = 19,
            scale = 8
    )
    private BigDecimal executionPrice;


    @Column(
            name = "candle_index",
            nullable = false
    )
    private int candleIndex;


    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;


    @PrePersist
    public void prePersist() {

        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }


    public Long getId() {
        return id;
    }


    public Long getPortfolioId() {
        return portfolioId;
    }


    public void setPortfolioId(Long portfolioId) {
        this.portfolioId = portfolioId;
    }


    public OrderSide getSide() {
        return side;
    }


    public void setSide(OrderSide side) {
        this.side = side;
    }


    public BigDecimal getQuantity() {
        return quantity;
    }


    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }


    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }


    public void setExecutionPrice(
            BigDecimal executionPrice
    ) {
        this.executionPrice = executionPrice;
    }


    public int getCandleIndex() {
        return candleIndex;
    }


    public void setCandleIndex(int candleIndex) {
        this.candleIndex = candleIndex;
    }


    public Instant getCreatedAt() {
        return createdAt;
    }


    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
