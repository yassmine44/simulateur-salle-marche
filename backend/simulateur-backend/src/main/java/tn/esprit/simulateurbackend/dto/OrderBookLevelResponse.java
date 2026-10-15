package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;

public record OrderBookLevelResponse(
        BigDecimal price,
        BigDecimal totalQuantity,
        int orderCount,
        // Part de ce niveau appartenant à l'utilisateur connecté.
        BigDecimal myQuantity
) {
}
