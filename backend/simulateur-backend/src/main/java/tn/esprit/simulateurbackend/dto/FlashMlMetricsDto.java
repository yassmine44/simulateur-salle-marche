package tn.esprit.simulateurbackend.dto;

/**
 * Hold-out metrics from the Python Random Forest microservice.
 * When {@code available} is false, rates are null and the UI must not
 * invent dummy percentages.
 */
public record FlashMlMetricsDto(
        Double historicalPredictionSuccessRate,
        Double crashDetectionRating,
        Double rallyPredictionRating,
        String explainer,
        boolean available
) {
    public static final String DEFAULT_EXPLAINER =
            "Hold-out metrics from the pre-trained ML Prediction Model on 2018–2026 historical data. "
                    + "Overall accuracy, crash recall (Down), and rally precision (Up).";

    public static final String UNAVAILABLE_EXPLAINER =
            "ML Prediction Model metrics are unavailable. Start the Python ml-service "
                    + "on localhost:5000 and retrain if artifacts are missing.";

    public static FlashMlMetricsDto unavailable() {
        return new FlashMlMetricsDto(
                null,
                null,
                null,
                UNAVAILABLE_EXPLAINER,
                false
        );
    }
}
