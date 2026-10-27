
package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.ScenarioExecution;
import tn.esprit.simulateurbackend.entity.ScenarioExecutionStatus;
import tn.esprit.simulateurbackend.entity.ScenarioType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ScenarioHistoryResponse(
        List<Item> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public record Item(
            Long id,
            ScenarioType type,
            String symbol,
            BigDecimal intensity,
            ScenarioExecutionStatus status,
            Instant startedAt,
            Instant scheduledEndsAt,
            Instant actualEndedAt
    ) {

        public static Item from(ScenarioExecution execution) {
            return new Item(
                    execution.getId(),
                    execution.getType(),
                    execution.getSymbol(),
                    execution.getIntensity(),
                    execution.getStatus(),
                    execution.getStartedAt(),
                    execution.getScheduledEndsAt(),
                    execution.getActualEndedAt()
            );
        }
    }
}
