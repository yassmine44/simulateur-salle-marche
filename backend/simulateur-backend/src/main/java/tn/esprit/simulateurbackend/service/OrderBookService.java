package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.esprit.simulateurbackend.dto.SlippageEstimateResponse;
import tn.esprit.simulateurbackend.dto.OrderBookLevelResponse;
import tn.esprit.simulateurbackend.dto.OrderBookMetricsResponse;
import tn.esprit.simulateurbackend.dto.OrderBookResponse;

import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.User;

import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


@Service
@Transactional(readOnly = true)
public class OrderBookService {


    // =========================================
    // CONSTANTS
    // =========================================

    private static final List<OrderStatus> OPEN_STATUSES =
            List.of(
                    OrderStatus.PENDING,
                    OrderStatus.PARTIALLY_FILLED
            );


    /*
     * Précision élevée pour les calculs
     * financiers.
     */

    private static final MathContext MC =
            MathContext.DECIMAL128;


    // =========================================
    // DEPENDENCIES
    // =========================================

    private final OrderRepository orderRepository;

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;


    public OrderBookService(
            OrderRepository orderRepository,
            AssetRepository assetRepository,
            UserRepository userRepository
    ) {

        this.orderRepository =
                orderRepository;

        this.assetRepository =
                assetRepository;
        this.userRepository = userRepository;
    }


    // =========================================
    // ORDER BOOK
    // =========================================

    public OrderBookResponse getOrderBook(
            String rawSymbol
    ) {

        return getOrderBook(rawSymbol, null);
    }

    public OrderBookResponse getOrderBook(
            String rawSymbol,
            String viewerEmail
    ) {

        String symbol =
                validateAndNormalizeSymbol(
                        rawSymbol
                );


        List<Order> bids =
                getBidOrders(
                        symbol
                );


        List<Order> asks =
                getAskOrders(
                        symbol
                );

        Long viewerId = viewerEmail == null ? null : userRepository
                .findByEmailIgnoreCase(viewerEmail.trim())
                .map(User::getId)
                .orElse(null);


        return new OrderBookResponse(

                symbol,

                aggregateLevels(
                        bids, viewerId
                ),

                aggregateLevels(
                        asks, viewerId
                ),

                Instant.now()
        );
    }
// =========================================
// ESTIMATED SLIPPAGE
// =========================================

