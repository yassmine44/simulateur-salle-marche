package tn.esprit.simulateurbackend.service;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.json.JsonMapper;
import tn.esprit.simulateurbackend.banking.BankingScenarioSeverity;
import tn.esprit.simulateurbackend.dto.BankingScenarioProposal;
import tn.esprit.simulateurbackend.dto.BankingScenarioProposalRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BankingScenarioProposalServiceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void requestsNonStreamingStructuredJsonAndValidatesTheSingleResponseObject() {
        TestFixture fixture = serviceWithResponse(proposalResponse());

        BankingScenarioProposal proposal = fixture.service().propose(request());

        assertEquals("Proposition de test", proposal.scenarioName());
        assertEquals(BankingScenarioSeverity.HIGH, proposal.severity());
        assertEquals(5, proposal.factors().size());
        assertTrue(proposal.assumptions().stream()
                .anyMatch(value -> value.contains("aucun coefficient de transmission")));
        fixture.server().verify();
    }

    @Test
    void rejectsResponsesContainingMoreThanOneJsonObject() {
        TestFixture fixture = serviceWithResponse(proposalResponse() + "{}");

        assertThrows(ResponseStatusException.class,
                () -> fixture.service().propose(request()));
        fixture.server().verify();
    }

    private static TestFixture serviceWithResponse(String proposalJson) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://127.0.0.1:11434");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String response = JSON.writeValueAsString(Map.of(
                "message", Map.of("role", "assistant", "content", proposalJson),
                "done", true
        ));
        server.expect(requestTo("http://127.0.0.1:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(allOf(
                        containsString("\"model\":\"test-model\""),
                        containsString("\"stream\":false"),
                        containsString("\"format\":")
                )))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
        return new TestFixture(
                new BankingScenarioProposalService(builder.build(), true, "test-model"),
                server
        );
    }

    private static BankingScenarioProposalRequest request() {
        return new BankingScenarioProposalRequest(BankingScenarioSeverity.HIGH, 365, null);
    }

    private static String proposalResponse() {
        return JSON.writeValueAsString(Map.of(
                "scenarioName", "Proposition de test",
                "severity", "HIGH",
                "horizonDays", 365,
                "factors", List.of(
                        factor("UNEMPLOYMENT_INCREASE", 2, "PERCENTAGE_POINTS"),
                        factor("GDP_CONTRACTION", 3, "PERCENT"),
                        factor("BANK_REVENUE_DECLINE", 5, "PERCENT"),
                        factor("ASSET_QUALITY_DETERIORATION", 4, "PERCENT"),
                        factor("DEFAULT_PROBABILITY_INCREASE", 2, "PERCENTAGE_POINTS")
                ),
                "engineParameters", Map.of(
                        "rateShock", 0.01,
                        "recessionProbabilityMultiplier", 1.5,
                        "recessionLossGivenDefaultAddOn", 0.05,
                        "equityReturnShock", -0.1
                ),
                "justifications", List.of("Justification de test."),
                "assumptions", List.of("Hypothèse de test.")
        ));
    }

    private static Map<String, Object> factor(String type, double value, String unit) {
        return Map.of(
                "type", type,
                "value", value,
                "unit", unit,
                "rationale", "Facteur fictif de test."
        );
    }

    private record TestFixture(
            BankingScenarioProposalService service,
            MockRestServiceServer server
    ) {
    }
}
