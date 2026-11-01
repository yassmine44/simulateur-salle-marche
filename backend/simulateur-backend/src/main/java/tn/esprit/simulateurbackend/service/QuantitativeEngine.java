package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Service;

import tn.esprit.simulateurbackend.dto.AiAnalysisReport;
import tn.esprit.simulateurbackend.dto.GamificationCandleResponse;
import tn.esprit.simulateurbackend.dto.HistoricalEvent;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/*
 * Moteur quantitatif offline : RSI 14, SMA 20, volatilité,
 * score fondamental et simulation de confiance ML.
 */
@Service
public class QuantitativeEngine {

    public static final int RSI_PERIOD = 14;

    public static final int SMA_PERIOD = 20;

    public static final int VOL_PERIOD = 20;

    private static final MathContext MATH =
            new MathContext(16, RoundingMode.HALF_UP);


    public AiAnalysisReport analyze(
            List<GamificationCandleResponse> visibleCandles,
            List<HistoricalEvent> weekEvents
    ) {

        String fundamentalSentiment =
                scoreFundamentalSentiment(weekEvents);

        if (
                visibleCandles == null
                ||
                visibleCandles.size() <= RSI_PERIOD
        ) {
            return AiAnalysisReport.learning(
                    fundamentalSentiment
            );
        }

        List<BigDecimal> closes =
                visibleCandles.stream()
                        .map(GamificationCandleResponse::close)
                        .toList();

        Double rsi =
                calculateRsi(closes, RSI_PERIOD);

        Double sma =
                closes.size() >= SMA_PERIOD
                        ? calculateSma(closes, SMA_PERIOD)
                        : null;

        Double volatility =
                closes.size() >= Math.min(VOL_PERIOD, 5)
                        ? calculateVolatility(
                                closes,
                                Math.min(VOL_PERIOD, closes.size())
                        )
                        : null;

        BigDecimal lastClose =
                closes.get(closes.size() - 1);

        String trendIndicator =
                resolveTrend(lastClose, sma, rsi);

        int technicalScore =
                scoreTechnical(rsi, trendIndicator, volatility);

        int fundamentalScore =
                fundamentalNumericScore(
                        weekEvents,
                        fundamentalSentiment
                );

        int combined =
                Math.round(
                        (technicalScore * 0.55f)
                        +
                        (fundamentalScore * 0.45f)
                );

        combined = clamp(combined, 0, 100);

        String predictionDirection =
                combined >= 50 ? "Baisse" : "Hausse";

        // Confiance = distance au point neutre 50.
        int mlConfidenceScore =
                clamp(Math.abs(combined - 50) * 2, 35, 95);

        if (
                volatility != null
                &&
                volatility >= 4.5
        ) {
            mlConfidenceScore =
                    clamp(mlConfidenceScore + 5, 35, 97);
        }

        String mlConfidenceLabel =
                mlConfidenceScore
                        + "% Probabilité de "
                        + predictionDirection;

        String detailedAnalysis =
                buildDetailedAnalysis(
                        lastClose,
                        rsi,
                        sma,
                        volatility,
                        trendIndicator,
                        fundamentalSentiment,
                        weekEvents,
                        mlConfidenceLabel,
                        predictionDirection
                );

        return new AiAnalysisReport(
                round1(rsi),
                sma == null ? null : round2(sma),
                volatility == null ? null : round2(volatility),
                false,
                trendIndicator,
                fundamentalSentiment,
                mlConfidenceScore,
                predictionDirection,
                mlConfidenceLabel,
                detailedAnalysis
        );
    }


    /*
     * RSI Wilder classique :
     * 1) moyennes simples des 14 premiers gains/pertes
     * 2) lissage : (prev * 13 + current) / 14
     */
    Double calculateRsi(
            List<BigDecimal> closes,
            int period
    ) {

        if (closes.size() <= period) {
            return null;
        }

        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;

        for (int i = 1; i <= period; i++) {

            BigDecimal change =
                    closes.get(i)
                            .subtract(closes.get(i - 1));

            if (change.signum() >= 0) {
                gainSum = gainSum.add(change);
            } else {
                lossSum = lossSum.add(change.abs());
            }
        }

        BigDecimal avgGain =
                gainSum.divide(
                        BigDecimal.valueOf(period),
                        MATH
                );

        BigDecimal avgLoss =
                lossSum.divide(
                        BigDecimal.valueOf(period),
                        MATH
                );

        for (int i = period + 1; i < closes.size(); i++) {

            BigDecimal change =
                    closes.get(i)
                            .subtract(closes.get(i - 1));

            BigDecimal gain =
                    change.signum() > 0
                            ? change
                            : BigDecimal.ZERO;

            BigDecimal loss =
                    change.signum() < 0
                            ? change.abs()
                            : BigDecimal.ZERO;

            avgGain =
                    avgGain
                            .multiply(BigDecimal.valueOf(period - 1L))
                            .add(gain)
                            .divide(
                                    BigDecimal.valueOf(period),
                                    MATH
                            );

            avgLoss =
                    avgLoss
                            .multiply(BigDecimal.valueOf(period - 1L))
                            .add(loss)
                            .divide(
                                    BigDecimal.valueOf(period),
                                    MATH
                            );
        }

        if (avgLoss.signum() == 0) {
            return 100.0;
        }

        BigDecimal rs =
                avgGain.divide(avgLoss, MATH);

        BigDecimal rsi =
                BigDecimal.valueOf(100)
                        .subtract(
                                BigDecimal.valueOf(100)
                                        .divide(
                                                BigDecimal.ONE.add(rs),
                                                MATH
                                        )
                        );

        return rsi.doubleValue();
    }