    public SlippageEstimateResponse estimateSlippage(

            String rawSymbol,

            OrderSide side,

            BigDecimal quantity
    ) {

        String symbol =
                validateAndNormalizeSymbol(
                        rawSymbol
                );


        // =====================================
        // VALIDATION
        // =====================================

        if (side == null) {

            throw new ResponseStatusException(

                    HttpStatus.BAD_REQUEST,

                    "Le sens BUY ou SELL est obligatoire."
            );
        }


        if (
                quantity == null
                        ||
                        quantity.compareTo(
                                BigDecimal.ZERO
                        ) <= 0
        ) {

            throw new ResponseStatusException(

                    HttpStatus.BAD_REQUEST,

                    "La quantité doit être strictement positive."
            );
        }


        // =====================================
        // ORDER BOOK
        // =====================================

        List<OrderBookLevelResponse> levels;


        /*
         * BUY MARKET consomme les ASK.
         *
         * SELL MARKET consomme les BID.
         */

        if (
                side == OrderSide.BUY
        ) {

            levels =
                    aggregateLevels(
                            getAskOrders(
                                    symbol
                            )
                    );

        } else {

            levels =
                    aggregateLevels(
                            getBidOrders(
                                    symbol
                            )
                    );
        }


        // =====================================
        // NO LIQUIDITY
        // =====================================

        if (
                levels.isEmpty()
        ) {

            return new SlippageEstimateResponse(

                    symbol,

                    side,

                    quantity,

                    BigDecimal.ZERO,

                    quantity,

                    false,

                    null,

                    null,

                    null,

                    null,

                    null,

                    0,

                    Instant.now()
            );
        }


        // =====================================
        // BEST PRICE
        // =====================================

        BigDecimal bestPrice =
                levels
                        .get(0)
                        .price();


        // =====================================
        // SIMULATION
        // =====================================

        BigDecimal remaining =
                quantity;


        BigDecimal executableQuantity =
                BigDecimal.ZERO;


        BigDecimal totalExecutionValue =
                BigDecimal.ZERO;


        int levelsConsumed =
                0;


        for (
                OrderBookLevelResponse level
                : levels
        ) {

            if (
                    remaining.compareTo(
                            BigDecimal.ZERO
                    ) <= 0
            ) {

                break;
            }


            BigDecimal levelQuantity =
                    level.totalQuantity();


            BigDecimal executedAtLevel =
                    remaining.min(
                            levelQuantity
                    );


            if (
                    executedAtLevel.compareTo(
                            BigDecimal.ZERO
                    ) <= 0
            ) {

                continue;
            }


            // =================================
            // PRICE × QUANTITY
            // =================================

            BigDecimal executionValue =
                    level
                            .price()
                            .multiply(
                                    executedAtLevel,
                                    MC
                            );


            totalExecutionValue =
                    totalExecutionValue
                            .add(
                                    executionValue,
                                    MC
                            );


            executableQuantity =
                    executableQuantity
                            .add(
                                    executedAtLevel,
                                    MC
                            );


            remaining =
                    remaining
                            .subtract(
                                    executedAtLevel,
                                    MC
                            );


            levelsConsumed++;
        }


        // =====================================
        // EXPECTED EXECUTION PRICE
        // =====================================

        BigDecimal expectedExecutionPrice =
                null;


        if (
                executableQuantity.compareTo(
                        BigDecimal.ZERO
                ) > 0
        ) {

            expectedExecutionPrice =
                    totalExecutionValue
                            .divide(
                                    executableQuantity,
                                    MC
                            );
        }


        // =====================================
        // ABSOLUTE SLIPPAGE
        // =====================================

        BigDecimal absoluteSlippage =
                null;


        BigDecimal relativeSlippage =
                null;


        BigDecimal slippagePercent =
                null;


        if (
                expectedExecutionPrice != null
                        &&
                        bestPrice.compareTo(
                                BigDecimal.ZERO
                        ) > 0
        ) {


            /*
             * BUY :
             *
             * expectedPrice - bestAsk
             *
             *
             * SELL :
             *
             * bestBid - expectedPrice
             *
             * Dans les deux cas :
             *
             * slippage > 0
             * signifie un coût défavorable.
             */

            if (
                    side == OrderSide.BUY
            ) {

                absoluteSlippage =
                        expectedExecutionPrice
                                .subtract(
                                        bestPrice,
                                        MC
                                );

            } else {

                absoluteSlippage =
                        bestPrice
                                .subtract(
                                        expectedExecutionPrice,
                                        MC
                                );
            }


            relativeSlippage =
                    absoluteSlippage
                            .divide(
                                    bestPrice,
                                    MC
                            );


            slippagePercent =
                    relativeSlippage
                            .multiply(
                                    BigDecimal.valueOf(100),
                                    MC
                            );
        }


        // =====================================
        // FULL EXECUTION ?
        // =====================================

        boolean fullyExecutable =
                remaining.compareTo(
                        BigDecimal.ZERO
                ) == 0;


        // =====================================
        // RESPONSE
        // =====================================

        return new SlippageEstimateResponse(

                symbol,

                side,

                quantity,

                executableQuantity,

                remaining,

                fullyExecutable,

                bestPrice,

                expectedExecutionPrice,

                absoluteSlippage,

                relativeSlippage,

                slippagePercent,

                levelsConsumed,

                Instant.now()
        );
    }

    // =========================================
    // ORDER BOOK METRICS
    // =========================================

