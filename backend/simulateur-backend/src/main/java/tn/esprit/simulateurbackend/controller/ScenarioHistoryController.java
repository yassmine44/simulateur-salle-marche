
package tn.esprit.simulateurbackend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.ScenarioHistoryResponse;
import tn.esprit.simulateurbackend.service.ScenarioHistoryService;
import tn.esprit.simulateurbackend.service.ScenarioMarketHistoryService;

@RestController
@RequestMapping("/api/admin/scenarios")
public class ScenarioHistoryController {

    private final ScenarioHistoryService historyService;
    private final ScenarioMarketHistoryService marketHistoryService;

    public ScenarioHistoryController(
            ScenarioHistoryService historyService,
            ScenarioMarketHistoryService marketHistoryService
    ) {
        this.historyService = historyService;
        this.marketHistoryService = marketHistoryService;
    }

    @GetMapping("/history")
    public ScenarioHistoryResponse history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page doit être >= 0 et size doit être entre 1 et 100."
            );
        }

        return historyService.getHistory(page, size);
    }

    @GetMapping("/history/{executionId}/market")
    public ScenarioMarketHistoryService.MarketHistory marketHistory(
            @PathVariable Long executionId
    ) {
        return marketHistoryService.getMarketHistory(executionId);
    }
}
