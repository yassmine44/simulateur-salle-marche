package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;

public record MarketSummaryResponse(
        String assetSymbol,
        BigDecimal lastPrice,
        BigDecimal changePercent,
        BigDecimal high24h,
        BigDecimal low24h,
        BigDecimal volume24h,
        long tradeCount24h
) {
}
