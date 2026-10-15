package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ModifyOrderRequest(

        @NotNull(message = "La quantité est obligatoire.")
        @DecimalMin(
                value = "0.00000001",
                message = "La quantité doit être strictement positive."
        )
        BigDecimal quantity,

        @NotNull(message = "Le prix limite est obligatoire.")
        @DecimalMin(
                value = "0.00000001",
                message = "Le prix limite doit être strictement positif."
        )
        BigDecimal limitPrice
) {
}
