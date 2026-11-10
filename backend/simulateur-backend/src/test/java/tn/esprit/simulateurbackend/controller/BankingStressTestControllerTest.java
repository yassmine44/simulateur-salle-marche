package tn.esprit.simulateurbackend.controller;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import tn.esprit.simulateurbackend.banking.BalanceSheetSide;
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
import tn.esprit.simulateurbackend.dto.BankingStressTestResponse.BankScenarioResult;
import tn.esprit.simulateurbackend.service.BankingStressTestService;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BankingStressTestControllerTest {

    private static final String ANALYZE_PATH = "/api/banking-stress-tests/analyze";
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void postsAnalyzeRequestAndReturnsResponseUsingTheAngularJsonContract()
            throws Exception {
        BankingStressTestService service = mock(BankingStressTestService.class);
        BankingStressTestRequest request = validRequest();
        BankingStressTestResponse response = validResponse();
        when(service.analyze(request)).thenReturn(response);

        LocalValidatorFactoryBean validator = validator();
        try {
            String requestJson = jsonMapper.writeValueAsString(request);
            String responseJson = mockMvc(service, validator)
                    .perform(post(ANALYZE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.currency").value("TND"))
                    .andExpect(jsonPath("$.creditHorizonDays").value(365))
                    .andExpect(jsonPath("$.simulatedResults").value(true))
                    .andExpect(jsonPath("$.regulatoryStatus")
                            .value("Ratios indicatifs non réglementaires."))
                    .andExpect(jsonPath("$.probabilityOfDefaultHorizon")
                            .value("PD à l'horizon creditHorizonDays."))
                    .andExpect(jsonPath("$.results[0].model").value("NON_CONVENTIONAL"))
                    .andExpect(jsonPath("$.results[0].scenarioType").value("COMBINED"))
                    .andExpect(jsonPath("$.results[0].creditLoss").value(20.0))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            verify(service).analyze(request);
            assertJsonFields(
                    jsonMapper.readTree(requestJson),
                    Set.of("currency", "creditHorizonDays", "banks", "scenarios")
            );
            assertRequestJsonContract(jsonMapper.readTree(requestJson));
            assertResponseJsonContract(jsonMapper.readTree(responseJson));
        } finally {
            validator.destroy();
        }

        RequestMapping root =
                BankingStressTestController.class.getAnnotation(RequestMapping.class);
        assertNotNull(root);
        assertArrayEquals(new String[]{"/api/banking-stress-tests"}, root.value());

        Method analyze = analyzeMethod();
        PostMapping postMapping = analyze.getAnnotation(PostMapping.class);
        assertNotNull(postMapping);
        assertArrayEquals(new String[]{"/analyze"}, postMapping.value());
        assertTrue(analyze.getParameters()[0].isAnnotationPresent(RequestBody.class));
        assertTrue(analyze.getParameters()[0].isAnnotationPresent(Valid.class));
    }

    @Test
    void rejectsBeanInvalidFinancialValuesBeforeCallingService() throws Exception {
        BankingStressTestService service = mock(BankingStressTestService.class);
        LocalValidatorFactoryBean validator = validator();
        try {
            mockMvc(service, validator)
                    .perform(post(ANALYZE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(invalidFinancialRequest())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail")
                            .value("Vérifiez les champs du formulaire."))
                    .andExpect(jsonPath("$.errors").exists());
            verifyNoInteractions(service);
        } finally {
            validator.destroy();
        }
    }

    @Test
    void rejectsUnknownEnumInJsonWithBadRequest() throws Exception {
        BankingStressTestService service = mock(BankingStressTestService.class);
        LocalValidatorFactoryBean validator = validator();
        try {
            String invalidJson = jsonMapper.writeValueAsString(validRequest())
                    .replace("\"NON_CONVENTIONAL\"", "\"ISLAMIC\"");
            mockMvc(service, validator)
                    .perform(post(ANALYZE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail")
                            .value("Le corps JSON est absent ou invalide."));
            verifyNoInteractions(service);
        } finally {
            validator.destroy();
        }
    }

    @Test
    void mapsServiceRejectionToBadRequestProblemDetail() throws Exception {
        BankingStressTestService service = mock(BankingStressTestService.class);
        BankingStressTestRequest request = validRequest();
        ResponseStatusException rejection = new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "La requête de simulation est invalide."
        );
        when(service.analyze(request)).thenThrow(rejection);

        LocalValidatorFactoryBean validator = validator();
        try {
            mockMvc(service, validator)
                    .perform(post(ANALYZE_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail")
                            .value("La requête de simulation est invalide."));
            verify(service).analyze(request);
        } finally {
            validator.destroy();
        }
    }

    @Test
    void requestAndResponseRecordFieldsMatchTheAngularContract() {
        assertRecordFields(
                BankingStressTestRequest.class,
                "currency", "creditHorizonDays", "banks", "scenarios"
        );
        assertRecordFields(
                BankInput.class,
                "bankId", "bankName", "model", "capitalProxy",
                "riskWeightedAssets", "creditExposures",
                "rateExposures", "marketExposures"
        );
        assertRecordFields(
                ExposureTerms.class,
                "contractType", "returnBasis", "bankLossShare",
                "investorLossShare"
        );
        assertRecordFields(
                CreditExposure.class,
                "exposureId", "terms", "exposureAtDefault",
                "probabilityOfDefault", "lossGivenDefault"
        );
        assertRecordFields(
                RateExposure.class,
                "exposureId", "terms", "side", "presentValue",
                "modifiedDurationYears", "convexityYearsSquared"
        );
        assertRecordFields(
                MarketExposure.class, "exposureId", "terms", "marketValue"
        );
        assertRecordFields(
                ScenarioInput.class,
                "scenarioId", "type", "rateShock",
                "recessionProbabilityMultiplier",
                "recessionLossGivenDefaultAddOn",
                "defaultProbabilityAddOn", "equityReturnShock"
        );
        assertRecordFields(
                BankingStressTestResponse.class,
                "currency", "creditHorizonDays", "simulatedResults",
                "regulatoryStatus", "probabilityOfDefaultHorizon",
                "assumptionsAndLimitations", "results"
        );
        assertRecordFields(
                BankScenarioResult.class,
                "bankId", "bankName", "model", "scenarioId", "scenarioType",
                "creditExpectedLossBefore", "creditExpectedLossAfter",
                "creditLoss", "creditLossBank", "creditLossInvestors",
                "economicValueChange", "bankEconomicValueChange",
                "investorEconomicValueChange", "rateRiskLoss",
                "rateRiskLossBank", "rateRiskLossInvestors",
                "marketProfitAndLoss", "marketLoss", "marketLossBank",
                "marketLossInvestors", "bankBorneLoss", "investorBorneLoss",
                "capitalProxyBefore", "capitalProxyAfter", "riskWeightedAssets",
                "capitalToRwaPercentBefore", "capitalToRwaPercentAfter"
        );
        assertTrue(Arrays.stream(BankingStressTestRequest.class.getRecordComponents())
                .noneMatch(component -> component.getName().equals("ownershipType")));

        assertEnumValues(BankingModel.values(), "CONVENTIONAL", "NON_CONVENTIONAL");
        assertEnumValues(
                BankingStressScenarioType.values(),
                "RATE_SHOCK", "RECESSION", "CREDIT_DEFAULT", "EQUITY_SHOCK", "COMBINED"
        );
        assertEnumValues(
                FinancialContractType.values(),
                "LOAN", "DEPOSIT", "BOND", "SUKUK", "MURABAHA",
                "IJARA", "MUSHARAKA", "MUDARABA", "EQUITY", "OTHER"
        );
        assertEnumValues(
                ReturnBasis.values(),
                "FIXED_RATE", "BENCHMARK_LINKED", "PROFIT_SHARING",
                "VARIABLE", "NOT_APPLICABLE"
        );
        assertEnumValues(BalanceSheetSide.values(), "ASSET", "LIABILITY");
    }

    private static Method analyzeMethod() {
        try {
            return BankingStressTestController.class.getDeclaredMethod(
                    "analyze",
                    BankingStressTestRequest.class
            );
        } catch (NoSuchMethodException exception) {
            throw new AssertionError("Expected analyze request handler.", exception);
        }
    }

    private static LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }

    private static MockMvc mockMvc(
            BankingStressTestService service,
            LocalValidatorFactoryBean validator
    ) {
        return MockMvcBuilders
                .standaloneSetup(new BankingStressTestController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private static BankingStressTestRequest validRequest() {
        ExposureTerms creditTerms = new ExposureTerms(
                FinancialContractType.MURABAHA,
                ReturnBasis.FIXED_RATE,
                new BigDecimal("0.35"),
                new BigDecimal("0.65")
        );
        ExposureTerms rateTerms = new ExposureTerms(
                FinancialContractType.SUKUK,
                ReturnBasis.PROFIT_SHARING,
                new BigDecimal("0.40"),
                new BigDecimal("0.60")
        );
        ExposureTerms marketTerms = new ExposureTerms(
                FinancialContractType.EQUITY,
                ReturnBasis.NOT_APPLICABLE,
                new BigDecimal("0.25"),
                new BigDecimal("0.75")
        );
        BankInput bank = new BankInput(
                "zitouna",
                "Banque Zitouna",
                BankingModel.NON_CONVENTIONAL,
                new BigDecimal("120000"),
                BigDecimal.ZERO,
                List.of(new CreditExposure(
                        "credit-1",
                        creditTerms,
                        new BigDecimal("1000.00"),
                        new BigDecimal("0.02"),
                        new BigDecimal("0.40")
                )),
                List.of(new RateExposure(
                        "rate-1",
                        rateTerms,
                        BalanceSheetSide.ASSET,
                        new BigDecimal("2500.00"),
                        new BigDecimal("2.0"),
                        new BigDecimal("3.0")
                )),
                List.of(new MarketExposure(
                        "market-1",
                        marketTerms,
                        new BigDecimal("300.00")
                ))
        );
        ScenarioInput scenario = new ScenarioInput(
                "combined-1",
                BankingStressScenarioType.COMBINED,
                new BigDecimal("0.01"),
                new BigDecimal("2"),
                new BigDecimal("0.05"),
                new BigDecimal("0.01"),
                new BigDecimal("-0.20")
        );
        return new BankingStressTestRequest(
                "TND",
                365,
                List.of(bank),
                List.of(scenario)
        );
    }

    private static BankingStressTestRequest invalidFinancialRequest() {
        BankingStressTestRequest valid = validRequest();
        ExposureTerms invalidAllocation = new ExposureTerms(
                FinancialContractType.MURABAHA,
                ReturnBasis.FIXED_RATE,
                new BigDecimal("1.00000001"),
                BigDecimal.ZERO
        );
        BankInput bank = valid.banks().get(0);
        BankInput invalidBank = new BankInput(
                bank.bankId(),
                bank.bankName(),
                bank.model(),
                bank.capitalProxy(),
                bank.riskWeightedAssets(),
                List.of(new CreditExposure(
                        "credit-1",
                        invalidAllocation,
                        new BigDecimal("1000"),
                        new BigDecimal("0.02"),
                        new BigDecimal("0.40")
                )),
                bank.rateExposures(),
                bank.marketExposures()
        );
        return new BankingStressTestRequest(
                valid.currency(),
                3651,
                List.of(invalidBank),
                valid.scenarios()
        );
    }

    private static BankingStressTestResponse validResponse() {
        BankScenarioResult result = new BankScenarioResult(
                "zitouna",
                "Banque Zitouna",
                BankingModel.NON_CONVENTIONAL,
                "combined-1",
                BankingStressScenarioType.COMBINED,
                new BigDecimal("8.00"),
                new BigDecimal("28.00"),
                new BigDecimal("20.00"),
                new BigDecimal("7.00"),
                new BigDecimal("13.00"),
                new BigDecimal("-100.00"),
                new BigDecimal("-40.00"),
                new BigDecimal("-60.00"),
                new BigDecimal("100.00"),
                new BigDecimal("40.00"),
                new BigDecimal("60.00"),
                new BigDecimal("-60.00"),
                new BigDecimal("60.00"),
                new BigDecimal("15.00"),
                new BigDecimal("45.00"),
                new BigDecimal("62.00"),
                new BigDecimal("118.00"),
                new BigDecimal("120000.00"),
                new BigDecimal("119938.00"),
                BigDecimal.ZERO,
                null,
                null
        );
        return new BankingStressTestResponse(
                "TND",
                365,
                true,
                "Ratios indicatifs non réglementaires.",
                "PD à l'horizon creditHorizonDays.",
                List.of("Résultats simulés."),
                List.of(result)
        );
    }

    private static void assertRequestJsonContract(JsonNode request) {
        JsonNode bank = request.path("banks").get(0);
        assertJsonFields(
                bank,
                Set.of(
                        "bankId", "bankName", "model", "capitalProxy",
                        "riskWeightedAssets", "creditExposures",
                        "rateExposures", "marketExposures"
                )
        );
        JsonNode credit = bank.path("creditExposures").get(0);
        assertJsonFields(
                credit,
                Set.of(
                        "exposureId", "terms", "exposureAtDefault",
                        "probabilityOfDefault", "lossGivenDefault"
                )
        );
        assertExposureTermsJson(credit.path("terms"));
        JsonNode rate = bank.path("rateExposures").get(0);
        assertJsonFields(
                rate,
                Set.of(
                        "exposureId", "terms", "side", "presentValue",
                        "modifiedDurationYears", "convexityYearsSquared"
                )
        );
        assertExposureTermsJson(rate.path("terms"));
        JsonNode market = bank.path("marketExposures").get(0);
        assertJsonFields(market, Set.of("exposureId", "terms", "marketValue"));
        assertExposureTermsJson(market.path("terms"));
        assertJsonFields(
                request.path("scenarios").get(0),
                Set.of(
                        "scenarioId", "type", "rateShock",
                        "recessionProbabilityMultiplier",
                        "recessionLossGivenDefaultAddOn",
                        "defaultProbabilityAddOn", "equityReturnShock"
                )
        );
    }

    private static void assertExposureTermsJson(JsonNode terms) {
        assertJsonFields(
                terms,
                Set.of(
                        "contractType", "returnBasis",
                        "bankLossShare", "investorLossShare"
                )
        );
    }

    private static void assertResponseJsonContract(JsonNode response) {
        assertJsonFields(
                response,
                Set.of(
                        "currency", "creditHorizonDays", "simulatedResults",
                        "regulatoryStatus", "probabilityOfDefaultHorizon",
                        "assumptionsAndLimitations", "results"
                )
        );
        JsonNode result = response.path("results").get(0);
        assertJsonFields(
                result,
                Set.of(
                        "bankId", "bankName", "model", "scenarioId", "scenarioType",
                        "creditExpectedLossBefore", "creditExpectedLossAfter",
                        "creditLoss", "creditLossBank", "creditLossInvestors",
                        "economicValueChange", "bankEconomicValueChange",
                        "investorEconomicValueChange", "rateRiskLoss",
                        "rateRiskLossBank", "rateRiskLossInvestors",
                        "marketProfitAndLoss", "marketLoss", "marketLossBank",
                        "marketLossInvestors", "bankBorneLoss", "investorBorneLoss",
                        "capitalProxyBefore", "capitalProxyAfter", "riskWeightedAssets",
                        "capitalToRwaPercentBefore", "capitalToRwaPercentAfter"
                )
        );
        assertTrue(result.path("capitalToRwaPercentAfter").isNull());
        assertFalse(response.has("ownershipType"));
    }

    private static void assertJsonFields(JsonNode node, Set<String> expectedFields) {
        Set<String> actualFields = Set.copyOf(node.propertyNames());
        assertEquals(expectedFields, actualFields);
    }

    private static void assertRecordFields(Class<?> type, String... expected) {
        Set<String> actual = Arrays.stream(type.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());
        assertEquals(Set.of(expected), actual, type.getSimpleName());
    }

    private static void assertEnumValues(Enum<?>[] values, String... expected) {
        Set<String> actual = Arrays.stream(values)
                .map(Enum::name)
                .collect(Collectors.toSet());
        assertEquals(Set.of(expected), actual);
    }
}
