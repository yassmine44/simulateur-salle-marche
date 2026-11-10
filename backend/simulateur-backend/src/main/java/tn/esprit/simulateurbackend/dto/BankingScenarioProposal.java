package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;
import java.util.List;

import tn.esprit.simulateurbackend.banking.BankingScenarioSeverity;
import tn.esprit.simulateurbackend.banking.EconomicStressFactorType;

public record BankingScenarioProposal(
        String scenarioName,
        BankingScenarioSeverity severity,
        int horizonDays,
        List<EconomicFactor> factors,
        EngineParameters engineParameters,
        List<String> justifications,
        List<String> assumptions
) {
    public record EconomicFactor(
            EconomicStressFactorType type,
            BigDecimal value,
            String unit,
            String rationale
    ) {
    }

    public record EngineParameters(
            BigDecimal rateShock,
            BigDecimal recessionProbabilityMultiplier,
            BigDecimal recessionLossGivenDefaultAddOn,
            BigDecimal equityReturnShock
    ) {
    }
}
