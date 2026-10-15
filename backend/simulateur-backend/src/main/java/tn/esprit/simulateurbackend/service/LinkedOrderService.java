package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Service;

import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.CancelReason;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.TimeInForce;
import tn.esprit.simulateurbackend.entity.TriggerType;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/*
 * Ordres liés : bracket et OCO ("l'un annule l'autre").
 *
 * Un groupe OCO réunit des ventes qui protègent la même quantité
 * de titres (oco_quantity) : un take-profit (vente limite au-dessus
 * du cours) et un stop-loss (vente au marché déclenchée en dessous).
 * Invariant maintenu après chaque exécution :
 *
 *   reste de chaque branche ouverte = quantité du groupe
 *                                     - quantité déjà exécutée par le groupe
 *
 * Dès qu'une branche est exécutée, les autres sont réduites d'autant,
 * puis annulées quand il ne reste rien à protéger.
 *
 * Bracket = achat + groupe OCO créé automatiquement : la quantité du
 * groupe suit la quantité réellement achetée (exécutions partielles).
 */
@Service
public class LinkedOrderService {

    private static final String BRACKET_GROUP_PREFIX = "BRK-";

    private final OrderRepository orderRepository;

    public LinkedOrderService(
            OrderRepository orderRepository
    ) {
        this.orderRepository = orderRepository;
    }


    // =========================================
    // AFTER EXECUTION
    // =========================================

    /*
     * À appeler après toute exécution. Retourne les ordres limite
     * créés ou agrandis qui doivent être appariés (take-profit).
     */
    public List<Long> afterExecution(
            Collection<Order> touchedOrders
    ) {

        List<Long> toMatch = new ArrayList<>();

        Set<String> groups = new LinkedHashSet<>();

        for (Order order : touchedOrders) {

            if (
                    order.hasBracket()
                            && order.getSide() == OrderSide.BUY
                            && order.executedQuantity().signum() > 0
            ) {
                toMatch.addAll(syncBracket(order));
                groups.add(bracketGroup(order));
            }

            if (order.getOcoGroup() != null) {
                groups.add(order.getOcoGroup());
            }
        }

        groups.forEach(this::rebalance);

        return toMatch;
    }


    /*
     * Crée les protections d'un achat bracket, ou les agrandit
     * quand l'achat a été exécuté davantage.
     */
    private List<Long> syncBracket(
            Order parent
    ) {

        String group = bracketGroup(parent);

        BigDecimal executed = parent.executedQuantity();

        List<Order> legs = orderRepository.findByOcoGroupOrderByIdAsc(group);

        List<Long> toMatch = new ArrayList<>();

        if (legs.isEmpty()) {

            if (parent.getBracketTakeProfitPrice() != null) {

                Order takeProfit =
                        newLeg(parent.getUser(), parent.getAssetSymbol(), group, executed);

                takeProfit.setType(OrderType.LIMIT);
                takeProfit.setLimitPrice(parent.getBracketTakeProfitPrice());
                takeProfit.setParentOrderId(parent.getId());

                toMatch.add(orderRepository.saveAndFlush(takeProfit).getId());
            }

            if (parent.getBracketStopPrice() != null) {

                Order stopLoss =
                        newLeg(parent.getUser(), parent.getAssetSymbol(), group, executed);

                stopLoss.setType(OrderType.MARKET);
                stopLoss.setTriggerType(TriggerType.STOP_LOSS);
                stopLoss.setTriggerPrice(parent.getBracketStopPrice());
                stopLoss.setTrailPercent(parent.getTrailPercent());
                stopLoss.setParentOrderId(parent.getId());

                orderRepository.saveAndFlush(stopLoss);
            }

            return toMatch;
        }

        BigDecimal groupQuantity = legs.get(0).getOcoQuantity();

        if (executed.compareTo(groupQuantity) > 0) {

            for (Order leg : legs) {

                leg.setOcoQuantity(executed);

                if (leg.isOpen() && leg.getType() == OrderType.LIMIT) {
                    toMatch.add(leg.getId());
                }
            }

            orderRepository.saveAll(legs);
        }

        return toMatch;
    }


    /*
     * Rétablit l'invariant du groupe après une exécution.
     */
    private void rebalance(
            String group
    ) {

        List<Order> legs = orderRepository.findByOcoGroupOrderByIdAsc(group);

        if (legs.isEmpty()) {
            return;
        }

        BigDecimal groupQuantity = legs.get(0).getOcoQuantity();

        BigDecimal executedByGroup =
                legs.stream()
                        .map(Order::executedQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal stillToProtect = groupQuantity.subtract(executedByGroup);

        for (Order leg : legs) {

            if (!leg.isOpen()) {
                continue;
            }

            if (stillToProtect.signum() <= 0) {

                leg.cancel(CancelReason.OCO);

            } else {

                leg.setQuantity(leg.executedQuantity().add(stillToProtect));
                leg.setRemainingQuantity(stillToProtect);
            }
        }

        orderRepository.saveAllAndFlush(legs);
    }


    // =========================================
    // STANDALONE OCO
    // =========================================

    /*
     * Protège des titres déjà détenus.
     * Retourne la branche take-profit, à apparier.
     */
    public List<Order> createOco(
            User user,
            Asset asset,
            BigDecimal quantity,
            BigDecimal takeProfitPrice,
            BigDecimal stopLossPrice,
            BigDecimal stopTrailPercent
    ) {

        String group = UUID.randomUUID().toString();

        Order takeProfit = newLeg(user, asset.getSymbol(), group, quantity);

        takeProfit.setType(OrderType.LIMIT);
        takeProfit.setLimitPrice(takeProfitPrice);

        Order stopLoss = newLeg(user, asset.getSymbol(), group, quantity);

        stopLoss.setType(OrderType.MARKET);
        stopLoss.setTriggerType(TriggerType.STOP_LOSS);
        stopLoss.setTriggerPrice(stopLossPrice);
        stopLoss.setTrailPercent(stopTrailPercent);

        return orderRepository.saveAllAndFlush(List.of(takeProfit, stopLoss));
    }


    // =========================================
    // CANCELLATION
    // =========================================

    /*
     * Annuler une branche annule tout le groupe :
     * une protection à moitié en place serait trompeuse.
     */
    public List<Order> cancelSiblings(
            Order leg,
            CancelReason reason
    ) {

        if (leg.getOcoGroup() == null) {
            return List.of();
        }

        List<Order> cancelled = new ArrayList<>();

        for (Order sibling : orderRepository.findByOcoGroupOrderByIdAsc(leg.getOcoGroup())) {

            if (!sibling.getId().equals(leg.getId()) && sibling.isOpen()) {

                sibling.cancel(reason);

                cancelled.add(sibling);
            }
        }

        orderRepository.saveAll(cancelled);

        return cancelled;
    }


    // =========================================
    // HELPERS
    // =========================================

    private Order newLeg(
            User user,
            String symbol,
            String group,
            BigDecimal quantity
    ) {

        Order leg = new Order();

        leg.setUser(user);
        leg.setAssetSymbol(symbol);
        leg.setSide(OrderSide.SELL);
        leg.setStatus(OrderStatus.PENDING);
        leg.setTimeInForce(TimeInForce.GTC);
        leg.setQuantity(quantity);
        leg.setRemainingQuantity(quantity);
        leg.setOcoGroup(group);
        leg.setOcoQuantity(quantity);

        return leg;
    }


    private static String bracketGroup(Order parent) {
        return BRACKET_GROUP_PREFIX + parent.getId();
    }
}
