package tn.esprit.simulateurbackend.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RecaptchaService {

    private final RestClient restClient =
            RestClient.builder()
                    .baseUrl("https://recaptchaenterprise.googleapis.com")
                    .build();

    @Value("${app.recaptcha.project-id}")
    private String projectId;

    @Value("${app.recaptcha.site-key}")
    private String siteKey;

    @Value("${app.recaptcha.api-key}")
    private String apiKey;

    @Value("${app.recaptcha.min-score:0.5}")
    private double minScore;


    public void verify(
            String token,
            String expectedAction,
            HttpServletRequest request
    ) {

        if (token == null || token.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Vérification anti-bot manquante."
            );
        }


        Map<String, Object> event =
                new HashMap<>();

        event.put("token", token);
        event.put("siteKey", siteKey);
        event.put("expectedAction", expectedAction);


        String userAgent =
                request.getHeader("User-Agent");

        if (userAgent != null && !userAgent.isBlank()) {

            event.put(
                    "userAgent",
                    userAgent
            );
        }


        Map<String, Object> body =
                Map.of(
                        "event",
                        event
                );


        AssessmentResponse response;

        try {

            response =
                    restClient
                            .post()
                            .uri(
                                    "/v1/projects/{projectId}/assessments",
                                    projectId
                            )
                            .header(
                                    "x-goog-api-key",
                                    apiKey
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(body)
                            .retrieve()
                            .body(
                                    AssessmentResponse.class
                            );

        } catch (RestClientException exception) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Service anti-bot temporairement indisponible."
            );
        }


        if (
                response == null ||
                        response.tokenProperties() == null
        ) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Réponse reCAPTCHA invalide."
            );
        }


        TokenProperties properties =
                response.tokenProperties();


        if (!properties.valid()) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Token reCAPTCHA invalide."
            );
        }


        if (
                properties.action() == null ||
                        !expectedAction.equals(
                                properties.action()
                        )
        ) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Action reCAPTCHA incorrecte."
            );
        }


        if (response.riskAnalysis() == null) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Score reCAPTCHA indisponible."
            );
        }


        if (
                response.riskAnalysis().score()
                        < minScore
        ) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Activité suspecte détectée."
            );
        }
    }


    public record AssessmentResponse(
            TokenProperties tokenProperties,
            RiskAnalysis riskAnalysis
    ) {
    }


    public record TokenProperties(
            boolean valid,
            String action,
            String invalidReason
    ) {
    }


    public record RiskAnalysis(
            double score,
            List<String> reasons
    ) {
    }
}