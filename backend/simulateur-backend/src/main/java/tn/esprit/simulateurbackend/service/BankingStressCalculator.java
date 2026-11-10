package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Component;

import tn.esprit.simulateurbackend.banking.BalanceSheetSide;
import tn.esprit.simulateurbackend.banking.BankingStressScenarioType;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.BankInput;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.CreditExposure;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.MarketExposure;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.RateExposure;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.ScenarioInput;
import tn.esprit.simulateurbackend.dto.BankingStressTestResponse.BankScenarioResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Component
public class BankingStressCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final int MONEY_SCALE = 2;
    private static final int PERCENT_SCALE = 4;

    public BankScenarioResult calculate(
            BankInput bank,
            ScenarioInput scenario
    ) {
        Objects.requireNonNull(bank, "bank");
        Objects.requireNonNull(scenario, "scenario");

        BigDecimal creditBefore = ZERO;
        BigDecimal creditAfter = ZERO;
        BigDecimal bankCreditChange = ZERO;
        BigDecimal eveChange = ZERO;
        BigDecimal bankEveChange = ZERO;
        BigDecimal marketPnl = ZERO;
        BigDecimal bankMarketLoss = ZERO;

        boolean stressesCredit = scenario.type() == BankingStressScenarioType.RECESSION
                || scenario.type() == BankingStressScenarioType.CREDIT_DEFAULT
                || scenario.type() == BankingStressScenarioType.COMBINED;
        boolean stressesRates = scenario.type() == BankingStressScenarioType.RATE_SHOCK
                || scenario.type() == BankingStressScenarioType.COMBINED;
        boolean stressesEquity = scenario.type() == BankingStressScenarioType.EQUITY_SHOCK
                || scenario.type() == BankingStressScenarioType.COMBINED;

        for (CreditExposure exposure : bank.creditExposures()) {
            BigDecimal basePd = exposure.probabilityOfDefault();
            BigDecimal baseEcl = exposure.exposureAtDefault()
                    .multiply(basePd)
                    .multiply(exposure.lossGivenDefault());
            creditBefore = creditBefore.add(baseEcl);

            BigDecimal stressedEcl = baseEcl;
            if (stressesCredit) {
                BigDecimal stressedPd = basePd;
                BigDecimal stressedLgd = exposure.lossGivenDefault();

                if (includesRecession(scenario)) {
                    stressedPd = stressedPd.multiply(
                            scenario.recessionProbabilityMultiplier()
                    );
                    stressedLgd = stressedLgd.add(
                            scenario.recessionLossGivenDefaultAddOn()
                    );
                }
                if (includesDefault(scenario)) {
                    stressedPd = stressedPd.add(scenario.defaultProbabilityAddOn());
                }

                stressedEcl = exposure.exposureAtDefault()
                        .multiply(stressedPd.min(ONE))
                        .multiply(stressedLgd.min(ONE));
                bankCreditChange = bankCreditChange.add(
                        stressedEcl.subtract(baseEcl)
                                .multiply(exposure.terms().bankLossShare())
                );
            }
            creditAfter = creditAfter.add(stressedEcl);
        }

        if (stressesRates) {
            BigDecimal shock = scenario.rateShock();
            BigDecimal shockSquared = shock.multiply(shock);

            for (RateExposure exposure : bank.rateExposures()) {
                BigDecimal deltaPv = exposure.presentValue()
                        .multiply(exposure.modifiedDurationYears())
                        .multiply(shock)
                        .negate()
                        .add(
                                HALF.multiply(exposure.presentValue())
                                        .multiply(exposure.convexityYearsSquared())
                                        .multiply(shockSquared)
                        );

                BigDecimal bankDeltaPv = deltaPv
                        .multiply(exposure.terms().bankLossShare());
                if (exposure.side() == BalanceSheetSide.LIABILITY) {
                    deltaPv = deltaPv.negate();
                    bankDeltaPv = bankDeltaPv.negate();
                }
                eveChange = eveChange.add(deltaPv);
                bankEveChange = bankEveChange.add(bankDeltaPv);
            }
        }

        if (stressesEquity) {
            for (MarketExposure exposure : bank.marketExposures()) {
                BigDecimal pnl = exposure.marketValue()
                        .multiply(scenario.equityReturnShock());
                marketPnl = marketPnl.add(pnl);
                bankMarketLoss = bankMarketLoss.add(
                        pnl.negate().multiply(exposure.terms().bankLossShare())
                );
            }
        }

        BigDecimal creditBeforeMoney = money(creditBefore);
        BigDecimal creditAfterMoney = money(creditAfter);
        BigDecimal creditLoss = creditAfterMoney.subtract(creditBeforeMoney);
        BigDecimal bankCreditLoss = money(bankCreditChange);
        BigDecimal investorCreditLoss = creditLoss.subtract(bankCreditLoss);

        BigDecimal eveChangeMoney = money(eveChange);
        BigDecimal bankEveChangeMoney = money(bankEveChange);
        BigDecimal investorEveChange = eveChangeMoney.subtract(bankEveChangeMoney);
        BigDecimal rateRiskLoss = eveChangeMoney.negate();
        BigDecimal bankRateLoss = bankEveChangeMoney.negate();
        BigDecimal investorRateLoss = rateRiskLoss.subtract(bankRateLoss);

        BigDecimal marketPnlMoney = money(marketPnl);
        BigDecimal marketLoss = marketPnlMoney.negate();
        BigDecimal bankMarketLossMoney = money(bankMarketLoss);
        BigDecimal investorMarketLoss = marketLoss.subtract(bankMarketLossMoney);

        BigDecimal bankBorneLoss = bankCreditLoss
                .add(bankRateLoss)
                .add(bankMarketLossMoney);
        BigDecimal investorBorneLoss = investorCreditLoss
                .add(investorRateLoss)
                .add(investorMarketLoss);

        BigDecimal capitalBefore = money(bank.capitalProxy());
        BigDecimal capitalAfter = money(capitalBefore.subtract(bankBorneLoss));
        BigDecimal rwa = money(bank.riskWeightedAssets());

        return new BankScenarioResult(
                bank.bankId(),
                bank.bankName(),
                bank.model(),
                scenario.scenarioId(),
                scenario.type(),
                creditBeforeMoney,
                creditAfterMoney,
                creditLoss,
                bankCreditLoss,
                investorCreditLoss,
                eveChangeMoney,
                bankEveChangeMoney,
                investorEveChange,
                rateRiskLoss,
                bankRateLoss,
                investorRateLoss,
                marketPnlMoney,
                marketLoss,
                bankMarketLossMoney,
                investorMarketLoss,
                bankBorneLoss,
                investorBorneLoss,
                capitalBefore,
                capitalAfter,
                rwa,
                capitalToRwaPercent(capitalBefore, rwa),
                capitalToRwaPercent(capitalAfter, rwa)
        );
    }

    private static boolean includesRecession(ScenarioInput scenario) {
        return scenario.type() == BankingStressScenarioType.RECESSION
                || scenario.type() == BankingStressScenarioType.COMBINED
                        && scenario.recessionProbabilityMultiplier() != null;
    }

    private static boolean includesDefault(ScenarioInput scenario) {
        return scenario.type() == BankingStressScenarioType.CREDIT_DEFAULT
                || scenario.type() == BankingStressScenarioType.COMBINED
                        && scenario.defaultProbabilityAddOn() != null;
    }

    private static BigDecimal capitalToRwaPercent(
            BigDecimal capital,
            BigDecimal riskWeightedAssets
    ) {
        if (riskWeightedAssets.signum() == 0) {
            return null;
        }

        return capital.multiply(HUNDRED)
                .divide(riskWeightedAssets, PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
