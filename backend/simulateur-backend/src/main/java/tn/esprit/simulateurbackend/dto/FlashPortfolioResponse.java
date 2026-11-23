package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;
import java.util.List;

public record FlashPortfolioResponse(
        String sessionId,
        String symbol,
        BigDecimal cashBalance,
        BigDecimal sharesHeld,
        BigDecimal averageEntryPrice,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal equity,
        BigDecimal unrealizedPnl,
        BigDecimal totalReturnPct,
        int visibleCandleCount,
        int totalCandles,
        boolean completed,
        FlashCandleDto currentCandle,
        List<FlashOrderDto> recentOrders,
        FlashMlMetricsDto mlMetrics
) {
}
