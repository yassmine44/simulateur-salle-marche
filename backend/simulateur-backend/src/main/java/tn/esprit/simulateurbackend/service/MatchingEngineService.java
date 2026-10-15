package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.CancelReason;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.TimeInForce;
import tn.esprit.simulateurbackend.entity.Trade;
import tn.esprit.simulateurbackend.entity.TriggerType;

import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.TradeRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Moteur d'appariement : priorité prix puis temps, exécution
 * au prix de l'ordre au repos, jamais contre soi-même.
 *
 * Après chaque exécution : durée de validité (IOC / FOK),
 * ordres liés (bracket / OCO) et ordres conditionnels
 * (stop-loss, take-profit, stop suiveur).
 */
@Service
public class MatchingEngineService {

    private static final List<OrderStatus> OPEN_STATUSES =
            List.of(
                    OrderStatus.PENDING,
                    OrderStatus.PARTIALLY_FILLED
            );

    /*
     * Un déclenchement peut faire bouger le cours
     * et déclencher d'autres ordres : on borne la cascade.
     */
    private static final int MAX_TRIGGER_ROUNDS = 20;

    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final AssetRepository assetRepository;
    private final MarketDataService marketDataService;
    private final TradingRulesService tradingRulesService;
    private final LinkedOrderService linkedOrderService;
    private final OrderValidator orderValidator;

    public MatchingEngineService(
            OrderRepository orderRepository,
            TradeRepository tradeRepository,
            AssetRepository assetRepository,
            MarketDataService marketDataService,
            TradingRulesService tradingRulesService,
            LinkedOrderService linkedOrderService,
            OrderValidator orderValidator
    ) {
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.assetRepository = assetRepository;
        this.marketDataService = marketDataService;
        this.tradingRulesService = tradingRulesService;
        this.linkedOrderService = linkedOrderService;
        this.orderValidator = orderValidator;
    }


    // =========================================
    // MATCHING
    // =========================================

    @Transactional
    public List<Trade> matchOrder(
            Long orderId
    ) {

        Order incoming =
                orderRepository
                        .findByIdForUpdate(orderId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Ordre introuvable."
                                )
                        );

        if (!incoming.isOpen() || incoming.isWaitingTrigger()) {
            return List.of();
        }

        List<Order> candidates = findCandidates(incoming);

        // Tout ou rien : sans liquidité suffisante, rien n'est exécuté.
        if (
                incoming.effectiveTimeInForce() == TimeInForce.FOK
                        && fillableQuantity(candidates).compareTo(incoming.getRemainingQuantity()) < 0
        ) {

            incoming.cancel(CancelReason.FOK);

            orderRepository.save(incoming);

            return List.of();
        }

        List<Trade> trades = new ArrayList<>();

        // Ordres modifiés par ces exécutions (ordre entrant compris).
        Map<Long, Order> touched = new LinkedHashMap<>();

        for (Order resting : candidates) {

            if (incoming.getRemainingQuantity().signum() <= 0) {
                break;
            }

            if (!resting.isOpen()) {
                continue;
            }

            trades.add(execute(incoming, resting));

            touched.put(resting.getId(), resting);
        }

        applyTimeInForce(incoming, trades.isEmpty());

        orderRepository.save(incoming);

        touched.put(incoming.getId(), incoming);

        // Bracket / OCO : protections créées, agrandies ou annulées.
        if (!trades.isEmpty()) {

            orderRepository.flush();

            for (Long linked : linkedOrderService.afterExecution(touched.values())) {
                matchOrder(linked);
            }
        }

