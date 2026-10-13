package tn.esprit.simulateurbackend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "order_book_snapshots",
        indexes = {
                @Index(
                        name = "idx_snapshot_symbol_created",
                        columnList = "asset_symbol, created_at"
                )
        }
)
public class OrderBookSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(
            name = "asset_symbol",
            nullable = false,
            length = 30
    )
    private String assetSymbol;


    @Column(
            name = "best_bid",
            precision = 19,
            scale = 8
    )
    private BigDecimal bestBid;


    @Column(
            name = "best_ask",
            precision = 19,
            scale = 8
    )
    private BigDecimal bestAsk;


    @Column(
            name = "mid_price",
            precision = 19,
            scale = 8
    )
    private BigDecimal midPrice;


    @Column(
            name = "spread",
            precision = 19,
            scale = 8
    )
    private BigDecimal spread;


    @Column(
            name = "relative_spread",
            precision = 19,
            scale = 12
    )
    private BigDecimal relativeSpread;


    @Column(
            name = "total_bid_volume",
            precision = 19,
            scale = 8
    )
    private BigDecimal totalBidVolume;


    @Column(
            name = "total_ask_volume",
            precision = 19,
            scale = 8
    )
    private BigDecimal totalAskVolume;


    @Column(
            name = "order_book_imbalance",
            precision = 19,
            scale = 12
    )
    private BigDecimal orderBookImbalance;


    @Column(
            name = "weighted_bid_depth",
            precision = 19,
            scale = 8
    )
    private BigDecimal weightedBidDepth;


    @Column(
            name = "weighted_ask_depth",
            precision = 19,
            scale = 8
    )
    private BigDecimal weightedAskDepth;


    @Column(
            name = "created_at",
            nullable = false
    )
    private Instant createdAt;


    // =========================================
    // CONSTRUCTOR
    // =========================================

    public OrderBookSnapshot() {
    }


    // =========================================
    // GETTERS / SETTERS
    // =========================================

    public Long getId() {
        return id;
    }


    public String getAssetSymbol() {
        return assetSymbol;
    }

    public void setAssetSymbol(
            String assetSymbol
    ) {
        this.assetSymbol = assetSymbol;
    }


    public BigDecimal getBestBid() {
        return bestBid;
    }

    public void setBestBid(
            BigDecimal bestBid
    ) {
        this.bestBid = bestBid;
    }


    public BigDecimal getBestAsk() {
        return bestAsk;
    }

    public void setBestAsk(
            BigDecimal bestAsk
    ) {
        this.bestAsk = bestAsk;
    }


    public BigDecimal getMidPrice() {
        return midPrice;
    }

    public void setMidPrice(
            BigDecimal midPrice
    ) {
        this.midPrice = midPrice;
    }


    public BigDecimal getSpread() {
        return spread;
    }

    public void setSpread(
            BigDecimal spread
    ) {
        this.spread = spread;
    }


    public BigDecimal getRelativeSpread() {
        return relativeSpread;
    }

    public void setRelativeSpread(
            BigDecimal relativeSpread
    ) {
        this.relativeSpread = relativeSpread;
    }


    public BigDecimal getTotalBidVolume() {
        return totalBidVolume;
    }

    public void setTotalBidVolume(
            BigDecimal totalBidVolume
    ) {
        this.totalBidVolume = totalBidVolume;
    }


    public BigDecimal getTotalAskVolume() {
        return totalAskVolume;
    }

    public void setTotalAskVolume(
            BigDecimal totalAskVolume
    ) {
        this.totalAskVolume = totalAskVolume;
    }


    public BigDecimal getOrderBookImbalance() {
        return orderBookImbalance;
    }

    public void setOrderBookImbalance(
            BigDecimal orderBookImbalance
    ) {
        this.orderBookImbalance =
                orderBookImbalance;
    }


    public BigDecimal getWeightedBidDepth() {
        return weightedBidDepth;
    }

    public void setWeightedBidDepth(
            BigDecimal weightedBidDepth
    ) {
        this.weightedBidDepth =
                weightedBidDepth;
    }


    public BigDecimal getWeightedAskDepth() {
        return weightedAskDepth;
    }

    public void setWeightedAskDepth(
            BigDecimal weightedAskDepth
    ) {
        this.weightedAskDepth =
                weightedAskDepth;
    }


    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(
            Instant createdAt
    ) {
        this.createdAt = createdAt;
    }


    // =========================================
    // PRE PERSIST
    // =========================================

    @PrePersist
    public void prePersist() {

        if (createdAt == null) {

            createdAt =
                    Instant.now();
        }
    }
}