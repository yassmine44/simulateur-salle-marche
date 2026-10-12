package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.simulateurbackend.dto.OrderBookLevelResponse;
import tn.esprit.simulateurbackend.dto.OrderBookResponse;

import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;

import tn.esprit.simulateurbackend.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class OrderBookService {

    private static final List<OrderStatus> OPEN_STATUSES =
            List.of(
                    OrderStatus.PENDING,
                    OrderStatus.PARTIALLY_FILLED
            );

    private final OrderRepository orderRepository;

    public OrderBookService(
            OrderRepository orderRepository
    ) {
        this.orderRepository = orderRepository;
    }

    public OrderBookResponse getOrderBook(
            String rawSymbol
    ) {

        String symbol = normalizeSymbol(rawSymbol);

        List<Order> bids =
                orderRepository
                        .findByAssetSymbolIgnoreCaseAndSideAndStatusInOrderByLimitPriceDescCreatedAtAsc(
                                symbol,
                                OrderSide.BUY,
                                OPEN_STATUSES
                        );

        List<Order> asks =
                orderRepository
                        .findByAssetSymbolIgnoreCaseAndSideAndStatusInOrderByLimitPriceAscCreatedAtAsc(
                                symbol,
                                OrderSide.SELL,
                                OPEN_STATUSES
                        );

        return new OrderBookResponse(
                symbol,
                aggregateLevels(bids),
                aggregateLevels(asks),
                Instant.now()
        );
    }

    private List<OrderBookLevelResponse> aggregateLevels(
            List<Order> orders
    ) {

        Map<BigDecimal, LevelAccumulator> levels =
                new LinkedHashMap<>();

        for (Order order : orders) {

            if (order.getLimitPrice() == null) {
                continue;
            }

            if (
                    order.getRemainingQuantity() == null
                            || order.getRemainingQuantity()
                            .compareTo(BigDecimal.ZERO) <= 0
            ) {
                continue;
            }

            BigDecimal normalizedPrice =
                    order.getLimitPrice()
                            .stripTrailingZeros();

            LevelAccumulator accumulator =
                    levels.computeIfAbsent(
                            normalizedPrice,
                            ignored ->
                                    new LevelAccumulator()
                    );

            accumulator.totalQuantity =
                    accumulator.totalQuantity.add(
                            order.getRemainingQuantity()
                    );

            accumulator.orderCount++;
        }

        List<OrderBookLevelResponse> response =
                new ArrayList<>();

        for (
                Map.Entry<BigDecimal, LevelAccumulator> entry
                : levels.entrySet()
        ) {

            response.add(
                    new OrderBookLevelResponse(
                            entry.getKey(),
                            entry.getValue().totalQuantity,
                            entry.getValue().orderCount
                    )
            );
        }

        return response;
    }

    private String normalizeSymbol(
            String rawSymbol
    ) {

        if (
                rawSymbol == null
                        || rawSymbol.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Le symbole de l'actif est obligatoire."
            );
        }

        return rawSymbol
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private static class LevelAccumulator {

        private BigDecimal totalQuantity =
                BigDecimal.ZERO;

        private int orderCount = 0;
    }
}