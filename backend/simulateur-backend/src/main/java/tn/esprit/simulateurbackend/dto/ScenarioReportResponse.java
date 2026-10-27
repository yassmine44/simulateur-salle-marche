
package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.ScenarioExecutionStatus;
import tn.esprit.simulateurbackend.entity.ScenarioType;

import java.math.BigDecimal;
import java.time.Instant;

public record ScenarioReportResponse(
        Long id,
        ScenarioType type,
        String symbol,
        BigDecimal intensity,
        ScenarioExecutionStatus status,

        Instant startedAt,
        Instant scheduledEndsAt,
        Instant actualEndedAt,

        long plannedDurationSeconds,
        Long actualDurationSeconds,
        BigDecimal completionPercent
) {
}