    public OrderBookMetricsResponse getOrderBookMetrics(
            String rawSymbol
    ) {

        String symbol =
                validateAndNormalizeSymbol(
                        rawSymbol
                );


        // =====================================
        // RAW ORDERS
        // =====================================

        List<Order> bidOrders =
                getBidOrders(
                        symbol
                );


        List<Order> askOrders =
                getAskOrders(
                        symbol
                );


        // =====================================
        // AGGREGATED LEVELS
        // =====================================

        List<OrderBookLevelResponse> bids =
                aggregateLevels(
                        bidOrders
                );


        List<OrderBookLevelResponse> asks =
                aggregateLevels(
                        askOrders
                );


        // =====================================
        // BEST BID
        // =====================================

        BigDecimal bestBid =
                bids.isEmpty()
                        ? null
                        : bids.get(0).price();


        // =====================================
        // BEST ASK
        // =====================================

        BigDecimal bestAsk =
                asks.isEmpty()
                        ? null
                        : asks.get(0).price();


        // =====================================
        // TOTAL BID VOLUME
        // =====================================

        BigDecimal totalBidVolume =
                calculateTotalVolume(
                        bids
                );


        // =====================================
        // TOTAL ASK VOLUME
        // =====================================

        BigDecimal totalAskVolume =
                calculateTotalVolume(
                        asks
                );


        // =====================================
        // MID PRICE
        // =====================================

        BigDecimal midPrice =
                calculateMidPrice(
                        bestBid,
                        bestAsk
                );


        // =====================================
        // SPREAD
        // =====================================

        BigDecimal spread =
                calculateSpread(
                        bestBid,
                        bestAsk
                );


        // =====================================
        // RELATIVE SPREAD
        // =====================================

        BigDecimal relativeSpread =
                calculateRelativeSpread(
                        spread,
                        midPrice
                );


        // =====================================
        // RELATIVE SPREAD %
        // =====================================

        BigDecimal relativeSpreadPercent =
                relativeSpread == null

                        ? null

                        : relativeSpread
                        .multiply(
                                BigDecimal.valueOf(100),
                                MC
                        );


        // =====================================
        // ORDER BOOK IMBALANCE
        // =====================================

        BigDecimal orderBookImbalance =
                calculateOrderBookImbalance(
                        totalBidVolume,
                        totalAskVolume
                );
        // =====================================
// WEIGHTED BID DEPTH
// =====================================

        BigDecimal weightedBidDepth =
                calculateWeightedDepth(
                        bids,
                        midPrice
                );


// =====================================
// WEIGHTED ASK DEPTH
// =====================================

        BigDecimal weightedAskDepth =
                calculateWeightedDepth(
                        asks,
                        midPrice
                );


        // =====================================
        // RESPONSE
        // =====================================

        return new OrderBookMetricsResponse(

                symbol,

                bestBid,

                bestAsk,

                midPrice,

                spread,

                relativeSpread,

                relativeSpreadPercent,

                totalBidVolume,

                totalAskVolume,

                orderBookImbalance,

                weightedBidDepth,

                weightedAskDepth,

                Instant.now()
        );
    }


    // =========================================
    // GET BID ORDERS
    // =========================================

    private List<Order> getBidOrders(
            String symbol
    ) {

        return orderRepository
                .findBookBids(symbol, OPEN_STATUSES);
    }


    // =========================================
    // GET ASK ORDERS
    // =========================================

    private List<Order> getAskOrders(
            String symbol
    ) {

        return orderRepository
                .findBookAsks(symbol, OPEN_STATUSES);
    }


    // =========================================
    // MID PRICE
    // =========================================

    /*
     *
     *           BestBid + BestAsk
     * Mid = -------------------------
     *                  2
     *
     */

    private BigDecimal calculateMidPrice(
            BigDecimal bestBid,
            BigDecimal bestAsk
    ) {

        if (
                bestBid == null
                        ||
                        bestAsk == null
        ) {

            return null;
        }


        return bestBid
                .add(
                        bestAsk,
                        MC
                )
                .divide(
                        BigDecimal.valueOf(2),
                        MC
                );
    }


