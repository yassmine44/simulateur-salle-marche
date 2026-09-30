package tn.esprit.simulateurbackend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "password_reset_tokens",
        indexes = {
                @Index(
                        name = "idx_password_reset_token_hash",
                        columnList = "token_hash"
                ),
                @Index(
                        name = "idx_password_reset_user_id",
                        columnList = "user_id"
                )
        }
)
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;


    /*
     * On ne stocke JAMAIS le token brut.
     *
     * Le token reçu par email sera hashé
     * avant d'être enregistré en base.
     */
    @Column(
            name = "token_hash",
            nullable = false,
            unique = true,
            length = 64
    )
    private String tokenHash;


    @Column(
            name = "expires_at",
            nullable = false
    )
    private LocalDateTime expiresAt;


    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;


    @Column(
            name = "used_at"
    )
    private LocalDateTime usedAt;


    @PrePersist
    void prePersist() {

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }


    public boolean isExpired() {
        return LocalDateTime.now()
                .isAfter(expiresAt);
    }


    public boolean isUsed() {
        return usedAt != null;
    }


    public Long getId() {
        return id;
    }


    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }


    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(
            String tokenHash
    ) {
        this.tokenHash = tokenHash;
    }


    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(
            LocalDateTime expiresAt
    ) {
        this.expiresAt = expiresAt;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(
            LocalDateTime usedAt
    ) {
        this.usedAt = usedAt;
    }
}