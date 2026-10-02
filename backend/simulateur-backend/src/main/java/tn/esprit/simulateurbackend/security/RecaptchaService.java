package tn.esprit.simulateurbackend.security;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import org.springframework.stereotype.Service;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.entity.AuditEventType;
import tn.esprit.simulateurbackend.service.AuditService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Service
public class RecaptchaService {

    private final RestClient restClient =
            RestClient.builder()

                    .baseUrl(
                            "https://recaptchaenterprise.googleapis.com"
                    )

                    .build();


    private final AuditService auditService;


    public RecaptchaService(
            AuditService auditService
    ) {

        this.auditService =
                auditService;
    }


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

        /*
         * =========================================
         * TOKEN ABSENT
         * =========================================
         */

        if (
                token == null ||
                        token.isBlank()
        ) {

            reject(
                    expectedAction,
                    "Token reCAPTCHA manquant.",
                    request
            );
        }


        /*
         * =========================================
         * BUILD EVENT
         * =========================================
         */

        Map<String, Object> event =
                new HashMap<>();


        event.put(
                "token",
                token
        );

        event.put(
                "siteKey",
                siteKey
        );

        event.put(
                "expectedAction",
                expectedAction
        );


        String userAgent =
                request.getHeader(
                        "User-Agent"
                );


        if (
                userAgent != null &&
                        !userAgent.isBlank()
        ) {

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


        /*
         * =========================================
         * GOOGLE ASSESSMENT
         * =========================================
         */

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

                            .body(
                                    body
                            )

                            .retrieve()

                            .body(
                                    AssessmentResponse.class
                            );


        } catch (
                RestClientException exception
        ) {

            auditService.log(
                    AuditEventType.RECAPTCHA_REJECTED,
                    null,
                    null,
                    null,
                    false,
                    "Service reCAPTCHA indisponible pour l'action : "
                            + expectedAction,
                    request
            );


            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Service anti-bot temporairement indisponible."
            );
        }


        /*
         * =========================================
         * RESPONSE VALIDATION
         * =========================================
         */

        if (
                response == null ||
                        response.tokenProperties()
                                == null
        ) {

            reject(
                    expectedAction,
                    "Réponse reCAPTCHA invalide.",
                    request
            );
        }


        TokenProperties properties =
                response.tokenProperties();


        /*
         * =========================================
         * TOKEN VALID
         * =========================================
         */

        if (!properties.valid()) {

            reject(
                    expectedAction,
                    "Token reCAPTCHA invalide : "
                            + properties.invalidReason(),
                    request
            );
        }


        /*
         * =========================================
         * ACTION CHECK
         * =========================================
         */

        if (
                properties.action() == null ||
                        !expectedAction.equals(
                                properties.action()
                        )
        ) {

            reject(
                    expectedAction,
                    "Action reCAPTCHA incorrecte.",
                    request
            );
        }


        /*
         * =========================================
         * SCORE
         * =========================================
         */

        if (
                response.riskAnalysis()
                        == null
        ) {

            reject(
                    expectedAction,
                    "Score reCAPTCHA indisponible.",
                    request
            );
        }


        double score =
                response
                        .riskAnalysis()
                        .score();


        if (
                score < minScore
        ) {

            reject(
                    expectedAction,
                    "Score reCAPTCHA insuffisant : "
                            + score,
                    request
            );
        }

    }


    /*
     * ==========================================
     * RECAPTCHA REJECTION
     * ==========================================
     */

    private void reject(
            String action,
            String reason,
            HttpServletRequest request
    ) {

        auditService.log(
                AuditEventType.RECAPTCHA_REJECTED,
                null,
                null,
                null,
                false,
                "Action="
                        + action
                        + " | "
                        + reason,
                request
        );


        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "La vérification de sécurité a échoué."
        );
    }


    /*
     * ==========================================
     * RESPONSE DTOs
     * ==========================================
     */

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