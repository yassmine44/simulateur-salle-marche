package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;

public record GamificationCandleResponse(
        String date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {
}
