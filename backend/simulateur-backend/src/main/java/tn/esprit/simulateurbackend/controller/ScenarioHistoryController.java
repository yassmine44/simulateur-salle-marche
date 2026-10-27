
package tn.esprit.simulateurbackend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.ScenarioHistoryResponse;
import tn.esprit.simulateurbackend.service.ScenarioHistoryService;

@RestController
@RequestMapping("/api/admin/scenarios")
public class ScenarioHistoryController {

    private final ScenarioHistoryService historyService;

    public ScenarioHistoryController(
            ScenarioHistoryService historyService
    ) {
        this.historyService = historyService;
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
}