    Double calculateSma(
            List<BigDecimal> closes,
            int period
    ) {

        if (closes.size() < period) {
            return null;
        }

        BigDecimal sum = BigDecimal.ZERO;

        for (
                int i = closes.size() - period;
                i < closes.size();
                i++
        ) {
            sum = sum.add(closes.get(i));
        }

        return sum
                .divide(BigDecimal.valueOf(period), MATH)
                .doubleValue();
    }


    Double calculateVolatility(
            List<BigDecimal> closes,
            int period
    ) {

        if (closes.size() < 2) {
            return null;
        }

        int use =
                Math.min(period, closes.size());

        List<BigDecimal> window =
                closes.subList(
                        closes.size() - use,
                        closes.size()
                );

        BigDecimal mean =
                BigDecimal.ZERO;

        for (BigDecimal value : window) {
            mean = mean.add(value);
        }

        mean =
                mean.divide(
                        BigDecimal.valueOf(window.size()),
                        MATH
                );

        BigDecimal varianceSum =
                BigDecimal.ZERO;

        for (BigDecimal value : window) {

            BigDecimal delta =
                    value.subtract(mean);

            varianceSum =
                    varianceSum.add(
                            delta.multiply(delta, MATH)
                    );
        }

        BigDecimal variance =
                varianceSum.divide(
                        BigDecimal.valueOf(window.size()),
                        MATH
                );

        double stdev =
                Math.sqrt(variance.doubleValue());

        if (mean.signum() == 0) {
            return stdev;
        }

        // Volatilité relative en % du prix moyen.
        return (stdev / mean.doubleValue()) * 100.0;
    }


    private String resolveTrend(
            BigDecimal lastClose,
            Double sma,
            Double rsi
    ) {

        int score = 0;

        if (sma != null) {

            if (
                    lastClose.compareTo(
                            BigDecimal.valueOf(sma)
                    ) > 0
            ) {
                score += 1;
            } else if (
                    lastClose.compareTo(
                            BigDecimal.valueOf(sma)
                    ) < 0
            ) {
                score -= 1;
            }
        }

        if (rsi != null) {

            if (rsi >= 55) {
                score += 1;
            } else if (rsi <= 45) {
                score -= 1;
            }
        }

        if (score > 0) {
            return "Bullish";
        }

        if (score < 0) {
            return "Bearish";
        }

        return "Neutral";
    }


    private int scoreTechnical(
            Double rsi,
            String trendIndicator,
            Double volatility
    ) {

        // 0 = très haussier, 100 = très baissier.
        int score = 50;

        if (rsi != null) {

            if (rsi >= 70) {
                score += 22;
            } else if (rsi >= 60) {
                score += 10;
            } else if (rsi <= 30) {
                score -= 22;
            } else if (rsi <= 40) {
                score -= 10;
            }
        }

        if ("Bearish".equals(trendIndicator)) {
            score += 12;
        } else if ("Bullish".equals(trendIndicator)) {
            score -= 12;
        }

        if (volatility != null) {

            if (volatility >= 5.0) {
                score += 8;
            } else if (volatility <= 1.5) {
                score -= 4;
            }
        }

        return clamp(score, 0, 100);
    }


    String scoreFundamentalSentiment(
            List<HistoricalEvent> weekEvents
    ) {

        if (
                weekEvents == null
                ||
                weekEvents.isEmpty()
        ) {
            return "Neutre — aucun catalyseur macro majeur.";
        }

        int bias = 0;

        List<String> labels =
                new ArrayList<>();

        for (HistoricalEvent event : weekEvents) {

            int signed =
                    eventBias(event);

            bias += signed;

            labels.add(event.description());
        }

        String joined =
                String.join(", ", labels);

        if (bias <= -3) {
            return "Extrêmement baissier — " + joined;
        }

        if (bias < 0) {
            return "Baissier — " + joined;
        }

        if (bias >= 3) {
            return "Fortement haussier — " + joined;
        }

        if (bias > 0) {
            return "Haussier — " + joined;
        }

        return "Mitigé — " + joined;
    }


