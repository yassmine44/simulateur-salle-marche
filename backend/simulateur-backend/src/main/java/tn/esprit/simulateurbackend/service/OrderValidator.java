package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.CreateOrderRequest;
import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.TimeInForce;
import tn.esprit.simulateurbackend.entity.TriggerType;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.repository.OrderRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/*
 * Règles d'un ordre, appliquées à l'identique pour passer
 * un ordre et pour en calculer l'aperçu.
 *
 * Chaque refus lève une ResponseStatusException dont le message
 * explique la cause et ce que l'utilisateur peut faire.
 */
@Component
public class OrderValidator {

    private static final List<OrderStatus> OPEN_STATUSES =
            List.of(
                    OrderStatus.PENDING,
                    OrderStatus.PARTIALLY_FILLED
            );

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AssetRepository assetRepository;
    private final OrderRepository orderRepository;
    private final MarketDataService marketDataService;
    private final TradingRulesService tradingRulesService;

    public OrderValidator(
            AssetRepository assetRepository,
            OrderRepository orderRepository,
            MarketDataService marketDataService,
            TradingRulesService tradingRulesService
    ) {
        this.assetRepository = assetRepository;
        this.orderRepository = orderRepository;
        this.marketDataService = marketDataService;
        this.tradingRulesService = tradingRulesService;
    }


    /*
     * Ordre validé, normalisé et prêt à être enregistré.
     */
    public record ValidatedOrder(
            Asset asset,
            String symbol,
            OrderSide side,
            OrderType type,
            TriggerType triggerType,
            BigDecimal quantity,
            BigDecimal limitPrice,
            BigDecimal triggerPrice,
            BigDecimal trailPercent,
            TimeInForce timeInForce,
            BigDecimal stopLossPrice,
            BigDecimal takeProfitPrice,
            BigDecimal lastPrice
    ) {

        public boolean isConditional() {
            return triggerType != null;
        }

        public boolean hasBracket() {
            return stopLossPrice != null || takeProfitPrice != null;
        }
    }


    public ValidatedOrder validate(
            User user,
            CreateOrderRequest request
    ) {

        // ---- Actif ----

        if (
                request.assetSymbol() == null
                        || request.assetSymbol().isBlank()
        ) {
            throw badRequest("Le symbole de l'actif est obligatoire.");
        }

        String symbol =
                request.assetSymbol()
                        .trim()
                        .toUpperCase(Locale.ROOT);

        Asset asset =
                assetRepository
                        .findBySymbolIgnoreCaseAndActiveTrue(symbol)
                        .orElseThrow(() ->
                                badRequest("Actif inconnu ou désactivé : " + symbol)
                        );

        // ---- Sens, quantité, type ----

        if (request.side() == null) {
            throw badRequest("Le sens de l'ordre est obligatoire.");
        }

        if (
                request.quantity() == null
                        || request.quantity().signum() <= 0
        ) {
            throw badRequest("La quantité doit être strictement positive.");
        }

        tradingRulesService.validateQuantity(asset, request.quantity());

        if (request.type() == null) {
            throw badRequest("Le type d'ordre est obligatoire.");
        }

        BigDecimal lastPrice =
                marketDataService
                        .lastPrice(asset)
                        .orElse(null);

        TriggerType triggerType = request.triggerType();

        TimeInForce timeInForce =
                resolveTimeInForce(request.type(), triggerType, request.timeInForce());

        BigDecimal limitPrice = null;
        BigDecimal triggerPrice = null;
        BigDecimal trailPercent = null;

        if (request.type() == OrderType.LIMIT) {

            // ---- Ordre limite ----

            if (triggerType != null) {
                throw badRequest(
                        "Un ordre stop-loss ou take-profit s'exécute au marché : "
                                + "il ne peut pas être de type LIMIT."
                );
            }

            limitPrice = request.limitPrice();

            if (limitPrice == null || limitPrice.signum() <= 0) {
                throw badRequest("Le prix limite doit être strictement positif.");
            }

            tradingRulesService.validatePrice(asset, limitPrice, lastPrice, "prix limite");

            rejectSelfCrossingOrder(user, symbol, request.side(), limitPrice);

        } else if (triggerType != null) {

            // ---- Stop-loss / take-profit / stop suiveur ----

            if (lastPrice == null) {
                throw conflict(
                        "Aucun cours disponible pour " + symbol
                                + " : impossible de placer un ordre conditionnel."
                );
            }

            if (request.trailPercent() != null) {

                if (triggerType != TriggerType.STOP_LOSS) {
                    throw badRequest(
                            "Seul un stop-loss peut être suiveur : un take-profit "
                                    + "attend que le cours atteigne un objectif fixe."
                    );
                }

                trailPercent = request.trailPercent();

                // Le seuil initial est calculé à partir du cours.
                triggerPrice =
                        trailingTrigger(asset, request.side(), lastPrice, trailPercent);

            } else {

                triggerPrice = request.triggerPrice();

                if (triggerPrice == null || triggerPrice.signum() <= 0) {
                    throw badRequest("Le prix de déclenchement doit être strictement positif.");
                }

                tradingRulesService.validatePrice(
                        asset, triggerPrice, lastPrice, "prix de déclenchement"
                );

                rejectImmediateTrigger(triggerType, request.side(), triggerPrice, lastPrice);
            }

        } else if (request.trailPercent() != null) {

            throw badRequest(
                    "Un stop suiveur est un stop-loss : choisissez le type Stop-loss."
            );
        }

        // ---- Bracket ----

        BigDecimal stopLossPrice = request.stopLossPrice();
        BigDecimal takeProfitPrice = request.takeProfitPrice();

        if (stopLossPrice != null || takeProfitPrice != null) {

            validateBracket(
                    asset,
                    request.side(),
                    triggerType,
                    limitPrice != null ? limitPrice : lastPrice,
                    lastPrice,
                    stopLossPrice,
                    takeProfitPrice
            );
        }

        return new ValidatedOrder(
                asset,
                symbol,
                request.side(),
                request.type(),
                triggerType,
                request.quantity(),
                limitPrice,
                triggerPrice,
                trailPercent,
                timeInForce,
                stopLossPrice,
                takeProfitPrice,
                lastPrice
        );
    }


