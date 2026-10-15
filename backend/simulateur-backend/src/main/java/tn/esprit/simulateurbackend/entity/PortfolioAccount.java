package tn.esprit.simulateurbackend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/*
 * Compte de portefeuille virtuel d'un utilisateur.
 *
 * Les liquidités et les positions ne sont pas stockées :
 * elles sont recalculées à partir des transactions, qui restent
 * la seule source de vérité. Ce compte ne garde que ce qui ne
 * peut pas s'en déduire : le capital de départ et le point de
 * départ après une réinitialisation.
 *
 * Absent tant que l'utilisateur n'a jamais réinitialisé son
 * portefeuille : les valeurs par défaut de la configuration
 * s'appliquent alors.
 */
@Entity
@Table(
        name = "portfolio_accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_portfolio_accounts_user",
                        columnNames = "user_id"
                )
        }
)
public class PortfolioAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Simple identifiant, sans clé étrangère :
     * la suppression d'un utilisateur n'est pas bloquée.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(
            name = "initial_capital",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal initialCapital;

    /*
     * Seules les transactions d'identifiant strictement
     * supérieur comptent dans le portefeuille.
     * 0 = toutes les transactions.
     */
    @Column(name = "reset_after_trade_id", nullable = false)
    private Long resetAfterTradeId = 0L;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "reset_count", nullable = false)
    private int resetCount = 0;


    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public BigDecimal getInitialCapital() {
        return initialCapital;
    }

    public void setInitialCapital(BigDecimal initialCapital) {
        this.initialCapital = initialCapital;
    }

    public Long getResetAfterTradeId() {
        return resetAfterTradeId;
    }

    public void setResetAfterTradeId(Long resetAfterTradeId) {
        this.resetAfterTradeId = resetAfterTradeId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public int getResetCount() {
        return resetCount;
    }

    public void setResetCount(int resetCount) {
        this.resetCount = resetCount;
    }
}
