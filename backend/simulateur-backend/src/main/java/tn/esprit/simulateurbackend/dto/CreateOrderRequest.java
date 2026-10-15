package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.TimeInForce;
import tn.esprit.simulateurbackend.entity.TriggerType;

import java.math.BigDecimal;

public record CreateOrderRequest(

        @NotBlank(message = "Le symbole de l'actif est obligatoire.")
        @Size(
                max = 30,
                message = "Le symbole de l'actif ne doit pas dépasser 30 caractères."
        )
        String assetSymbol,

        @NotNull(message = "Le sens de l'ordre est obligatoire.")
        OrderSide side,

        @NotNull(message = "Le type d'ordre est obligatoire.")
        OrderType type,

        @NotNull(message = "La quantité est obligatoire.")
        @DecimalMin(
                value = "0.00000001",
                message = "La quantité doit être strictement positive."
        )
        @DecimalMax(
                value = "1000000000",
                message = "La quantité est trop élevée."
        )
        BigDecimal quantity,

        @DecimalMin(
                value = "0.00000001",
                message = "Le prix limite doit être strictement positif."
        )
        BigDecimal limitPrice,

        /*
         * Ordre conditionnel (type MARKET uniquement) :
         * STOP_LOSS ou TAKE_PROFIT + prix de déclenchement.
         */
        TriggerType triggerType,

        @DecimalMin(
                value = "0.00000001",
                message = "Le prix de déclenchement doit être strictement positif."
        )
        BigDecimal triggerPrice,

        // Durée de validité : GTC par défaut.
        TimeInForce timeInForce,

        /*
         * Stop suiveur (STOP_LOSS uniquement) : distance en %
         * entre le cours et le seuil, qui suit le cours.
         */
        @DecimalMin(
                value = "0.1",
                message = "L'écart du stop suiveur doit être d'au moins 0,1 %."
        )
        @DecimalMax(
                value = "50",
                message = "L'écart du stop suiveur ne peut pas dépasser 50 %."
        )
        BigDecimal trailPercent,

        /*
         * Bracket (achat uniquement) : ordres de sortie créés
         * automatiquement une fois l'achat exécuté.
         */
        @DecimalMin(
                value = "0.00000001",
                message = "Le prix du stop-loss doit être strictement positif."
        )
        BigDecimal stopLossPrice,

        @DecimalMin(
                value = "0.00000001",
                message = "Le prix du take-profit doit être strictement positif."
        )
        BigDecimal takeProfitPrice
) {

    public CreateOrderRequest(
            String assetSymbol,
            OrderSide side,
            OrderType type,
            BigDecimal quantity,
            BigDecimal limitPrice
    ) {
        this(
                assetSymbol,
                side,
                type,
                quantity,
                limitPrice,
                null,
                null
        );
    }


    public CreateOrderRequest(
            String assetSymbol,
            OrderSide side,
            OrderType type,
            BigDecimal quantity,
            BigDecimal limitPrice,
            TriggerType triggerType,
            BigDecimal triggerPrice
    ) {
        this(
                assetSymbol,
                side,
                type,
                quantity,
                limitPrice,
                triggerType,
                triggerPrice,
                null,
                null,
                null,
                null
        );
    }


    public CreateOrderRequest withTimeInForce(TimeInForce value) {

        return new CreateOrderRequest(
                assetSymbol, side, type, quantity, limitPrice,
                triggerType, triggerPrice, value, trailPercent,
                stopLossPrice, takeProfitPrice
        );
    }
}
