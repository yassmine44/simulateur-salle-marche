package tn.esprit.simulateurbackend.dto;

import java.util.List;

public record MarketOverviewResponse(
        MarketSummaryResponse summary,
        List<MarketTradeResponse> recentTrades,
        String candleInterval,
        List<CandleResponse> candles
) {
}
