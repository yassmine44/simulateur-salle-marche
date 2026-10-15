package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.Trade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record TradeResponse(

        Long id,
        String assetSymbol,
        OrderSide side,
        Long orderId,
        BigDecimal executionPrice,
        BigDecimal quantity,
        Instant createdAt,

        // ---- Qualité d'exécution ----

        /*
         * TAKER : votre ordre a pris la liquidité (il est arrivé
         * sur un prix existant) ; MAKER : il attendait dans le carnet.
         */
        String liquidity,

        // Prix milieu du carnet à l'arrivée de votre ordre.
        BigDecimal arrivalMidPrice,

        /*
         * Écart entre le prix obtenu et le prix milieu, en points
         * de base (1 pb = 0,01 %). Positif = coût (acheté plus cher
         * ou vendu moins cher que le milieu), négatif = gain.
         */
        BigDecimal slippageBps,

        // Même écart en devise, sur la quantité exécutée.
        BigDecimal slippageAmount

) {

    private static final BigDecimal BPS = BigDecimal.valueOf(10_000);

    public static TradeResponse from(
            Trade trade,
            Long currentUserId
    ) {

        boolean isBuyer =
                trade.getBuyOrder()
                        .getUser()
                        .getId()
                        .equals(currentUserId);

        OrderSide side =
                isBuyer
                        ? OrderSide.BUY
                        : OrderSide.SELL;

        Order myOrder =
                isBuyer
                        ? trade.getBuyOrder()
                        : trade.getSellOrder();

        String liquidity =
                trade.getAggressorSide() == null
                        ? null
                        : trade.getAggressorSide() == side
                        ? "TAKER"
                        : "MAKER";

        BigDecimal mid = myOrder.getArrivalMidPrice();

        BigDecimal slippageBps = null;
        BigDecimal slippageAmount = null;

        if (mid != null && mid.signum() > 0) {

            // Achat : payer au-dessus du milieu est un coût.
            // Vente : vendre en dessous du milieu est un coût.
            BigDecimal perUnit =
                    side == OrderSide.BUY
                            ? trade.getExecutionPrice().subtract(mid)
                            : mid.subtract(trade.getExecutionPrice());

            slippageBps =
                    perUnit.multiply(BPS)
                            .divide(mid, 1, RoundingMode.HALF_UP);

            slippageAmount =
                    perUnit.multiply(trade.getQuantity())
                            .setScale(2, RoundingMode.HALF_UP);
        }

        return new TradeResponse(

                trade.getId(),

                trade.getAssetSymbol(),

                side,

                myOrder.getId(),

                trade.getExecutionPrice(),

                trade.getQuantity(),

                trade.getCreatedAt(),

                liquidity,

                mid,

                slippageBps,

                slippageAmount
        );
    }
}
