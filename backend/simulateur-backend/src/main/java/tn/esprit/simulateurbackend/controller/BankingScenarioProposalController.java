package tn.esprit.simulateurbackend.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.simulateurbackend.dto.BankingScenarioProposal;
import tn.esprit.simulateurbackend.dto.BankingScenarioProposalRequest;
import tn.esprit.simulateurbackend.service.BankingScenarioProposalService;

@RestController
@RequestMapping("/api/banking-stress-tests")
public class BankingScenarioProposalController {

    private final BankingScenarioProposalService proposalService;

    public BankingScenarioProposalController(
            BankingScenarioProposalService proposalService
    ) {
        this.proposalService = proposalService;
    }

    @PostMapping("/scenario-proposals")
    public ResponseEntity<BankingScenarioProposal> propose(
            @Valid @RequestBody BankingScenarioProposalRequest request
    ) {
        return ResponseEntity.ok(proposalService.propose(request));
    }
}