    // =========================================
    // TIME IN FORCE
    // =========================================

    private TimeInForce resolveTimeInForce(
            OrderType type,
            TriggerType triggerType,
            TimeInForce requested
    ) {

        if (type == OrderType.MARKET && triggerType == null) {

            // Un ordre au marché est toujours immédiat :
            // le reste est annulé (IOC) sauf si l'on exige tout (FOK).
            if (requested == TimeInForce.GTC || requested == TimeInForce.DAY) {
                throw badRequest(
                        "Un ordre au marché s'exécute immédiatement : il ne peut pas "
                                + "rester en attente. Choisissez IOC ou FOK, ou un ordre limite."
                );
            }

            return requested == null ? TimeInForce.IOC : requested;
        }

        if (triggerType != null && (requested == TimeInForce.IOC || requested == TimeInForce.FOK)) {
            throw badRequest(
                    "Un ordre stop-loss ou take-profit attend son seuil : "
                            + "il ne peut être que GTC (jusqu'à annulation) ou DAY (journée)."
            );
        }

        return requested == null ? TimeInForce.GTC : requested;
    }


    // =========================================
    // TRAILING STOP
    // =========================================

    /*
     * Seuil d'un stop suiveur : vente sous le cours, achat au-dessus,
     * arrondi au pas de cotation dans le sens prudent.
     */
    public BigDecimal trailingTrigger(
            Asset asset,
            OrderSide side,
            BigDecimal referencePrice,
            BigDecimal trailPercent
    ) {

        BigDecimal factor =
                side == OrderSide.SELL
                        ? HUNDRED.subtract(trailPercent)
                        : HUNDRED.add(trailPercent);

        BigDecimal raw =
                referencePrice
                        .multiply(factor)
                        .divide(HUNDRED, 8, RoundingMode.HALF_UP);

        BigDecimal tick = asset.getTickSize();

        if (tick == null || tick.signum() <= 0) {
            return raw;
        }

        return raw
                .divide(
                        tick,
                        0,
                        side == OrderSide.SELL ? RoundingMode.FLOOR : RoundingMode.CEILING
                )
                .multiply(tick);
    }


    // =========================================
    // BRACKET
    // =========================================

    private void validateBracket(
            Asset asset,
            OrderSide side,
            TriggerType triggerType,
            BigDecimal entryPrice,
            BigDecimal lastPrice,
            BigDecimal stopLossPrice,
            BigDecimal takeProfitPrice
    ) {

        if (side != OrderSide.BUY || triggerType != null) {
            throw badRequest(
                    "Un stop-loss et un take-profit ne peuvent être attachés qu'à un "
                            + "achat (limite ou au marché). Pour protéger des titres déjà "
                            + "détenus, utilisez un ordre OCO."
            );
        }

        if (entryPrice == null) {
            throw conflict("Aucun cours disponible : impossible de placer les protections.");
        }

        if (stopLossPrice != null) {

            tradingRulesService.validatePrice(asset, stopLossPrice, lastPrice, "prix du stop-loss");

            if (stopLossPrice.compareTo(entryPrice) >= 0) {
                throw badRequest(
                        "Le stop-loss (" + plain(stopLossPrice) + ") doit être inférieur au prix "
                                + "d'achat (" + plain(entryPrice) + ") : il limite la perte "
                                + "si le cours baisse."
                );
            }
        }

        if (takeProfitPrice != null) {

            tradingRulesService.validatePrice(asset, takeProfitPrice, lastPrice, "prix du take-profit");

            if (takeProfitPrice.compareTo(entryPrice) <= 0) {
                throw badRequest(
                        "Le take-profit (" + plain(takeProfitPrice) + ") doit être supérieur au prix "
                                + "d'achat (" + plain(entryPrice) + ") : il encaisse le gain "
                                + "si le cours monte."
                );
            }
        }
    }


