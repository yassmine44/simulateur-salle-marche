
package tn.esprit.simulateurbackend.service;
import tn.esprit.simulateurbackend.dto.ScenarioReportResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.simulateurbackend.entity.ScenarioExecutionStatus;
import java.util.List;
import tn.esprit.simulateurbackend.entity.ScenarioExecution;
import tn.esprit.simulateurbackend.entity.ScenarioType;
import tn.esprit.simulateurbackend.repository.ScenarioExecutionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import tn.esprit.simulateurbackend.dto.ScenarioHistoryResponse;
import java.math.BigDecimal;
import java.time.Instant;

@Service
public class ScenarioHistoryService {

    private final ScenarioExecutionRepository repository;

    public ScenarioHistoryService(
            ScenarioExecutionRepository repository
    ) {
        this.repository = repository;
    }

    // =========================================
    // ENREGISTRER LE DÉMARRAGE
    // =========================================

    @Transactional
    public Long recordStart(
            ScenarioType type,
            String symbol,
            BigDecimal intensity,
            Instant startedAt,
            Instant scheduledEndsAt
    ) {
        ScenarioExecution execution = new ScenarioExecution(
                type,
                symbol,
                intensity,
                startedAt,
                scheduledEndsAt
        );

        return repository.save(execution).getId();
    }

    // =========================================
    // ENREGISTRER UN ARRÊT MANUEL
    // =========================================

    @Transactional
    public void recordStop(
            Long executionId,
            Instant endedAt
    ) {
        ScenarioExecution execution = findExecution(executionId);

        execution.markStopped(endedAt);
    }

    // =========================================
    // ENREGISTRER UNE EXPIRATION
    // =========================================

    @Transactional
    public void recordExpiration(
            Long executionId,
            Instant endedAt
    ) {
        ScenarioExecution execution = findExecution(executionId);

        execution.markExpired(endedAt);
    }

    // =========================================
    // LECTURE D'UNE EXÉCUTION
    // =========================================

    @Transactional(readOnly = true)
    public ScenarioExecution findExecution(Long executionId) {
        return repository.findById(executionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Simulation introuvable : " + executionId
                ));
    }

    @Transactional
    public int reconcileAfterRestart(Instant restartedAt) {

        List<ScenarioExecution> runningExecutions =
                repository.findByStatus(
                        ScenarioExecutionStatus.RUNNING
                );

        for (ScenarioExecution execution : runningExecutions) {

            if (!execution.getScheduledEndsAt().isAfter(restartedAt)) {

                // Le scénario devait déjà être terminé.
                execution.markExpired(
                        execution.getScheduledEndsAt()
                );

            } else {

                // Le serveur a redémarré avant la fin prévue.
                execution.markInterrupted(restartedAt);
            }
        }

        return runningExecutions.size();
    }

    @Transactional(readOnly = true)
    public ScenarioHistoryResponse getHistory(int page, int size) {

        Page<ScenarioExecution> executions =
                repository.findAllByOrderByStartedAtDesc(
                        PageRequest.of(page, size)
                );

        return new ScenarioHistoryResponse(
                executions.getContent()
                        .stream()
                        .map(ScenarioHistoryResponse.Item::from)
                        .toList(),
                executions.getNumber(),
                executions.getSize(),
                executions.getTotalElements(),
                executions.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ScenarioReportResponse getReport(Long executionId) {

        ScenarioExecution execution = repository.findById(executionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Stress test introuvable : " + executionId
                ));

        Instant startedAt = execution.getStartedAt();
        Instant scheduledEndsAt = execution.getScheduledEndsAt();
        Instant actualEndedAt = execution.getActualEndedAt();

        long plannedSeconds = Math.max(
                0,
                Duration.between(startedAt, scheduledEndsAt).getSeconds()
        );

        Long actualSeconds = null;
        BigDecimal completionPercent = null;

        if (actualEndedAt != null) {

            actualSeconds = Math.max(
                    0,
                    Duration.between(startedAt, actualEndedAt)
                            .getSeconds()
            );

            if (plannedSeconds > 0) {
                completionPercent = BigDecimal
                        .valueOf(actualSeconds)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(
                                BigDecimal.valueOf(plannedSeconds),
                                2,
                                RoundingMode.HALF_UP
                        );
            }
        }

        return new ScenarioReportResponse(
                execution.getId(),
                execution.getType(),
                execution.getSymbol(),
                execution.getIntensity(),
                execution.getStatus(),
                startedAt,
                scheduledEndsAt,
                actualEndedAt,
                plannedSeconds,
                actualSeconds,
                completionPercent
        );
    }



}
