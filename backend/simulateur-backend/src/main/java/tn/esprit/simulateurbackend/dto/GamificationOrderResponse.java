package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.GamificationOrder;
import tn.esprit.simulateurbackend.entity.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

public record GamificationOrderResponse(
        Long id,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal executionPrice,
        int candleIndex,
        Instant createdAt
) {

    public static GamificationOrderResponse from(
            GamificationOrder order
    ) {

        return new GamificationOrderResponse(
                order.getId(),
                order.getSide(),
                order.getQuantity(),
                order.getExecutionPrice(),
                order.getCandleIndex(),
                order.getCreatedAt()
        );
    }
}
