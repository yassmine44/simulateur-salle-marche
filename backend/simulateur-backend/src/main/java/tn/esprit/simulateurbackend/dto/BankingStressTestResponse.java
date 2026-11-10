package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.banking.BankingModel;
import tn.esprit.simulateurbackend.banking.BankingStressScenarioType;

import java.math.BigDecimal;
import java.util.List;

public record BankingStressTestResponse(
        String currency,
        int creditHorizonDays,
        boolean simulatedResults,
        String regulatoryStatus,
        String probabilityOfDefaultHorizon,
        List<String> assumptionsAndLimitations,
        List<BankScenarioResult> results
) {

    public record BankScenarioResult(
            String bankId,
            String bankName,
            BankingModel model,
            String scenarioId,
            BankingStressScenarioType scenarioType,

            BigDecimal creditExpectedLossBefore,
            BigDecimal creditExpectedLossAfter,
            BigDecimal creditLoss,
            BigDecimal creditLossBank,
            BigDecimal creditLossInvestors,

            BigDecimal economicValueChange,
            BigDecimal bankEconomicValueChange,
            BigDecimal investorEconomicValueChange,
            BigDecimal rateRiskLoss,
            BigDecimal rateRiskLossBank,
            BigDecimal rateRiskLossInvestors,

            BigDecimal marketProfitAndLoss,
            BigDecimal marketLoss,
            BigDecimal marketLossBank,
            BigDecimal marketLossInvestors,

            BigDecimal bankBorneLoss,
            BigDecimal investorBorneLoss,
            BigDecimal capitalProxyBefore,
            BigDecimal capitalProxyAfter,
            BigDecimal riskWeightedAssets,
            BigDecimal capitalToRwaPercentBefore,
            BigDecimal capitalToRwaPercentAfter
    ) {
    }
}
