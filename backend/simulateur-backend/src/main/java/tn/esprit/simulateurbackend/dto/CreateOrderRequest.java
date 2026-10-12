package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderType;

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
        BigDecimal quantity,

        @DecimalMin(
                value = "0.00000001",
                message = "Le prix limite doit être strictement positif."
        )
        BigDecimal limitPrice
) {
}