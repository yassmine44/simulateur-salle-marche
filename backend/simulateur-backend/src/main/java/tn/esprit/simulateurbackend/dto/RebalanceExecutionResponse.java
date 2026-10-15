package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

/*
 * Résultat ordre par ordre : un refus n'empêche pas les suivants.
 */
public record RebalanceExecutionResponse(

        int ordersSent,

        int ordersFilled,

        List<Result> results
) {

    public record Result(

            String assetSymbol,

            OrderSide side,

            BigDecimal requestedQuantity,

            BigDecimal executedQuantity,

            Long orderId,

            OrderStatus status,

            // Raison d'un refus ou d'une exécution partielle.
            String message
    ) {
    }
}
