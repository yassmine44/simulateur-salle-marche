package tn.esprit.simulateurbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import tn.esprit.simulateurbackend.config.MlServiceProperties;
import tn.esprit.simulateurbackend.dto.GamificationCandleResponse;
import tn.esprit.simulateurbackend.dto.MlCandleRequest;
import tn.esprit.simulateurbackend.dto.MlPredictRequest;
import tn.esprit.simulateurbackend.dto.MlPredictResponse;

import java.util.List;
import java.util.Optional;

/**
 * HTTP client toward the Python Random Forest microservice.
 * Failures are swallowed as Optional.empty() so the trading loop never breaks.
 */
@Service
public class MlPredictionClient {

    private static final Logger log =
            LoggerFactory.getLogger(MlPredictionClient.class);

    private final RestTemplate mlRestTemplate;

    private final MlServiceProperties properties;


    public MlPredictionClient(
            RestTemplate mlRestTemplate,
            MlServiceProperties properties
    ) {
        this.mlRestTemplate = mlRestTemplate;
        this.properties = properties;
    }


    public Optional<MlPredictResponse> predict(
            List<GamificationCandleResponse> candles
    ) {

        if (
                !properties.isEnabled()
                ||
                candles == null
                ||
                candles.isEmpty()
        ) {
            return Optional.empty();
        }

        List<MlCandleRequest> payload =
                candles.stream()
                        .map(MlCandleRequest::from)
                        .toList();

        try {

            MlPredictResponse response =
                    mlRestTemplate.postForObject(
                            properties.predictUrl(),
                            new MlPredictRequest(payload),
                            MlPredictResponse.class
                    );

            return Optional.ofNullable(response);

        } catch (RestClientException ex) {

            log.warn(
                    "ML microservice unavailable at {}: {}",
                    properties.predictUrl(),
                    ex.getMessage()
            );

            return Optional.empty();
        }
    }
}
