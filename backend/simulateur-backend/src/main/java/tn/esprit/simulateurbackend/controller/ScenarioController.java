package tn.esprit.simulateurbackend.controller;

import tn.esprit.simulateurbackend.dto.StartScenarioRequest;
import tn.esprit.simulateurbackend.dto.ScenarioStatusResponse;
import tn.esprit.simulateurbackend.service.ScenarioService;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import tn.esprit.simulateurbackend.dto.ScenarioRecoveryStatusResponse;
@RestController
@RequestMapping("/api/admin/scenarios")
public class ScenarioController {

    private final ScenarioService scenarioService;

    public ScenarioController(ScenarioService scenarioService) {
        this.scenarioService = scenarioService;
    }

    @GetMapping("/status")
    public ResponseEntity<ScenarioStatusResponse> status() {
        return ResponseEntity.ok(
                scenarioService.status()
        );
    }

    @PostMapping("/start")
    public ResponseEntity<ScenarioStatusResponse> start(
            @Valid @RequestBody StartScenarioRequest request
    ) {
        return ResponseEntity.ok(
                scenarioService.start(request)
        );
    }

    @PostMapping("/stop")
    public ResponseEntity<ScenarioStatusResponse> stop() {
        return ResponseEntity.ok(
                scenarioService.stop()
        );
    }

    @GetMapping("/recovery/{symbol}")
    public ScenarioRecoveryStatusResponse recoveryStatus(
            @PathVariable String symbol
    ) {

        return scenarioService.recoveryStatus(symbol);
    }

}