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
                "Fenêtre ML insuffisante",
                "La fenêtre de bougies visibles est encore trop courte "
                        + "pour l'inférence du modèle pré-entraîné "
                        + "(minimum 10 observations). "
                        + "Le calendrier fondamental reste actif."
        );
    }
}
