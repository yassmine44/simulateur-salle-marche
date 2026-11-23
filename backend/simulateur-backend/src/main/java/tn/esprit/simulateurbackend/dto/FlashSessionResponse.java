package tn.esprit.simulateurbackend.dto;

import java.util.List;

/**
 * Multiplayer sync payload. Candles are usually loaded once via REST;
 * ticks carry the authoritative visibleCandleCount.
 */
public record FlashSessionResponse(
        String sessionId,
        String symbol,
        String status,
        long serverNow,
        long startAtEpochMs,
        long intervalMs,
        int visibleCandleCount,
        int totalCandles,
        int initialVisibleCandles,
        List<FlashCandleDto> candles,
        FlashCandleDto currentCandle,
        FlashMlMetricsDto mlMetrics,
        String messageType
) {
}
