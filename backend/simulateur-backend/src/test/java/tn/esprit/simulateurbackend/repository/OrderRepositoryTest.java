package tn.esprit.simulateurbackend.repository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.User;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class OrderRepositoryTest {


    @Autowired
    private OrderRepository orderRepository;


    @Autowired
    private EntityManager entityManager;


    private User buyer;

    private User seller1;

    private User seller2;

    private User seller3;


    private static final List<OrderStatus> OPEN_STATUSES =
            List.of(
                    OrderStatus.PENDING,
                    OrderStatus.PARTIALLY_FILLED
            );


    // =========================================================
    // SETUP
    // =========================================================

    @BeforeEach
    void setUp() {

        buyer =
                persistUser(
                        "Buyer",
                        "Test",
                        "repo-buyer@test.local"
                );


        seller1 =
                persistUser(
                        "Seller",
                        "One",
                        "repo-seller1@test.local"
                );


        seller2 =
                persistUser(
                        "Seller",
                        "Two",
                        "repo-seller2@test.local"
                );


        seller3 =
                persistUser(
                        "Seller",
                        "Three",
                        "repo-seller3@test.local"
                );
    }


    // =========================================================
    // TEST 1
    //
    // ASK :
    //
    // prix le plus faible d'abord
    // puis ordre le plus ancien
    // =========================================================

    @Test
    void shouldOrderMatchingAsksByLowestPriceThenOldest()
            throws InterruptedException {

        String symbol =
                "TESTASK";


        // =====================================================
        // ASK 199 - ANCIEN
        // =====================================================

        Order ask199Oldest =
                persistOrder(
                        seller1,
                        symbol,
                        OrderSide.SELL,
                        "199",
                        "2",
                        OrderStatus.PENDING
                );


        /*
         * On force un petit écart temporel
         * afin que createdAt soit clairement différent.
         */
        Thread.sleep(
                10
        );


        // =====================================================
        // ASK 199 - RÉCENT
        // =====================================================

        Order ask199Newest =
                persistOrder(
                        seller2,
                        symbol,
                        OrderSide.SELL,
                        "199",
                        "3",
                        OrderStatus.PENDING
                );


        Thread.sleep(
                10
        );


        // =====================================================
        // ASK 200
        // =====================================================

        Order ask200 =
                persistOrder(
                        seller3,
                        symbol,
                        OrderSide.SELL,
                        "200",
                        "4",
                        OrderStatus.PENDING
                );


        Thread.sleep(
                10
        );


        // =====================================================
        // ASK 205
        // =====================================================

        Order ask205 =
                persistOrder(
                        seller1,
                        symbol,
                        OrderSide.SELL,
                        "205",
                        "5",
                        OrderStatus.PENDING
                );


        // =====================================================
        // PRECONDITION
        // =====================================================

        assertTrue(
                ask199Oldest
                        .getCreatedAt()
                        .isBefore(
                                ask199Newest.getCreatedAt()
                        )
        );


        // =====================================================
        // QUERY
        //
        // BUY LIMIT maximum = 205
        // =====================================================

        List<Order> result =
                orderRepository
                        .findMatchingAsks(
                                symbol,
                                OrderSide.SELL,
                                OPEN_STATUSES,
                                new BigDecimal("205"),
                                buyer.getId()
                        );


        // =====================================================
        // ASSERTIONS
        // =====================================================

        assertEquals(
                4,
                result.size()
        );


        /*
         * 199 ancien
         */
        assertEquals(
                ask199Oldest.getId(),
                result.get(0).getId()
        );


        /*
         * 199 récent
         */
        assertEquals(
                ask199Newest.getId(),
                result.get(1).getId()
        );


        /*
         * 200
         */
        assertEquals(
                ask200.getId(),
                result.get(2).getId()
        );


        /*
         * 205
         */
        assertEquals(
                ask205.getId(),
                result.get(3).getId()
        );
    }


    // =========================================================
    // TEST 2
    //
    // BID :
    //
    // prix le plus élevé d'abord
    // puis ordre le plus ancien
    // =========================================================

    @Test
    void shouldOrderMatchingBidsByHighestPriceThenOldest()
            throws InterruptedException {

        String symbol =
                "TESTBID";


        // =====================================================
        // BID 205 - ANCIEN
        // =====================================================

        Order bid205Oldest =
                persistOrder(
                        seller1,
                        symbol,
                        OrderSide.BUY,
                        "205",
                        "2",
                        OrderStatus.PENDING
                );


        Thread.sleep(
                10
        );


        // =====================================================
        // BID 205 - RÉCENT
        // =====================================================

        Order bid205Newest =
                persistOrder(
                        seller2,
                        symbol,
                        OrderSide.BUY,
                        "205",
                        "3",
                        OrderStatus.PENDING
                );


        Thread.sleep(
                10
        );


        // =====================================================
        // BID 201
        // =====================================================

        Order bid201 =
                persistOrder(
                        seller3,
                        symbol,
                        OrderSide.BUY,
                        "201",
                        "4",
                        OrderStatus.PENDING
                );


        Thread.sleep(
                10
        );


        // =====================================================
        // BID 199
        // =====================================================

        Order bid199 =
                persistOrder(
                        seller1,
                        symbol,
                        OrderSide.BUY,
                        "199",
                        "5",
                        OrderStatus.PENDING
                );


        // =====================================================
        // PRECONDITION
        // =====================================================

        assertTrue(
                bid205Oldest
                        .getCreatedAt()
                        .isBefore(
                                bid205Newest.getCreatedAt()
                        )
        );


        // =====================================================
        // QUERY
        //
        // SELL LIMIT minimum = 199
        // =====================================================

        List<Order> result =
                orderRepository
                        .findMatchingBids(
                                symbol,
                                OrderSide.BUY,
                                OPEN_STATUSES,
                                new BigDecimal("199"),
                                buyer.getId()
                        );


        // =====================================================
        // ASSERTIONS
        // =====================================================

        assertEquals(
                4,
                result.size()
        );


        /*
         * 205 ancien
         */
        assertEquals(
                bid205Oldest.getId(),
                result.get(0).getId()
        );


        /*
         * 205 récent
         */
        assertEquals(
                bid205Newest.getId(),
                result.get(1).getId()
        );


        /*
         * 201
         */
        assertEquals(
                bid201.getId(),
                result.get(2).getId()
        );


        /*
         * 199
         */
        assertEquals(
                bid199.getId(),
                result.get(3).getId()
        );
    }


    // =========================================================
    // TEST 3
    //
    // SELF MATCH INTERDIT
    // =========================================================

    @Test
    void shouldExcludeOrdersBelongingToIncomingUser() {

        String symbol =
                "TESTSELF";


        /*
         * Ordre du même utilisateur.
         *
         * Il ne doit PAS apparaître.
         */
        persistOrder(
                buyer,
                symbol,
                OrderSide.SELL,
                "199",
                "5",
                OrderStatus.PENDING
        );


        /*
         * Ordre d'un autre utilisateur.
         *
         * Il doit apparaître.
         */
        Order externalAsk =
                persistOrder(
                        seller1,
                        symbol,
                        OrderSide.SELL,
                        "200",
                        "5",
                        OrderStatus.PENDING
                );


        List<Order> result =
                orderRepository
                        .findMatchingAsks(
                                symbol,
                                OrderSide.SELL,
                                OPEN_STATUSES,
                                new BigDecimal("210"),
                                buyer.getId()
                        );


        assertEquals(
                1,
                result.size()
        );


        assertEquals(
                externalAsk.getId(),
                result.get(0).getId()
        );


        assertNotEquals(
                buyer.getId(),
                result
                        .get(0)
                        .getUser()
                        .getId()
        );
    }


    // =========================================================
    // TEST 4
    //
    // STATUS + PRIX LIMITE
    // =========================================================

    @Test
    void shouldFilterClosedOrdersAndOrdersOutsideLimitPrice() {

        String symbol =
                "TESTFILTER";


        // =====================================================
        // DOIT ÊTRE RETOURNÉ
        // =====================================================

        Order ask200 =
                persistOrder(
                        seller1,
                        symbol,
                        OrderSide.SELL,
                        "200",
                        "2",
                        OrderStatus.PENDING
                );


        Order ask201 =
                persistOrder(
                        seller2,
                        symbol,
                        OrderSide.SELL,
                        "201",
                        "2",
                        OrderStatus.PARTIALLY_FILLED
                );


        // =====================================================
        // NE DOIT PAS ÊTRE RETOURNÉ :
        //
        // CANCELLED
        // =====================================================

        persistOrder(
                seller1,
                symbol,
                OrderSide.SELL,
                "199",
                "2",
                OrderStatus.CANCELLED
        );


        // =====================================================
        // NE DOIT PAS ÊTRE RETOURNÉ :
        //
        // EXECUTED
        // =====================================================

        persistOrder(
                seller2,
                symbol,
                OrderSide.SELL,
                "198",
                "2",
                OrderStatus.EXECUTED
        );


        // =====================================================
        // NE DOIT PAS ÊTRE RETOURNÉ :
        //
        // prix 210 > limite 205
        // =====================================================

        persistOrder(
                seller3,
                symbol,
                OrderSide.SELL,
                "210",
                "2",
                OrderStatus.PENDING
        );


        // =====================================================
        // QUERY
        // =====================================================

        List<Order> result =
                orderRepository
                        .findMatchingAsks(
                                symbol,
                                OrderSide.SELL,
                                OPEN_STATUSES,
                                new BigDecimal("205"),
                                buyer.getId()
                        );


        // =====================================================
        // ASSERTIONS
        // =====================================================

        assertEquals(
                2,
                result.size()
        );


        assertEquals(
                ask200.getId(),
                result.get(0).getId()
        );


        assertEquals(
                ask201.getId(),
                result.get(1).getId()
        );


        assertTrue(
                result.stream()
                        .allMatch(
                                order ->
                                        OPEN_STATUSES.contains(
                                                order.getStatus()
                                        )
                        )
        );


        assertTrue(
                result.stream()
                        .allMatch(
                                order ->
                                        order.getLimitPrice()
                                                .compareTo(
                                                        new BigDecimal("205")
                                                )
                                                <= 0
                        )
        );
    }


    // =========================================================
    // HELPER : USER
    // =========================================================

    private User persistUser(
            String firstName,
            String lastName,
            String email
    ) {

        User user =
                new User();


        user.setFirstName(
                firstName
        );


        user.setLastName(
                lastName
        );


        user.setEmail(
                email
        );


        user.setEnabled(
                true
        );


        entityManager.persist(
                user
        );


        entityManager.flush();


        return user;
    }


    // =========================================================
    // HELPER : ORDER
    // =========================================================

    private Order persistOrder(
            User user,
            String symbol,
            OrderSide side,
            String price,
            String quantity,
            OrderStatus status
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
                OrderType.LIMIT
        );


        order.setLimitPrice(
                new BigDecimal(
                        price
                )
        );


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
                status
        );


        entityManager.persist(
                order
        );


        entityManager.flush();


        return order;
    }
}