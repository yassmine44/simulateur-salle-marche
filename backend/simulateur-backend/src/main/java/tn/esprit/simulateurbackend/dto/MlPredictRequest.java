package tn.esprit.simulateurbackend.dto;

import java.util.List;

public record MlPredictRequest(
        List<MlCandleRequest> candles
) {
}
