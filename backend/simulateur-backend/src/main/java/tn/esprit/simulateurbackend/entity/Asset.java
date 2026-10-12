package tn.esprit.simulateurbackend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "assets",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_assets_symbol",
                        columnNames = "symbol"
                )
        },
        indexes = {
                @Index(
                        name = "idx_assets_symbol",
                        columnList = "symbol"
                ),
                @Index(
                        name = "idx_assets_type",
                        columnList = "asset_type"
                ),
                @Index(
                        name = "idx_assets_active",
                        columnList = "active"
                )
        }
)
public class Asset {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;


    @Column(
            nullable = false,
            length = 30
    )
    private String symbol;


    @Column(
            nullable = false,
            length = 120
    )
    private String name;


    @Enumerated(EnumType.STRING)
    @Column(
            name = "asset_type",
            nullable = false,
            length = 30
    )
    private AssetType type;


    @Column(
            nullable = false,
            length = 10
    )
    private String currency;


    @Column(
            nullable = false
    )
    private boolean active = true;


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

        if (symbol != null) {
            symbol =
                    symbol.trim()
                            .toUpperCase();
        }

        if (currency != null) {
            currency =
                    currency.trim()
                            .toUpperCase();
        }
    }


    public Long getId() {
        return id;
    }


    public void setId(Long id) {
        this.id = id;
    }


    public String getSymbol() {
        return symbol;
    }


    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }


    public String getName() {
        return name;
    }


    public void setName(String name) {
        this.name = name;
    }


    public AssetType getType() {
        return type;
    }


    public void setType(AssetType type) {
        this.type = type;
    }


    public String getCurrency() {
        return currency;
    }


    public void setCurrency(String currency) {
        this.currency = currency;
    }


    public boolean isActive() {
        return active;
    }


    public void setActive(boolean active) {
        this.active = active;
    }


    public Instant getCreatedAt() {
        return createdAt;
    }


    public void setCreatedAt(
            Instant createdAt
    ) {
        this.createdAt = createdAt;
    }
}