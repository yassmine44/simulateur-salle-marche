package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Service;

import tn.esprit.simulateurbackend.dto.DashboardResponse.Protection;
import tn.esprit.simulateurbackend.dto.PortfolioResponse;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.TriggerType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Protection de chaque position acheteuse : quelle quantité est
 * couverte par un stop-loss (fixe, suiveur ou branche d'OCO), à
 * quelle distance du cours, et avec quel objectif de vente.
 */
@Service
public class ProtectionCoverageService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);


    public List<Protection> protections(
            PortfolioResponse portfolio,
            List<Order> openOrders
    ) {

        List<Protection> protections = new ArrayList<>();

        for (PortfolioResponse.Position position : portfolio.openPositions()) {

            if (!"LONG".equals(position.direction())) {
                continue;
            }

            protections.add(coverage(position, openOrders));
        }

        return protections;
    }


    private Protection coverage(
            PortfolioResponse.Position position,
            List<Order> openOrders
    ) {

        String symbol = position.assetSymbol();

        BigDecimal last = position.lastPrice();

        // Stops en attente : une seule fois par groupe OCO.
        Map<String, BigDecimal> ocoStops = new HashMap<>();

        BigDecimal standaloneStops = BigDecimal.ZERO;

        BigDecimal stopPrice = null;

        boolean trailing = false;

        BigDecimal takeProfit = null;

        for (Order order : openOrders) {

            if (
                    !symbol.equals(order.getAssetSymbol())
                            || order.getSide() != OrderSide.SELL
            ) {
                continue;
            }

            if (
                    order.getTriggerType() == TriggerType.STOP_LOSS
                            && order.isWaitingTrigger()
            ) {

                if (order.getOcoGroup() != null) {
                    ocoStops.merge(order.getOcoGroup(), order.getRemainingQuantity(), BigDecimal::max);
                } else {
                    standaloneStops = standaloneStops.add(order.getRemainingQuantity());
                }

                // Le stop le plus haut est celui qui protège en premier.
                if (stopPrice == null || order.getTriggerPrice().compareTo(stopPrice) > 0) {
                    stopPrice = order.getTriggerPrice();
                }

                trailing = trailing || order.getTrailPercent() != null;

                continue;
            }

            boolean sellAboveMarket =
                    order.getType() == OrderType.LIMIT
                            && order.getLimitPrice() != null
                            && order.getLimitPrice().compareTo(last) > 0;

            boolean takeProfitTrigger =
                    order.getTriggerType() == TriggerType.TAKE_PROFIT
                            && order.isWaitingTrigger();

            BigDecimal target =
                    sellAboveMarket
                            ? order.getLimitPrice()
                            : takeProfitTrigger ? order.getTriggerPrice() : null;

            if (target != null && (takeProfit == null || target.compareTo(takeProfit) < 0)) {
                takeProfit = target;
            }
        }

        BigDecimal covered =
                ocoStops.values().stream()
                        .reduce(standaloneStops, BigDecimal::add)
                        .min(position.quantity());

        String status =
                covered.signum() == 0
                        ? "UNPROTECTED"
                        : covered.compareTo(position.quantity()) >= 0 ? "PROTECTED" : "PARTIAL";

        return new Protection(
                symbol,
                position.quantity(),
                last,
                status,
                plain(covered),
                stopPrice,
                distance(stopPrice, last),
                trailing,
                takeProfit,
                distance(takeProfit, last),
                stopPrice == null
                        ? null
                        : last.subtract(stopPrice).multiply(covered).setScale(2, RoundingMode.HALF_UP),
                position.weightPercent()
        );
    }


    // Écart entre un prix et le cours, en % du cours (toujours positif).
    private BigDecimal distance(
            BigDecimal price,
            BigDecimal last
    ) {

        if (price == null || last == null || last.signum() == 0) {
            return null;
        }

        return price.subtract(last).abs()
                .multiply(HUNDRED)
                .divide(last, 2, RoundingMode.HALF_UP);
    }


    private static BigDecimal plain(BigDecimal value) {

        BigDecimal stripped = value.stripTrailingZeros();

        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
