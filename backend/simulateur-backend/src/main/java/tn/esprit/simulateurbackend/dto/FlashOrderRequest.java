package tn.esprit.simulateurbackend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Multiplayer limit order. Solo gamification does not use this flow.
 */
public record FlashOrderRequest(
        @NotBlank String side,
        @NotNull
        @DecimalMin(value = "0.00000001")
        BigDecimal quantity,
        @NotNull
        @DecimalMin(value = "0.0001")
        BigDecimal price
) {
}
