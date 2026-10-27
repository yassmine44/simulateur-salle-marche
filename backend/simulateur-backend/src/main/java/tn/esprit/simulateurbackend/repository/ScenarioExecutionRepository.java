
package tn.esprit.simulateurbackend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.simulateurbackend.entity.ScenarioExecution;
import tn.esprit.simulateurbackend.entity.ScenarioExecutionStatus;

import java.util.List;

public interface ScenarioExecutionRepository
        extends JpaRepository<ScenarioExecution, Long> {

    Page<ScenarioExecution> findAllByOrderByStartedAtDesc(
            Pageable pageable
    );

    List<ScenarioExecution> findByStatus(
            ScenarioExecutionStatus status
    );
}
