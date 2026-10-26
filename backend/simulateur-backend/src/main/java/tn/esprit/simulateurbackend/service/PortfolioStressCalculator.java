
package tn.esprit.simulateurbackend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class PortfolioStressCalculator {

    private static final BigDecimal HUNDRED =
            BigDecimal.valueOf(100);

    private PortfolioStressCalculator() {
    }

    public record Result(
            String symbol,
            BigDecimal quantity,
            BigDecimal normalPrice,
            BigDecimal stressedPrice,
            BigDecimal normalPositionValue,
            BigDecimal stressedPositionValue,
            BigDecimal pnlImpact,
            BigDecimal normalPortfolioValue,
            BigDecimal stressedPortfolioValue,
            BigDecimal portfolioImpactPercent
    ) {
    }

    public static Result calculate(
            String symbol,
            BigDecimal quantity,
            BigDecimal normalPrice,
            BigDecimal stressedPrice,
            BigDecimal normalPortfolioValue
    ) {

        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(normalPrice, "normalPrice");
        Objects.requireNonNull(stressedPrice, "stressedPrice");
        Objects.requireNonNull(
                normalPortfolioValue,
                "normalPortfolioValue"
        );

        if (symbol.isBlank()) {
            throw new IllegalArgumentException(
                    "Le symbole ne peut pas être vide."
            );
        }

        if (normalPrice.signum() <= 0
                || stressedPrice.signum() <= 0) {

            throw new IllegalArgumentException(
                    "Les prix doivent être strictement positifs."
            );
        }

        BigDecimal normalPositionValue =
                money(quantity.multiply(normalPrice));

        BigDecimal stressedPositionValue =
                money(quantity.multiply(stressedPrice));

        BigDecimal pnlImpact =
                stressedPositionValue.subtract(normalPositionValue);

        BigDecimal stressedPortfolioValue =
                money(normalPortfolioValue.add(pnlImpact));

        BigDecimal impactPercent =
                normalPortfolioValue.signum() > 0
                        ? pnlImpact
                        .multiply(HUNDRED)
                        .divide(
                                normalPortfolioValue,
                                2,
                                RoundingMode.HALF_UP
                        )
                        : null;

        return new Result(
                symbol,
                quantity,
                normalPrice,
                stressedPrice,
                normalPositionValue,
                stressedPositionValue,
                pnlImpact,
                money(normalPortfolioValue),
                stressedPortfolioValue,
                impactPercent
        );
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
