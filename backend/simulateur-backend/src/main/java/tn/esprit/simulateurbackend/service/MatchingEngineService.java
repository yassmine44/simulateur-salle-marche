package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
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
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
    }


    @Transactional
    public List<Trade> matchOrder(
            Long orderId
    ) {

        Order incomingOrder =
                orderRepository
                        .findByIdForUpdate(orderId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Ordre introuvable."
                                )
                        );


        if (!isOpen(incomingOrder)) {
            return List.of();
        }


        List<Order> candidates =
                findCandidates(incomingOrder);


        List<Trade> executedTrades =
                new ArrayList<>();


        for (Order restingOrder : candidates) {

            if (
                    incomingOrder
                            .getRemainingQuantity()
                            .compareTo(BigDecimal.ZERO)
                            <= 0
            ) {
                break;
            }


            if (!isOpen(restingOrder)) {
                continue;
            }


            // =====================================
            // EXECUTED QUANTITY
            // =====================================

            BigDecimal executedQuantity =
                    incomingOrder
                            .getRemainingQuantity()
                            .min(
                                    restingOrder
                                            .getRemainingQuantity()
                            );


            // =====================================
            // EXECUTION PRICE
            // =====================================

            /*
             * Le nouvel ordre est l'agresseur.
             *
             * L'ordre déjà présent dans le carnet
             * est l'ordre resting.
             *
             * On utilise donc son prix comme
             * prix d'exécution.
             */
            BigDecimal executionPrice =
                    restingOrder.getLimitPrice();


            // =====================================
            // TRADE
            // =====================================

            Trade trade = new Trade();

            trade.setAssetSymbol(
                    incomingOrder.getAssetSymbol()
            );

            trade.setExecutionPrice(
                    executionPrice
            );

            trade.setQuantity(
                    executedQuantity
            );


            if (
                    incomingOrder.getSide()
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

            executedTrades.add(
                    tradeRepository.save(trade)
            );
        }


        orderRepository.save(
                incomingOrder
        );


        return executedTrades;
    }


    private List<Order> findCandidates(
            Order incomingOrder
    ) {

        if (
                incomingOrder.getSide()
                        == OrderSide.BUY
        ) {

            return orderRepository
                    .findMatchingAsks(
                            incomingOrder.getAssetSymbol(),

                            OrderSide.SELL,

                            OPEN_STATUSES,

                            incomingOrder.getLimitPrice(),

                            incomingOrder
                                    .getUser()
                                    .getId()
                    );
        }


        return orderRepository
                .findMatchingBids(
                        incomingOrder.getAssetSymbol(),

                        OrderSide.BUY,

                        OPEN_STATUSES,

                        incomingOrder.getLimitPrice(),

                        incomingOrder
                                .getUser()
                                .getId()
                );
    }


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


    private void updateStatus(
            Order order
    ) {

        if (
                order.getRemainingQuantity()
                        .compareTo(BigDecimal.ZERO)
                        == 0
        ) {

            order.setStatus(
                    OrderStatus.EXECUTED
            );

        } else {

            order.setStatus(
                    OrderStatus.PARTIALLY_FILLED
            );
        }
    }
}