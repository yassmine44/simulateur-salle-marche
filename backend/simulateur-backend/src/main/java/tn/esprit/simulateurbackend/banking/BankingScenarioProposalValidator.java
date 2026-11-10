package tn.esprit.simulateurbackend.banking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tn.esprit.simulateurbackend.dto.BankingScenarioProposal;

public final class BankingScenarioProposalValidator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final String LIMITATION =
            "Les facteurs chômage, PIB, revenus et qualité des actifs restent contextuels; "
                    + "aucun coefficient de transmission vers pertes, PD ou LGD n'est appliqué.";
    private static final Map<EconomicStressFactorType, String> EXPECTED_UNITS =
            expectedUnits();

    private BankingScenarioProposalValidator() {
    }

    public static BankingScenarioProposal validate(
            BankingScenarioProposal proposal,
            BankingScenarioSeverity requestedSeverity,
            int requestedHorizonDays
    ) {
        if (proposal == null
                || requestedSeverity == null
                || requestedHorizonDays < 1
                || requestedHorizonDays > 3650) {
            throw new IllegalArgumentException("La proposition ou la requête est invalide.");
        }
        if (proposal.scenarioName() == null
                || proposal.scenarioName().isBlank()
                || proposal.scenarioName().length() > 120) {
            throw new IllegalArgumentException("Le nom du scénario doit contenir 1 à 120 caractères.");
        }
        if (proposal.severity() != requestedSeverity
                || proposal.horizonDays() != requestedHorizonDays) {
            throw new IllegalArgumentException(
                    "La sévérité et l'horizon proposés doivent respecter la requête."
            );
        }

        validateFactors(proposal.factors());
        validateEngineParameters(proposal.engineParameters());
        validateTextList(proposal.justifications(), 1, 8, "justifications");
        validateTextList(proposal.assumptions(), 0, 11, "hypothèses");

        List<String> assumptions = proposal.assumptions().stream()
                .filter(value -> !LIMITATION.equals(value))
                .toList();
        return new BankingScenarioProposal(
                proposal.scenarioName().trim(),
                proposal.severity(),
                proposal.horizonDays(),
                List.copyOf(proposal.factors()),
                proposal.engineParameters(),
                List.copyOf(proposal.justifications()),
                appendLimitation(assumptions)
        );
    }

    public static BigDecimal defaultProbabilityAddOn(
            BankingScenarioProposal proposal
    ) {
        if (proposal == null) {
            throw new IllegalArgumentException("La proposition est obligatoire.");
        }
        BankingScenarioProposal.EconomicFactor factor = proposal.factors().stream()
                .filter(item -> item.type() == EconomicStressFactorType.DEFAULT_PROBABILITY_INCREASE)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Le supplément de probabilité de défaut est absent."
                ));
        if (!"PERCENTAGE_POINTS".equals(factor.unit())) {
            throw new IllegalArgumentException("L'unité du supplément PD doit être en points.");
        }
        if (factor.value() == null || factor.value().compareTo(ZERO) < 0
                || factor.value().compareTo(HUNDRED) > 0
                || factor.value().scale() > 8) {
            throw new IllegalArgumentException("Le supplément PD proposé est hors limites.");
        }
        return factor.value().divide(HUNDRED, 8, RoundingMode.HALF_UP);
    }

    private static void validateFactors(List<BankingScenarioProposal.EconomicFactor> factors) {
        if (factors == null || factors.size() != EconomicStressFactorType.values().length) {
            throw new IllegalArgumentException("La proposition doit couvrir les cinq facteurs économiques.");
        }
        Set<EconomicStressFactorType> seen = EnumSet.noneOf(EconomicStressFactorType.class);
        for (BankingScenarioProposal.EconomicFactor factor : factors) {
            if (factor == null || factor.type() == null || !seen.add(factor.type())) {
                throw new IllegalArgumentException("Les types de facteurs doivent être présents et uniques.");
            }
            if (factor.value() == null || factor.value().compareTo(ZERO) < 0
                    || factor.value().compareTo(HUNDRED) > 0
                    || factor.value().scale() > 8) {
                throw new IllegalArgumentException(
                        "La valeur de chaque facteur doit être comprise entre 0 et 100 (maximum 8 décimales)."
                );
            }
            if (!EXPECTED_UNITS.get(factor.type()).equals(factor.unit())) {
                throw new IllegalArgumentException(
                        "Unité incorrecte pour le facteur " + factor.type() + "."
                );
            }
            validateText(factor.rationale(), 1, 500, "justification du facteur");
        }
        if (!seen.equals(EnumSet.allOf(EconomicStressFactorType.class))) {
            throw new IllegalArgumentException("Un ou plusieurs facteurs économiques sont absents.");
        }
    }

    private static void validateEngineParameters(
            BankingScenarioProposal.EngineParameters parameters
    ) {
        if (parameters == null) {
            throw new IllegalArgumentException("Les paramètres du moteur sont obligatoires, même s'ils sont nuls.");
        }
        validateRange(parameters.rateShock(), new BigDecimal("-1"), ONE, "choc de taux");
        validateRange(
                parameters.recessionProbabilityMultiplier(),
                ONE,
                HUNDRED,
                "multiplicateur PD"
        );
        validateRange(
                parameters.recessionLossGivenDefaultAddOn(),
                ZERO,
                ONE,
                "supplément LGD"
        );
        validateRange(parameters.equityReturnShock(), ONE.negate(), ONE, "choc actions");
        boolean recessionMultiplierPresent = parameters.recessionProbabilityMultiplier() != null;
        boolean recessionLgdPresent = parameters.recessionLossGivenDefaultAddOn() != null;
        if (recessionMultiplierPresent != recessionLgdPresent) {
            throw new IllegalArgumentException(
                    "Le multiplicateur PD et le supplément LGD de récession doivent être proposés ensemble."
            );
        }
    }

    private static void validateRange(
            BigDecimal value,
            BigDecimal minimum,
            BigDecimal maximum,
            String label
    ) {
        if (value != null && (value.compareTo(minimum) < 0
                || value.compareTo(maximum) > 0
                || value.scale() > 8)) {
            throw new IllegalArgumentException(
                    label + " hors limites ou avec plus de 8 décimales."
            );
        }
    }

    private static void validateTextList(
            List<String> values,
            int minimumSize,
            int maximumSize,
            String label
    ) {
        if (values == null || values.size() < minimumSize || values.size() > maximumSize) {
            throw new IllegalArgumentException(
                    "Le nombre de " + label + " doit être compris entre "
                            + minimumSize + " et " + maximumSize + "."
            );
        }
        values.forEach(value -> validateText(value, 1, 500, label));
    }

    private static void validateText(String value, int minimum, int maximum, String label) {
        if (value == null || value.isBlank()
                || value.length() < minimum || value.length() > maximum) {
            throw new IllegalArgumentException(
                    "Le champ " + label + " doit contenir " + minimum + " à " + maximum + " caractères."
            );
        }
    }

    private static List<String> appendLimitation(List<String> assumptions) {
        return java.util.stream.Stream.concat(
                assumptions.stream(),
                java.util.stream.Stream.of(LIMITATION)
        ).distinct().toList();
    }

    private static Map<EconomicStressFactorType, String> expectedUnits() {
        Map<EconomicStressFactorType, String> units =
                new EnumMap<>(EconomicStressFactorType.class);
        units.put(EconomicStressFactorType.UNEMPLOYMENT_INCREASE, "PERCENTAGE_POINTS");
        units.put(EconomicStressFactorType.GDP_CONTRACTION, "PERCENT");
        units.put(EconomicStressFactorType.BANK_REVENUE_DECLINE, "PERCENT");
        units.put(EconomicStressFactorType.ASSET_QUALITY_DETERIORATION, "PERCENT");
        units.put(EconomicStressFactorType.DEFAULT_PROBABILITY_INCREASE, "PERCENTAGE_POINTS");
        return Map.copyOf(units);
    }
}
