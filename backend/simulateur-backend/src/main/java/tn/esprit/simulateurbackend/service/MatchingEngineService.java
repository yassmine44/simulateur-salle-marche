package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.Trade;

import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.TradeRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


@Service
public class MatchingEngineService {

    private static final List<OrderStatus> OPEN_STATUSES =
            List.of(
                    OrderStatus.PENDING,
                    OrderStatus.PARTIALLY_FILLED
            );


    private final OrderRepository orderRepository;

    private final TradeRepository tradeRepository;


    public MatchingEngineService(
            OrderRepository orderRepository,
            TradeRepository tradeRepository
    ) {

        this.orderRepository =
                orderRepository;

        this.tradeRepository =
                tradeRepository;
    }


    // =========================================
    // MATCH ORDER
    // =========================================

    @Transactional
    public List<Trade> matchOrder(
            Long orderId
    ) {

        // =========================================
        // LOCK DE L'ORDRE ENTRANT
        // =========================================

        Order incomingOrder =
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


        /*
         * Seuls les ordres ouverts peuvent
         * participer au matching.
         */

        if (!isOpen(incomingOrder)) {

            return List.of();
        }


        // =========================================
        // RECHERCHE DES ORDRES OPPOSÉS
        // =========================================

        List<Order> candidates =
                findCandidates(
                        incomingOrder
                );


        List<Trade> executedTrades =
                new ArrayList<>();


        // =========================================
        // MATCHING
        // =========================================

        for (
                Order restingOrder
                : candidates
        ) {

            /*
             * L'ordre entrant est complètement
             * exécuté.
             */

            if (
                    incomingOrder
                            .getRemainingQuantity()
                            .compareTo(BigDecimal.ZERO)
                            <= 0
            ) {

                break;
            }


            /*
             * Protection supplémentaire.
             */

            if (!isOpen(restingOrder)) {

                continue;
            }


            /*
             * =====================================
             * QUANTITÉ EXÉCUTÉE
             * =====================================
             *
             * On prend la plus petite quantité
             * entre :
             *
             * quantité restante ordre entrant
             *
             * et
             *
             * quantité restante ordre resting.
             */

            BigDecimal executedQuantity =
                    incomingOrder
                            .getRemainingQuantity()
                            .min(
                                    restingOrder
                                            .getRemainingQuantity()
                            );


            // =====================================
            // PRIX D'EXÉCUTION
            // =====================================

            /*
             * L'ordre déjà présent dans le carnet
             * est le resting order.
             *
             * Son prix devient le prix
             * d'exécution.
             *
             * Exemple :
             *
             * ASK resting = 200
             * MARKET BUY
             *
             * => exécution à 200.
             */

            BigDecimal executionPrice =
                    restingOrder
                            .getLimitPrice();


            // =====================================
            // TRADE
            // =====================================

            Trade trade =
                    new Trade();


            trade.setAssetSymbol(
                    incomingOrder
                            .getAssetSymbol()
            );


            trade.setExecutionPrice(
                    executionPrice
            );


            trade.setQuantity(
                    executedQuantity
            );


            /*
             * Identifier correctement :
             *
             * BUY ORDER
             * SELL ORDER
             */

            if (
                    incomingOrder
                            .getSide()
                            == OrderSide.BUY
            ) {

                trade.setBuyOrder(
                        incomingOrder
                );

                trade.setSellOrder(
                        restingOrder
                );

            } else {

                trade.setBuyOrder(
                        restingOrder
                );

                trade.setSellOrder(
                        incomingOrder
                );
            }


            // =====================================
            // UPDATE QUANTITIES
            // =====================================

            BigDecimal incomingRemaining =
                    incomingOrder
                            .getRemainingQuantity()
                            .subtract(
                                    executedQuantity
                            );


            BigDecimal restingRemaining =
                    restingOrder
                            .getRemainingQuantity()
                            .subtract(
                                    executedQuantity
                            );


            incomingOrder.setRemainingQuantity(
                    incomingRemaining
            );


            restingOrder.setRemainingQuantity(
                    restingRemaining
            );


            // =====================================
            // UPDATE STATUSES
            // =====================================

            updateStatus(
                    incomingOrder
            );


            updateStatus(
                    restingOrder
            );


            // =====================================
            // DATABASE
            // =====================================

            orderRepository.save(
                    restingOrder
            );


            Trade savedTrade =
                    tradeRepository.save(
                            trade
                    );


            executedTrades.add(
                    savedTrade
            );
        }


        // =========================================
        // MARKET ORDER REMAINDER
        // =========================================

        /*
         * Un ordre MARKET ne doit jamais rester
         * dans le carnet.
         *
         * MARKET = exécution immédiate.
         *
         * Si le carnet ne contient pas assez
         * de liquidité, la quantité non exécutée
         * est annulée.
         *
         * Exemple :
         *
         * MARKET BUY 10
         *
         * seulement 6 disponibles
         *
         * => 6 exécutées
         * => 4 non exécutées
         * => ordre terminé en CANCELLED
         *
         * Les trades exécutés restent évidemment
         * enregistrés.
         */

        if (
                incomingOrder
                        .getType()
                        == OrderType.MARKET

                        &&

                        incomingOrder
                                .getRemainingQuantity()
                                .compareTo(BigDecimal.ZERO)
                                > 0
        ) {

            incomingOrder.setStatus(
                    OrderStatus.CANCELLED
            );
        }


        // =========================================
        // SAVE FINAL ORDER
        // =========================================

        orderRepository.save(
                incomingOrder
        );


        return executedTrades;
    }


