package tn.esprit.simulateurbackend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import tn.esprit.simulateurbackend.config.ActuarialServiceProperties;
import tn.esprit.simulateurbackend.dto.actuarial.ChainLadderRequest;
import tn.esprit.simulateurbackend.dto.actuarial.ComparisonRequest;
import tn.esprit.simulateurbackend.dto.actuarial.ComparisonResponse;
import tn.esprit.simulateurbackend.dto.actuarial.PricingAssumptionsDto;
import tn.esprit.simulateurbackend.dto.actuarial.QuoteRequest;
import tn.esprit.simulateurbackend.dto.actuarial.QuoteResponse;

/**
 * Client HTTP du moteur actuariel Python (ml-service, /api/actuarial/*).
 *
 * <p>Traduit les erreurs techniques en statuts HTTP compréhensibles :
 * <ul>
 *   <li>profil refusé par le moteur (422) → 400 avec le détail des champs ;</li>
 *   <li>modèles non entraînés (503) → 503 avec la consigne du moteur ;</li>
 *   <li>moteur injoignable ou trop lent → 503 ;</li>
 *   <li>toute autre réponse inattendue → 502.</li>
 * </ul>
 */
@Component
public class ActuarialPricingClient {

    private static final Logger log = LoggerFactory.getLogger(ActuarialPricingClient.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient restClient;

    @Autowired
    public ActuarialPricingClient(RestClient.Builder builder, ActuarialServiceProperties properties) {
        this(createRestClient(builder, properties));
    }

    /** Pour les tests unitaires. */
    ActuarialPricingClient(RestClient restClient) {
        this.restClient = restClient;
    }

    private static RestClient createRestClient(RestClient.Builder builder, ActuarialServiceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        // clone() : le builder est un singleton partagé, il ne doit pas être modifié.
        return builder.clone()
                .baseUrl(properties.rootUrl() + "/api/actuarial")
                .requestFactory(requestFactory)
                .build();
    }

    public QuoteResponse quote(QuoteRequest request) {
        QuoteRequest outbound = withInputAssumptionsOnly(request);
        return call(() -> restClient.post()
                .uri("/quote")
                .contentType(MediaType.APPLICATION_JSON)
                .body(outbound)
                .retrieve()
                .body(QuoteResponse.class));
    }

    public ComparisonResponse compare(ComparisonRequest request) {
        return call(() -> restClient.post()
                .uri("/compare")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ComparisonResponse.class));
    }

    /** Provisions Chain-Ladder et erreurs standard de Mack d'un triangle utilisateur. */
    public JsonNode chainLadder(ChainLadderRequest request) {
        return call(() -> restClient.post()
                .uri("/reserving/chain-ladder")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class));
    }

    public JsonNode status() {
        return get("/status");
    }

    public JsonNode metadata() {
        return get("/metadata");
    }

    public JsonNode report(String name) {
        return get("/reports/" + name);
    }

    public JsonNode modelCards() {
        return get("/model-cards");
    }

    /**
     * Le moteur refuse les champs inconnus : on ne transmet que les trois
     * hypothèses d'entrée ({@code commercialFactor} est un champ de réponse).
     */
    static QuoteRequest withInputAssumptionsOnly(QuoteRequest request) {
        PricingAssumptionsDto assumptions = request.assumptions();
        if (assumptions == null || assumptions.commercialFactor() == null) {
            return request;
        }
        return new QuoteRequest(
                request.profile(),
                request.years(),
                PricingAssumptionsDto.of(
                        assumptions.expenseRatio(), assumptions.profitRatio(), assumptions.safetyLoading()
                ),
                request.explain(),
                request.top()
        );
    }

    private JsonNode get(String path) {
        return call(() -> restClient.get().uri(path).retrieve().body(JsonNode.class));
    }

    private <T> T call(Supplier<T> exchange) {
        T body;
        try {
            body = exchange.get();
        } catch (HttpClientErrorException error) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Requête refusée par le moteur actuariel : " + detail(error.getResponseBodyAsString()),
                    error
            );
        } catch (HttpServerErrorException error) {
            if (error.getStatusCode().value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Moteur actuariel indisponible : " + detail(error.getResponseBodyAsString()),
                        error
                );
            }
            log.error("Actuarial engine error {}: {}", error.getStatusCode(), error.getResponseBodyAsString());
            throw badGateway(error);
        } catch (ResourceAccessException error) {
            log.warn("Actuarial engine unreachable: {}", error.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Le moteur actuariel (ml-service) est injoignable. Vérifiez qu'il est démarré.",
                    error
            );
        } catch (RestClientException error) {
            log.error("Unexpected actuarial engine response: {}", error.getMessage());
            throw badGateway(error);
        }
        if (body == null) {
            throw badGateway(null);
        }
        return body;
    }

    private static ResponseStatusException badGateway(Exception cause) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "Réponse inattendue du moteur actuariel.", cause
        );
    }

    /**
     * Extrait un message lisible du corps d'erreur FastAPI :
     * {@code {"detail": "..."}} ou {@code {"detail": [{"loc": [...], "msg": "..."}]}}.
     */
    static String detail(String body) {
        try {
            JsonNode detail = JSON.readTree(body).path("detail");
            if (detail.isString()) {
                return detail.asString();
            }
            if (detail.isArray()) {
                List<String> messages = new ArrayList<>();
                for (JsonNode item : detail) {
                    List<String> location = new ArrayList<>();
                    for (JsonNode part : item.path("loc")) {
                        if (!"body".equals(part.asString())) {
                            location.add(part.asString());
                        }
                    }
                    messages.add(String.join(".", location) + " : " + item.path("msg").asString());
                }
                return String.join(" ; ", messages);
            }
        } catch (RuntimeException ignored) {
            // corps non JSON : on renvoie le texte brut ci-dessous
        }
        return body == null || body.isBlank() ? "aucun détail" : body;
    }
}