    private int fundamentalNumericScore(
            List<HistoricalEvent> weekEvents,
            String sentiment
    ) {

        if (
                weekEvents == null
                ||
                weekEvents.isEmpty()
        ) {
            return 50;
        }

        int score = 50;

        for (HistoricalEvent event : weekEvents) {

            int bias = eventBias(event);

            // bias négatif = baissier => score technique baissier ↑
            score -= bias * 8;
        }

        String lower =
                sentiment.toLowerCase(Locale.ROOT);

        if (lower.contains("extrêmement baissier")) {
            score += 10;
        } else if (lower.contains("fortement haussier")) {
            score -= 10;
        }

        return clamp(score, 0, 100);
    }


    /*
     * Bias signé :
     *  > 0 haussier (Fed cut, QE, turnaround…)
     *  < 0 baissier (faillite, invasion, CPI chaud…)
     * Impact amplifie l'amplitude.
     */
    private int eventBias(
            HistoricalEvent event
    ) {

        String text =
                (event.description() + " " + event.date())
                        .toLowerCase(Locale.ROOT);

        int direction = 0;

        if (
                containsAny(
                        text,
                        "rate cut",
                        "emergency cut",
                        "zero interest",
                        "unlimited qe",
                        "qe",
                        "bailout",
                        "tarp",
                        "turnaround",
                        "bottom"
                )
        ) {
            direction = 1;
        } else if (
                containsAny(
                        text,
                        "bankruptcy",
                        "collapse",
                        "failure",
                        "invades",
                        "oil shock",
                        "cpi",
                        "rate hike",
                        "bps hike",
                        "hawkish",
                        "black monday",
                        "black thursday",
                        "pandemic",
                        "crack",
                        "worst week",
                        "market peak"
                )
        ) {
            direction = -1;
        }

        int magnitude =
                switch (event.impact()) {

                    case HIGH -> 3;

                    case MEDIUM -> 2;

                    case LOW -> 1;
                };

        // Market Peak (LOW) reste légèrement baissier/contrarian.
        if (text.contains("market peak")) {
            direction = -1;
            magnitude = Math.max(magnitude, 1);
        }

        return direction * magnitude;
    }


    private String buildDetailedAnalysis(
            BigDecimal lastClose,
            Double rsi,
            Double sma,
            Double volatility,
            String trendIndicator,
            String fundamentalSentiment,
            List<HistoricalEvent> weekEvents,
            String mlConfidenceLabel,
            String predictionDirection
    ) {

        StringBuilder text =
                new StringBuilder();

        text.append("Desk quant — semaine active : le spot S&P 500 ")
                .append("clôture à ")
                .append(round2(lastClose.doubleValue()))
                .append(". ");

        if (rsi != null) {

            text.append("Le RSI(14) s'établit à ")
                    .append(round1(rsi));

            if (rsi >= 70) {
                text.append(" (zone de surachat). ");
            } else if (rsi <= 30) {
                text.append(" (zone de survente). ");
            } else {
                text.append(" (zone intermédiaire). ");
            }
        }

        if (sma != null) {

            text.append("La SMA(20) vaut ")
                    .append(round2(sma))
                    .append(", ce qui place le régime de tendance en mode ")
                    .append(trendIndicator)
                    .append(". ");
        } else {

            text.append("La tendance courte est classée ")
                    .append(trendIndicator)
                    .append(". ");
        }

        if (volatility != null) {

            text.append("La volatilité relative atteint ")
                    .append(round2(volatility))
                    .append("%");

            if (volatility >= 4.5) {
                text.append(", signal de panique / stress élevé. ");
            } else {
                text.append(". ");
            }
        }

        text.append("Lecture fondamentale : ")
                .append(fundamentalSentiment)
                .append(". ");

        if (
                weekEvents != null
                &&
                !weekEvents.isEmpty()
        ) {

            text.append("Les catalyseurs de la semaine (")
                    .append(weekEvents.size())
                    .append(") sont croisés avec le score technique ")
                    .append("pour produire une inférence ML simulée. ");
        } else {

            text.append("Sans catalyseur macro dominant, ")
                    .append("le modèle s'appuie surtout sur le prix. ");
        }

        text.append("Verdict algorithmique : ")
                .append(mlConfidenceLabel)
                .append(" — biais directionnel ")
                .append(predictionDirection)
                .append(".");

        return text.toString();
    }


    private static boolean containsAny(
            String text,
            String... needles
    ) {

        for (String needle : needles) {

            if (text.contains(needle)) {
                return true;
            }
        }

        return false;
    }


    private static int clamp(
            int value,
            int min,
            int max
    ) {

        return Math.max(min, Math.min(max, value));
    }


    private static Double round1(
            Double value
    ) {

        if (value == null) {
            return null;
        }

        return BigDecimal.valueOf(value)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }


    private static Double round2(
            Double value
    ) {

        if (value == null) {
            return null;
        }

        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
