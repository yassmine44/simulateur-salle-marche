package tn.esprit.simulateurbackend.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.CreateOcoRequest;
import tn.esprit.simulateurbackend.dto.CreateOrderRequest;
import tn.esprit.simulateurbackend.dto.ModifyOrderRequest;
import tn.esprit.simulateurbackend.dto.OrderResponse;

import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.CancelReason;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.TimeInForce;
import tn.esprit.simulateurbackend.entity.User;

import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;
import tn.esprit.simulateurbackend.service.OrderValidator.ValidatedOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/*
 * Cycle de vie d'un ordre : création, modification, annulation.
 *
 * Création : validation (OrderValidator) -> contrôle du portefeuille
 * (PreTradeCheckService) -> enregistrement -> appariement
 * (MatchingEngineService) -> déclenchements -> notification temps réel.
 */
@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final MatchingEngineService matchingEngineService;
    private final MarketDataService marketDataService;
    private final OrderValidator orderValidator;
    private final PreTradeCheckService preTradeCheckService;
    private final LinkedOrderService linkedOrderService;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(
            OrderRepository orderRepository,
            UserRepository userRepository,
            AssetRepository assetRepository,
            MatchingEngineService matchingEngineService,
            MarketDataService marketDataService,
            OrderValidator orderValidator,
            PreTradeCheckService preTradeCheckService,
            LinkedOrderService linkedOrderService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
        this.matchingEngineService = matchingEngineService;
        this.marketDataService = marketDataService;
        this.orderValidator = orderValidator;
        this.preTradeCheckService = preTradeCheckService;
        this.linkedOrderService = linkedOrderService;
        this.eventPublisher = eventPublisher;
    }


    // =========================================
    // CREATE ORDER
    // =========================================

    public OrderResponse createOrder(
            String authenticatedEmail,
            CreateOrderRequest request
    ) {

        User user = findAuthenticatedUser(authenticatedEmail);

        ValidatedOrder validated = orderValidator.validate(user, request);

        // Fonds suffisants pour acheter, titres suffisants pour vendre.
        preTradeCheckService.check(
                user,
                validated.asset(),
                validated.side(),
                validated.type(),
                validated.triggerType(),
                validated.quantity(),
                validated.limitPrice(),
                validated.triggerPrice(),
                validated.lastPrice()
        );

        Order order = new Order();

        order.setUser(user);
        order.setAssetSymbol(validated.symbol());
        order.setSide(validated.side());
        order.setType(validated.type());
        order.setLimitPrice(validated.limitPrice());
        order.setTriggerType(validated.triggerType());
        order.setTriggerPrice(validated.triggerPrice());
        order.setTrailPercent(validated.trailPercent());
        order.setTimeInForce(validated.timeInForce());
        order.setExpiresAt(expiryOf(validated.timeInForce()));
        order.setBracketStopPrice(validated.stopLossPrice());
        order.setBracketTakeProfitPrice(validated.takeProfitPrice());
        order.setQuantity(validated.quantity());
        order.setRemainingQuantity(validated.quantity());
        order.setStatus(OrderStatus.PENDING);

        // Référence de la qualité d'exécution.
        order.setArrivalMidPrice(
                marketDataService.midPrice(validated.asset()).orElse(null)
        );

        Order saved = orderRepository.saveAndFlush(order);

        // Un ordre conditionnel attend son seuil hors du carnet :
        // matchOrder ne l'apparie pas.
        matchingEngineService.matchOrder(saved.getId());

        // Les transactions ont pu faire bouger le cours
        // jusqu'au seuil d'ordres conditionnels.
        matchingEngineService.triggerConditionalOrders(validated.symbol());

        eventPublisher.publishEvent(new OrderBookChangedEvent(validated.symbol()));

        return OrderResponse.from(reload(saved.getId()));
    }


    /*
     * Fin de la journée en cours (minuit, fuseau du serveur)
     * pour un ordre DAY.
     */
    private Instant expiryOf(
            TimeInForce timeInForce
    ) {

        if (timeInForce != TimeInForce.DAY) {
            return null;
        }

        ZoneId zone = ZoneId.systemDefault();

        return LocalDate.now(zone)
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant();
    }


    // =========================================
    // OCO (protéger une position)
    // =========================================

    /*
     * Deux ventes liées sur des titres détenus : take-profit au-dessus
     * du cours, stop-loss en dessous. La première exécutée annule l'autre.
     */
    public List<OrderResponse> createOco(
            String authenticatedEmail,
            CreateOcoRequest request
    ) {

        User user = findAuthenticatedUser(authenticatedEmail);

        String symbol =
                request.assetSymbol()
                        .trim()
                        .toUpperCase(Locale.ROOT);

        Asset asset =
                assetRepository
                        .findBySymbolIgnoreCaseAndActiveTrue(symbol)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Actif inconnu ou désactivé : " + symbol
                                )
                        );

        BigDecimal lastPrice = marketDataService.lastPrice(asset).orElse(null);

        BigDecimal stopLossPrice =
                request.trailPercent() == null
                        ? request.stopLossPrice()
                        : orderValidator.trailingTrigger(
                        asset,
                        OrderSide.SELL,
                        lastPrice == null ? request.stopLossPrice() : lastPrice,
                        request.trailPercent()
                );

        orderValidator.validateOco(asset, request.takeProfitPrice(), stopLossPrice, lastPrice);

        // Les deux branches protègent les mêmes titres : contrôlés une seule fois.
        preTradeCheckService.check(
                user,
                asset,
                OrderSide.SELL,
                OrderType.LIMIT,
                null,
                request.quantity(),
                request.takeProfitPrice(),
                null,
                lastPrice
        );

        List<Order> legs =
                linkedOrderService.createOco(
                        user,
                        asset,
                        request.quantity(),
                        request.takeProfitPrice(),
                        stopLossPrice,
                        request.trailPercent()
                );

        BigDecimal mid = marketDataService.midPrice(asset).orElse(null);

        legs.forEach(leg -> leg.setArrivalMidPrice(mid));

        orderRepository.saveAllAndFlush(legs);

        // Le take-profit peut déjà croiser un acheteur.
        legs.stream()
                .filter(leg -> leg.getType() == OrderType.LIMIT)
                .forEach(leg -> matchingEngineService.matchOrder(leg.getId()));

        matchingEngineService.triggerConditionalOrders(symbol);

        eventPublisher.publishEvent(new OrderBookChangedEvent(symbol));

        return legs.stream()
                .map(leg -> OrderResponse.from(reload(leg.getId())))
                .toList();
    }


    // =========================================
    // MY ORDERS
    // =========================================

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(
            String authenticatedEmail
    ) {

        User user = findAuthenticatedUser(authenticatedEmail);

        return orderRepository
                .findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(OrderResponse::from)
                .toList();
    }


    @Transactional(readOnly = true)
    public String findOrderSymbol(
            Long orderId
    ) {

        return orderRepository
                .findById(orderId)
                .map(Order::getAssetSymbol)
                .orElseThrow(this::orderNotFound);
    }


    // =========================================
    // MODIFY ORDER
    // =========================================

    /*
     * Modification = annulation + remplacement.
     *
     * L'ordre d'origine est annulé et un nouvel ordre est créé avec
     * le nouveau prix et la nouvelle quantité : il perd sa priorité
     * temporelle, comme sur un marché réel. Il garde sa durée de
     * validité. Si le nouvel ordre est refusé, la transaction est
     * annulée et l'ordre d'origine reste intact.
     */
    public OrderResponse modifyOrder(
            Long orderId,
            String authenticatedEmail,
            ModifyOrderRequest request
    ) {

        User user = findAuthenticatedUser(authenticatedEmail);

        Order order =
                findOwnOrderForUpdate(
                        orderId,
                        user,
                        "Vous ne pouvez pas modifier l'ordre d'un autre utilisateur."
                );

        if (order.getType() != OrderType.LIMIT || !order.isOpen()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Seul un ordre limite encore ouvert peut être modifié."
            );
        }

        if (order.getOcoGroup() != null) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cet ordre fait partie d'une protection (bracket ou OCO) : "
                            + "annulez la protection et recréez-la avec les nouveaux prix."
            );
        }

        order.cancel(CancelReason.REPLACED);

        orderRepository.saveAndFlush(order);

        return createOrder(
                authenticatedEmail,
                new CreateOrderRequest(
                        order.getAssetSymbol(),
                        order.getSide(),
                        OrderType.LIMIT,
                        request.quantity(),
                        request.limitPrice()
                ).withTimeInForce(order.effectiveTimeInForce())
        );
    }


    // =========================================
    // CANCEL ORDER
    // =========================================

    public OrderResponse cancelOrder(
            Long orderId,
            String authenticatedEmail
    ) {

        User user = findAuthenticatedUser(authenticatedEmail);

        Order order =
                findOwnOrderForUpdate(
                        orderId,
                        user,
                        "Vous ne pouvez pas annuler l'ordre d'un autre utilisateur."
                );

        switch (order.getStatus()) {

            case EXECUTED -> throw conflict("Un ordre déjà exécuté ne peut pas être annulé.");

            case CANCELLED -> throw conflict("Cet ordre est déjà annulé.");

            case REJECTED -> throw conflict("Un ordre rejeté ne peut pas être annulé.");

            default -> {
                // PENDING ou PARTIALLY_FILLED : annulable.
            }
        }

        order.cancel(CancelReason.USER);

        Order saved = orderRepository.saveAndFlush(order);

        // Annuler une branche annule toute la protection.
        linkedOrderService.cancelSiblings(saved, CancelReason.USER);

        eventPublisher.publishEvent(new OrderBookChangedEvent(saved.getAssetSymbol()));

        return OrderResponse.from(saved);
    }


    // =========================================
    // HELPERS
    // =========================================

    private Order reload(Long orderId) {

        return orderRepository
                .findById(orderId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Impossible de récupérer l'ordre créé."
                        )
                );
    }


    private Order findOwnOrderForUpdate(
            Long orderId,
            User user,
            String forbiddenMessage
    ) {

        Order order =
                orderRepository
                        .findByIdForUpdate(orderId)
                        .orElseThrow(this::orderNotFound);

        if (!order.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }

        return order;
    }


    private User findAuthenticatedUser(
            String authenticatedEmail
    ) {

        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Utilisateur non authentifié."
            );
        }

        return userRepository
                .findByEmailIgnoreCase(
                        authenticatedEmail.trim().toLowerCase(Locale.ROOT)
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Utilisateur introuvable."
                        )
                );
    }


    private ResponseStatusException orderNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Ordre introuvable.");
    }


    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
