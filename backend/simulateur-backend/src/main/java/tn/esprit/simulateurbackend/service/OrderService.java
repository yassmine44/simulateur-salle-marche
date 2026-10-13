package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.CreateOrderRequest;
import tn.esprit.simulateurbackend.dto.OrderResponse;

import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.User;

import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;


@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;

    private final UserRepository userRepository;

    private final MatchingEngineService matchingEngineService;

    private final AssetRepository assetRepository;


    public OrderService(
            OrderRepository orderRepository,
            UserRepository userRepository,
            MatchingEngineService matchingEngineService,
            AssetRepository assetRepository
    ) {

        this.orderRepository =
                orderRepository;

        this.userRepository =
                userRepository;

        this.matchingEngineService =
                matchingEngineService;

        this.assetRepository =
                assetRepository;
    }


    // =========================================
    // CREATE ORDER
    // =========================================

    public OrderResponse createOrder(
            String authenticatedEmail,
            CreateOrderRequest request
    ) {

        // =========================================
        // USER
        // =========================================

        User user =
                findAuthenticatedUser(
                        authenticatedEmail
                );


        // =========================================
        // ASSET SYMBOL
        // =========================================

        if (
                request.assetSymbol() == null
                        ||
                        request.assetSymbol().isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le symbole de l'actif est obligatoire."
            );
        }


        String symbol =
                request
                        .assetSymbol()
                        .trim()
                        .toUpperCase(Locale.ROOT);


        // =========================================
        // ASSET VALIDATION
        // =========================================

        assetRepository
                .findBySymbolIgnoreCaseAndActiveTrue(
                        symbol
                )
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Actif inconnu ou désactivé : "
                                                + symbol
                                )
                );


        // =========================================
        // SIDE
        // =========================================

        if (request.side() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le sens de l'ordre est obligatoire."
            );
        }


        // =========================================
        // QUANTITY
        // =========================================

        if (
                request.quantity() == null
                        ||
                        request.quantity()
                                .compareTo(BigDecimal.ZERO) <= 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La quantité doit être strictement positive."
            );
        }


        // =========================================
        // ORDER TYPE
        // =========================================

        if (request.type() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le type d'ordre est obligatoire."
            );
        }


        // =========================================
        // LIMIT PRICE
        // =========================================

        /*
         * LIMIT :
         * le prix limite est obligatoire.
         *
         * MARKET :
         * aucun prix limite n'est fourni.
         */

        if (
                request.type() == OrderType.LIMIT
                        &&
                        (
                                request.limitPrice() == null
                                        ||
                                        request.limitPrice()
                                                .compareTo(BigDecimal.ZERO) <= 0
                        )
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le prix limite doit être strictement positif pour un ordre LIMIT."
            );
        }


        // =========================================
        // MARKET PRICE VALIDATION
        // =========================================

        /*
         * Pour éviter une requête incohérente du type :
         *
         * MARKET + limitPrice = 200
         *
         * on ignore complètement le prix côté MARKET.
         *
         * Il sera déterminé par les ordres opposés
         * présents dans le carnet.
         */


        // =========================================
        // CREATE ORDER
        // =========================================

        Order order =
                new Order();


        order.setUser(
                user
        );


        order.setAssetSymbol(
                symbol
        );


        order.setSide(
                request.side()
        );


        order.setType(
                request.type()
        );


        /*
         * LIMIT  -> conserve le prix demandé.
         * MARKET -> aucun prix limite.
         */

        if (
                request.type()
                        == OrderType.LIMIT
        ) {

            order.setLimitPrice(
                    request.limitPrice()
            );

        } else {

            order.setLimitPrice(
                    null
            );
        }


        order.setQuantity(
                request.quantity()
        );


        order.setRemainingQuantity(
                request.quantity()
        );


        order.setStatus(
                OrderStatus.PENDING
        );


        // =========================================
        // DATABASE
        // =========================================

        Order savedOrder =
                orderRepository
                        .saveAndFlush(
                                order
                        );


        // =========================================
        // MATCHING ENGINE
        // =========================================

        /*
         * Le moteur décidera ensuite :
         *
         * LIMIT :
         * vérifier la compatibilité des prix.
         *
         * MARKET :
         * consommer directement les meilleurs
         * ordres disponibles du côté opposé.
         */

        matchingEngineService
                .matchOrder(
                        savedOrder.getId()
                );


        // =========================================
        // FINAL ORDER STATE
        // =========================================

        Order finalOrder =
                orderRepository
                        .findById(
                                savedOrder.getId()
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.INTERNAL_SERVER_ERROR,
                                                "Impossible de récupérer l'ordre créé."
                                        )
                        );


        return OrderResponse.from(
                finalOrder
        );
    }


    // =========================================
    // MY ORDERS
    // =========================================

    @Transactional(readOnly = true)
    public List<OrderResponse> getMyOrders(
            String authenticatedEmail
    ) {

        User user =
                findAuthenticatedUser(
                        authenticatedEmail
                );


        return orderRepository
                .findByUserIdOrderByCreatedAtDesc(
                        user.getId()
                )
                .stream()
                .map(
                        OrderResponse::from
                )
                .toList();
    }


    // =========================================
    // CANCEL ORDER
    // =========================================

    public OrderResponse cancelOrder(
            Long orderId,
            String authenticatedEmail
    ) {

        User user =
                findAuthenticatedUser(
                        authenticatedEmail
                );


        // =========================================
        // LOCK ORDER
        // =========================================

        Order order =
                orderRepository
                        .findByIdForUpdate(
                                orderId
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Ordre introuvable."
                                        )
                        );


        // =========================================
        // OWNERSHIP
        // =========================================

        if (
                !order
                        .getUser()
                        .getId()
                        .equals(
                                user.getId()
                        )
        ) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Vous ne pouvez pas annuler l'ordre d'un autre utilisateur."
            );
        }


        // =========================================
        // STATUS CHECK
        // =========================================

        if (
                order.getStatus()
                        == OrderStatus.EXECUTED
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Un ordre déjà exécuté ne peut pas être annulé."
            );
        }


        if (
                order.getStatus()
                        == OrderStatus.CANCELLED
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cet ordre est déjà annulé."
            );
        }


        if (
                order.getStatus()
                        == OrderStatus.REJECTED
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Un ordre rejeté ne peut pas être annulé."
            );
        }


        if (
                order.getStatus()
                        != OrderStatus.PENDING

                        &&

                        order.getStatus()
                                != OrderStatus.PARTIALLY_FILLED
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cet ordre ne peut pas être annulé."
            );
        }


        // =========================================
        // CANCEL
        // =========================================

        order.setStatus(
                OrderStatus.CANCELLED
        );


        Order savedOrder =
                orderRepository
                        .saveAndFlush(
                                order
                        );


        return OrderResponse.from(
                savedOrder
        );
    }


    // =========================================
    // AUTHENTICATED USER
    // =========================================

    private User findAuthenticatedUser(
            String authenticatedEmail
    ) {

        if (
                authenticatedEmail == null
                        ||
                        authenticatedEmail.isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Utilisateur non authentifié."
            );
        }


        String email =
                authenticatedEmail
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );


        return userRepository
                .findByEmailIgnoreCase(
                        email
                )
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Utilisateur introuvable."
                                )
                );
    }
}