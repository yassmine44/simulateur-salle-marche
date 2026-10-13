package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderBookMetricsResponse(

        String assetSymbol,

        BigDecimal bestBid,

        BigDecimal bestAsk,

        BigDecimal midPrice,

        BigDecimal spread,

        BigDecimal relativeSpread,

        BigDecimal relativeSpreadPercent,

        BigDecimal totalBidVolume,

        BigDecimal totalAskVolume,

        BigDecimal orderBookImbalance,

        BigDecimal weightedBidDepth,

        BigDecimal weightedAskDepth,

        Instant generatedAt

) {
}