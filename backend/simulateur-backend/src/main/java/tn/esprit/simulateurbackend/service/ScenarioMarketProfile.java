package tn.esprit.simulateurbackend.service;

import tn.esprit.simulateurbackend.entity.ScenarioType;

/**
 * Coefficients appliques par cycle de cotation pour un scenario donne.
 *
 * Les valeurs sont des hypotheses de simulation (pas des observations de marche).
 * Toutes les intensites sont bornees entre 0 et 1.
 */
public record ScenarioMarketProfile(
        double priceDriftPerCycle,
        double volatilityMultiplier,
        double spreadMultiplier,
        double bidLiquidityFactor,
        double askLiquidityFactor,
        double buyFlowProbability
) {
    public static ScenarioMarketProfile normal() {
        return new ScenarioMarketProfile(0, 1, 1, 1, 1, 0.5);
    }

    /** Recuperation CRASH : liquidite uniquement, sans derive du prix. */
    public static ScenarioMarketProfile crashRecovery(double intensity) {
        double i = clamp(intensity);
        return new ScenarioMarketProfile(
                0, 1, 1 + 2 * i,
                liquidityFactor(0.80, i),
                liquidityFactor(0.30, i),
                0.5
        );
    }

    /**
     * @param progress fraction de la duree du scenario deja ecoulee, de 0 a 1.
     *                 Utilisee pour les phases du FLASH_CRASH uniquement.
     */
    public static ScenarioMarketProfile active(
            ScenarioType type,
            double intensity,
            double progress
    ) {
        if (type == null) {
            throw new IllegalArgumentException("Scenario type is required");
        }
        double i = clamp(intensity);
        double p = clamp(progress);

        return switch (type) {
            case CRASH -> new ScenarioMarketProfile(
                    -0.006 * i, 1 + 2 * i, 1 + 2 * i,
                    liquidityFactor(0.80, i),
                    liquidityFactor(0.30, i),
                    0.5
            );
            case LIQUIDITY_CRISIS -> new ScenarioMarketProfile(
                    -0.0002 * i, 1 + 0.6 * i, 1 + 4 * i,
                    liquidityFactor(0.90, i),
                    liquidityFactor(0.85, i),
                    0.5
            );
            case SELLING_PANIC -> new ScenarioMarketProfile(
                    -0.0045 * i, 1 + 1.8 * i, 1 + 1.5 * i,
                    liquidityFactor(0.90, i),
                    liquidityFactor(0.35, i),
                    0.5 - 0.35 * i
            );
            case BULL_RALLY -> new ScenarioMarketProfile(
                    0.004 * i, 1 + 1.2 * i, 1 + 0.5 * i,
                    liquidityFactor(0.20, i),
                    liquidityFactor(0.75, i),
                    0.5 + 0.35 * i
            );
            case FLASH_CRASH -> {
                if (p < 0.20) {
                    // Retrait immediat du BID et chute acceleree.
                    yield new ScenarioMarketProfile(
                            -0.02 * i, 1 + 4 * i, 1 + 5 * i,
                            liquidityFactor(0.95, i),
                            liquidityFactor(0.50, i),
                            0.5 - 0.40 * i
                    );
                }
                if (p < 0.60) {
                    // Reconstitution partielle des cours et de la profondeur.
                    yield new ScenarioMarketProfile(
                            0.007 * i, 1 + 2 * i, 1 + 3 * i,
                            liquidityFactor(0.60, i),
                            liquidityFactor(0.35, i),
                            0.5 + 0.15 * i
                    );
                }
                yield new ScenarioMarketProfile(
                        0, 1 + i, 1 + i,
                        liquidityFactor(0.25, i),
                        liquidityFactor(0.20, i),
                        0.5
                );
            }
        };
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Intensity/progress must be finite");
        }
        return Math.max(0, Math.min(1, value));
    }

    private static double liquidityFactor(double withdrawal, double intensity) {
        // Decimal arithmetic prevents values such as 100 * 0.36 becoming 35.999...
        return java.math.BigDecimal.ONE.subtract(
                java.math.BigDecimal.valueOf(withdrawal)
                        .multiply(java.math.BigDecimal.valueOf(intensity))
        ).doubleValue();
    }
}
