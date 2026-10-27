
package tn.esprit.simulateurbackend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "scenario_executions",
        indexes = {
                @Index(
                        name = "idx_scenario_execution_started",
                        columnList = "started_at"
                ),
                @Index(
                        name = "idx_scenario_execution_symbol",
                        columnList = "symbol"
                )
        }
)
public class ScenarioExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scenario_type", nullable = false, length = 40)
    private ScenarioType type;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(
            nullable = false,
            precision = 18,
            scale = 6
    )
    private BigDecimal intensity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ScenarioExecutionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "scheduled_ends_at", nullable = false)
    private Instant scheduledEndsAt;

    @Column(name = "actual_ended_at")
    private Instant actualEndedAt;

    protected ScenarioExecution() {
        // Constructeur requis par JPA.
    }

    public ScenarioExecution(
            ScenarioType type,
            String symbol,
            BigDecimal intensity,
            Instant startedAt,
            Instant scheduledEndsAt
    ) {
        this.type = Objects.requireNonNull(type);
        this.symbol = Objects.requireNonNull(symbol);
        this.intensity = Objects.requireNonNull(intensity);
        this.startedAt = Objects.requireNonNull(startedAt);
        this.scheduledEndsAt =
                Objects.requireNonNull(scheduledEndsAt);

        this.status = ScenarioExecutionStatus.RUNNING;
    }

    public void markStopped(Instant endedAt) {
        finish(ScenarioExecutionStatus.STOPPED, endedAt);
    }

    public void markExpired(Instant endedAt) {
        finish(ScenarioExecutionStatus.EXPIRED, endedAt);
    }

    public void markInterrupted(Instant endedAt) {
        finish(ScenarioExecutionStatus.INTERRUPTED, endedAt);
    }

    private void finish(
            ScenarioExecutionStatus newStatus,
            Instant endedAt
    ) {
        Objects.requireNonNull(newStatus);
        Objects.requireNonNull(endedAt);

        if (status != ScenarioExecutionStatus.RUNNING) {
            throw new IllegalStateException(
                    "Le scénario est déjà terminé."
            );
        }

        if (endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException(
                    "La date de fin précède le début."
            );
        }

        this.status = newStatus;
        this.actualEndedAt = endedAt;
    }

    public Long getId() {
        return id;
    }

    public ScenarioType getType() {
        return type;
    }

    public String getSymbol() {
        return symbol;
    }

    public BigDecimal getIntensity() {
        return intensity;
    }

    public ScenarioExecutionStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getScheduledEndsAt() {
        return scheduledEndsAt;
    }

    public Instant getActualEndedAt() {
        return actualEndedAt;
    }
}
