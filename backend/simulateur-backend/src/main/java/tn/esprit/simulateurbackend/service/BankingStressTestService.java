package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.banking.BankingModel;
import tn.esprit.simulateurbackend.banking.BankingStressScenarioType;
import tn.esprit.simulateurbackend.banking.FinancialContractType;
import tn.esprit.simulateurbackend.banking.ReturnBasis;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.BankInput;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.CreditExposure;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.ExposureTerms;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.MarketExposure;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.RateExposure;
import tn.esprit.simulateurbackend.dto.BankingStressTestRequest.ScenarioInput;
import tn.esprit.simulateurbackend.dto.BankingStressTestResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class BankingStressTestService {

    private static final int MAX_RESULTS = 1_000;
    private static final long MAX_EXPOSURE_SCENARIO_EVALUATIONS = 1_000_000;

    private final BankingStressCalculator calculator;

    public BankingStressTestService(BankingStressCalculator calculator) {
        this.calculator = calculator;
    }

    public BankingStressTestResponse analyze(BankingStressTestRequest request) {
        validateRequest(request);

        List<BankingStressTestResponse.BankScenarioResult> results =
                new ArrayList<>(request.banks().size() * request.scenarios().size());

        for (BankInput bank : request.banks()) {
            for (ScenarioInput scenario : request.scenarios()) {
                results.add(calculator.calculate(bank, scenario));
            }
        }

        return new BankingStressTestResponse(
                request.currency(),
                request.creditHorizonDays(),
                true,
                "Les indicateurs capital/RWA sont des proxies économiques simulés, "
                        + "pas des ratios réglementaires officiels.",
                "Les PD fournies sont exprimées pour l'horizon creditHorizonDays "
                        + "de la requête.",
                List.of(
                        "Les parts de pertes sont fournies explicitement par exposition; "
                                + "elles ne sont pas déduites du modèle bancaire.",
                        "Les chocs de récession et de défaut combinés modifient une seule "
                                + "fois la PD (min(PD * multiplicateur + supplément, 1)) "
                                + "et la LGD de chaque exposition de crédit.",
                        "Le choc de taux utilise une approximation duration modifiée et "
                                + "convexité; les chocs sont des fractions, pas des points de base.",
                        "Les durées sont en années et les montants utilisent la devise indiquée.",
                        "Les montants sont exprimés en unités de la devise déclarée; "
                                + "aucune conversion depuis ou vers KDT n'est effectuée.",
                        "Éviter de déclarer plusieurs fois une même exposition économique "
                                + "dans les catégories crédit, taux et marché.",
                        "Le modèle simplifie les effets de second ordre, la liquidité, "
                                + "les corrélations et les réactions comportementales."
                ),
                List.copyOf(results)
        );
    }

    private static void validateRequest(BankingStressTestRequest request) {
        if (request == null) {
            throw badRequest("Le corps de la requête est obligatoire.");
        }
        if (request.currency() == null || !request.currency().matches("[A-Z]{3}")
                || request.creditHorizonDays() == null
                || request.creditHorizonDays() < 1
                || request.creditHorizonDays() > 3650) {
            throw badRequest("La devise doit être un code ISO à trois lettres et "
                    + "l'horizon de crédit doit être compris entre 1 et 3650 jours.");
        }
        if (request.banks() == null || request.banks().isEmpty()
                || request.scenarios() == null || request.scenarios().isEmpty()) {
            throw badRequest("Au moins une banque et un scénario sont requis.");
        }
        if (request.banks().size() > 100 || request.scenarios().size() > 100) {
            throw badRequest("La requête dépasse le maximum de banques ou scénarios.");
        }
        if ((long) request.banks().size() * request.scenarios().size() > MAX_RESULTS) {
            throw badRequest("La requête dépasse le maximum de résultats autorisé.");
        }

        Set<String> bankIds = new HashSet<>();
        long exposureScenarioEvaluations = 0;
        for (BankInput bank : request.banks()) {
            if (bank == null) {
                throw badRequest("Une entrée banque ne peut pas être nulle.");
            }
            if (!bankIds.add(bank.bankId())) {
                throw badRequest("Identifiant de banque dupliqué : " + bank.bankId());
            }

            validateExposures(bank);
            exposureScenarioEvaluations += (long) totalExposures(bank)
                    * request.scenarios().size();
            if (exposureScenarioEvaluations > MAX_EXPOSURE_SCENARIO_EVALUATIONS) {
                throw badRequest("La requête dépasse le maximum d'évaluations autorisé.");
            }
        }

        Set<String> scenarioIds = new HashSet<>();
        for (ScenarioInput scenario : request.scenarios()) {
            if (scenario == null) {
                throw badRequest("Une entrée scénario ne peut pas être nulle.");
            }
            if (!scenarioIds.add(scenario.scenarioId())) {
                throw badRequest("Identifiant de scénario dupliqué : " + scenario.scenarioId());
            }
            validateScenario(scenario);
        }
    }

    private static void validateExposures(BankInput bank) {
        if (bank.model() == null
                || bank.bankId() == null || bank.bankId().isBlank()
                || bank.bankName() == null || bank.bankName().isBlank()
                || bank.creditExposures() == null
                || bank.rateExposures() == null
                || bank.marketExposures() == null) {
            throw badRequest("Le modèle et les listes d'expositions sont obligatoires.");
        }
        if (bank.creditExposures().size() > 500
                || bank.rateExposures().size() > 500
                || bank.marketExposures().size() > 500) {
            throw badRequest("Une banque dépasse le maximum de 500 expositions par catégorie.");
        }
        Set<String> exposureIds = new HashSet<>();

        for (CreditExposure exposure : bank.creditExposures()) {
            if (exposure == null) {
                throw badRequest("Une exposition de crédit ne peut pas être nulle.");
            }
            validateExposure(exposureIds, bank, exposure.exposureId(), exposure.terms());
        }
        for (RateExposure exposure : bank.rateExposures()) {
            if (exposure == null) {
                throw badRequest("Une exposition de taux ne peut pas être nulle.");
            }
            validateExposure(exposureIds, bank, exposure.exposureId(), exposure.terms());
        }
        for (MarketExposure exposure : bank.marketExposures()) {
            if (exposure == null) {
                throw badRequest("Une exposition de marché ne peut pas être nulle.");
            }
            validateExposure(exposureIds, bank, exposure.exposureId(), exposure.terms());
        }
    }

    private static void validateExposure(
            Set<String> exposureIds,
            BankInput bank,
            String exposureId,
            ExposureTerms terms
    ) {
        if (exposureId == null || exposureId.isBlank()) {
            throw badRequest("L'identifiant d'exposition est obligatoire.");
        }
        checkUniqueExposure(exposureIds, exposureId);
        if (terms == null) {
            throw badRequest("Les modalités de contrat sont obligatoires pour " + exposureId + ".");
        }
        validateAllocation(terms, exposureId);
        validateContract(bank.model(), terms, exposureId);
    }

    private static long totalExposures(BankInput bank) {
        return (long) bank.creditExposures().size()
                + bank.rateExposures().size()
                + bank.marketExposures().size();
    }

    private static void checkUniqueExposure(Set<String> ids, String exposureId) {
        if (!ids.add(exposureId)) {
            throw badRequest("Identifiant d'exposition dupliqué dans une banque : " + exposureId);
        }
    }

    private static void validateAllocation(ExposureTerms terms, String exposureId) {
        if (terms.bankLossShare() == null || terms.investorLossShare() == null) {
            throw badRequest("Les parts de pertes sont obligatoires pour " + exposureId + ".");
        }
        BigDecimal totalShare = terms.bankLossShare()
                .add(terms.investorLossShare());
        if (totalShare.compareTo(BigDecimal.ONE) != 0) {
            throw badRequest(
                    "Les parts de pertes banque/investisseurs doivent totaliser 1 pour "
                            + exposureId + "."
            );
        }
    }

    private static void validateContract(
            BankingModel model,
            ExposureTerms terms,
            String exposureId
    ) {
        if (terms.contractType() == null || terms.returnBasis() == null) {
            throw badRequest("Le contrat et sa base de rendement sont obligatoires pour "
                    + exposureId + ".");
        }

        FinancialContractType contract = terms.contractType();
        ReturnBasis basis = terms.returnBasis();
        if (model == BankingModel.CONVENTIONAL && switch (contract) {
            case SUKUK, MURABAHA, IJARA, MUSHARAKA, MUDARABA -> true;
            default -> false;
        }) {
            throw badRequest("Le contrat " + contract + " n'est pas cohérent avec le modèle "
                    + "CONVENTIONAL pour " + exposureId + ".");
        }

        boolean compatible = switch (contract) {
            case MURABAHA -> basis == ReturnBasis.FIXED_RATE;
            case MUSHARAKA, MUDARABA -> basis == ReturnBasis.PROFIT_SHARING;
            case SUKUK -> basis == ReturnBasis.BENCHMARK_LINKED
                    || basis == ReturnBasis.PROFIT_SHARING
                    || basis == ReturnBasis.VARIABLE;
            case IJARA -> basis == ReturnBasis.FIXED_RATE
                    || basis == ReturnBasis.BENCHMARK_LINKED
                    || basis == ReturnBasis.VARIABLE;
            case LOAN, DEPOSIT, BOND -> basis == ReturnBasis.FIXED_RATE
                    || basis == ReturnBasis.BENCHMARK_LINKED
                    || basis == ReturnBasis.VARIABLE;
            case EQUITY -> basis == ReturnBasis.PROFIT_SHARING
                    || basis == ReturnBasis.VARIABLE
                    || basis == ReturnBasis.NOT_APPLICABLE;
            case OTHER -> true;
        };
        if (!compatible) {
            throw badRequest("La base " + basis + " n'est pas cohérente avec le contrat "
                    + contract + " pour " + exposureId + ".");
        }
    }

    private static void validateScenario(ScenarioInput scenario) {
        if (scenario.scenarioId() == null || scenario.type() == null) {
            throw badRequest("L'identifiant et le type de scénario sont obligatoires.");
        }
        switch (scenario.type()) {
            case RATE_SHOCK -> {
                require(scenario.rateShock(), "rateShock", scenario.scenarioId());
                reject(scenario.recessionProbabilityMultiplier(),
                        scenario.recessionLossGivenDefaultAddOn(),
                        scenario.defaultProbabilityAddOn(),
                        scenario.equityReturnShock(),
                        scenario.scenarioId());
            }
            case RECESSION -> {
                require(scenario.recessionProbabilityMultiplier(),
                        "recessionProbabilityMultiplier", scenario.scenarioId());
                require(scenario.recessionLossGivenDefaultAddOn(),
                        "recessionLossGivenDefaultAddOn", scenario.scenarioId());
                reject(scenario.rateShock(), scenario.defaultProbabilityAddOn(),
                        scenario.equityReturnShock(), scenario.scenarioId());
            }
            case CREDIT_DEFAULT -> {
                require(scenario.defaultProbabilityAddOn(),
                        "defaultProbabilityAddOn", scenario.scenarioId());
                reject(scenario.rateShock(),
                        scenario.recessionProbabilityMultiplier(),
                        scenario.recessionLossGivenDefaultAddOn(),
                        scenario.equityReturnShock(), scenario.scenarioId());
            }
            case EQUITY_SHOCK -> {
                require(scenario.equityReturnShock(),
                        "equityReturnShock", scenario.scenarioId());
                reject(scenario.rateShock(),
                        scenario.recessionProbabilityMultiplier(),
                        scenario.recessionLossGivenDefaultAddOn(),
                        scenario.defaultProbabilityAddOn(), scenario.scenarioId());
            }
            case COMBINED -> {
                if (scenario.rateShock() == null
                        && scenario.recessionProbabilityMultiplier() == null
                        && scenario.recessionLossGivenDefaultAddOn() == null
                        && scenario.defaultProbabilityAddOn() == null
                        && scenario.equityReturnShock() == null) {
                    throw badRequest("Un scénario combiné doit contenir au moins un choc : "
                            + scenario.scenarioId() + ".");
                }
                if ((scenario.recessionProbabilityMultiplier() == null)
                        != (scenario.recessionLossGivenDefaultAddOn() == null)) {
                    throw badRequest("Les deux paramètres de récession doivent être fournis "
                            + "ensemble pour " + scenario.scenarioId() + ".");
                }
            }
        }
    }

    private static void require(
            BigDecimal value,
            String field,
            String scenarioId
    ) {
        if (value == null) {
            throw badRequest(field + " est obligatoire pour le scénario " + scenarioId + ".");
        }
    }

    private static void reject(
            BigDecimal first,
            BigDecimal second,
            BigDecimal third,
            BigDecimal fourth,
            String scenarioId
    ) {
        if (first != null || second != null || third != null || fourth != null) {
            throw badRequest(
                    "Paramètre de choc incompatible avec le type du scénario "
                            + scenarioId + "."
            );
        }
    }

    private static void reject(
            BigDecimal first,
            BigDecimal second,
            BigDecimal third,
            String scenarioId
    ) {
        reject(first, second, third, null, scenarioId);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
