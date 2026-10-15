package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/*
 * Analyse de la performance : comment le résultat a été obtenu,
 * et ce qu'aurait donné une stratégie passive de référence.
 */
public record PerformanceResponse(

        String currency,

        TraderStats stats,

        List<Contribution> contributions,

        ExecutionQuality execution,

        Benchmark benchmark
) {

    /*
     * Statistiques sur les positions fermées (allers-retours).
     */
    public record TraderStats(

            int closedTrades,

            int wins,

            int losses,

            BigDecimal winRatePercent,

            BigDecimal averageWin,

            BigDecimal averageLoss,

            // Somme des gains / somme des pertes (null sans perte).
            BigDecimal profitFactor,

            // Résultat moyen par position fermée.
            BigDecimal expectancy,

            // Durée moyenne de détention, en minutes.
            Long averageHoldingMinutes,

            TradeHighlight bestTrade,

            TradeHighlight worstTrade
    ) {
    }

    public record TradeHighlight(

            String assetSymbol,

            BigDecimal realizedPnl,

            BigDecimal returnPercent,

            Instant closedAt
    ) {
    }

    /*
     * Part de chaque actif dans le résultat total.
     */
    public record Contribution(

            String assetSymbol,

            String assetName,

            BigDecimal realizedPnl,

            BigDecimal unrealizedPnl,

            BigDecimal totalPnl,

            // Résultat de l'actif rapporté au capital de départ.
            BigDecimal contributionPercent,

            boolean open
    ) {
    }

    /*
     * Coût d'exécution : écart entre les prix obtenus
     * et le prix milieu à l'arrivée des ordres.
     */
    public record ExecutionQuality(

            int takerFills,

            int makerFills,

            // Moyenne pondérée par les montants (points de base).
            BigDecimal averageTakerSlippageBps,

            // Coût total (> 0) ou gain (< 0) par rapport au prix milieu.
            BigDecimal totalSlippageCost
    ) {
    }

    /*
     * Référence : le capital de départ réparti à parts égales sur
     * tous les actifs au début du portefeuille, puis conservé.
     */
    public record Benchmark(

            String name,

            String description,

            Instant startedAt,

            int assetCount,

            BigDecimal currentValue,

            BigDecimal returnPercent,

            BigDecimal portfolioReturnPercent,

            // Rendement du portefeuille - rendement de la référence.
            BigDecimal outperformancePercent,

            List<PortfolioResponse.EquityPoint> history
    ) {
    }
}
