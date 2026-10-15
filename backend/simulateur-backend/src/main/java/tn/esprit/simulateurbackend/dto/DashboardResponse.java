package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/*
 * Tableau de bord de l'utilisateur connecté : tout ce que la page
 * affiche, en un seul appel, calculé à partir de ses données.
 */
public record DashboardResponse(

        String currency,

        // Phrase de synthèse, écrite à partir des chiffres du jour.
        Headline headline,

        PortfolioResponse portfolio,

        PerformanceResponse performance,

        // Points qui méritent une action, du plus urgent au moins urgent.
        List<Insight> insights,

        List<WatchedAsset> watchlist,

        List<Protection> protections,

        Habits habits,

        List<ActivityEvent> activity,

        Instant generatedAt
) {

    /*
     * tone : POSITIVE, NEGATIVE ou NEUTRAL (couleur de la phrase).
     */
    public record Headline(

            String text,

            String tone,

            // Variation de la valeur depuis minuit.
            BigDecimal dayChange,

            BigDecimal dayChangePercent
    ) {
    }

    /*
     * severity : CRITICAL, WARNING ou INFO.
     */
    public record Insight(

            String severity,

            String category,

            String title,

            String message,

            String assetSymbol,

            Long orderId
    ) {
    }

    public record WatchedAsset(

            String assetSymbol,

            String assetName,

            BigDecimal lastPrice,

            BigDecimal changePercent,

            // Clôtures récentes (intervalles de 15 minutes), pour la mini-courbe.
            List<BigDecimal> sparkline,

            BigDecimal positionValue,

            BigDecimal unrealizedPnl
    ) {
    }

    /*
     * status : PROTECTED (toute la position a un stop),
     *          PARTIAL (une partie seulement) ou UNPROTECTED.
     */
    public record Protection(

            String assetSymbol,

            BigDecimal quantity,

            BigDecimal lastPrice,

            String status,

            BigDecimal protectedQuantity,

            // Stop le plus proche sous le cours.
            BigDecimal stopPrice,

            BigDecimal stopDistancePercent,

            boolean trailing,

            // Objectif de vente au-dessus du cours, s'il existe.
            BigDecimal takeProfitPrice,

            BigDecimal takeProfitDistancePercent,

            // Ce que la position perdrait si le stop était touché maintenant.
            BigDecimal lossAtStop,

            BigDecimal weightPercent
    ) {
    }

    public record Habits(

            int ordersPlaced,

            BigDecimal limitOrderPercent,

            BigDecimal marketOrderPercent,

            BigDecimal conditionalOrderPercent,

            // Part des achats passés avec stop-loss / take-profit attachés.
            BigDecimal protectedEntryPercent,

            Integer closedTrades,

            BigDecimal winRatePercent,

            BigDecimal profitFactor,

            BigDecimal averageTakerSlippageBps,

            // Conseils tirés de ces chiffres.
            List<String> advice
    ) {
    }

    /*
     * type : PLACED, FILLED, TRIGGERED, CANCELLED ou REJECTED.
     * tone : POSITIVE, NEGATIVE ou NEUTRAL.
     */
    public record ActivityEvent(

            Instant time,

            String type,

            String title,

            String detail,

            String assetSymbol,

            Long orderId,

            String tone
    ) {
    }
}
