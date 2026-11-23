package tn.esprit.simulateurbackend.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.FlashOrderBookLevelDto;
import tn.esprit.simulateurbackend.dto.FlashOrderBookSnapshot;
import tn.esprit.simulateurbackend.dto.FlashOrderDto;
import tn.esprit.simulateurbackend.dto.FlashOrderRequest;
import tn.esprit.simulateurbackend.dto.FlashPortfolioResponse;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.UserRepository;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Peer-to-peer CLOB for the active multiplayer session only.
 * Price-time priority; solo weekly challenges are untouched.
 */
@Service
public class FlashOrderBookService {

    public static final String TOPIC = "/topic/multiplayer/orderbook";
    private static final int DEPTH = 10;
    private static final MathContext MATH =
            new MathContext(16, RoundingMode.HALF_UP);
    private static final int MONEY_SCALE = 2;
    private static final int QTY_SCALE = 8;

    private final FlashPortfolioService portfolioService;
    private final FlashEventService flashEventService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final List<RestingOrder> bids = new ArrayList<>();
    private final List<RestingOrder> asks = new ArrayList<>();
    private final AtomicLong seq = new AtomicLong(0);
    private String sessionId;


    public FlashOrderBookService(
            @Lazy FlashPortfolioService portfolioService,
            @Lazy FlashEventService flashEventService,
            UserRepository userRepository,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.portfolioService = portfolioService;
        this.flashEventService = flashEventService;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }


    public synchronized void clear() {
        bids.clear();
        asks.clear();
        sessionId = null;
        seq.set(0);
        broadcast();
    }


    public synchronized void bindSession(String newSessionId) {
        clear();
        this.sessionId = newSessionId;
        broadcast();
    }


    /**
     * Seed / refresh house quotes around the reference mid so the carnet
     * is never empty at start (students still trade peer-to-peer first).
     */
    public synchronized void seedMarketMaker(BigDecimal midPrice) {
        FlashEventService.LiveSession live = flashEventService.currentOrNull();
        if (live == null || sessionId == null || midPrice == null
                || midPrice.signum() <= 0) {
            return;
        }

        // Drop previous house quotes and free their reservations.
        removeHouseOrders();
        FlashPortfolioService.Book house =
                portfolioService.ensureHouseBook(live);
        house.reservedCash = BigDecimal.ZERO.setScale(MONEY_SCALE);
        house.reservedShares = BigDecimal.ZERO.setScale(QTY_SCALE);

        BigDecimal mid = midPrice.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal levelQty = new BigDecimal("50.00000000");

        // Asks slightly above mid, bids slightly below (tight classroom spread).
        BigDecimal[] askOffsets = {
                new BigDecimal("1.005"),
                new BigDecimal("1.010"),
                new BigDecimal("1.015")
        };
        BigDecimal[] bidOffsets = {
                new BigDecimal("0.995"),
                new BigDecimal("0.990"),
                new BigDecimal("0.985")
        };

        for (BigDecimal offset : askOffsets) {
            BigDecimal price = mid.multiply(offset, MATH)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            postHouseResting("SELL", price, levelQty, house);
        }
        for (BigDecimal offset : bidOffsets) {
            BigDecimal price = mid.multiply(offset, MATH)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            postHouseResting("BUY", price, levelQty, house);
        }

        sortBids();
        sortAsks();
        broadcast();
    }


    private void removeHouseOrders() {
        bids.removeIf(o -> o.userId == FlashPortfolioService.HOUSE_USER_ID);
        asks.removeIf(o -> o.userId == FlashPortfolioService.HOUSE_USER_ID);
    }


    private void postHouseResting(
            String side,
            BigDecimal price,
            BigDecimal quantity,
            FlashPortfolioService.Book house
    ) {
        if ("BUY".equals(side)) {
            BigDecimal notional = quantity.multiply(price, MATH)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (house.availableCash().compareTo(notional) < 0) {
                return;
            }
            house.reservedCash = house.reservedCash.add(notional);
            bids.add(new RestingOrder(
                    UUID.randomUUID().toString(),
                    FlashPortfolioService.HOUSE_USER_ID,
                    "BUY",
                    price,
                    quantity,
                    seq.incrementAndGet()
            ));
        } else {
            if (house.availableShares().compareTo(quantity) < 0) {
                return;
            }
            house.reservedShares = house.reservedShares.add(quantity);
            asks.add(new RestingOrder(
                    UUID.randomUUID().toString(),
                    FlashPortfolioService.HOUSE_USER_ID,
                    "SELL",
                    price,
                    quantity,
                    seq.incrementAndGet()
            ));
        }
    }


    public synchronized FlashOrderBookSnapshot snapshot() {
        return buildSnapshot();
    }


