package tn.esprit.simulateurbackend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.Trade;
import tn.esprit.simulateurbackend.entity.User;

import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.repository.TradeRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class MatchingEngineServiceTest {


    @Mock
    private OrderRepository orderRepository;


    @Mock
    private TradeRepository tradeRepository;


    private MatchingEngineService matchingEngineService;


    @BeforeEach
    void setUp() {

        matchingEngineService =
                new MatchingEngineService(
                        orderRepository,
                        tradeRepository,
                        mock(AssetRepository.class),
                        mock(MarketDataService.class),
                        mock(TradingRulesService.class),
                        mock(LinkedOrderService.class),
                        mock(OrderValidator.class)
                );
    }


    // =========================================================
    // TEST 1
    // BUY LIMIT vs SELL LIMIT
    // EXECUTION TOTALE
    // =========================================================

    @Test
    void shouldFullyExecuteBuyLimitAgainstSellLimit() {

        User buyer =
                mock(User.class);

        User seller =
                mock(User.class);


        when(
                buyer.getId()
        ).thenReturn(
                2L
        );


        Order sellOrder =
                createOrder(
                        seller,
                        "AAPL",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        "200",
                        "5"
                );


        Order buyOrder =
                createOrder(
                        buyer,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        "201",
                        "5"
                );


        when(
                orderRepository
                        .findByIdForUpdate(
                                2L
                        )
        ).thenReturn(
                Optional.of(
                        buyOrder
                )
        );


        when(
                orderRepository
                        .findMatchingAsks(
                                eq("AAPL"),
                                eq(OrderSide.SELL),
                                anyList(),
                                eq(
                                        new BigDecimal("201")
                                ),
                                eq(2L)
                        )
        ).thenReturn(
                List.of(
                        sellOrder
                )
        );


        when(
                tradeRepository
                        .save(
                                any(Trade.class)
                        )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );


        List<Trade> trades =
                matchingEngineService
                        .matchOrder(
                                2L
                        );


        assertNotNull(
                trades
        );


        assertEquals(
                1,
                trades.size()
        );


        Trade trade =
                trades.get(0);


        assertDecimal(
                "200",
                trade.getExecutionPrice()
        );


        assertDecimal(
                "5",
                trade.getQuantity()
        );


        assertSame(
                buyOrder,
                trade.getBuyOrder()
        );


        assertSame(
                sellOrder,
                trade.getSellOrder()
        );


        assertDecimal(
                "0",
                buyOrder.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.EXECUTED,
                buyOrder.getStatus()
        );


        assertDecimal(
                "0",
                sellOrder.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.EXECUTED,
                sellOrder.getStatus()
        );


        verify(
                tradeRepository,
                times(1)
        ).save(
                any(Trade.class)
        );


        verify(
                orderRepository,
                times(1)
        ).save(
                sellOrder
        );


        verify(
                orderRepository,
                times(1)
        ).save(
                buyOrder
        );
    }


    // =========================================================
    // TEST 2
    // EXECUTION PARTIELLE DU RESTING ORDER
    // =========================================================

    @Test
    void shouldPartiallyFillRestingSellWhenIncomingBuyIsSmaller() {

        User buyer =
                mock(User.class);

        User seller =
                mock(User.class);


        when(
                buyer.getId()
        ).thenReturn(
                2L
        );


        /*
         * SELL LIMIT 10 @ 200
         */
        Order sellOrder =
                createOrder(
                        seller,
                        "AAPL",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        "200",
                        "10"
                );


        /*
         * BUY LIMIT 4 @ 201
         */
        Order buyOrder =
                createOrder(
                        buyer,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        "201",
                        "4"
                );


        when(
                orderRepository
                        .findByIdForUpdate(
                                2L
                        )
        ).thenReturn(
                Optional.of(
                        buyOrder
                )
        );


        when(
                orderRepository
                        .findMatchingAsks(
                                eq("AAPL"),
                                eq(OrderSide.SELL),
                                anyList(),
                                eq(
                                        new BigDecimal("201")
                                ),
                                eq(2L)
                        )
        ).thenReturn(
                List.of(
                        sellOrder
                )
        );


        when(
                tradeRepository
                        .save(
                                any(Trade.class)
                        )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );


        List<Trade> trades =
                matchingEngineService
                        .matchOrder(
                                2L
                        );


        assertEquals(
                1,
                trades.size()
        );


        Trade trade =
                trades.get(0);


        /*
         * min(4, 10) = 4
         */
        assertDecimal(
                "4",
                trade.getQuantity()
        );


        /*
         * Le resting SELL est à 200.
         */
        assertDecimal(
                "200",
                trade.getExecutionPrice()
        );


        /*
         * BUY entièrement exécuté.
         */
        assertDecimal(
                "0",
                buyOrder.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.EXECUTED,
                buyOrder.getStatus()
        );


        /*
         * SELL :
         *
         * 10 - 4 = 6
         */
        assertDecimal(
                "6",
                sellOrder.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.PARTIALLY_FILLED,
                sellOrder.getStatus()
        );


        verify(
                tradeRepository,
                times(1)
        ).save(
                any(Trade.class)
        );


        verify(
                orderRepository,
                times(2)
        ).save(
                any(Order.class)
        );
    }


    // =========================================================
    // TEST 3
    // PLUSIEURS NIVEAUX DE PRIX
    // =========================================================

    @Test
    void shouldExecuteBuyAcrossSeveralAskPriceLevels() {

        User buyer =
                mock(User.class);

        User seller =
                mock(User.class);


        when(
                buyer.getId()
        ).thenReturn(
                2L
        );


        /*
         * Carnet ASK :
         *
         * 200 x 2
         * 201 x 3
         * 205 x 5
         */

        Order ask200 =
                createOrder(
                        seller,
                        "AAPL",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        "200",
                        "2"
                );


        Order ask201 =
                createOrder(
                        seller,
                        "AAPL",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        "201",
                        "3"
                );


        Order ask205 =
                createOrder(
                        seller,
                        "AAPL",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        "205",
                        "5"
                );


        /*
         * BUY LIMIT 6 @ 205
         */
        Order buyOrder =
                createOrder(
                        buyer,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        "205",
                        "6"
                );


        when(
                orderRepository
                        .findByIdForUpdate(
                                10L
                        )
        ).thenReturn(
                Optional.of(
                        buyOrder
                )
        );


        /*
         * Le repository fournit les ASK
         * dans l'ordre prix-temps :
         *
         * 200
         * 201
         * 205
         */
        when(
                orderRepository
                        .findMatchingAsks(
                                eq("AAPL"),
                                eq(OrderSide.SELL),
                                anyList(),
                                eq(
                                        new BigDecimal("205")
                                ),
                                eq(2L)
                        )
        ).thenReturn(
                List.of(
                        ask200,
                        ask201,
                        ask205
                )
        );


        when(
                tradeRepository
                        .save(
                                any(Trade.class)
                        )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );


        List<Trade> trades =
                matchingEngineService
                        .matchOrder(
                                10L
                        );


        /*
         * 6 unités :
         *
         * 2 @ 200
         * 3 @ 201
         * 1 @ 205
         */
        assertEquals(
                3,
                trades.size()
        );


        // Trade 1
        assertDecimal(
                "200",
                trades.get(0)
                        .getExecutionPrice()
        );

        assertDecimal(
                "2",
                trades.get(0)
                        .getQuantity()
        );


        // Trade 2
        assertDecimal(
                "201",
                trades.get(1)
                        .getExecutionPrice()
        );

        assertDecimal(
                "3",
                trades.get(1)
                        .getQuantity()
        );


        // Trade 3
        assertDecimal(
                "205",
                trades.get(2)
                        .getExecutionPrice()
        );

        assertDecimal(
                "1",
                trades.get(2)
                        .getQuantity()
        );


        /*
         * Incoming BUY :
         *
         * complètement exécuté.
         */
        assertDecimal(
                "0",
                buyOrder.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.EXECUTED,
                buyOrder.getStatus()
        );


        /*
         * ASK @ 200 complètement consommé.
         */
        assertDecimal(
                "0",
                ask200.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.EXECUTED,
                ask200.getStatus()
        );


        /*
         * ASK @ 201 complètement consommé.
         */
        assertDecimal(
                "0",
                ask201.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.EXECUTED,
                ask201.getStatus()
        );


        /*
         * ASK @ 205 :
         *
         * 5 - 1 = 4
         */
        assertDecimal(
                "4",
                ask205.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.PARTIALLY_FILLED,
                ask205.getStatus()
        );


        verify(
                tradeRepository,
                times(3)
        ).save(
                any(Trade.class)
        );


        /*
         * 3 resting orders
         * +
         * 1 sauvegarde finale incoming
         */
        verify(
                orderRepository,
                times(4)
        ).save(
                any(Order.class)
        );
    }


    // =========================================================
    // HELPER : CREATE ORDER
    // =========================================================

    private Order createOrder(
            User user,
            String symbol,
            OrderSide side,
            OrderType type,
            String price,
            String quantity
    ) {

        Order order =
                new Order();


        order.setUser(
                user
        );


        order.setAssetSymbol(
                symbol
        );


        order.setSide(
                side
        );


        order.setType(
                type
        );


        if (price != null) {

            order.setLimitPrice(
                    new BigDecimal(
                            price
                    )
            );

        } else {

            order.setLimitPrice(
                    null
            );
        }


        BigDecimal quantityValue =
                new BigDecimal(
                        quantity
                );


        order.setQuantity(
                quantityValue
        );


        order.setRemainingQuantity(
                quantityValue
        );


        order.setStatus(
                OrderStatus.PENDING
        );


        return order;
    }


    // =========================================================
    // HELPER : BIG DECIMAL ASSERTION
    // =========================================================

    private void assertDecimal(
            String expected,
            BigDecimal actual
    ) {

        assertNotNull(
                actual
        );


        assertEquals(
                0,
                actual.compareTo(
                        new BigDecimal(
                                expected
                        )
                )
        );
    }
    @Test
    void shouldExecuteMarketSellAgainstBestAvailableBids() {

        User seller =
                mock(User.class);

        User buyer1 =
                mock(User.class);

        User buyer2 =
                mock(User.class);


        when(
                seller.getId()
        ).thenReturn(
                10L
        );


        // =====================================================
        // RESTING BID 1
        //
        // BUY LIMIT 2 @ 205
        // =====================================================

        Order bid205 =
                createOrder(
                        buyer1,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        "205",
                        "2"
                );


        // =====================================================
        // RESTING BID 2
        //
        // BUY LIMIT 5 @ 200
        // =====================================================

        Order bid200 =
                createOrder(
                        buyer2,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        "200",
                        "5"
                );


        // =====================================================
        // INCOMING MARKET SELL
        //
        // SELL 4
        // =====================================================

        Order marketSell =
                createOrder(
                        seller,
                        "AAPL",
                        OrderSide.SELL,
                        OrderType.MARKET,
                        null,
                        "4"
                );


        when(
                orderRepository
                        .findByIdForUpdate(
                                50L
                        )
        ).thenReturn(
                Optional.of(
                        marketSell
                )
        );


        /*
         * MARKET SELL consomme les meilleurs BID :
         *
         * 205 d'abord
         * puis 200
         */
        when(
                orderRepository
                        .findMatchingBids(
                                eq("AAPL"),
                                eq(OrderSide.BUY),
                                anyList(), any(),
                                eq(10L)
                        )
        ).thenReturn(
                List.of(
                        bid205,
                        bid200
                )
        );


        when(
                tradeRepository
                        .save(
                                any(Trade.class)
                        )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );


        // =====================================================
        // EXECUTION
        // =====================================================

        List<Trade> trades =
                matchingEngineService
                        .matchOrder(
                                50L
                        );


        // =====================================================
        // RESULT
        //
        // SELL 4
        //
        // 2 @ 205
        // 2 @ 200
        // =====================================================

        assertEquals(
                2,
                trades.size()
        );


        // Trade 1
        assertDecimal(
                "205",
                trades.get(0)
                        .getExecutionPrice()
        );

        assertDecimal(
                "2",
                trades.get(0)
                        .getQuantity()
        );


        // Trade 2
        assertDecimal(
                "200",
                trades.get(1)
                        .getExecutionPrice()
        );

        assertDecimal(
                "2",
                trades.get(1)
                        .getQuantity()
        );


        // =====================================================
        // MARKET SELL
        // =====================================================

        assertDecimal(
                "0",
                marketSell.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.EXECUTED,
                marketSell.getStatus()
        );


        // =====================================================
        // BID 205
        //
        // complètement consommé
        // =====================================================

        assertDecimal(
                "0",
                bid205.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.EXECUTED,
                bid205.getStatus()
        );


        // =====================================================
        // BID 200
        //
        // 5 - 2 = 3
        // =====================================================

        assertDecimal(
                "3",
                bid200.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.PARTIALLY_FILLED,
                bid200.getStatus()
        );


        // =====================================================
        // BUY / SELL ASSOCIATION
        // =====================================================

        assertSame(
                bid205,
                trades.get(0)
                        .getBuyOrder()
        );

        assertSame(
                marketSell,
                trades.get(0)
                        .getSellOrder()
        );


        // =====================================================
        // DATABASE
        // =====================================================

        verify(
                tradeRepository,
                times(2)
        ).save(
                any(Trade.class)
        );


        /*
         * bid205
         * bid200
         * marketSell final
         */
        verify(
                orderRepository,
                times(3)
        ).save(
                any(Order.class)
        );
    }
    @Test
    void shouldCancelRemainingQuantityWhenMarketBuyHasInsufficientLiquidity() {

        User buyer =
                mock(User.class);

        User seller1 =
                mock(User.class);

        User seller2 =
                mock(User.class);


        when(
                buyer.getId()
        ).thenReturn(
                20L
        );


        // =====================================================
        // RESTING ASK 1
        //
        // SELL LIMIT 2 @ 200
        // =====================================================

        Order ask200 =
                createOrder(
                        seller1,
                        "AAPL",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        "200",
                        "2"
                );


        // =====================================================
        // RESTING ASK 2
        //
        // SELL LIMIT 3 @ 201
        // =====================================================

        Order ask201 =
                createOrder(
                        seller2,
                        "AAPL",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        "201",
                        "3"
                );


        // =====================================================
        // INCOMING MARKET BUY
        //
        // BUY 10
        //
        // Liquidité disponible totale = 5
        // =====================================================

        Order marketBuy =
                createOrder(
                        buyer,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        null,
                        "10"
                );


        // =====================================================
        // MOCKS
        // =====================================================

        when(
                orderRepository
                        .findByIdForUpdate(
                                60L
                        )
        ).thenReturn(
                Optional.of(
                        marketBuy
                )
        );


        when(
                orderRepository
                        .findMatchingAsks(
                                eq("AAPL"),
                                eq(OrderSide.SELL),
                                anyList(), any(),
                                eq(20L)
                        )
        ).thenReturn(
                List.of(
                        ask200,
                        ask201
                )
        );


        when(
                tradeRepository
                        .save(
                                any(Trade.class)
                        )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );


        // =====================================================
        // EXECUTION
        // =====================================================

        List<Trade> trades =
                matchingEngineService
                        .matchOrder(
                                60L
                        );


        // =====================================================
        // TRADES
        //
        // 2 @ 200
        // 3 @ 201
        //
        // Total exécuté = 5
        // =====================================================

        assertEquals(
                2,
                trades.size()
        );


        // Trade 1
        assertDecimal(
                "200",
                trades.get(0)
                        .getExecutionPrice()
        );

        assertDecimal(
                "2",
                trades.get(0)
                        .getQuantity()
        );


        // Trade 2
        assertDecimal(
                "201",
                trades.get(1)
                        .getExecutionPrice()
        );

        assertDecimal(
                "3",
                trades.get(1)
                        .getQuantity()
        );


        // =====================================================
        // MARKET BUY
        //
        // Quantité demandée = 10
        // Quantité exécutée = 5
        // Quantité restante = 5
        //
        // MARKET ne reste jamais dans le carnet :
        // statut final = CANCELLED
        // =====================================================

        assertDecimal(
                "5",
                marketBuy.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.CANCELLED,
                marketBuy.getStatus()
        );


        // =====================================================
        // RESTING ASK 200
        // =====================================================

        assertDecimal(
                "0",
                ask200.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.EXECUTED,
                ask200.getStatus()
        );


        // =====================================================
        // RESTING ASK 201
        // =====================================================

        assertDecimal(
                "0",
                ask201.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.EXECUTED,
                ask201.getStatus()
        );


        // =====================================================
        // BUY / SELL ASSOCIATION
        // =====================================================

        assertSame(
                marketBuy,
                trades.get(0)
                        .getBuyOrder()
        );


        assertSame(
                ask200,
                trades.get(0)
                        .getSellOrder()
        );


        // =====================================================
        // DATABASE
        // =====================================================

        verify(
                tradeRepository,
                times(2)
        ).save(
                any(Trade.class)
        );


        /*
         * ask200
         * ask201
         * marketBuy final
         */
        verify(
                orderRepository,
                times(3)
        ).save(
                any(Order.class)
        );
    }
    @Test
    void shouldRejectMarketBuyWhenOrderBookIsEmpty() {

        User buyer =
                mock(User.class);


        when(
                buyer.getId()
        ).thenReturn(
                30L
        );


        // =====================================================
        // INCOMING MARKET BUY
        //
        // BUY 10
        //
        // Aucun ASK disponible.
        // =====================================================

        Order marketBuy =
                createOrder(
                        buyer,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.MARKET,
                        null,
                        "10"
                );


        // =====================================================
        // MOCKS
        // =====================================================

        when(
                orderRepository
                        .findByIdForUpdate(
                                70L
                        )
        ).thenReturn(
                Optional.of(
                        marketBuy
                )
        );


        /*
         * Carnet ASK vide.
         */
        when(
                orderRepository
                        .findMatchingAsks(
                                eq("AAPL"),
                                eq(OrderSide.SELL),
                                anyList(), any(),
                                eq(30L)
                        )
        ).thenReturn(
                List.of()
        );


        // =====================================================
        // EXECUTION
        // =====================================================

        List<Trade> trades =
                matchingEngineService
                        .matchOrder(
                                70L
                        );


        // =====================================================
        // AUCUN TRADE
        // =====================================================

        assertNotNull(
                trades
        );


        assertTrue(
                trades.isEmpty()
        );


        // =====================================================
        // MARKET BUY
        //
        // Rien n'a été exécuté :
        //
        // quantité restante = 10
        // statut = REJECTED (aucune liquidité)
        // =====================================================

        assertDecimal(
                "10",
                marketBuy.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.REJECTED,
                marketBuy.getStatus()
        );


        // =====================================================
        // DATABASE
        // =====================================================

        /*
         * Aucun trade ne doit être créé.
         */
        verify(
                tradeRepository,
                never()
        ).save(
                any(Trade.class)
        );


        /*
         * Seul l'ordre MARKET final est sauvegardé.
         */
        verify(
                orderRepository,
                times(1)
        ).save(
                marketBuy
        );
    }
    @Test
    void shouldConsumeSamePriceOrdersInRepositoryOrder() {

        User buyer =
                mock(User.class);

        User sellerOldest =
                mock(User.class);

        User sellerNewest =
                mock(User.class);


        when(
                buyer.getId()
        ).thenReturn(
                40L
        );


        // =====================================================
        // FIRST RESTING ORDER
        //
        // SELL 2 @ 200
        // considéré comme le plus ancien
        // =====================================================

        Order oldestAsk =
                createOrder(
                        sellerOldest,
                        "AAPL",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        "200",
                        "2"
                );


        // =====================================================
        // SECOND RESTING ORDER
        //
        // SELL 5 @ 200
        // considéré comme plus récent
        // =====================================================

        Order newestAsk =
                createOrder(
                        sellerNewest,
                        "AAPL",
                        OrderSide.SELL,
                        OrderType.LIMIT,
                        "200",
                        "5"
                );


        // =====================================================
        // INCOMING BUY
        //
        // BUY LIMIT 3 @ 200
        // =====================================================

        Order buyOrder =
                createOrder(
                        buyer,
                        "AAPL",
                        OrderSide.BUY,
                        OrderType.LIMIT,
                        "200",
                        "3"
                );


        // =====================================================
        // MOCKS
        // =====================================================

        when(
                orderRepository
                        .findByIdForUpdate(
                                80L
                        )
        ).thenReturn(
                Optional.of(
                        buyOrder
                )
        );


        /*
         * Le repository fournit :
         *
         * 1. oldestAsk
         * 2. newestAsk
         *
         * Le moteur doit conserver cet ordre.
         */
        when(
                orderRepository
                        .findMatchingAsks(
                                eq("AAPL"),
                                eq(OrderSide.SELL),
                                anyList(),
                                eq(
                                        new BigDecimal("200")
                                ),
                                eq(40L)
                        )
        ).thenReturn(
                List.of(
                        oldestAsk,
                        newestAsk
                )
        );


        when(
                tradeRepository
                        .save(
                                any(Trade.class)
                        )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );


        // =====================================================
        // EXECUTION
        // =====================================================

        List<Trade> trades =
                matchingEngineService
                        .matchOrder(
                                80L
                        );


        // =====================================================
        // RESULT
        //
        // BUY 3
        //
        // oldestAsk : 2 exécutées
        // newestAsk : 1 exécutée
        // =====================================================

        assertEquals(
                2,
                trades.size()
        );


        // =====================================================
        // TRADE 1 = OLDEST
        // =====================================================

        assertSame(
                oldestAsk,
                trades.get(0)
                        .getSellOrder()
        );


        assertDecimal(
                "2",
                trades.get(0)
                        .getQuantity()
        );


        assertDecimal(
                "200",
                trades.get(0)
                        .getExecutionPrice()
        );


        // =====================================================
        // TRADE 2 = NEWEST
        // =====================================================

        assertSame(
                newestAsk,
                trades.get(1)
                        .getSellOrder()
        );


        assertDecimal(
                "1",
                trades.get(1)
                        .getQuantity()
        );


        assertDecimal(
                "200",
                trades.get(1)
                        .getExecutionPrice()
        );


        // =====================================================
        // INCOMING BUY
        // =====================================================

        assertDecimal(
                "0",
                buyOrder.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.EXECUTED,
                buyOrder.getStatus()
        );


        // =====================================================
        // OLDEST ASK
        //
        // 2 - 2 = 0
        // =====================================================

        assertDecimal(
                "0",
                oldestAsk.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.EXECUTED,
                oldestAsk.getStatus()
        );


        // =====================================================
        // NEWEST ASK
        //
        // 5 - 1 = 4
        // =====================================================

        assertDecimal(
                "4",
                newestAsk.getRemainingQuantity()
        );


        assertEquals(
                OrderStatus.PARTIALLY_FILLED,
                newestAsk.getStatus()
        );


        // =====================================================
        // DATABASE
        // =====================================================

        verify(
                tradeRepository,
                times(2)
        ).save(
                any(Trade.class)
        );


        verify(
                orderRepository,
                times(3)
        ).save(
                any(Order.class)
        );
    }
}