package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.TriggerType;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.OrderRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/*
 * Contrôle avant ordre : l'utilisateur peut-il payer ce qu'il
 * achète et livrer ce qu'il vend ?
 *
 * - Achat : le coût maximal de l'ordre doit tenir dans les
 *   liquidités disponibles (liquidités - montants déjà réservés
 *   par ses ordres d'achat en attente).
 * - Vente : la quantité doit tenir dans les titres disponibles
 *   (titres détenus - titres déjà engagés dans des ventes en
 *   attente). La vente à découvert n'est pas autorisée.
 *
 * Les comptes techniques du teneur de marché ne passent pas
 * par ce contrôle : leurs ordres ne transitent pas par OrderService.
 */
@Service
public class PreTradeCheckService {

    private static final List<OrderStatus> OPEN_STATUSES =
            List.of(
                    OrderStatus.PENDING,
                    OrderStatus.PARTIALLY_FILLED
            );

    private final PortfolioService portfolioService;
    private final OrderRepository orderRepository;
    private final TradingRulesService tradingRulesService;

    public PreTradeCheckService(
            PortfolioService portfolioService,
            OrderRepository orderRepository,
            TradingRulesService tradingRulesService
    ) {
        this.portfolioService = portfolioService;
        this.orderRepository = orderRepository;
        this.tradingRulesService = tradingRulesService;
    }


    public void check(
            User user,
            Asset asset,
            OrderSide side,
            OrderType type,
            TriggerType triggerType,
            BigDecimal quantity,
            BigDecimal limitPrice,
            BigDecimal triggerPrice,
            BigDecimal lastPrice
    ) {

        if (!portfolioService.isEnforcingLimits()) {
            return;
        }

        PortfolioService.Snapshot snapshot =
                portfolioService.snapshot(user);

        String currency = portfolioService.currency();

        if (side == OrderSide.BUY) {

            BigDecimal cost =
                    estimatedCost(
                            user,
                            asset,
                            type,
                            triggerType,
                            quantity,
                            limitPrice,
                            triggerPrice,
                            lastPrice
                    );

            BigDecimal available = snapshot.availableCash();

            if (cost.compareTo(available) > 0) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Fonds insuffisants : cet achat peut coûter jusqu'à "
                                + money(cost) + " " + currency
                                + ", or vous disposez de "
                                + money(available.max(BigDecimal.ZERO)) + " " + currency
                                + describeReservedCash(snapshot, currency)
                                + ". Réduisez la quantité"
                                + maxQuantityHint(asset, available, cost, quantity)
                                + ", ou annulez un ordre d'achat en attente."
                );
            }

            return;
        }

        BigDecimal held = snapshot.ledger().quantity(asset.getSymbol());

        BigDecimal reserved = snapshot.reservedQuantity(asset.getSymbol());

        BigDecimal available = held.subtract(reserved);

        if (quantity.compareTo(available) <= 0) {
            return;
        }

        if (held.signum() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Vous ne détenez pas de " + asset.getSymbol()
                            + " : on ne peut vendre que ce que l'on possède "
                            + "(la vente à découvert n'est pas autorisée). "
                            + "Achetez d'abord cet actif."
            );
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Quantité insuffisante : vous détenez "
                        + plain(held) + " " + asset.getSymbol()
                        + (reserved.signum() > 0
                        ? ", dont " + plain(reserved)
                        + " déjà engagés dans vos ordres de vente en attente"
                        : "")
                        + ". Vous pouvez vendre au plus "
                        + plain(available.max(BigDecimal.ZERO)) + "."
        );
    }


    /*
     * Coût maximal d'un achat :
     * - ordre limite : quantité x prix limite ;
     * - stop / take-profit : quantité x seuil de déclenchement ;
     * - ordre au marché : coût réel en parcourant les ventes du carnet,
     *   dans la limite de la protection de prix (le reste sera annulé).
     */
    private BigDecimal estimatedCost(
            User user,
            Asset asset,
            OrderType type,
            TriggerType triggerType,
            BigDecimal quantity,
            BigDecimal limitPrice,
            BigDecimal triggerPrice,
            BigDecimal lastPrice
    ) {

        if (type == OrderType.LIMIT) {
            return quantity.multiply(limitPrice);
        }

        if (triggerType != null) {
            return quantity.multiply(triggerPrice);
        }

        BigDecimal priceLimit =
                tradingRulesService.marketPriceLimit(OrderSide.BUY, lastPrice);

        BigDecimal remaining = quantity;
        BigDecimal cost = BigDecimal.ZERO;

        for (
                Order ask
                : orderRepository.findBookAsks(asset.getSymbol(), OPEN_STATUSES)
        ) {

            if (
                    remaining.signum() <= 0
                            || ask.getLimitPrice().compareTo(priceLimit) > 0
            ) {
                break;
            }

            // Ses propres ordres ne sont jamais contrepartie.
            if (ask.getUser().getId().equals(user.getId())) {
                continue;
            }

            BigDecimal filled = remaining.min(ask.getRemainingQuantity());

            cost = cost.add(filled.multiply(ask.getLimitPrice()));

            remaining = remaining.subtract(filled);
        }

        return cost;
    }


    private String describeReservedCash(
            PortfolioService.Snapshot snapshot,
            String currency
    ) {

        if (snapshot.reservedCash().signum() <= 0) {
            return "";
        }

        return " (liquidités " + money(snapshot.ledger().cash()) + " " + currency
                + ", dont " + money(snapshot.reservedCash()) + " " + currency
                + " déjà réservés par vos ordres d'achat en attente)";
    }


    // Quantité maximale achetable au même prix moyen, arrondie à la quantité minimale.
    private String maxQuantityHint(
            Asset asset,
            BigDecimal available,
            BigDecimal cost,
            BigDecimal quantity
    ) {

        if (
                available.signum() <= 0
                        || cost.signum() <= 0
        ) {
            return "";
        }

        BigDecimal unitCost =
                cost.divide(quantity, 8, RoundingMode.HALF_UP);

        BigDecimal step =
                asset.getMinQuantity() != null
                        && asset.getMinQuantity().signum() > 0
                        ? asset.getMinQuantity()
                        : BigDecimal.ONE;

        BigDecimal maxQuantity =
                available
                        .divide(unitCost, 8, RoundingMode.DOWN)
                        .divide(step, 0, RoundingMode.DOWN)
                        .multiply(step);

        if (maxQuantity.signum() <= 0) {
            return "";
        }

        return " (au plus " + plain(maxQuantity) + " à ce prix)";
    }


    private static String money(BigDecimal value) {

        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }


    private static String plain(BigDecimal value) {

        return value.stripTrailingZeros().toPlainString();
    }
}
