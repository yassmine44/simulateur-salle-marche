package tn.esprit.simulateurbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tn.esprit.simulateurbackend.dto.FlashMlMetricsDto;

import java.util.Map;

/**
 * Pulls real hold-out metrics from the Python ML microservice.
 * Never invents 42/40/44 dummy scores when the service is offline.
 */
@Component
public class FlashMlMetricsClient {

    private static final Logger log =
            LoggerFactory.getLogger(FlashMlMetricsClient.class);

    private final boolean enabled;
    private final RestClient restClient;

    public FlashMlMetricsClient(
            RestClient.Builder builder,
            @Value("${gamification.ml-service.enabled:true}") boolean enabled,
            @Value("${gamification.ml-service.base-url:http://localhost:5000}")
            String baseUrl
    ) {
        this.enabled = enabled;
        this.restClient = builder.baseUrl(baseUrl).build();
    }


    public FlashMlMetricsDto fetchMetrics() {
        if (!enabled) {
            return FlashMlMetricsDto.unavailable();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.get()
                    .uri("/api/metrics")
                    .retrieve()
                    .body(Map.class);

            if (body == null || body.isEmpty()) {
                return FlashMlMetricsDto.unavailable();
            }

            Double accuracy = firstDouble(
                    body.get("historicalPredictionSuccessRate"),
                    body.get("holdout_accuracy"),
                    body.get("overall_accuracy")
            );
            Double crash = firstDouble(
                    body.get("crashDetectionRating"),
                    body.get("crash_detection_rating"),
                    nested(body, "Down", "recall")
            );
            // Rally = Up-trend precision (as required for the academic panel).
            Double rally = firstDouble(
                    body.get("rallyPredictionRating"),
                    body.get("rally_prediction_rating"),
                    nested(body, "Up", "precision"),
                    nested(body, "Up", "recall")
            );

            if (accuracy == null && crash == null && rally == null) {
                return FlashMlMetricsDto.unavailable();
            }

            String explainer = asString(body.get("explainer"));
            if (explainer == null || explainer.isBlank()) {
                explainer = FlashMlMetricsDto.DEFAULT_EXPLAINER;
            }

            return new FlashMlMetricsDto(
                    roundOrNull(accuracy),
                    roundOrNull(crash),
                    roundOrNull(rally),
                    explainer,
                    true
            );
        } catch (RuntimeException exception) {
            log.warn("ML metrics unavailable: {}", exception.getMessage());
            return FlashMlMetricsDto.unavailable();
        }
    }


    private static Object nested(
            Map<String, Object> body,
            String className,
            String field
    ) {
        Object report = body.get("classification_report");
        if (!(report instanceof Map<?, ?> map)) {
            return null;
        }
        Object clazz = map.get(className);
        if (!(clazz instanceof Map<?, ?> classMap)) {
            return null;
        }
        return classMap.get(field);
    }


    private static Double firstDouble(Object... values) {
        for (Object value : values) {
            Double parsed = asDouble(value);
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }


    private static Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }


    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }


    private static Double roundOrNull(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return null;
        }
        return Math.round(value * 1000.0) / 1000.0;
    }
}
