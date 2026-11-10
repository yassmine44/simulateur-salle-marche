package tn.esprit.simulateurbackend.service;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.banking.BankingModel;
import tn.esprit.simulateurbackend.banking.BankingStressScenarioType;
import tn.esprit.simulateurbackend.banking.BalanceSheetSide;
import tn.esprit.simulateurbackend.banking.FinancialContractType;
import tn.esprit.simulateurbackend.banking.ReturnBasis;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.BankInput;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.CreditExposure;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.ExposureTerms;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.MarketExposure;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.RateExposure;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.ScenarioInput;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankingStressTestServiceTest {

    private final BankingStressTestService service =
            new BankingStressTestService(new BankingStressCalculator());

    @Test
    void combinedScenarioReturnsSimulationAndHorizonAssumptions() {
        var response = service.analyze(request(
                List.of(bank("bank-1", BankingModel.NON_CONVENTIONAL, List.of())),
                List.of(new ScenarioInput(
                        "combined",
                        BankingStressScenarioType.COMBINED,
                        new BigDecimal("0.01"),
                        null,
                        null,
                        null,
                        null
                ))
        ));

        assertTrue(response.simulatedResults());
        assertTrue(response.regulatoryStatus().contains("pas des ratios réglementaires"));
        assertTrue(response.probabilityOfDefaultHorizon().contains("creditHorizonDays"));
        assertTrue(response.assumptionsAndLimitations().stream()
                .anyMatch(item -> item.contains("une seule fois")));
        assertTrue(response.assumptionsAndLimitations().stream()
                .anyMatch(item -> item.contains("aucune conversion depuis ou vers KDT")));
    }

    @Test
    void combinedScenarioComparesFictionalConventionalAndNonConventionalBanks() {
        BankInput biat = new BankInput(
                "biat-demo",
                "BIAT — hypothèses fictives",
                BankingModel.CONVENTIONAL,
                new BigDecimal("1000"),
                new BigDecimal("5000"),
                List.of(new CreditExposure(
                        "biat-credit",
                        terms(FinancialContractType.LOAN, ReturnBasis.FIXED_RATE,
                                "0.75", "0.25"),
                        new BigDecimal("1000"),
                        new BigDecimal("0.02"),
                        new BigDecimal("0.40")
                )),
                List.of(new RateExposure(
                        "biat-rate",
                        terms(FinancialContractType.BOND, ReturnBasis.FIXED_RATE,
                                "0.60", "0.40"),
                        BalanceSheetSide.ASSET,
                        new BigDecimal("100"),
                        new BigDecimal("2"),
                        new BigDecimal("3")
                )),
                List.of(new MarketExposure(
                        "biat-market",
                        terms(FinancialContractType.EQUITY, ReturnBasis.VARIABLE,
                                "0.40", "0.60"),
                        new BigDecimal("200")
                ))
        );
        BankInput zitouna = new BankInput(
                "zitouna-demo",
                "Banque Zitouna — hypothèses fictives",
                BankingModel.NON_CONVENTIONAL,
                new BigDecimal("800"),
                new BigDecimal("4000"),
                List.of(new CreditExposure(
                        "zitouna-credit",
                        terms(FinancialContractType.MURABAHA, ReturnBasis.FIXED_RATE,
                                "0.30", "0.70"),
                        new BigDecimal("500"),
                        new BigDecimal("0.04"),
                        new BigDecimal("0.50")
                )),
                List.of(new RateExposure(
                        "zitouna-rate",
                        terms(FinancialContractType.SUKUK, ReturnBasis.BENCHMARK_LINKED,
                                "0.25", "0.75"),
                        BalanceSheetSide.LIABILITY,
                        new BigDecimal("80"),
                        new BigDecimal("1.5"),
                        new BigDecimal("2")
                )),
                List.of(new MarketExposure(
                        "zitouna-market",
                        terms(FinancialContractType.EQUITY, ReturnBasis.VARIABLE,
                                "0.20", "0.80"),
                        new BigDecimal("100")
                ))
        );
        ScenarioInput combined = new ScenarioInput(
                "combined-demo",
                BankingStressScenarioType.COMBINED,
                new BigDecimal("0.01"),
                new BigDecimal("2"),
                new BigDecimal("0.10"),
                new BigDecimal("0.03"),
                new BigDecimal("-0.25")
        );

        var response = service.analyze(request(List.of(biat, zitouna), List.of(combined)));

        assertEquals(2, response.results().size());
        var biatResult = response.results().get(0);
        assertEquals("BIAT — hypothèses fictives", biatResult.bankName());
        assertEquals(new BigDecimal("8.00"), biatResult.creditExpectedLossBefore());
        assertEquals(new BigDecimal("35.00"), biatResult.creditExpectedLossAfter());
        assertEquals(new BigDecimal("27.00"), biatResult.creditLoss());
        assertEquals(new BigDecimal("20.25"), biatResult.creditLossBank());
        assertEquals(new BigDecimal("6.75"), biatResult.creditLossInvestors());
        assertEquals(new BigDecimal("-1.99"), biatResult.economicValueChange());
        assertEquals(new BigDecimal("-1.19"), biatResult.bankEconomicValueChange());
        assertEquals(new BigDecimal("-0.80"), biatResult.investorEconomicValueChange());
        assertEquals(new BigDecimal("1.99"), biatResult.rateRiskLoss());
        assertEquals(new BigDecimal("1.19"), biatResult.rateRiskLossBank());
        assertEquals(new BigDecimal("0.80"), biatResult.rateRiskLossInvestors());
        assertEquals(new BigDecimal("-50.00"), biatResult.marketProfitAndLoss());
        assertEquals(new BigDecimal("50.00"), biatResult.marketLoss());
        assertEquals(new BigDecimal("20.00"), biatResult.marketLossBank());
        assertEquals(new BigDecimal("30.00"), biatResult.marketLossInvestors());
        assertEquals(new BigDecimal("41.44"), biatResult.bankBorneLoss());
        assertEquals(new BigDecimal("37.55"), biatResult.investorBorneLoss());
        assertEquals(new BigDecimal("958.56"), biatResult.capitalProxyAfter());
        assertEquals(new BigDecimal("20.0000"), biatResult.capitalToRwaPercentBefore());
        assertEquals(new BigDecimal("19.1712"), biatResult.capitalToRwaPercentAfter());

        var zitounaResult = response.results().get(1);
        assertEquals("Banque Zitouna — hypothèses fictives", zitounaResult.bankName());
        assertEquals(new BigDecimal("10.00"), zitounaResult.creditExpectedLossBefore());
        assertEquals(new BigDecimal("33.00"), zitounaResult.creditExpectedLossAfter());
        assertEquals(new BigDecimal("23.00"), zitounaResult.creditLoss());
        assertEquals(new BigDecimal("6.90"), zitounaResult.creditLossBank());
        assertEquals(new BigDecimal("16.10"), zitounaResult.creditLossInvestors());
        assertEquals(new BigDecimal("1.19"), zitounaResult.economicValueChange());
        assertEquals(new BigDecimal("0.30"), zitounaResult.bankEconomicValueChange());
        assertEquals(new BigDecimal("0.89"), zitounaResult.investorEconomicValueChange());
        assertEquals(new BigDecimal("-1.19"), zitounaResult.rateRiskLoss());
        assertEquals(new BigDecimal("-0.30"), zitounaResult.rateRiskLossBank());
        assertEquals(new BigDecimal("-0.89"), zitounaResult.rateRiskLossInvestors());
        assertEquals(new BigDecimal("-25.00"), zitounaResult.marketProfitAndLoss());
        assertEquals(new BigDecimal("25.00"), zitounaResult.marketLoss());
        assertEquals(new BigDecimal("5.00"), zitounaResult.marketLossBank());
        assertEquals(new BigDecimal("20.00"), zitounaResult.marketLossInvestors());
        assertEquals(new BigDecimal("11.60"), zitounaResult.bankBorneLoss());
        assertEquals(new BigDecimal("35.21"), zitounaResult.investorBorneLoss());
        assertEquals(new BigDecimal("788.40"), zitounaResult.capitalProxyAfter());
        assertEquals(new BigDecimal("20.0000"), zitounaResult.capitalToRwaPercentBefore());
        assertEquals(new BigDecimal("19.7100"), zitounaResult.capitalToRwaPercentAfter());
    }

    @Test
    void combinedScenarioRequiresAtLeastOneShock() {
        ScenarioInput scenario = new ScenarioInput(
                "combined",
                BankingStressScenarioType.COMBINED,
                null,
                null,
                null,
                null,
                null
        );

        assertThrows(ResponseStatusException.class, () -> service.analyze(
                request(List.of(bank("bank-1", BankingModel.CONVENTIONAL, List.of())),
                        List.of(scenario))
        ));
    }

    @Test
    void contractAndReturnBasisMustBeConsistentWithBankModel() {
        CreditExposure islamicContract = new CreditExposure(
                "credit-1",
                new ExposureTerms(
                        FinancialContractType.MURABAHA,
                        ReturnBasis.FIXED_RATE,
                        BigDecimal.ONE,
                        BigDecimal.ZERO
                ),
                BigDecimal.TEN,
                new BigDecimal("0.01"),
                new BigDecimal("0.50")
        );

        assertThrows(ResponseStatusException.class, () -> service.analyze(
                request(List.of(bank(
                        "conventional-1",
                        BankingModel.CONVENTIONAL,
                        List.of(islamicContract)
                )), List.of(equityScenario("scenario-1")))
        ));
    }

    @Test
    void nonConventionalContractUsesItsExplicitLossAllocation() {
        CreditExposure murabaha = new CreditExposure(
                "credit-1",
                new ExposureTerms(
                        FinancialContractType.MURABAHA,
                        ReturnBasis.FIXED_RATE,
                        new BigDecimal("0.30"),
                        new BigDecimal("0.70")
                ),
                new BigDecimal("1000"),
                new BigDecimal("0.02"),
                new BigDecimal("0.40")
        );
        ScenarioInput scenario = new ScenarioInput(
                "default",
                BankingStressScenarioType.CREDIT_DEFAULT,
                null,
                null,
                null,
                new BigDecimal("0.03"),
                null
        );

        var result = service.analyze(request(
                List.of(bank("non-conventional-1", BankingModel.NON_CONVENTIONAL,
                        List.of(murabaha))),
                List.of(scenario)
        )).results().get(0);

        assertEquals(BankingModel.NON_CONVENTIONAL, result.model());
        assertTrue(result.creditLossBank().compareTo(new BigDecimal("3.60")) == 0);
        assertTrue(result.creditLossInvestors().compareTo(new BigDecimal("8.40")) == 0);
    }

    @Test
    void lossAllocationUsesExactDecimalEquality() {
        assertAllocationAccepted("0", "1");
        assertAllocationAccepted("0.5", "0.5");
        assertAllocationAccepted("0.33333333", "0.66666667");

        assertAllocationRejected("0.33333333", "0.66666666");
        assertAllocationRejected("0.50000001", "0.5");
    }

    @Test
    void dtoRejectsLossSharesWithMoreThanEightDecimalPlaces() {
        assertAllocationAccepted("0.333333333", "0.666666667");

        BankingStressTestRequest request = request(
                List.of(bank(
                        "bank-1",
                        BankingModel.NON_CONVENTIONAL,
                        List.of(creditExposure("0.333333333", "0.666666667"))
                )),
                List.of(equityScenario("scenario-1"))
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).stream()
                    .anyMatch(violation -> violation.getPropertyPath().toString()
                            .contains("bankLossShare")));
        }
    }

    @Test
    void conventionalContractWithIncompatibleReturnBasisIsRejected() {
        CreditExposure loanWithProfitSharing = new CreditExposure(
                "credit-1",
                new ExposureTerms(
                        FinancialContractType.LOAN,
                        ReturnBasis.PROFIT_SHARING,
                        BigDecimal.ONE,
                        BigDecimal.ZERO
                ),
                BigDecimal.TEN,
                new BigDecimal("0.01"),
                new BigDecimal("0.50")
        );

        assertThrows(ResponseStatusException.class, () -> service.analyze(
                request(List.of(bank(
                        "conventional-1",
                        BankingModel.CONVENTIONAL,
                        List.of(loanWithProfitSharing)
                )), List.of(equityScenario("scenario-1")))
        ));
    }

    @Test
    void rejectsScenarioMatrixOverTheMaximumResultCount() {
        List<BankInput> banks = IntStream.range(0, 32)
                .mapToObj(index -> bank("bank-" + index, BankingModel.CONVENTIONAL, List.of()))
                .toList();
        List<ScenarioInput> scenarios = IntStream.range(0, 32)
                .mapToObj(index -> equityScenario("scenario-" + index))
                .toList();

        assertThrows(ResponseStatusException.class,
                () -> service.analyze(request(banks, scenarios)));
    }

    @Test
    void beanValidationRejectsOutOfRangeShocksAndOversizedFinancialValues() {
        ScenarioInput invalidScenario = new ScenarioInput(
                "invalid",
                BankingStressScenarioType.RATE_SHOCK,
                new BigDecimal("1.01"),
                null,
                null,
                null,
                null
        );
        BankInput invalidBank = new BankInput(
                "bank-1",
                "Banque",
                BankingModel.CONVENTIONAL,
                new BigDecimal("1000000000000000"),
                BigDecimal.ZERO,
                List.of(),
                List.of(),
                List.of()
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(request(
                    List.of(invalidBank),
                    List.of(invalidScenario)
            )).size() >= 2);
        }
    }

    private static BankingStressTestRequest request(
            List<BankInput> banks,
            List<ScenarioInput> scenarios
    ) {
        return new BankingStressTestRequest("EUR", 90, banks, scenarios);
    }

    private static BankInput bank(
            String id,
            BankingModel model,
            List<CreditExposure> creditExposures
    ) {
        return new BankInput(
                id,
                "Banque " + id,
                model,
                new BigDecimal("1000"),
                new BigDecimal("5000"),
                creditExposures,
                List.of(),
                List.of()
        );
    }

    private void assertAllocationAccepted(String bankShare, String investorShare) {
        var response = service.analyze(request(
                List.of(bank(
                        "bank-1",
                        BankingModel.NON_CONVENTIONAL,
                        List.of(creditExposure(bankShare, investorShare))
                )),
                List.of(equityScenario("scenario-1"))
        ));
        assertEquals(1, response.results().size());
    }

    private void assertAllocationRejected(String bankShare, String investorShare) {
        assertThrows(ResponseStatusException.class, () -> service.analyze(request(
                List.of(bank(
                        "bank-1",
                        BankingModel.NON_CONVENTIONAL,
                        List.of(creditExposure(bankShare, investorShare))
                )),
                List.of(equityScenario("scenario-1"))
        )));
    }

    private static CreditExposure creditExposure(
            String bankShare,
            String investorShare
    ) {
        return new CreditExposure(
                "credit-1",
                new ExposureTerms(
                        FinancialContractType.MURABAHA,
                        ReturnBasis.FIXED_RATE,
                        new BigDecimal(bankShare),
                        new BigDecimal(investorShare)
                ),
                BigDecimal.TEN,
                new BigDecimal("0.01"),
                new BigDecimal("0.50")
        );
    }

    private static ExposureTerms terms(
            FinancialContractType contract,
            ReturnBasis basis,
            String bankShare,
            String investorShare
    ) {
        return new ExposureTerms(
                contract,
                basis,
                new BigDecimal(bankShare),
                new BigDecimal(investorShare)
        );
    }

    private static ScenarioInput equityScenario(String id) {
        return new ScenarioInput(
                id,
                BankingStressScenarioType.EQUITY_SHOCK,
                null,
                null,
                null,
                null,
                BigDecimal.ZERO
        );
    }
}
