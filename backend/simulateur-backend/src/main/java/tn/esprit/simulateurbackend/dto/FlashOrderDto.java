package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;

public record FlashOrderDto(
        String side,
        BigDecimal quantity,
        BigDecimal executionPrice,
        int candleIndex
) {
}