    // =========================================
    // OCO (protection d'une position)
    // =========================================

    public void validateOco(
            Asset asset,
            BigDecimal takeProfitPrice,
            BigDecimal stopLossPrice,
            BigDecimal lastPrice
    ) {

        if (lastPrice == null) {
            throw conflict("Aucun cours disponible pour " + asset.getSymbol() + ".");
        }

        tradingRulesService.validatePrice(asset, takeProfitPrice, lastPrice, "prix du take-profit");
        tradingRulesService.validatePrice(asset, stopLossPrice, lastPrice, "prix du stop-loss");

        if (takeProfitPrice.compareTo(lastPrice) <= 0) {
            throw badRequest(
                    "Le take-profit (" + plain(takeProfitPrice) + ") doit être supérieur au "
                            + "dernier cours (" + plain(lastPrice) + ")."
            );
        }

        if (stopLossPrice.compareTo(lastPrice) >= 0) {
            throw badRequest(
                    "Le stop-loss (" + plain(stopLossPrice) + ") doit être inférieur au "
                            + "dernier cours (" + plain(lastPrice) + ")."
            );
        }
    }


    // =========================================
    // SHARED RULES
    // =========================================

    private void rejectImmediateTrigger(
            TriggerType triggerType,
            OrderSide side,
            BigDecimal triggerPrice,
            BigDecimal lastPrice
    ) {

        if (!MatchingEngineService.isTriggered(triggerType, side, triggerPrice, lastPrice)) {
            return;
        }

        // Stop-loss de vente et take-profit d'achat se déclenchent
        // à la baisse, les deux autres à la hausse.
        boolean triggersWhenPriceFalls =
                (triggerType == TriggerType.STOP_LOSS) == (side == OrderSide.SELL);

        throw badRequest(
                "Un "
                        + (triggerType == TriggerType.STOP_LOSS ? "stop-loss" : "take-profit")
                        + (side == OrderSide.SELL ? " de vente" : " d'achat")
                        + " se déclenche quand le cours "
                        + (triggersWhenPriceFalls ? "descend" : "monte")
                        + " jusqu'au prix indiqué. Ce prix doit donc être "
                        + (triggersWhenPriceFalls ? "inférieur" : "supérieur")
                        + " au dernier cours (" + plain(lastPrice) + "), or vous avez saisi "
                        + plain(triggerPrice) + " : l'ordre partirait immédiatement."
        );
    }


    /*
     * Les ordres d'un même utilisateur ne s'exécutent jamais entre eux :
     * un ordre qui croiserait l'un des siens laisserait le carnet croisé.
     */
    private void rejectSelfCrossingOrder(
            User user,
            String symbol,
            OrderSide side,
            BigDecimal limitPrice
    ) {

        OrderSide oppositeSide =
                side == OrderSide.BUY ? OrderSide.SELL : OrderSide.BUY;

        orderRepository
                .findOpenLimitOrdersOfUser(user.getId(), symbol, oppositeSide, OPEN_STATUSES)
                .stream()
                .filter(own ->
                        side == OrderSide.BUY
                                ? own.getLimitPrice().compareTo(limitPrice) <= 0
                                : own.getLimitPrice().compareTo(limitPrice) >= 0
                )
                .findFirst()
                .ifPresent(own -> {
                    throw selfCrossing(own, side, symbol);
                });
    }


    private ResponseStatusException selfCrossing(
            Order own,
            OrderSide side,
            String symbol
    ) {

        boolean buying = side == OrderSide.BUY;

        String ownPrice = plain(own.getLimitPrice());

        return conflict(
                "Vous avez déjà un ordre " + (buying ? "de vente" : "d'achat")
                        + " #" + own.getId() + " à " + ownPrice + " sur " + symbol + ". "
                        + (buying ? "Un achat" : "Une vente") + " à " + ownPrice
                        + (buying ? " ou plus" : " ou moins")
                        + " s'exécuterait contre votre propre ordre, ce qui est interdit : "
                        + "on ne négocie pas avec soi-même. Indiquez un prix "
                        + (buying ? "inférieur" : "supérieur") + " à " + ownPrice
                        + ", ou annulez / modifiez d'abord l'ordre #" + own.getId() + "."
        );
    }


    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }


    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }


    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
