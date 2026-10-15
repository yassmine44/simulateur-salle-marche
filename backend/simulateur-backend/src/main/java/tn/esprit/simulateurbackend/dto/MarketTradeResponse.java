package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.Trade;

import java.math.BigDecimal;
import java.time.Instant;

/*
 * Exécution publique du marché :
 * aucune information sur les utilisateurs.
 */
public record MarketTradeResponse(
        Long id,
        BigDecimal price,
        BigDecimal quantity,
        OrderSide aggressorSide,
        Instant createdAt
) {

    public static MarketTradeResponse from(Trade trade) {

        return new MarketTradeResponse(
                trade.getId(),
                trade.getExecutionPrice(),
                trade.getQuantity(),
                trade.getAggressorSide(),
                trade.getCreatedAt()
        );
    }
}
