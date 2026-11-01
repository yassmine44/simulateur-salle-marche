package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import tn.esprit.simulateurbackend.entity.OrderSide;

import java.math.BigDecimal;

public record GamificationOrderRequest(

        @NotNull(message = "Le sens de l'ordre est obligatoire.")
        OrderSide side,

        @NotNull(message = "La quantité est obligatoire.")
        @DecimalMin(
                value = "0.00000001",
                message = "La quantité doit être strictement positive."
        )
        BigDecimal quantity
) {
}
