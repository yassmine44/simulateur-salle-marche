package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;

public record FlashOrderBookLevelDto(
        BigDecimal price,
        BigDecimal quantity,
        int orderCount
) {
}
