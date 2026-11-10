package tn.esprit.simulateurbackend.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.simulateurbackend.dto.BankingStressTestRequest;
import tn.esprit.simulateurbackend.dto.BankingStressTestResponse;
import tn.esprit.simulateurbackend.service.BankingStressTestService;

@RestController
@RequestMapping("/api/banking-stress-tests")
public class BankingStressTestController {

    private final BankingStressTestService bankingStressTestService;

    public BankingStressTestController(
            BankingStressTestService bankingStressTestService
    ) {
        this.bankingStressTestService = bankingStressTestService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<BankingStressTestResponse> analyze(
            @Valid @RequestBody BankingStressTestRequest request
    ) {
        return ResponseEntity.ok(bankingStressTestService.analyze(request));
    }
}
