package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.OrderBookLevelResponse;
import tn.esprit.simulateurbackend.dto.OrderBookResponse;

import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.User;

import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;

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
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;

    public OrderBookService(
            OrderRepository orderRepository,
            AssetRepository assetRepository,
            UserRepository userRepository
    ) {
        this.orderRepository = orderRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
    }

    public OrderBookResponse getOrderBook(
            String rawSymbol
    ) {

        return getOrderBook(rawSymbol, null);
    }

    /*
     * viewerEmail : utilisateur connecté, pour indiquer
     * la part de chaque niveau qui lui appartient.
     */
    public OrderBookResponse getOrderBook(
            String rawSymbol,
            String viewerEmail
    ) {

        String symbol = resolveActiveSymbol(rawSymbol);

        Long viewerId =
                viewerEmail == null
                        ? null
                        : userRepository
                        .findByEmailIgnoreCase(viewerEmail.trim())
                        .map(User::getId)
                        .orElse(null);

        List<Order> bids =
                orderRepository.findBookBids(
                        symbol,
                        OPEN_STATUSES
                );

        List<Order> asks =
                orderRepository.findBookAsks(
                        symbol,
                        OPEN_STATUSES
                );

        return new OrderBookResponse(
                symbol,
                aggregateLevels(bids, viewerId),
                aggregateLevels(asks, viewerId),
                Instant.now()
        );
    }

    private List<OrderBookLevelResponse> aggregateLevels(
            List<Order> orders,
            Long viewerId
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

            if (
                    viewerId != null
                            && viewerId.equals(order.getUser().getId())
            ) {

                accumulator.myQuantity =
                        accumulator.myQuantity.add(
                                order.getRemainingQuantity()
                        );
            }
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
                            entry.getValue().orderCount,
                            entry.getValue().myQuantity
                    )
            );
        }

        return response;
    }

    /*
     * Normalise le symbole et vérifie que l'actif
     * existe et est actif.
     *
     * Un carnet ne peut pas être consulté
     * pour un actif inexistant.
     */
    public String resolveActiveSymbol(
            String rawSymbol
    ) {

        if (
                rawSymbol == null
                        || rawSymbol.isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le symbole de l'actif est obligatoire."
            );
        }

        String symbol =
                rawSymbol
                        .trim()
                        .toUpperCase(Locale.ROOT);

        assetRepository
                .findBySymbolIgnoreCaseAndActiveTrue(symbol)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Actif inconnu ou désactivé : "
                                        + symbol
                        )
                );

        return symbol;
    }

    private static class LevelAccumulator {

        private BigDecimal totalQuantity =
                BigDecimal.ZERO;

        private BigDecimal myQuantity =
                BigDecimal.ZERO;

        private int orderCount = 0;
    }
}
