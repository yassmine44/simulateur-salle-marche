package tn.esprit.simulateurbackend.banking;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import tn.esprit.simulateurbackend.dto.BankingScenarioProposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BankingScenarioProposalValidatorTest {

    @Test
    void acceptsAllEconomicFactorsAndAppendsTransmissionLimitation() {
        BankingScenarioProposal validated = BankingScenarioProposalValidator.validate(
                proposal(),
                BankingScenarioSeverity.HIGH,
                365
        );

        assertEquals(5, validated.factors().size());
        assertTrue(validated.assumptions().stream()
                .anyMatch(assumption -> assumption.contains("aucun coefficient de transmission")));
    }

    @Test
    void rejectsSeverityOrHorizonDifferentFromTheRequest() {
        assertThrows(IllegalArgumentException.class, () ->
                BankingScenarioProposalValidator.validate(
                        proposal(),
                        BankingScenarioSeverity.SEVERE,
                        365
                ));
        assertThrows(IllegalArgumentException.class, () ->
                BankingScenarioProposalValidator.validate(
                        proposal(),
                        BankingScenarioSeverity.HIGH,
                        180
                ));
    }

    @Test
    void rejectsMissingDuplicateOrUnknownFactors() {
        BankingScenarioProposal missing = withFactors(
                proposal(),
                proposal().factors().subList(0, 4)
        );
        assertThrows(IllegalArgumentException.class, () ->
                BankingScenarioProposalValidator.validate(
                        missing,
                        BankingScenarioSeverity.HIGH,
                        365
                ));

        List<BankingScenarioProposal.EconomicFactor> duplicate =
                List.of(proposal().factors().get(0), proposal().factors().get(0),
                        proposal().factors().get(2), proposal().factors().get(3),
                        proposal().factors().get(4));
        assertThrows(IllegalArgumentException.class, () ->
                BankingScenarioProposalValidator.validate(
                        withFactors(proposal(), duplicate),
                        BankingScenarioSeverity.HIGH,
                        365
                ));
    }

    @Test
    void rejectsIncorrectUnitsAndValuesOutsideTheDocumentedRange() {
        List<BankingScenarioProposal.EconomicFactor> wrongUnit =
                replaceFactor(EconomicStressFactorType.UNEMPLOYMENT_INCREASE,
                        new BigDecimal("2"), "PERCENT");
        assertThrows(IllegalArgumentException.class, () ->
                BankingScenarioProposalValidator.validate(
                        withFactors(proposal(), wrongUnit),
                        BankingScenarioSeverity.HIGH,
                        365
                ));

        List<BankingScenarioProposal.EconomicFactor> outOfRange =
                replaceFactor(EconomicStressFactorType.GDP_CONTRACTION,
                        new BigDecimal("100.01"), "PERCENT");
        assertThrows(IllegalArgumentException.class, () ->
                BankingScenarioProposalValidator.validate(
                        withFactors(proposal(), outOfRange),
                        BankingScenarioSeverity.HIGH,
                        365
                ));
    }

    @Test
    void rejectsOutOfRangeEngineParametersAndPartialRecessionPair() {
        BankingScenarioProposal.EngineParameters outOfRange =
                new BankingScenarioProposal.EngineParameters(
                        new BigDecimal("1.01"), null, null, null
                );
        assertThrows(IllegalArgumentException.class, () ->
                BankingScenarioProposalValidator.validate(
                        withEngineParameters(proposal(), outOfRange),
                        BankingScenarioSeverity.HIGH,
                        365
                ));

        BankingScenarioProposal.EngineParameters partialRecession =
                new BankingScenarioProposal.EngineParameters(
                        null, BigDecimal.ONE, null, null
                );
        assertThrows(IllegalArgumentException.class, () ->
                BankingScenarioProposalValidator.validate(
                        withEngineParameters(proposal(), partialRecession),
                        BankingScenarioSeverity.HIGH,
                        365
                ));
    }

    @Test
    void convertsDefaultProbabilityPercentagePointsToEngineFractionOnlyOnExplicitUse() {
        BigDecimal converted = BankingScenarioProposalValidator
                .defaultProbabilityAddOn(proposal());

        assertEquals(new BigDecimal("0.02000000"), converted);
        assertEquals(new BigDecimal("2"), proposal().factors().stream()
                .filter(factor -> factor.type()
                        == EconomicStressFactorType.DEFAULT_PROBABILITY_INCREASE)
                .findFirst().orElseThrow().value());
    }

    private static BankingScenarioProposal proposal() {
        return new BankingScenarioProposal(
                "Proposition unitaire",
                BankingScenarioSeverity.HIGH,
                365,
                List.of(
                        factor(EconomicStressFactorType.UNEMPLOYMENT_INCREASE,
                                "2", "PERCENTAGE_POINTS"),
                        factor(EconomicStressFactorType.GDP_CONTRACTION,
                                "3", "PERCENT"),
                        factor(EconomicStressFactorType.BANK_REVENUE_DECLINE,
                                "5", "PERCENT"),
                        factor(EconomicStressFactorType.ASSET_QUALITY_DETERIORATION,
                                "4", "PERCENT"),
                        factor(EconomicStressFactorType.DEFAULT_PROBABILITY_INCREASE,
                                "2", "PERCENTAGE_POINTS")
                ),
                new BankingScenarioProposal.EngineParameters(
                        new BigDecimal("0.01"),
                        new BigDecimal("1.5"),
                        new BigDecimal("0.05"),
                        new BigDecimal("-0.1")
                ),
                List.of("Justification de test."),
                List.of("Hypothèse de test.")
        );
    }

    private static BankingScenarioProposal.EconomicFactor factor(
            EconomicStressFactorType type,
            String value,
            String unit
    ) {
        return new BankingScenarioProposal.EconomicFactor(
                type, new BigDecimal(value), unit, "Justification."
        );
    }

    private static BankingScenarioProposal withFactors(
            BankingScenarioProposal source,
            List<BankingScenarioProposal.EconomicFactor> factors
    ) {
        return new BankingScenarioProposal(
                source.scenarioName(), source.severity(), source.horizonDays(),
                factors, source.engineParameters(), source.justifications(), source.assumptions()
        );
    }

    private static List<BankingScenarioProposal.EconomicFactor> replaceFactor(
            EconomicStressFactorType type,
            BigDecimal value,
            String unit
    ) {
        return proposal().factors().stream()
                .map(factor -> factor.type() == type
                        ? new BankingScenarioProposal.EconomicFactor(
                                type, value, unit, factor.rationale()
                        )
                        : factor)
                .toList();
    }

    private static BankingScenarioProposal withEngineParameters(
            BankingScenarioProposal source,
            BankingScenarioProposal.EngineParameters parameters
    ) {
        return new BankingScenarioProposal(
                source.scenarioName(), source.severity(), source.horizonDays(),
                source.factors(), parameters, source.justifications(), source.assumptions()
        );
    }
}
