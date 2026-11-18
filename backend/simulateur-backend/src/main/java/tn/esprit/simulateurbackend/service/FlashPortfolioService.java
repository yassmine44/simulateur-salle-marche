package tn.esprit.simulateurbackend.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.FlashCandleDto;
import tn.esprit.simulateurbackend.dto.FlashOrderDto;
import tn.esprit.simulateurbackend.dto.FlashOrderRequest;
import tn.esprit.simulateurbackend.dto.FlashPortfolioResponse;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.UserRepository;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory multiplayer portfolios. Order execution is peer-to-peer via
 * {@link FlashOrderBookService} (CLOB). Solo weekly challenges are separate.
 */
@Service
public class FlashPortfolioService {

    public static final BigDecimal INITIAL_CASH =
            new BigDecimal("100000.00");

    /** Starting inventory so students can sell into the carnet. */
    public static final BigDecimal INITIAL_SHARES =
            new BigDecimal("500.00000000");

    /** Synthetic house desk that quotes both sides of the book. */
    public static final long HOUSE_USER_ID = -1L;

    private static final MathContext MATH =
            new MathContext(16, RoundingMode.HALF_UP);
    private static final int MONEY_SCALE = 2;
    private static final int QTY_SCALE = 8;

    private final UserRepository userRepository;
    private final FlashEventService flashEventService;
    private final FlashOrderBookService orderBookService;

    private final Map<Long, Book> books = new ConcurrentHashMap<>();

    public FlashPortfolioService(
            UserRepository userRepository,
            @Lazy FlashEventService flashEventService,
            @Lazy FlashOrderBookService orderBookService
    ) {
        this.userRepository = userRepository;
        this.flashEventService = flashEventService;
        this.orderBookService = orderBookService;
    }


    public void clearAll() {
        books.clear();
        orderBookService.clear();
    }


    public FlashPortfolioResponse join(String email) {
        FlashEventService.LiveSession live = flashEventService.requireLive();
        User user = findUser(email);
        BigDecimal mark = currentMark(live);
        books.compute(user.getId(), (id, existing) -> {
            if (existing != null
                    && live.sessionId().equals(existing.sessionId)) {
                return existing;
            }
            return Book.freshStudent(live.sessionId(), live.symbol(), mark);
        });
        return toResponse(user.getId(), live);
    }


    public FlashPortfolioResponse getPortfolio(String email) {
        FlashEventService.LiveSession live = flashEventService.requireLive();
        User user = findUser(email);
        Book book = books.get(user.getId());
        if (book == null || !live.sessionId().equals(book.sessionId)) {
            return join(email);
        }
        return toResponse(user.getId(), live);
    }


    public FlashPortfolioResponse placeOrder(
            String email,
            FlashOrderRequest request
    ) {
        return orderBookService.placeLimitOrder(email, request);
    }


    Book requireBook(long userId, FlashEventService.LiveSession live) {
        BigDecimal mark = currentMark(live);
        return books.compute(userId, (id, existing) -> {
            if (existing != null && live.sessionId().equals(existing.sessionId)) {
                return existing;
            }
            if (userId == HOUSE_USER_ID) {
                return Book.freshHouse(live.sessionId(), live.symbol(), mark);
            }
            return Book.freshStudent(live.sessionId(), live.symbol(), mark);
        });
    }


    Book getBook(long userId) {
        return books.get(userId);
    }


    /**
     * Ensure the house desk book exists with deep inventory for quoting.
     */
    Book ensureHouseBook(FlashEventService.LiveSession live) {
        return requireBook(HOUSE_USER_ID, live);
    }


