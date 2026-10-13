package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;


public record OrderBookTemporalMetricsResponse(

        String assetSymbol,

        BigDecimal currentMidPrice,

        BigDecimal volatility60s,

        BigDecimal volatility60sPercent,

        BigDecimal momentum30s,

        BigDecimal momentum30sPercent,

        int observationCount,

        Instant generatedAt

) {
}