    public synchronized FlashPortfolioResponse placeLimitOrder(
            String email,
            FlashOrderRequest request
    ) {
        FlashEventService.LiveSession live = flashEventService.requireLive();
        if (sessionId == null || !sessionId.equals(live.sessionId())) {
            bindSession(live.sessionId());
        }

        User user = userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Utilisateur introuvable."
                ));

        FlashPortfolioService.Book book =
                portfolioService.requireBook(user.getId(), live);

        BigDecimal quantity = request.quantity()
                .setScale(QTY_SCALE, RoundingMode.HALF_UP);
        BigDecimal price = request.price()
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (quantity.signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La quantité doit être strictement positive."
            );
        }
        if (price.signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le prix doit être strictement positif."
            );
        }

        String side = request.side().trim().toUpperCase(Locale.ROOT);
        int candleIndex = Math.max(0, live.visibleCandleCount() - 1);

        if ("BUY".equals(side)) {
            BigDecimal notional = quantity.multiply(price, MATH)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (book.availableCash().compareTo(notional) < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Liquidités insuffisantes pour cet achat (cash réservé inclus)."
                );
            }
            book.reservedCash = book.reservedCash.add(notional);
            RestingOrder incoming = new RestingOrder(
                    UUID.randomUUID().toString(),
                    user.getId(),
                    "BUY",
                    price,
                    quantity,
                    seq.incrementAndGet()
            );
            matchIncomingBuy(incoming, book, candleIndex);
            if (incoming.remaining.signum() > 0) {
                bids.add(incoming);
                sortBids();
            } else {
                // Fully filled: release unused reservation (already consumed in fills).
            }
        } else if ("SELL".equals(side)) {
            if (book.availableShares().compareTo(quantity) < 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Titres insuffisants (pas de vente à découvert)."
                );
            }
            book.reservedShares = book.reservedShares.add(quantity);
            RestingOrder incoming = new RestingOrder(
                    UUID.randomUUID().toString(),
                    user.getId(),
                    "SELL",
                    price,
                    quantity,
                    seq.incrementAndGet()
            );
            matchIncomingSell(incoming, book, candleIndex);
            if (incoming.remaining.signum() > 0) {
                asks.add(incoming);
                sortAsks();
            }
        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sens d'ordre non supporté."
            );
        }

        broadcast();
        return portfolioService.toResponse(user.getId(), live);
    }


    private void matchIncomingBuy(
            RestingOrder buy,
            FlashPortfolioService.Book buyerBook,
            int candleIndex
    ) {
        sortAsks();
        Iterator<RestingOrder> it = asks.iterator();
        while (it.hasNext() && buy.remaining.signum() > 0) {
            RestingOrder ask = it.next();
            if (ask.price.compareTo(buy.price) > 0) {
                break;
            }
            if (ask.userId == buy.userId) {
                continue;
            }
            BigDecimal tradeQty = buy.remaining.min(ask.remaining);
            BigDecimal tradePrice = ask.price; // resting price
            settleTrade(buy.userId, ask.userId, tradeQty, tradePrice, candleIndex);

            // Release buyer reservation for filled notional at LIMIT price reserved earlier.
            BigDecimal reservedRelease = tradeQty.multiply(buy.price, MATH)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            buyerBook.reservedCash = buyerBook.reservedCash.subtract(reservedRelease)
                    .max(BigDecimal.ZERO.setScale(MONEY_SCALE));

            FlashPortfolioService.Book sellerBook =
                    portfolioService.getBook(ask.userId);
            if (sellerBook != null) {
                sellerBook.reservedShares = sellerBook.reservedShares.subtract(tradeQty)
                        .max(BigDecimal.ZERO.setScale(QTY_SCALE));
            }

            buy.remaining = buy.remaining.subtract(tradeQty);
            ask.remaining = ask.remaining.subtract(tradeQty);
            if (ask.remaining.signum() <= 0) {
                it.remove();
            }
        }

        // If partially filled, keep reservation only for remaining * limit price.
        // (already reduced per fill above)
    }


    private void matchIncomingSell(
            RestingOrder sell,
            FlashPortfolioService.Book sellerBook,
            int candleIndex
    ) {
        sortBids();
        Iterator<RestingOrder> it = bids.iterator();
        while (it.hasNext() && sell.remaining.signum() > 0) {
            RestingOrder bid = it.next();
            if (bid.price.compareTo(sell.price) < 0) {
                break;
            }
            if (bid.userId == sell.userId) {
                continue;
            }
            BigDecimal tradeQty = sell.remaining.min(bid.remaining);
            BigDecimal tradePrice = bid.price;
            settleTrade(bid.userId, sell.userId, tradeQty, tradePrice, candleIndex);

            sellerBook.reservedShares = sellerBook.reservedShares.subtract(tradeQty)
                    .max(BigDecimal.ZERO.setScale(QTY_SCALE));

            FlashPortfolioService.Book buyerBook =
                    portfolioService.getBook(bid.userId);
            if (buyerBook != null) {
                BigDecimal reservedRelease = tradeQty.multiply(bid.price, MATH)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                buyerBook.reservedCash = buyerBook.reservedCash.subtract(reservedRelease)
                        .max(BigDecimal.ZERO.setScale(MONEY_SCALE));
            }

            sell.remaining = sell.remaining.subtract(tradeQty);
            bid.remaining = bid.remaining.subtract(tradeQty);
            if (bid.remaining.signum() <= 0) {
                it.remove();
            }
        }
    }


    private void settleTrade(
            long buyerId,
            long sellerId,
            BigDecimal qty,
            BigDecimal price,
            int candleIndex
    ) {
        FlashPortfolioService.Book buyer = portfolioService.getBook(buyerId);
        FlashPortfolioService.Book seller = portfolioService.getBook(sellerId);
        if (buyer == null || seller == null) {
            return;
        }

        BigDecimal notional = qty.multiply(price, MATH)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        // Cash already reserved on buy side at limit; debit actual trade notional from cash.
        buyer.cashBalance = buyer.cashBalance.subtract(notional);
        BigDecimal prevShares = buyer.sharesHeld;
        BigDecimal prevCost = prevShares.multiply(buyer.averageEntryPrice, MATH);
        BigDecimal newShares = prevShares.add(qty);
        buyer.averageEntryPrice = newShares.signum() == 0
                ? BigDecimal.ZERO.setScale(MONEY_SCALE)
                : prevCost.add(notional)
                        .divide(newShares, QTY_SCALE, RoundingMode.HALF_UP);
        buyer.sharesHeld = newShares.setScale(QTY_SCALE, RoundingMode.HALF_UP);

        seller.sharesHeld = seller.sharesHeld.subtract(qty)
                .setScale(QTY_SCALE, RoundingMode.HALF_UP);
        seller.cashBalance = seller.cashBalance.add(notional);
        if (seller.sharesHeld.signum() == 0) {
            seller.averageEntryPrice = BigDecimal.ZERO.setScale(MONEY_SCALE);
        }

        buyer.orders.add(0, new FlashOrderDto("BUY", qty, price, candleIndex));
        seller.orders.add(0, new FlashOrderDto("SELL", qty, price, candleIndex));
        trimOrders(buyer);
        trimOrders(seller);
    }


    private static void trimOrders(FlashPortfolioService.Book book) {
        if (book.orders.size() > 20) {
            book.orders = new ArrayList<>(book.orders.subList(0, 20));
        }
    }


    private void sortBids() {
        bids.sort(Comparator
                .comparing((RestingOrder o) -> o.price).reversed()
                .thenComparingLong(o -> o.sequence));
    }


    private void sortAsks() {
        asks.sort(Comparator
                .comparing((RestingOrder o) -> o.price)
                .thenComparingLong(o -> o.sequence));
    }


    private void broadcast() {
        messagingTemplate.convertAndSend(TOPIC, buildSnapshot());
    }


    private FlashOrderBookSnapshot buildSnapshot() {
        return new FlashOrderBookSnapshot(
                sessionId,
                flashEventService.currentOrNull() == null
                        ? null
                        : flashEventService.currentOrNull().symbol(),
                System.currentTimeMillis(),
                aggregate(bids, true),
                aggregate(asks, false)
        );
    }


    private List<FlashOrderBookLevelDto> aggregate(
            List<RestingOrder> orders,
            boolean bidsSide
    ) {
        Map<BigDecimal, Agg> levels = new LinkedHashMap<>();
        List<RestingOrder> sorted = new ArrayList<>(orders);
        if (bidsSide) {
            sorted.sort(Comparator
                    .comparing((RestingOrder o) -> o.price).reversed()
                    .thenComparingLong(o -> o.sequence));
        } else {
            sorted.sort(Comparator
                    .comparing((RestingOrder o) -> o.price)
                    .thenComparingLong(o -> o.sequence));
        }
        for (RestingOrder order : sorted) {
            if (order.remaining.signum() <= 0) {
                continue;
            }
            if (!levels.containsKey(order.price) && levels.size() >= DEPTH) {
                break;
            }
            Agg agg = levels.computeIfAbsent(order.price, p -> new Agg());
            agg.qty = agg.qty.add(order.remaining);
            agg.count++;
        }
        List<FlashOrderBookLevelDto> out = new ArrayList<>();
        for (Map.Entry<BigDecimal, Agg> entry : levels.entrySet()) {
            out.add(new FlashOrderBookLevelDto(
                    entry.getKey(),
                    entry.getValue().qty.setScale(QTY_SCALE, RoundingMode.HALF_UP),
                    entry.getValue().count
            ));
        }
        return out;
    }


    private static final class Agg {
        private BigDecimal qty = BigDecimal.ZERO;
        private int count = 0;
    }


    private static final class RestingOrder {
        private final String id;
        private final long userId;
        private final String side;
        private final BigDecimal price;
        private BigDecimal remaining;
        private final long sequence;

        private RestingOrder(
                String id,
                long userId,
                String side,
                BigDecimal price,
                BigDecimal remaining,
                long sequence
        ) {
            this.id = id;
            this.userId = userId;
            this.side = side;
            this.price = price;
            this.remaining = remaining;
            this.sequence = sequence;
        }
    }
}