    FlashPortfolioResponse toResponse(
            long userId,
            FlashEventService.LiveSession live
    ) {
        Book book = books.get(userId);
        if (book == null) {
            book = Book.freshStudent(
                    live.sessionId(),
                    live.symbol(),
                    currentMark(live)
            );
            books.put(userId, book);
        }

        int visible = live.visibleCandleCount();
        int idx = Math.max(0, visible - 1);
        FlashCandleDto current = live.candles().get(idx);
        BigDecimal mark = current.close();

        BigDecimal marketValue = book.sharesHeld
                .multiply(mark, MATH)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal costBasis = book.sharesHeld
                .multiply(book.averageEntryPrice, MATH)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal unrealized = marketValue.subtract(costBasis);
        BigDecimal equity = book.cashBalance.add(marketValue)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal totalReturnPct = equity.subtract(book.initialEquity)
                .multiply(new BigDecimal("100"))
                .divide(
                        book.initialEquity.signum() == 0
                                ? INITIAL_CASH
                                : book.initialEquity,
                        4,
                        RoundingMode.HALF_UP
                );

        return new FlashPortfolioResponse(
                live.sessionId(),
                live.symbol(),
                book.cashBalance,
                book.sharesHeld,
                book.averageEntryPrice,
                mark,
                marketValue,
                equity,
                unrealized,
                totalReturnPct,
                visible,
                live.candles().size(),
                visible >= live.candles().size()
                        || "STOPPED".equals(live.status()),
                current,
                List.copyOf(book.orders),
                live.mlMetrics()
        );
    }


    private static BigDecimal currentMark(FlashEventService.LiveSession live) {
        int idx = Math.max(0, live.visibleCandleCount() - 1);
        return live.candles().get(idx).close();
    }


    private User findUser(String email) {
        return userRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Utilisateur introuvable."
                ));
    }


    static final class Book {
        final String sessionId;
        final String symbol;
        final BigDecimal initialEquity;
        BigDecimal cashBalance;
        BigDecimal sharesHeld;
        BigDecimal averageEntryPrice;
        BigDecimal reservedCash;
        BigDecimal reservedShares;
        List<FlashOrderDto> orders;

        private Book(
                String sessionId,
                String symbol,
                BigDecimal cashBalance,
                BigDecimal sharesHeld,
                BigDecimal averageEntryPrice,
                BigDecimal reservedCash,
                BigDecimal reservedShares,
                BigDecimal initialEquity,
                List<FlashOrderDto> orders
        ) {
            this.sessionId = sessionId;
            this.symbol = symbol;
            this.cashBalance = cashBalance;
            this.sharesHeld = sharesHeld;
            this.averageEntryPrice = averageEntryPrice;
            this.reservedCash = reservedCash;
            this.reservedShares = reservedShares;
            this.initialEquity = initialEquity;
            this.orders = orders;
        }

        static Book freshStudent(
                String sessionId,
                String symbol,
                BigDecimal mark
        ) {
            BigDecimal entry = mark.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal shares = INITIAL_SHARES;
            BigDecimal stockValue = shares.multiply(entry, MATH)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal equity = INITIAL_CASH.add(stockValue);
            return new Book(
                    sessionId,
                    symbol,
                    INITIAL_CASH,
                    shares,
                    entry,
                    BigDecimal.ZERO.setScale(MONEY_SCALE),
                    BigDecimal.ZERO.setScale(QTY_SCALE),
                    equity,
                    new ArrayList<>()
            );
        }

        static Book freshHouse(
                String sessionId,
                String symbol,
                BigDecimal mark
        ) {
            BigDecimal entry = mark.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal cash = new BigDecimal("10000000.00");
            BigDecimal shares = new BigDecimal("1000000.00000000");
            BigDecimal equity = cash.add(
                    shares.multiply(entry, MATH)
                            .setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            );
            return new Book(
                    sessionId,
                    symbol,
                    cash,
                    shares,
                    entry,
                    BigDecimal.ZERO.setScale(MONEY_SCALE),
                    BigDecimal.ZERO.setScale(QTY_SCALE),
                    equity,
                    new ArrayList<>()
            );
        }

        BigDecimal availableCash() {
            return cashBalance.subtract(reservedCash)
                    .max(BigDecimal.ZERO.setScale(MONEY_SCALE));
        }

        BigDecimal availableShares() {
            return sharesHeld.subtract(reservedShares)
                    .max(BigDecimal.ZERO.setScale(QTY_SCALE));
        }
    }
}
