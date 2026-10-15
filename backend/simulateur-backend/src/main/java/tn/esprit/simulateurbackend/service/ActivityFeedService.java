package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Service;

import tn.esprit.simulateurbackend.dto.DashboardResponse.ActivityEvent;
import tn.esprit.simulateurbackend.entity.CancelReason;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.TimeInForce;
import tn.esprit.simulateurbackend.entity.Trade;
import tn.esprit.simulateurbackend.entity.TriggerType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static tn.esprit.simulateurbackend.service.FrenchFormat.amount;
import static tn.esprit.simulateurbackend.service.FrenchFormat.price;
import static tn.esprit.simulateurbackend.service.FrenchFormat.quantity;

/*
 * Activité récente en une seule chronologie : ordres placés,
 * exécutions, déclenchements, annulations et rejets.
 */
@Service
public class ActivityFeedService {

    static final String POSITIVE = "POSITIVE";
    static final String NEGATIVE = "NEGATIVE";
    static final String NEUTRAL = "NEUTRAL";

    private static final int MAX_EVENTS = 15;

    private static final Map<CancelReason, String> CANCEL_REASONS =
            Map.of(
                    CancelReason.USER, "annulé par vous",
                    CancelReason.REPLACED, "remplacé par une modification",
                    CancelReason.EXPIRED, "expiré à minuit (ordre « journée »)",
                    CancelReason.IOC, "reste non exécuté immédiatement (IOC)",
                    CancelReason.FOK, "quantité indisponible en totalité (tout ou rien)",
                    CancelReason.OCO, "l'autre branche de la protection a été exécutée",
                    CancelReason.RESET, "portefeuille réinitialisé",
                    CancelReason.NO_LIQUIDITY, "pas assez de contrepartie"
            );


    public List<ActivityEvent> recent(
            Long userId,
            List<Order> orders,
            List<Trade> trades
    ) {

        List<ActivityEvent> events = new ArrayList<>();

        // Une protection (bracket ou OCO) compte deux ordres :
        // sa mise en place n'apparaît qu'une fois.
        Set<String> announcedGroups = new HashSet<>();

        for (Order order : orders) {

            boolean announce =
                    order.getOcoGroup() == null
                            || announcedGroups.add(order.getOcoGroup());

            events.addAll(eventsOf(order, announce));
        }

        for (Trade trade : trades) {
            events.add(fill(trade, userId));
        }

        return events.stream()
                .sorted(Comparator.comparing(ActivityEvent::time).reversed())
                .limit(MAX_EVENTS)
                .toList();
    }


    // =========================================
    // ORDER EVENTS
    // =========================================

    private List<ActivityEvent> eventsOf(
            Order order,
            boolean announcePlacement
    ) {

        List<ActivityEvent> events = new ArrayList<>();

        String symbol = order.getAssetSymbol();

        if (announcePlacement) events.add(
                new ActivityEvent(
                        order.getCreatedAt(),
                        "PLACED",
                        order.getParentOrderId() != null
                                ? "Protection mise en place"
                                : placedTitle(order),
                        describe(order),
                        symbol,
                        order.getId(),
                        NEUTRAL
                )
        );

        if (order.getTriggeredAt() != null) {

            events.add(
                    new ActivityEvent(
                            order.getTriggeredAt(),
                            "TRIGGERED",
                            (order.getTriggerType() == TriggerType.TAKE_PROFIT ? "Take-profit" : "Stop")
                                    + " déclenché sur " + symbol,
                            "Seuil " + price(order.getTriggerPrice()) + " atteint : "
                                    + (order.getSide() == OrderSide.SELL ? "vente" : "achat")
                                    + " au marché de " + quantity(order.getQuantity()) + ".",
                            symbol,
                            order.getId(),
                            order.getTriggerType() == TriggerType.TAKE_PROFIT ? POSITIVE : NEGATIVE
                    )
            );
        }

        // Les annulations faites en interne (remplacement d'un ordre modifié)
        // n'ajoutent rien : le nouvel ordre apparaît déjà.
        if (
                order.getStatus() == OrderStatus.CANCELLED
                        && order.getCancelReason() != CancelReason.REPLACED
        ) {

            events.add(
                    new ActivityEvent(
                            order.getUpdatedAt(),
                            "CANCELLED",
                            "Ordre #" + order.getId() + " annulé",
                            reason(order),
                            symbol,
                            order.getId(),
                            NEUTRAL
                    )
            );
        }

        if (order.getStatus() == OrderStatus.REJECTED) {

            events.add(
                    new ActivityEvent(
                            order.getUpdatedAt(),
                            "REJECTED",
                            "Ordre #" + order.getId() + " rejeté",
                            reason(order),
                            symbol,
                            order.getId(),
                            NEGATIVE
                    )
            );
        }

        return events;
    }


    private String placedTitle(Order order) {

        String side = order.getSide() == OrderSide.BUY ? "d'achat" : "de vente";

        if (order.getOcoGroup() != null) {
            return "Protection OCO sur " + order.getAssetSymbol();
        }

        if (order.getTriggerType() == TriggerType.STOP_LOSS) {
            return order.getTrailPercent() != null ? "Stop suiveur placé" : "Stop-loss placé";
        }

        if (order.getTriggerType() == TriggerType.TAKE_PROFIT) {
            return "Take-profit placé";
        }

        return "Ordre " + side + (order.getType() == OrderType.MARKET ? " au marché" : " limite");
    }


    private String describe(Order order) {

        StringBuilder text =
                new StringBuilder(quantity(order.getQuantity()))
                        .append(' ')
                        .append(order.getAssetSymbol());

        if (order.getLimitPrice() != null) {
            text.append(" à ").append(price(order.getLimitPrice()));
        }

        if (order.getTriggerPrice() != null) {
            text.append(", seuil ").append(price(order.getTriggerPrice()));
        }

        if (order.getTrailPercent() != null) {
            text.append(" (suit le cours à ").append(quantity(order.getTrailPercent())).append(" %)");
        }

        if (order.hasBracket()) {
            text.append(", avec protections");
        }

        if (order.effectiveTimeInForce() == TimeInForce.DAY) {
            text.append(", valable aujourd'hui");
        }

        return text.append('.').toString();
    }


    private String reason(Order order) {

        String label =
                order.getCancelReason() == null
                        ? null
                        : CANCEL_REASONS.get(order.getCancelReason());

        return label == null ? "—" : capitalize(label) + ".";
    }


    // =========================================
    // FILLS
    // =========================================

    private ActivityEvent fill(
            Trade trade,
            Long userId
    ) {

        boolean buyer = trade.getBuyOrder().getUser().getId().equals(userId);

        Order order = buyer ? trade.getBuyOrder() : trade.getSellOrder();

        return new ActivityEvent(
                trade.getCreatedAt(),
                "FILLED",
                (buyer ? "Achat" : "Vente") + " exécuté" + (buyer ? "" : "e"),
                quantity(trade.getQuantity()) + " " + trade.getAssetSymbol() + " à "
                        + price(trade.getExecutionPrice()) + " (" + amount(
                        trade.getQuantity().multiply(trade.getExecutionPrice())
                ) + ").",
                trade.getAssetSymbol(),
                order.getId(),
                POSITIVE
        );
    }


    private static String capitalize(String text) {

        return text.isEmpty()
                ? text
                : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
