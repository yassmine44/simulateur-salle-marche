package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.CreateOrderRequest;
import tn.esprit.simulateurbackend.dto.OrderPreviewResponse;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.TimeInForce;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.service.OrderValidator.ValidatedOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/*
 * Aperçu d'un ordre : simule son exécution sur le carnet actuel,
 * sans rien enregistrer ni verrouiller.
 *
 * Mêmes règles que pour un ordre réel (OrderValidator, contrôle du
 * portefeuille) : un ordre accepté par l'aperçu l'est aussi à l'envoi,
 * tant que le carnet n'a pas changé entre-temps.
 */
@Service
@Transactional(readOnly = true)
public class OrderPreviewService {

    private static final List<OrderStatus> OPEN_STATUSES =
            List.of(
                    OrderStatus.PENDING,
                    OrderStatus.PARTIALLY_FILLED
            );

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final BigDecimal BPS = BigDecimal.valueOf(10_000);

    // Seuils des avertissements.
    private static final BigDecimal LARGE_ORDER_PERCENT = BigDecimal.valueOf(25);

    private static final BigDecimal HIGH_SLIPPAGE_BPS = BigDecimal.valueOf(50);

    private static final BigDecimal FAR_FROM_MARKET_PERCENT = BigDecimal.valueOf(2);

    private static final int MANY_LEVELS = 3;

    private final OrderValidator orderValidator;
    private final PreTradeCheckService preTradeCheckService;
    private final PortfolioService portfolioService;
    private final OrderRepository orderRepository;
    private final MarketDataService marketDataService;
    private final TradingRulesService tradingRulesService;

    public OrderPreviewService(
            OrderValidator orderValidator,
            PreTradeCheckService preTradeCheckService,
            PortfolioService portfolioService,
            OrderRepository orderRepository,
            MarketDataService marketDataService,
            TradingRulesService tradingRulesService
    ) {
        this.orderValidator = orderValidator;
        this.preTradeCheckService = preTradeCheckService;
        this.portfolioService = portfolioService;
        this.orderRepository = orderRepository;
        this.marketDataService = marketDataService;
        this.tradingRulesService = tradingRulesService;
    }


    public OrderPreviewResponse preview(
            String authenticatedEmail,
            CreateOrderRequest request
    ) {

        User user = portfolioService.findUser(authenticatedEmail);

        String currency = portfolioService.currency();

        ValidatedOrder order;

        try {
            order = orderValidator.validate(user, request);
        } catch (ResponseStatusException exception) {
            return refused(exception.getReason(), currency);
        }

        String refusal = null;

        try {
            preTradeCheckService.check(
                    user,
                    order.asset(),
                    order.side(),
                    order.type(),
                    order.triggerType(),
                    order.quantity(),
                    order.limitPrice(),
                    order.triggerPrice(),
                    order.lastPrice()
            );
        } catch (ResponseStatusException exception) {
            // On montre quand même la simulation : l'utilisateur voit
            // ce que l'ordre coûterait et pourquoi c'est trop.
            refusal = exception.getReason();
        }

        return simulate(user, order, refusal, currency);
    }


    // =========================================
    // SIMULATION
    // =========================================

    private OrderPreviewResponse simulate(
            User user,
            ValidatedOrder order,
            String refusal,
            String currency
    ) {

        List<String> warnings = new ArrayList<>();

        String symbol = order.symbol();

        List<Order> bids = orderRepository.findBookBids(symbol, OPEN_STATUSES);
        List<Order> asks = orderRepository.findBookAsks(symbol, OPEN_STATUSES);

        BigDecimal bestBid = bids.isEmpty() ? null : bids.get(0).getLimitPrice();
        BigDecimal bestAsk = asks.isEmpty() ? null : asks.get(0).getLimitPrice();

        BigDecimal mid =
                bestBid != null && bestAsk != null
                        ? bestBid.add(bestAsk).divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP)
                        : order.lastPrice();

        // ---- Exécution immédiate ----

        BigDecimal filled = BigDecimal.ZERO;
        BigDecimal amount = BigDecimal.ZERO;
        int levels = 0;
        BigDecimal lastLevelPrice = null;

