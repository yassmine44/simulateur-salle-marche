package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.Trade;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeResponse(

        Long id,
        String assetSymbol,
        OrderSide side,
        Long orderId,
        BigDecimal executionPrice,
        BigDecimal quantity,
        Instant createdAt

) {

    public static TradeResponse from(
            Trade trade,
            Long currentUserId
    ) {

        boolean isBuyer =
                trade.getBuyOrder()
                        .getUser()
                        .getId()
                        .equals(currentUserId);

        return new TradeResponse(

                trade.getId(),

                trade.getAssetSymbol(),

                isBuyer
                        ? OrderSide.BUY
                        : OrderSide.SELL,

                isBuyer
                        ? trade.getBuyOrder().getId()
                        : trade.getSellOrder().getId(),

                trade.getExecutionPrice(),

                trade.getQuantity(),

                trade.getCreatedAt()
        );
    }
}