    // =========================================
    // SPREAD
    // =========================================

    /*
     *
     * Spread =
     *
     * BestAsk - BestBid
     *
     */

    private BigDecimal calculateSpread(
            BigDecimal bestBid,
            BigDecimal bestAsk
    ) {

        if (
                bestBid == null
                        ||
                        bestAsk == null
        ) {

            return null;
        }


        return bestAsk
                .subtract(
                        bestBid,
                        MC
                );
    }


    // =========================================
    // RELATIVE SPREAD
    // =========================================

    /*
     *
     *                     Spread
     * RelativeSpread = -------------
     *                    MidPrice
     *
     */

    private BigDecimal calculateRelativeSpread(
            BigDecimal spread,
            BigDecimal midPrice
    ) {

        if (
                spread == null
                        ||
                        midPrice == null
                        ||
                        midPrice.compareTo(
                                BigDecimal.ZERO
                        ) == 0
        ) {

            return null;
        }


        return spread
                .divide(
                        midPrice,
                        MC
                );
    }


    // =========================================
    // TOTAL VOLUME
    // =========================================

    private BigDecimal calculateTotalVolume(
            List<OrderBookLevelResponse> levels
    ) {

        BigDecimal total =
                BigDecimal.ZERO;


        for (
                OrderBookLevelResponse level
                : levels
        ) {

            total =
                    total.add(
                            level.totalQuantity(),
                            MC
                    );
        }


        return total;
    }


    // =========================================
    // ORDER BOOK IMBALANCE
    // =========================================

    /*
     *
     *               BidVolume - AskVolume
     * OBI = --------------------------------
     *               BidVolume + AskVolume
     *
     *
     * OBI proche de +1 :
     * forte dominance des volumes BID.
     *
     * OBI proche de -1 :
     * forte dominance des volumes ASK.
     *
     * OBI proche de 0 :
     * volumes relativement équilibrés.
     *
     */

    private BigDecimal calculateOrderBookImbalance(
            BigDecimal bidVolume,
            BigDecimal askVolume
    ) {

        BigDecimal denominator =
                bidVolume
                        .add(
                                askVolume,
                                MC
                        );


        /*
         * Carnet totalement vide.
         *
         * Mathématiquement OBI serait 0 / 0.
         *
         * On retourne donc null plutôt que 0,
         * car 0 pourrait être interprété à tort
         * comme un carnet équilibré.
         */

        if (
                denominator
                        .compareTo(
                                BigDecimal.ZERO
                        ) == 0
        ) {

            return null;
        }


        BigDecimal numerator =
                bidVolume
                        .subtract(
                                askVolume,
                                MC
                        );


        return numerator
                .divide(
                        denominator,
                        MC
                );
    }

// =========================================
// WEIGHTED DEPTH
// =========================================

    /*
     *
     * Pour chaque niveau :
     *
     * Distance_i =
     *
     * | Price_i - MidPrice |
     * ----------------------
     *       MidPrice
     *
     *
     * Puis :
     *
     * WeightedDepth =
     *
     * Σ Volume_i / (1 + Distance_i)
     *
     *
     * Plus un volume est proche du MidPrice,
     * plus son poids est important.
     *
     */

    private BigDecimal calculateWeightedDepth(
            List<OrderBookLevelResponse> levels,
            BigDecimal midPrice
    ) {

        /*
         * Impossible de mesurer la distance
         * sans MidPrice.
         */

        if (
                midPrice == null
                        ||
                        midPrice.compareTo(
                                BigDecimal.ZERO
                        ) == 0
        ) {

            return null;
        }


        BigDecimal weightedDepth =
                BigDecimal.ZERO;


        for (
                OrderBookLevelResponse level
                : levels
        ) {

            // =====================================
            // ABSOLUTE PRICE DISTANCE
            // =====================================

            BigDecimal absoluteDistance =
                    level
                            .price()
                            .subtract(
                                    midPrice,
                                    MC
                            )
                            .abs();


            // =====================================
            // RELATIVE DISTANCE
            // =====================================

            BigDecimal relativeDistance =
                    absoluteDistance
                            .divide(
                                    midPrice,
                                    MC
                            );


            // =====================================
            // WEIGHT
            // =====================================

            BigDecimal denominator =
                    BigDecimal.ONE
                            .add(
                                    relativeDistance,
                                    MC
                            );


            BigDecimal weightedVolume =
                    level
                            .totalQuantity()
                            .divide(
                                    denominator,
                                    MC
                            );


            // =====================================
            // ACCUMULATION
            // =====================================

            weightedDepth =
                    weightedDepth
                            .add(
                                    weightedVolume,
                                    MC
                            );
        }


        return weightedDepth;
    }
    // =========================================
    // AGGREGATE LEVELS
    // =========================================

