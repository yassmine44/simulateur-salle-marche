package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.GamificationEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GamificationPortfolioResponse(
        String event,
        String eventLabel,
        BigDecimal cashBalance,
        BigDecimal sharesHeld,
        BigDecimal averageEntryPrice,
        int visibleCandleCount,
        int totalCandles,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl,
        BigDecimal equity,
        BigDecimal totalReturnPct,
        boolean completed,
        Instant startedAt,
        Instant updatedAt,
        List<GamificationOrderResponse> recentOrders,
        GamificationCandleResponse currentCandle,
        WeeklyContext weeklyContext
) {

    public static GamificationPortfolioResponse of(
            GamificationEvent event,
            BigDecimal cashBalance,
            BigDecimal sharesHeld,
            BigDecimal averageEntryPrice,
            int visibleCandleCount,
            int totalCandles,
            BigDecimal currentPrice,
            BigDecimal marketValue,
            BigDecimal unrealizedPnl,
            BigDecimal equity,
            BigDecimal totalReturnPct,
            boolean completed,
            Instant startedAt,
            Instant updatedAt,
            List<GamificationOrderResponse> recentOrders,
            GamificationCandleResponse currentCandle,
            WeeklyContext weeklyContext
    ) {

        return new GamificationPortfolioResponse(
                event.getPathKey(),
                event.getDisplayName(),
                cashBalance,
                sharesHeld,
                averageEntryPrice,
                visibleCandleCount,
                totalCandles,
                currentPrice,
                marketValue,
                unrealizedPnl,
                equity,
                totalReturnPct,
                completed,
                startedAt,
                updatedAt,
                recentOrders,
                currentCandle,
                weeklyContext
        );
    }
}
