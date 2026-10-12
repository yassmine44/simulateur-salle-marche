package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(

        Long id,

        Long userId,

        String assetSymbol,

        OrderSide side,

        OrderType type,

        BigDecimal limitPrice,

        BigDecimal quantity,

        BigDecimal remainingQuantity,

        OrderStatus status,

        Instant createdAt,

        Instant updatedAt

) {

    public static OrderResponse from(Order order) {

        return new OrderResponse(

                order.getId(),

                order.getUser().getId(),

                order.getAssetSymbol(),

                order.getSide(),

                order.getType(),

                order.getLimitPrice(),

                order.getQuantity(),

                order.getRemainingQuantity(),

                order.getStatus(),

                order.getCreatedAt(),

                order.getUpdatedAt()
        );
    }
}