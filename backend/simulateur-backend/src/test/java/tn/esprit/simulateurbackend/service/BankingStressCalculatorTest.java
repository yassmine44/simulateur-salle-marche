package tn.esprit.simulateurbackend.service;

import org.junit.jupiter.api.Test;

import tn.esprit.simulateurbackend.banking.BalanceSheetSide;
import tn.esprit.simulateurbackend.banking.BankingModel;
import tn.esprit.simulateurbackend.banking.BankingStressScenarioType;
import tn.esprit.simulateurbackend.banking.FinancialContractType;
import tn.esprit.simulateurbackend.banking.ReturnBasis;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.BankInput;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.CreditExposure;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.ExposureTerms;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.MarketExposure;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.RateExposure;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.ScenarioInput;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BankingStressCalculatorTest {

    private final BankingStressCalculator calculator = new BankingStressCalculator();

    @Test
    void creditDefaultCalculatesIncrementalExpectedLossAndAllocatesIt() {
        BankInput bank = bank(
                new BigDecimal("1000"),
                new BigDecimal("5000"),
                List.of(new CreditExposure(
                        "credit-1",
                        terms(new BigDecimal("0.75"), new BigDecimal("0.25")),
                        new BigDecimal("1000"),
                        new BigDecimal("0.02"),
                        new BigDecimal("0.40")
                )),
                List.of(),
                List.of()
        );
        ScenarioInput scenario = scenario(
                BankingStressScenarioType.CREDIT_DEFAULT,
                null,
                null,
                null,
                new BigDecimal("0.03"),
                null
        );

        var result = calculator.calculate(bank, scenario);

        assertEquals(new BigDecimal("8.00"), result.creditExpectedLossBefore());
        assertEquals(new BigDecimal("20.00"), result.creditExpectedLossAfter());
        assertEquals(new BigDecimal("12.00"), result.creditLoss());
        assertEquals(new BigDecimal("9.00"), result.creditLossBank());
        assertEquals(new BigDecimal("3.00"), result.creditLossInvestors());
        assertEquals(new BigDecimal("991.00"), result.capitalProxyAfter());
        assertEquals(new BigDecimal("19.8200"), result.capitalToRwaPercentAfter());
    }

    @Test
    void recessionAppliesProbabilityMultiplierAndLossGivenDefaultAddOn() {
        BankInput bank = bank(
                new BigDecimal("1000"),
                new BigDecimal("5000"),
                List.of(new CreditExposure(
                        "credit-1",
                        terms(BigDecimal.ONE, BigDecimal.ZERO),
                        new BigDecimal("1000"),
                        new BigDecimal("0.02"),
                        new BigDecimal("0.40")
                )),
                List.of(),
                List.of()
        );
        ScenarioInput scenario = scenario(
                BankingStressScenarioType.RECESSION,
                null,
                new BigDecimal("2"),
                new BigDecimal("0.10"),
                null,
                null
        );

        var result = calculator.calculate(bank, scenario);

        assertEquals(new BigDecimal("8.00"), result.creditExpectedLossBefore());
        assertEquals(new BigDecimal("20.00"), result.creditExpectedLossAfter());
        assertEquals(new BigDecimal("12.00"), result.creditLoss());
        assertEquals(new BigDecimal("0.00"), result.marketLoss());
        assertEquals(new BigDecimal("988.00"), result.capitalProxyAfter());
    }

    @Test
    void rateShockAppliesDurationAndConvexityAndDoesNotMixWithCreditLoss() {
        BankInput bank = bank(
                new BigDecimal("1000"),
                new BigDecimal("5000"),
                List.of(),
                List.of(new RateExposure(
                        "rate-asset",
                        terms(new BigDecimal("0.60"), new BigDecimal("0.40")),
                        BalanceSheetSide.ASSET,
                        new BigDecimal("100"),
                        new BigDecimal("2"),
                        new BigDecimal("3")
                )),
                List.of()
        );
        ScenarioInput scenario = scenario(
                BankingStressScenarioType.RATE_SHOCK,
                new BigDecimal("0.01"),
                null,
                null,
                null,
                null
        );

        var result = calculator.calculate(bank, scenario);

        assertEquals(new BigDecimal("-1.99"), result.economicValueChange());
        assertEquals(new BigDecimal("1.99"), result.rateRiskLoss());
        assertEquals(new BigDecimal("0.00"), result.creditLoss());
        assertEquals(new BigDecimal("1.19"), result.rateRiskLossBank());
        assertEquals(new BigDecimal("0.80"), result.rateRiskLossInvestors());
        assertEquals(new BigDecimal("998.81"), result.capitalProxyAfter());
    }

    @Test
    void liabilityValueChangeHasOppositeEffectOnEconomicValue() {
        BankInput bank = bank(
                new BigDecimal("1000"),
                new BigDecimal("0"),
                List.of(),
                List.of(new RateExposure(
                        "rate-liability",
                        terms(BigDecimal.ONE, BigDecimal.ZERO),
                        BalanceSheetSide.LIABILITY,
                        new BigDecimal("100"),
                        new BigDecimal("2"),
                        BigDecimal.ZERO
                )),
                List.of()
        );
        ScenarioInput scenario = scenario(
                BankingStressScenarioType.RATE_SHOCK,
                new BigDecimal("0.01"),
                null,
                null,
                null,
                null
        );

        var result = calculator.calculate(bank, scenario);

        assertEquals(new BigDecimal("2.00"), result.economicValueChange());
        assertEquals(new BigDecimal("-2.00"), result.rateRiskLoss());
        assertEquals(new BigDecimal("1002.00"), result.capitalProxyAfter());
        assertNull(result.capitalToRwaPercentAfter());
    }

    @Test
    void equityShockCalculatesMarketLossAndSharesItExplicitly() {
        BankInput bank = bank(
                new BigDecimal("1000"),
                new BigDecimal("5000"),
                List.of(new CreditExposure(
                        "credit-reference",
                        terms(new BigDecimal("0.40"), new BigDecimal("0.60")),
                        new BigDecimal("1000"),
                        new BigDecimal("0.02"),
                        new BigDecimal("0.40")
                )),
                List.of(),
                List.of(new MarketExposure(
                        "equity-1",
                        terms(new BigDecimal("0.40"), new BigDecimal("0.60")),
                        new BigDecimal("200")
                ))
        );
        ScenarioInput scenario = scenario(
                BankingStressScenarioType.EQUITY_SHOCK,
                null,
                null,
                null,
                null,
                new BigDecimal("-0.25")
        );

        var result = calculator.calculate(bank, scenario);

        assertEquals(new BigDecimal("-50.00"), result.marketProfitAndLoss());
        assertEquals(new BigDecimal("50.00"), result.marketLoss());
        assertEquals(new BigDecimal("20.00"), result.marketLossBank());
        assertEquals(new BigDecimal("30.00"), result.marketLossInvestors());
        assertEquals(new BigDecimal("8.00"), result.creditExpectedLossBefore());
        assertEquals(new BigDecimal("8.00"), result.creditExpectedLossAfter());
        assertEquals(new BigDecimal("0.00"), result.creditLoss());
        assertEquals(new BigDecimal("980.00"), result.capitalProxyAfter());
    }

    @Test
    void combinedScenarioAppliesCreditRateAndEquityShocksTogether() {
        BankInput bank = bank(
                new BigDecimal("1000"),
                new BigDecimal("5000"),
                List.of(new CreditExposure(
                        "credit-1",
                        terms(new BigDecimal("0.60"), new BigDecimal("0.40")),
                        new BigDecimal("1000"),
                        new BigDecimal("0.10"),
                        new BigDecimal("0.50")
                )),
                List.of(new RateExposure(
                        "rate-1",
                        terms(new BigDecimal("0.60"), new BigDecimal("0.40")),
                        BalanceSheetSide.ASSET,
                        new BigDecimal("100"),
                        BigDecimal.ONE,
                        BigDecimal.ZERO
                )),
                List.of(new MarketExposure(
                        "equity-1",
                        terms(new BigDecimal("0.60"), new BigDecimal("0.40")),
                        new BigDecimal("200")
                ))
        );
        ScenarioInput scenario = scenario(
                BankingStressScenarioType.COMBINED,
                new BigDecimal("0.01"),
                new BigDecimal("2"),
                new BigDecimal("0.10"),
                new BigDecimal("0.10"),
                new BigDecimal("-0.20")
        );

        var result = calculator.calculate(bank, scenario);

        assertEquals(new BigDecimal("50.00"), result.creditExpectedLossBefore());
        assertEquals(new BigDecimal("180.00"), result.creditExpectedLossAfter());
        assertEquals(new BigDecimal("130.00"), result.creditLoss());
        assertEquals(new BigDecimal("1.00"), result.rateRiskLoss());
        assertEquals(new BigDecimal("40.00"), result.marketLoss());
        assertEquals(new BigDecimal("102.60"), result.bankBorneLoss());
        assertEquals(new BigDecimal("68.40"), result.investorBorneLoss());
    }

    @Test
    void roundedAllocationsReconcileExactlyToRoundedTotals() {
        BankInput bank = bank(
                new BigDecimal("100"),
                new BigDecimal("200"),
                List.of(new CreditExposure(
                        "credit-rounding",
                        terms(new BigDecimal("0.50"), new BigDecimal("0.50")),
                        BigDecimal.ONE,
                        new BigDecimal("0.01"),
                        BigDecimal.ONE
                )),
                List.of(new RateExposure(
                        "rate-rounding",
                        terms(new BigDecimal("0.50"), new BigDecimal("0.50")),
                        BalanceSheetSide.ASSET,
                        BigDecimal.ONE,
                        new BigDecimal("0.015"),
                        BigDecimal.ZERO
                )),
                List.of(new MarketExposure(
                        "market-rounding",
                        terms(new BigDecimal("0.50"), new BigDecimal("0.50")),
                        BigDecimal.ONE
                ))
        );
        ScenarioInput scenario = scenario(
                BankingStressScenarioType.COMBINED,
                BigDecimal.ONE,
                null,
                null,
                new BigDecimal("0.01"),
                new BigDecimal("-0.01")
        );

        var result = calculator.calculate(bank, scenario);

        assertEquals(result.creditLoss(), result.creditLossBank()
                .add(result.creditLossInvestors()));
        assertEquals(result.economicValueChange(), result.bankEconomicValueChange()
                .add(result.investorEconomicValueChange()));
        assertEquals(result.rateRiskLoss(), result.rateRiskLossBank()
                .add(result.rateRiskLossInvestors()));
        assertEquals(result.marketLoss(), result.marketLossBank()
                .add(result.marketLossInvestors()));
        assertEquals(result.bankBorneLoss().add(result.investorBorneLoss()),
                result.creditLoss().add(result.rateRiskLoss()).add(result.marketLoss()));
    }

    @Test
    void conventionalModelRetainsItsExplicitContractAllocation() {
        BankInput bank = new BankInput(
                "conventional-1",
                "Banque conventionnelle",
                BankingModel.CONVENTIONAL,
                new BigDecimal("1000"),
                new BigDecimal("5000"),
                List.of(new CreditExposure(
                        "credit-1",
                        new ExposureTerms(
                                FinancialContractType.LOAN,
                                ReturnBasis.FIXED_RATE,
                                new BigDecimal("0.25"),
                                new BigDecimal("0.75")
                        ),
                        new BigDecimal("1000"),
                        new BigDecimal("0.02"),
                        new BigDecimal("0.40")
                )),
                List.of(),
                List.of()
        );

        var result = calculator.calculate(
                bank,
                scenario(BankingStressScenarioType.CREDIT_DEFAULT, null, null,
                        null, new BigDecimal("0.03"), null)
        );

        assertEquals(BankingModel.CONVENTIONAL, result.model());
        assertEquals(new BigDecimal("3.00"), result.creditLossBank());
        assertEquals(new BigDecimal("9.00"), result.creditLossInvestors());
    }

    private static BankInput bank(
            BigDecimal capital,
            BigDecimal rwa,
            List<CreditExposure> credit,
            List<RateExposure> rates,
            List<MarketExposure> market
    ) {
        return new BankInput(
                "bank-1",
                "Banque de test",
                BankingModel.NON_CONVENTIONAL,
                capital,
                rwa,
                credit,
                rates,
                market
        );
    }

    private static ExposureTerms terms(
            BigDecimal bankShare,
            BigDecimal investorShare
    ) {
        return new ExposureTerms(
                FinancialContractType.MUSHARAKA,
                ReturnBasis.PROFIT_SHARING,
                bankShare,
                investorShare
        );
    }

    private static ScenarioInput scenario(
            BankingStressScenarioType type,
            BigDecimal rateShock,
            BigDecimal recessionPdMultiplier,
            BigDecimal recessionLgdAddOn,
            BigDecimal defaultPdAddOn,
            BigDecimal equityShock
    ) {
        return new ScenarioInput(
                "scenario-1",
                type,
                rateShock,
                recessionPdMultiplier,
                recessionLgdAddOn,
                defaultPdAddOn,
                equityShock
        );
    }
}