    private List<OrderBookLevelResponse> aggregateLevels(
            List<Order> orders
    ) {

        return aggregateLevels(orders, null);
    }

    private List<OrderBookLevelResponse> aggregateLevels(
            List<Order> orders,
            Long viewerId
    ) {

        Map<BigDecimal, LevelAccumulator> levels =
                new LinkedHashMap<>();


        for (
                Order order
                : orders
        ) {


            // MARKET orders ne doivent
            // pas apparaître dans le carnet.

            if (
                    order.getLimitPrice() == null
            ) {

                continue;
            }


            if (
                    order.getRemainingQuantity() == null
                            ||
                            order
                                    .getRemainingQuantity()
                                    .compareTo(
                                            BigDecimal.ZERO
                                    )
                                    <= 0
            ) {

                continue;
            }


            BigDecimal normalizedPrice =
                    order
                            .getLimitPrice()
                            .stripTrailingZeros();


            LevelAccumulator accumulator =
                    levels.computeIfAbsent(

                            normalizedPrice,

                            ignored ->
                                    new LevelAccumulator()
                    );


            accumulator.totalQuantity =
                    accumulator
                            .totalQuantity
                            .add(
                                    order.getRemainingQuantity(),
                                    MC
                            );


            accumulator.orderCount++;

            if (viewerId != null && viewerId.equals(order.getUser().getId())) {
                accumulator.myQuantity = accumulator.myQuantity.add(order.getRemainingQuantity());
            }
        }


        List<OrderBookLevelResponse> response =
                new ArrayList<>();


        for (
                Map.Entry<
                        BigDecimal,
                        LevelAccumulator
                        > entry
                : levels.entrySet()
        ) {

            response.add(

                    new OrderBookLevelResponse(

                            entry.getKey(),

                            entry
                                    .getValue()
                                    .totalQuantity,

                            entry
                                    .getValue()
                                    .orderCount,
                            entry.getValue().myQuantity
                    )
            );
        }


        return response;
    }


    // =========================================
    // SYMBOL VALIDATION
    // =========================================

    public String resolveActiveSymbol(String rawSymbol) {
        return validateAndNormalizeSymbol(rawSymbol);
    }

    private String validateAndNormalizeSymbol(
            String rawSymbol
    ) {

        if (
                rawSymbol == null
                        ||
                        rawSymbol.isBlank()
        ) {

            throw new ResponseStatusException(

                    HttpStatus.BAD_REQUEST,

                    "Le symbole de l'actif est obligatoire."
            );
        }


        String symbol =
                rawSymbol
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );


        assetRepository
                .findBySymbolIgnoreCaseAndActiveTrue(
                        symbol
                )
                .orElseThrow(
                        () ->
                                new ResponseStatusException(

                                        HttpStatus.NOT_FOUND,

                                        "Actif inconnu ou désactivé : "
                                                + symbol
                                )
                );


        return symbol;
    }


    // =========================================
    // LEVEL ACCUMULATOR
    // =========================================

    private static class LevelAccumulator {

        private BigDecimal totalQuantity =
                BigDecimal.ZERO;

        private BigDecimal myQuantity = BigDecimal.ZERO;

        private int orderCount =
                0;
    }
}
