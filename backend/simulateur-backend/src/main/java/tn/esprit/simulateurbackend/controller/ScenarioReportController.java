
package tn.esprit.simulateurbackend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.simulateurbackend.dto.ScenarioReportResponse;
import tn.esprit.simulateurbackend.service.ScenarioHistoryService;

@RestController
@RequestMapping("/api/admin/scenarios/history")
public class ScenarioReportController {

    private final ScenarioHistoryService historyService;

    public ScenarioReportController(
            ScenarioHistoryService historyService
    ) {
        this.historyService = historyService;
    }

    @GetMapping("/{id}/report")
    public ScenarioReportResponse getReport(
            @PathVariable Long id
    ) {
        return historyService.getReport(id);
    }
}
