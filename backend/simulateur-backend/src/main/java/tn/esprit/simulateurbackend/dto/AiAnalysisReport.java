package tn.esprit.simulateurbackend.dto;

public record AiAnalysisReport(
        Double rsiValue,
        Double smaValue,
        Double volatility,
        boolean learningPhase,
        String trendIndicator,
        String fundamentalSentiment,
        Integer mlConfidenceScore,
        String predictionDirection,
        String mlConfidenceLabel,
        String detailedAnalysis
) {

    public static AiAnalysisReport learning(
            String fundamentalSentiment
    ) {

        return new AiAnalysisReport(
                null,
                null,
                null,
                true,
                "Neutral",
                fundamentalSentiment,
                null,
                "—",
                "En phase d'apprentissage",
                "En phase d'apprentissage : l'historique visible "
                        + "est encore trop court pour calculer un RSI 14 "
                        + "et une SMA 20 robustes. Le modèle quantitatif "
                        + "accumule les bougies et affinera le score ML "
                        + "dès que la profondeur de marché sera suffisante. "
                        + "Le calendrier fondamental reste néanmoins actif "
                        + "pour contextualiser la semaine courante."
        );
    }
}
