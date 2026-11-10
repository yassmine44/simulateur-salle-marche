package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import tn.esprit.simulateurbackend.banking.BankingScenarioSeverity;

public record BankingScenarioProposalRequest(

        @NotNull
        BankingScenarioSeverity severity,

        @NotNull
        @Min(1)
        @Max(3650)
        Integer horizonDays,

        @Size(max = 500)
        String context
) {
}
