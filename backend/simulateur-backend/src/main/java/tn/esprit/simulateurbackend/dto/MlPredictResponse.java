package tn.esprit.simulateurbackend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MlPredictResponse(
        String prediction,
        Double probability,
        @JsonProperty("feature_importance")
        Map<String, Double> featureImportance,
        @JsonProperty("live_features")
        Map<String, Double> liveFeatures,
        String model
) {
}