        if (!order.isConditional()) {

            List<Order> opposite = order.side() == OrderSide.BUY ? asks : bids;

            BigDecimal priceLimit =
                    order.type() == OrderType.LIMIT
                            ? order.limitPrice()
                            : tradingRulesService.marketPriceLimit(order.side(), order.lastPrice());

            BigDecimal remaining = order.quantity();

            for (Order resting : opposite) {

                if (remaining.signum() <= 0) {
                    break;
                }

                boolean priceAcceptable =
                        order.side() == OrderSide.BUY
                                ? resting.getLimitPrice().compareTo(priceLimit) <= 0
                                : resting.getLimitPrice().compareTo(priceLimit) >= 0;

                if (!priceAcceptable) {
                    break;
                }

                // Ses propres ordres ne sont jamais contrepartie.
                if (resting.getUser().getId().equals(user.getId())) {
                    continue;
                }

                BigDecimal quantity = remaining.min(resting.getRemainingQuantity());

                filled = filled.add(quantity);
                amount = amount.add(quantity.multiply(resting.getLimitPrice()));
                remaining = remaining.subtract(quantity);

                if (!resting.getLimitPrice().equals(lastLevelPrice)) {
                    levels++;
                    lastLevelPrice = resting.getLimitPrice();
                }
            }

            // Tout ou rien : sans liquidité suffisante, rien n'est exécuté.
            if (
                    order.timeInForce() == TimeInForce.FOK
                            && filled.compareTo(order.quantity()) < 0
            ) {

                warnings.add(
                        "Ordre « tout ou rien » : seules " + plain(filled) + " sur "
                                + plain(order.quantity()) + " seraient disponibles à ce prix, "
                                + "l'ordre serait donc entièrement annulé."
                );

                filled = BigDecimal.ZERO;
                amount = BigDecimal.ZERO;
                levels = 0;
            }
        }

        BigDecimal unfilled = order.quantity().subtract(filled);

        boolean restsInBook =
                order.type() == OrderType.LIMIT
                        && (order.timeInForce() == TimeInForce.GTC || order.timeInForce() == TimeInForce.DAY);

        BigDecimal resting = order.isConditional() ? BigDecimal.ZERO : (restsInBook ? unfilled : BigDecimal.ZERO);

        BigDecimal cancelled = order.isConditional() ? BigDecimal.ZERO : (restsInBook ? BigDecimal.ZERO : unfilled);

        BigDecimal averagePrice =
                filled.signum() > 0
                        ? amount.divide(filled, 8, RoundingMode.HALF_UP)
                        : null;

        BigDecimal slippageBps = null;

        if (averagePrice != null && mid != null && mid.signum() > 0) {

            BigDecimal perUnit =
                    order.side() == OrderSide.BUY
                            ? averagePrice.subtract(mid)
                            : mid.subtract(averagePrice);

            slippageBps = perUnit.multiply(BPS).divide(mid, 1, RoundingMode.HALF_UP);
        }

        // ---- Portefeuille ----

        PortfolioService.Snapshot snapshot = portfolioService.snapshot(user);

        BigDecimal availableBefore = snapshot.availableCash();

        BigDecimal heldBefore = snapshot.ledger().quantity(symbol);

        BigDecimal cashChange =
                order.side() == OrderSide.BUY ? amount.negate() : amount;

        // Un achat en attente réserve des liquidités.
        BigDecimal reservation = BigDecimal.ZERO;

        if (order.side() == OrderSide.BUY) {

            if (resting.signum() > 0) {
                reservation = resting.multiply(order.limitPrice());
            } else if (order.isConditional()) {
                reservation = order.quantity().multiply(order.triggerPrice());
            }
        }

        BigDecimal availableAfter = availableBefore.add(cashChange).subtract(reservation);

        BigDecimal heldAfter =
                order.side() == OrderSide.BUY
                        ? heldBefore.add(filled)
                        : heldBefore.subtract(filled);

        BigDecimal referencePrice = order.lastPrice() != null ? order.lastPrice() : mid;

        BigDecimal weightAfter = null;

        if (referencePrice != null) {

            BigDecimal totalValue = totalValue(snapshot);

            if (totalValue.signum() > 0) {

                weightAfter =
                        heldAfter.abs()
                                .multiply(referencePrice)
                                .multiply(HUNDRED)
                                .divide(totalValue, 2, RoundingMode.HALF_UP);
            }

            BigDecimal orderValue = order.quantity().multiply(referencePrice);

            if (
                    totalValue.signum() > 0
                            && orderValue.multiply(HUNDRED).divide(totalValue, 2, RoundingMode.HALF_UP)
                            .compareTo(LARGE_ORDER_PERCENT) >= 0
            ) {

                warnings.add(
                        "Ordre important : environ "
                                + orderValue.multiply(HUNDRED).divide(totalValue, 0, RoundingMode.HALF_UP)
                                + " % de la valeur de votre portefeuille."
                );
            }
        }

        // ---- Avertissements d'exécution ----

        if (slippageBps != null && slippageBps.compareTo(HIGH_SLIPPAGE_BPS) >= 0) {

            warnings.add(
                    "Prix moyen éloigné du prix milieu de " + plain(slippageBps)
                            + " points de base (" + plain(slippageBps.divide(HUNDRED, 2, RoundingMode.HALF_UP))
                            + " %) : un ordre limite limiterait ce coût."
            );
        }

