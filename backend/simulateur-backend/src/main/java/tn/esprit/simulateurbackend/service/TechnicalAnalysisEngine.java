package tn.esprit.simulateurbackend.service;

import tn.esprit.simulateurbackend.dto.GamificationCandleResponse;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/*
 * Analyse technique simplifiée sur les 3 à 5 dernières
 * bougies visibles du défi gamification.
 */
public final class TechnicalAnalysisEngine {

    private static final MathContext MATH =
            new MathContext(12, RoundingMode.HALF_UP);

    private static final BigDecimal BODY_DOMINANCE =
            new BigDecimal("0.72");

    private static final BigDecimal WICK_DOMINANCE =
            new BigDecimal("0.55");

    private static final BigDecimal LEVEL_TOLERANCE =
            new BigDecimal("0.012");


    private TechnicalAnalysisEngine() {
    }


    public static List<String> analyze(
            List<GamificationCandleResponse> visibleCandles
    ) {

        if (
                visibleCandles == null
                ||
                visibleCandles.size() < 3
        ) {
            return List.of(
                    "Données insuffisantes pour une analyse technique fiable."
            );
        }

        int from =
                Math.max(
                        0,
                        visibleCandles.size() - 5
                );

        List<GamificationCandleResponse> window =
                visibleCandles.subList(
                        from,
                        visibleCandles.size()
                );

        List<String> insights =
                new ArrayList<>();

        detectFairValueGaps(window, insights);
        detectLiquiditySweeps(window, insights);
        detectSupportResistance(window, insights);

        if (insights.isEmpty()) {
            insights.add(
                    "Aucun déséquilibre majeur détecté sur la fenêtre récente."
            );
        }

        return List.copyOf(insights);
    }


    private static void detectFairValueGaps(
            List<GamificationCandleResponse> window,
            List<String> insights
    ) {

        for (int i = 1; i < window.size(); i++) {

            GamificationCandleResponse previous =
                    window.get(i - 1);

            GamificationCandleResponse current =
                    window.get(i);

            BigDecimal range =
                    range(current);

            if (range.signum() <= 0) {
                continue;
            }

            BigDecimal body =
                    current.close()
                            .subtract(current.open())
                            .abs();

            BigDecimal bodyRatio =
                    body.divide(range, MATH);

            boolean impulsive =
                    bodyRatio.compareTo(BODY_DOMINANCE) >= 0;

            boolean bullishGap =
                    current.low()
                            .compareTo(previous.high()) > 0;

            boolean bearishGap =
                    current.high()
                            .compareTo(previous.low()) < 0;

            if (impulsive && (bullishGap || bearishGap)) {

                String direction =
                        current.close()
                                .compareTo(current.open()) >= 0
                                ? "haussier"
                                : "baissier";

                insights.add(
                        "Fair Value Gap / Déséquilibre : "
                                + "mouvement "
                                + direction
                                + " impulsif autour de "
                                + current.date()
                                + " (corps "
                                + percent(bodyRatio)
                                + " de la range)."
                );
            }
        }
    }


    private static void detectLiquiditySweeps(
            List<GamificationCandleResponse> window,
            List<String> insights
    ) {

        for (GamificationCandleResponse candle : window) {

            BigDecimal range = range(candle);

            if (range.signum() <= 0) {
                continue;
            }

            BigDecimal upperWick =
                    candle.high()
                            .subtract(
                                    candle.open()
                                            .max(candle.close())
                            );

            BigDecimal lowerWick =
                    candle.open()
                            .min(candle.close())
                            .subtract(candle.low());

            BigDecimal upperRatio =
                    upperWick.max(BigDecimal.ZERO)
                            .divide(range, MATH);

            BigDecimal lowerRatio =
                    lowerWick.max(BigDecimal.ZERO)
                            .divide(range, MATH);

            if (
                    lowerRatio.compareTo(WICK_DOMINANCE) >= 0
            ) {

                insights.add(
                        "Prise de liquidité : balayage des stops vendeurs "
                                + "(mèche basse) le "
                                + candle.date()
                                + "."
                );
            }

            if (
                    upperRatio.compareTo(WICK_DOMINANCE) >= 0
            ) {

                insights.add(
                        "Prise de liquidité : balayage des stops acheteurs "
                                + "(mèche haute) le "
                                + candle.date()
                                + "."
                );
            }
        }
    }


    private static void detectSupportResistance(
            List<GamificationCandleResponse> window,
            List<String> insights
    ) {

        BigDecimal[] levels = {
                window.get(window.size() - 1).close(),
                window.get(window.size() - 1).low(),
                window.get(window.size() - 1).high()
        };

        String[] labels = {
                "clôture",
                "plus bas",
                "plus haut"
        };

        for (int i = 0; i < levels.length; i++) {

            BigDecimal level = levels[i];
            int touches = 0;

            for (GamificationCandleResponse candle : window) {

                if (
                        near(candle.low(), level)
                        ||
                        near(candle.high(), level)
                        ||
                        near(candle.close(), level)
                        ||
                        near(candle.open(), level)
                ) {
                    touches++;
                }
            }

            if (touches >= 3) {

                insights.add(
                        "Support/Résistance : zone autour de "
                                + money(level)
                                + " testée "
                                + touches
                                + " fois (référence "
                                + labels[i]
                                + ")."
                );
            }
        }
    }


    private static boolean near(
            BigDecimal value,
            BigDecimal level
    ) {

        if (level.signum() == 0) {
            return value.abs()
                    .compareTo(new BigDecimal("0.01")) < 0;
        }

        BigDecimal distance =
                value.subtract(level)
                        .abs()
                        .divide(level.abs(), MATH);

        return distance.compareTo(LEVEL_TOLERANCE) <= 0;
    }


    private static BigDecimal range(
            GamificationCandleResponse candle
    ) {

        return candle.high()
                .subtract(candle.low())
                .max(BigDecimal.ZERO);
    }


    private static String percent(
            BigDecimal ratio
    ) {

        return ratio
                .multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                + "%";
    }


    private static String money(
            BigDecimal value
    ) {

        return value
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }
}