    // =========================================
    // FIND CANDIDATES
    // =========================================

    private List<Order> findCandidates(
            Order incomingOrder
    ) {

        /*
         * =========================================
         * BUY
         * =========================================
         */

        if (
                incomingOrder
                        .getSide()
                        == OrderSide.BUY
        ) {

            /*
             * MARKET BUY
             *
             * Pas de prix maximum.
             *
             * On prend les ASK disponibles :
             *
             * prix le plus faible d'abord.
             */

            if (
                    incomingOrder
                            .getType()
                            == OrderType.MARKET
            ) {

                return orderRepository
                        .findMarketAsks(
                                incomingOrder
                                        .getAssetSymbol(),

                                OrderSide.SELL,

                                OPEN_STATUSES,

                                incomingOrder
                                        .getUser()
                                        .getId()
                        );
            }


            /*
             * LIMIT BUY
             *
             * On prend uniquement :
             *
             * ASK <= prix limite.
             */

            return orderRepository
                    .findMatchingAsks(
                            incomingOrder
                                    .getAssetSymbol(),

                            OrderSide.SELL,

                            OPEN_STATUSES,

                            incomingOrder
                                    .getLimitPrice(),

                            incomingOrder
                                    .getUser()
                                    .getId()
                    );
        }


        /*
         * =========================================
         * SELL
         * =========================================
         */

        /*
         * MARKET SELL
         *
         * Pas de prix minimum.
         *
         * On prend les BID disponibles :
         *
         * prix le plus élevé d'abord.
         */

        if (
                incomingOrder
                        .getType()
                        == OrderType.MARKET
        ) {

            return orderRepository
                    .findMarketBids(
                            incomingOrder
                                    .getAssetSymbol(),

                            OrderSide.BUY,

                            OPEN_STATUSES,

                            incomingOrder
                                    .getUser()
                                    .getId()
                    );
        }


        /*
         * LIMIT SELL
         *
         * On prend uniquement :
         *
         * BID >= prix limite.
         */

        return orderRepository
                .findMatchingBids(
                        incomingOrder
                                .getAssetSymbol(),

                        OrderSide.BUY,

                        OPEN_STATUSES,

                        incomingOrder
                                .getLimitPrice(),

                        incomingOrder
                                .getUser()
                                .getId()
                );
    }


    // =========================================
    // IS OPEN
    // =========================================

    private boolean isOpen(
            Order order
    ) {

        return (
                order.getStatus()
                        == OrderStatus.PENDING

                        ||

                        order.getStatus()
                                == OrderStatus.PARTIALLY_FILLED
        );
    }


    // =========================================
    // UPDATE STATUS
    // =========================================

    private void updateStatus(
            Order order
    ) {

        /*
         * Plus aucune quantité restante.
         */

        if (
                order
                        .getRemainingQuantity()
                        .compareTo(BigDecimal.ZERO)
                        == 0
        ) {

            order.setStatus(
                    OrderStatus.EXECUTED
            );

            return;
        }


        /*
         * Il reste une quantité.
         *
         * L'ordre a donc été exécuté
         * partiellement.
         */

        order.setStatus(
                OrderStatus.PARTIALLY_FILLED
        );
    }

}