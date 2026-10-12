package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;

public record OrderBookLevelResponse(
        BigDecimal price,
        BigDecimal totalQuantity,
        int orderCount
) {
}