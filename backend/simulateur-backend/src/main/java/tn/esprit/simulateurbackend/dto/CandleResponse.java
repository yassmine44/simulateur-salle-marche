package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CandleResponse(
        Instant time,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume
) {
}