        if (levels >= MANY_LEVELS) {

            warnings.add(
                    "Cet ordre consomme " + levels + " niveaux de prix du carnet : "
                            + "chaque niveau supplémentaire est moins favorable."
            );
        }

        if (order.type() == OrderType.MARKET && !order.isConditional() && cancelled.signum() > 0) {

            warnings.add(
                    filled.signum() == 0
                            ? "Aucune contrepartie dans la plage de prix autorisée : l'ordre serait rejeté."
                            : "Contrepartie insuffisante : " + plain(cancelled) + " ne seraient pas exécutés."
            );
        }

        if (resting.signum() > 0 && order.limitPrice() != null && mid != null && mid.signum() > 0) {

            BigDecimal distance =
                    order.limitPrice().subtract(mid).abs()
                            .multiply(HUNDRED)
                            .divide(mid, 2, RoundingMode.HALF_UP);

            if (distance.compareTo(FAR_FROM_MARKET_PERCENT) >= 0) {

                warnings.add(
                        "Prix limite à " + plain(distance) + " % du marché : "
                                + "l'ordre risque d'attendre longtemps avant d'être exécuté."
                );
            }
        }

        if (order.timeInForce() == TimeInForce.DAY && resting.signum() > 0) {
            warnings.add("Ordre valable aujourd'hui seulement : annulé automatiquement à minuit.");
        }

        // ---- Seuil (ordre conditionnel) ----

        BigDecimal triggerDistance = null;

        if (order.isConditional() && order.lastPrice() != null && order.lastPrice().signum() > 0) {

            triggerDistance =
                    order.triggerPrice().subtract(order.lastPrice())
                            .multiply(HUNDRED)
                            .divide(order.lastPrice(), 2, RoundingMode.HALF_UP);
        }

        // ---- Bracket : gain et perte possibles ----

        BigDecimal potentialGain = null;
        BigDecimal potentialLoss = null;
        BigDecimal ratio = null;

        if (order.hasBracket()) {

            BigDecimal entry =
                    averagePrice != null
                            ? averagePrice
                            : order.limitPrice() != null ? order.limitPrice() : order.lastPrice();

            if (entry != null) {

                if (order.takeProfitPrice() != null) {
                    potentialGain = order.takeProfitPrice().subtract(entry).multiply(order.quantity());
                }

                if (order.stopLossPrice() != null) {
                    potentialLoss = entry.subtract(order.stopLossPrice()).multiply(order.quantity());
                }

                if (
                        potentialGain != null
                                && potentialLoss != null
                                && potentialLoss.signum() > 0
                ) {

                    ratio = potentialGain.divide(potentialLoss, 2, RoundingMode.HALF_UP);

                    if (ratio.compareTo(BigDecimal.ONE) < 0) {

                        warnings.add(
                                "Le gain visé est inférieur à la perte acceptée (ratio "
                                        + plain(ratio) + ") : il faudrait gagner plus d'une fois "
                                        + "sur deux pour être rentable."
                        );
                    }
                }
            }
        }

        return new OrderPreviewResponse(
                refusal == null,
                refusal,
                warnings,
                currency,
                bestBid,
                bestAsk,
                mid,
                order.lastPrice(),
                plain(filled),
                averagePrice == null ? null : averagePrice.stripTrailingZeros(),
                money(amount),
                slippageBps,
                levels,
                plain(resting),
                plain(cancelled),
                order.triggerPrice(),
                triggerDistance,
                expiryOf(order.timeInForce()),
                money(availableBefore),
                money(availableAfter),
                plain(heldBefore),
                plain(heldAfter),
                weightAfter,
                potentialGain == null ? null : money(potentialGain),
                potentialLoss == null ? null : money(potentialLoss),
                ratio
        );
    }


    private BigDecimal totalValue(
            PortfolioService.Snapshot snapshot
    ) {

        BigDecimal total = snapshot.ledger().cash();

        for (var holding : snapshot.ledger().holdings()) {

            if (!holding.isOpen()) {
                continue;
            }

            BigDecimal price =
                    marketDataService
                            .lastPrice(holding.symbol())
                            .orElse(holding.averagePrice());

            total = total.add(holding.quantity().multiply(price));
        }

        return total;
    }


    private Instant expiryOf(TimeInForce timeInForce) {

        if (timeInForce != TimeInForce.DAY) {
            return null;
        }

        ZoneId zone = ZoneId.systemDefault();

        return LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant();
    }


    private OrderPreviewResponse refused(
            String reason,
            String currency
    ) {

        return new OrderPreviewResponse(
                false, reason, List.of(), currency,
                null, null, null, null,
                null, null, null, null, 0,
                null, null, null, null, null,
                null, null, null, null, null,
                null, null, null
        );
    }


    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }


    private static BigDecimal plain(BigDecimal value) {

        BigDecimal stripped = value.stripTrailingZeros();

        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
