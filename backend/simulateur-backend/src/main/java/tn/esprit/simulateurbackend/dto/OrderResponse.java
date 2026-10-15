package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.CancelReason;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.TimeInForce;
import tn.esprit.simulateurbackend.entity.TriggerType;

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

        TriggerType triggerType,

        BigDecimal triggerPrice,

        Instant triggeredAt,

        BigDecimal trailPercent,

        TimeInForce timeInForce,

        Instant expiresAt,

        CancelReason cancelReason,

        BigDecimal bracketStopPrice,

        BigDecimal bracketTakeProfitPrice,

        Long parentOrderId,

        String ocoGroup,

        BigDecimal arrivalMidPrice,

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

                order.getTriggerType(),

                order.getTriggerPrice(),

                order.getTriggeredAt(),

                order.getTrailPercent(),

                order.effectiveTimeInForce(),

                order.getExpiresAt(),

                order.getCancelReason(),

                order.getBracketStopPrice(),

                order.getBracketTakeProfitPrice(),

                order.getParentOrderId(),

                order.getOcoGroup(),

                order.getArrivalMidPrice(),

                order.getCreatedAt(),

                order.getUpdatedAt()
        );
    }
}
