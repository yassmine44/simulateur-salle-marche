
package tn.esprit.simulateurbackend.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Profile("!banking-test")
public class ScenarioHistoryStartup {

    private final ScenarioHistoryService historyService;

    public ScenarioHistoryStartup(
            ScenarioHistoryService historyService
    ) {
        this.historyService = historyService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileHistory() {

        int count = historyService.reconcileAfterRestart(
                Instant.now()
        );

        System.out.println(
                "[Scenario History] " + count
                        + " execution(s) reconciled after restart."
        );
    }
}
