package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.OrderSide;

import java.math.BigDecimal;
import java.util.List;

/*
 * Ordres nécessaires pour atteindre la répartition cible.
 * Estimation : les prix réels dépendront du carnet à l'exécution.
 */
public record RebalancePlanResponse(

        String currency,

        BigDecimal totalValue,

        BigDecimal availableCash,

        // Part de la valeur totale laissée en liquidités par la cible.
        BigDecimal targetCashPercent,

        BigDecimal estimatedCashAfter,

        List<Line> lines,

        List<String> warnings
) {

    public record Line(

            String assetSymbol,

            String assetName,

            BigDecimal currentQuantity,

            BigDecimal currentValue,

            BigDecimal currentPercent,

            BigDecimal targetPercent,

            BigDecimal targetValue,

            // null : aucun ordre (déjà à la cible ou écart trop faible).
            OrderSide side,

            BigDecimal quantity,

            BigDecimal estimatedPrice,

            BigDecimal estimatedAmount,

            String note
    ) {
    }
}
