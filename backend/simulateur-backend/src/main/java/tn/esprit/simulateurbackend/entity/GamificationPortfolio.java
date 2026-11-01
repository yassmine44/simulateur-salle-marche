package tn.esprit.simulateurbackend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/*
 * Portefeuille virtuel isolé pour un défi historique.
 * Ne touche jamais PortfolioAccount / Order / Trade du marché live.
 */
@Entity
@Table(
        name = "gamification_portfolios",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_gamification_portfolios_user_event",
                        columnNames = {
                                "user_id",
                                "event_code"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_gamification_portfolios_user",
                        columnList = "user_id"
                )
        }
)
public class GamificationPortfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(name = "user_id", nullable = false)
    private Long userId;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "event_code",
            nullable = false,
            length = 40
    )
    private GamificationEvent event;


    @Column(
            name = "cash_balance",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal cashBalance;


    @Column(
            name = "shares_held",
            nullable = false,
            precision = 19,
            scale = 8
    )
    private BigDecimal sharesHeld;


    @Column(
            name = "average_entry_price",
            nullable = false,
            precision = 19,
            scale = 8
    )
    private BigDecimal averageEntryPrice;


    /*
     * Nombre de bougies déjà révélées (fenêtre visible).
     * Le prix courant = close de la bougie (visibleCandleCount - 1).
     */
    @Column(
            name = "visible_candle_count",
            nullable = false
    )
    private int visibleCandleCount;


    @Column(
            name = "started_at",
            nullable = false
    )
    private Instant startedAt;


    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;


    @PrePersist
    public void prePersist() {

        Instant now = Instant.now();

        if (startedAt == null) {
            startedAt = now;
        }

        updatedAt = now;
    }


    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }


    public Long getId() {
        return id;
    }


    public Long getUserId() {
        return userId;
    }


    public void setUserId(Long userId) {
        this.userId = userId;
    }


    public GamificationEvent getEvent() {
        return event;
    }


    public void setEvent(GamificationEvent event) {
        this.event = event;
    }


    public BigDecimal getCashBalance() {
        return cashBalance;
    }


    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }


    public BigDecimal getSharesHeld() {
        return sharesHeld;
    }


    public void setSharesHeld(BigDecimal sharesHeld) {
        this.sharesHeld = sharesHeld;
    }


    public BigDecimal getAverageEntryPrice() {
        return averageEntryPrice;
    }


    public void setAverageEntryPrice(
            BigDecimal averageEntryPrice
    ) {
        this.averageEntryPrice = averageEntryPrice;
    }


    public int getVisibleCandleCount() {
        return visibleCandleCount;
    }


    public void setVisibleCandleCount(
            int visibleCandleCount
    ) {
        this.visibleCandleCount = visibleCandleCount;
    }


    public Instant getStartedAt() {
        return startedAt;
    }


    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }


    public Instant getUpdatedAt() {
        return updatedAt;
    }


    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