        return trades;
    }


    /*
     * Une exécution entre l'ordre entrant (agresseur)
     * et un ordre au repos, au prix de l'ordre au repos.
     */
    private Trade execute(
            Order incoming,
            Order resting
    ) {

        BigDecimal quantity =
                incoming.getRemainingQuantity()
                        .min(resting.getRemainingQuantity());

        Trade trade = new Trade();

        trade.setAssetSymbol(incoming.getAssetSymbol());
        trade.setExecutionPrice(resting.getLimitPrice());
        trade.setQuantity(quantity);
        trade.setAggressorSide(incoming.getSide());

        if (incoming.getSide() == OrderSide.BUY) {
            trade.setBuyOrder(incoming);
            trade.setSellOrder(resting);
        } else {
            trade.setBuyOrder(resting);
            trade.setSellOrder(incoming);
        }

        incoming.setRemainingQuantity(incoming.getRemainingQuantity().subtract(quantity));
        resting.setRemainingQuantity(resting.getRemainingQuantity().subtract(quantity));

        updateStatus(incoming);
        updateStatus(resting);

        orderRepository.save(resting);

        return tradeRepository.save(trade);
    }


    /*
     * Ce qui n'a pas été exécuté immédiatement :
     * - ordre au marché : annulé (ou rejeté si rien n'a été exécuté) ;
     * - ordre limite IOC : annulé ;
     * - ordre limite GTC / DAY : reste dans le carnet.
     */
    private void applyTimeInForce(
            Order order,
            boolean nothingExecuted
    ) {

        if (order.getRemainingQuantity().signum() <= 0) {
            return;
        }

        if (order.getType() == OrderType.MARKET) {

            if (nothingExecuted) {
                order.setStatus(OrderStatus.REJECTED);
                order.setCancelReason(CancelReason.NO_LIQUIDITY);
            } else {
                order.cancel(CancelReason.NO_LIQUIDITY);
            }

            return;
        }

        if (order.effectiveTimeInForce() == TimeInForce.IOC) {
            order.cancel(CancelReason.IOC);
        }
    }


    private BigDecimal fillableQuantity(
            List<Order> candidates
    ) {

        return candidates.stream()
                .filter(Order::isOpen)
                .map(Order::getRemainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


    // =========================================
    // CONDITIONAL ORDERS
    // =========================================

    /*
     * Fait suivre le cours aux stops suiveurs, puis déclenche
     * les ordres stop-loss / take-profit dont le seuil est atteint
     * et les exécute au marché.
     *
     * À appeler après chaque traitement susceptible d'avoir
     * produit des transactions.
     */
    @Transactional
    public int triggerConditionalOrders(
            String symbol
    ) {

        Asset asset =
                assetRepository
                        .findBySymbolIgnoreCase(symbol)
                        .orElse(null);

        int triggered = 0;

        for (int round = 0; round < MAX_TRIGGER_ROUNDS; round++) {

            BigDecimal lastPrice =
                    marketDataService
                            .lastPrice(symbol)
                            .orElse(null);

            if (lastPrice == null) {
                break;
            }

            List<Order> waiting =
                    orderRepository.findWaitingConditionalOrders(symbol);

            if (asset != null) {
                followPrice(asset, waiting, lastPrice);
            }

            List<Order> toTrigger =
                    waiting.stream()
                            .filter(order -> isTriggered(order, lastPrice))
                            .toList();

            if (toTrigger.isEmpty()) {
                break;
            }

            for (Order order : toTrigger) {

                order.setTriggeredAt(Instant.now());

                orderRepository.saveAndFlush(order);

                matchOrder(order.getId());

                triggered++;
            }
        }

        return triggered;
    }


    /*
     * Stop suiveur : le seuil se rapproche du cours quand celui-ci
     * évolue favorablement, et ne recule jamais.
     */
    private void followPrice(
            Asset asset,
            List<Order> waiting,
            BigDecimal lastPrice
    ) {

        for (Order order : waiting) {

            if (order.getTrailPercent() == null) {
                continue;
            }

            BigDecimal candidate =
                    orderValidator.trailingTrigger(
                            asset,
                            order.getSide(),
                            lastPrice,
                            order.getTrailPercent()
                    );

            boolean better =
                    order.getSide() == OrderSide.SELL
                            ? candidate.compareTo(order.getTriggerPrice()) > 0
                            : candidate.compareTo(order.getTriggerPrice()) < 0;

            if (better) {

                order.setTriggerPrice(candidate);

                orderRepository.save(order);
            }
        }
    }


    /*
     * STOP_LOSS   : vente si le cours baisse jusqu'au seuil,
     *               achat s'il monte jusqu'au seuil.
     * TAKE_PROFIT : vente si le cours monte jusqu'au seuil,
     *               achat s'il baisse jusqu'au seuil.
     */
    public static boolean isTriggered(
            TriggerType triggerType,
            OrderSide side,
            BigDecimal triggerPrice,
            BigDecimal lastPrice
    ) {

        boolean triggersWhenPriceFalls =
                (triggerType == TriggerType.STOP_LOSS)
                        == (side == OrderSide.SELL);

        return triggersWhenPriceFalls
                ? lastPrice.compareTo(triggerPrice) <= 0
                : lastPrice.compareTo(triggerPrice) >= 0;
    }


    private boolean isTriggered(
            Order order,
            BigDecimal lastPrice
    ) {

        return isTriggered(
                order.getTriggerType(),
                order.getSide(),
                order.getTriggerPrice(),
                lastPrice
        );
    }


    // =========================================
    // CANDIDATES
    // =========================================

    private List<Order> findCandidates(
            Order incoming
    ) {

        BigDecimal priceLimit = priceLimit(incoming);

        Long userId = incoming.getUser().getId();

        if (incoming.getSide() == OrderSide.BUY) {

            return orderRepository.findMatchingAsks(
                    incoming.getAssetSymbol(),
                    OrderSide.SELL,
                    OPEN_STATUSES,
                    priceLimit,
                    userId
            );
        }

        return orderRepository.findMatchingBids(
                incoming.getAssetSymbol(),
                OrderSide.BUY,
                OPEN_STATUSES,
                priceLimit,
                userId
        );
    }


    /*
     * Ordre limite : son prix limite.
     * Ordre au marché : la borne de protection autour du dernier cours.
     */
    private BigDecimal priceLimit(
            Order incoming
    ) {

        if (incoming.getType() == OrderType.LIMIT) {
            return incoming.getLimitPrice();
        }

        return tradingRulesService.marketPriceLimit(
                incoming.getSide(),
                marketDataService
                        .lastPrice(incoming.getAssetSymbol())
                        .orElse(null)
        );
    }


    private void updateStatus(
            Order order
    ) {

        order.setStatus(
                order.getRemainingQuantity().signum() == 0
                        ? OrderStatus.EXECUTED
                        : OrderStatus.PARTIALLY_FILLED
        );
    }
}
