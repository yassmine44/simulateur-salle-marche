package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.AiAnalysisReport;
import tn.esprit.simulateurbackend.dto.GamificationCandleResponse;
import tn.esprit.simulateurbackend.dto.GamificationOrderRequest;
import tn.esprit.simulateurbackend.dto.GamificationOrderResponse;
import tn.esprit.simulateurbackend.dto.GamificationPortfolioResponse;
import tn.esprit.simulateurbackend.dto.HistoricalEvent;
import tn.esprit.simulateurbackend.dto.WeeklyContext;
import tn.esprit.simulateurbackend.entity.GamificationEvent;
import tn.esprit.simulateurbackend.entity.GamificationOrder;
import tn.esprit.simulateurbackend.entity.GamificationPortfolio;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.GamificationOrderRepository;
import tn.esprit.simulateurbackend.repository.GamificationPortfolioRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/*
 * Moteur simplifié offline : exécution instantanée
 * au close de la bougie visible courante.
 * Aucune interaction avec MatchingEngineService / OrderBook.
 */
@Service
public class GamificationService {

    public static final BigDecimal INITIAL_CASH =
            new BigDecimal("100000.00");

    public static final int INITIAL_VISIBLE_CANDLES =
            10;

    private static final MathContext MATH =
            new MathContext(16, RoundingMode.HALF_UP);

    private static final int MONEY_SCALE = 2;

    private static final int QTY_SCALE = 8;


    private final GamificationHistoricalDataService
            historicalDataService;

    private final GamificationEventService
            gamificationEventService;

    private final QuantitativeEngine
            quantitativeEngine;

    private final GamificationPortfolioRepository
            portfolioRepository;

    private final GamificationOrderRepository
            orderRepository;

    private final UserRepository userRepository;


    public GamificationService(
            GamificationHistoricalDataService historicalDataService,
            GamificationEventService gamificationEventService,
            QuantitativeEngine quantitativeEngine,
            GamificationPortfolioRepository portfolioRepository,
            GamificationOrderRepository orderRepository,
            UserRepository userRepository
    ) {
        this.historicalDataService = historicalDataService;
        this.gamificationEventService = gamificationEventService;
        this.quantitativeEngine = quantitativeEngine;
        this.portfolioRepository = portfolioRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }


    public List<GamificationCandleResponse> getHistoricalData(
            String eventPath
    ) {

        GamificationEvent event =
                parseEvent(eventPath);

        return historicalDataService.getCandles(event);
    }


    @Transactional
    public GamificationPortfolioResponse startChallenge(
            String authenticatedEmail,
            String eventPath
    ) {

        User user = findUser(authenticatedEmail);
        GamificationEvent event = parseEvent(eventPath);

        List<GamificationCandleResponse> candles =
                historicalDataService.getCandles(event);

        if (candles.size() < INITIAL_VISIBLE_CANDLES) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Pas assez de bougies pour démarrer le défi."
            );
        }

        GamificationPortfolio portfolio =
                portfolioRepository
                        .findByUserIdAndEvent(
                                user.getId(),
                                event
                        )
                        .orElseGet(GamificationPortfolio::new);

        if (portfolio.getId() != null) {
            orderRepository.deleteByPortfolioId(
                    portfolio.getId()
            );
        }

        portfolio.setUserId(user.getId());
        portfolio.setEvent(event);
        portfolio.setCashBalance(INITIAL_CASH);
        portfolio.setSharesHeld(zeroQty());
        portfolio.setAverageEntryPrice(zeroMoney());
        portfolio.setVisibleCandleCount(
                INITIAL_VISIBLE_CANDLES
        );
        portfolio.setStartedAt(Instant.now());

        portfolio =
                portfolioRepository.save(portfolio);

        return toResponse(portfolio, candles, List.of());
    }


    @Transactional(readOnly = true)
    public GamificationPortfolioResponse getSession(
            String authenticatedEmail,
            String eventPath
    ) {

        User user = findUser(authenticatedEmail);
        GamificationEvent event = parseEvent(eventPath);

        GamificationPortfolio portfolio =
                requirePortfolio(user.getId(), event);

        List<GamificationCandleResponse> candles =
                historicalDataService.getCandles(event);

        List<GamificationOrderResponse> orders =
                orderRepository
                        .findByPortfolioIdOrderByCreatedAtDesc(
                                portfolio.getId()
                        )
                        .stream()
                        .limit(20)
                        .map(GamificationOrderResponse::from)
                        .toList();

        return toResponse(portfolio, candles, orders);
    }


    @Transactional
    public GamificationPortfolioResponse advanceWeek(
            String authenticatedEmail,
            String eventPath
    ) {

        User user = findUser(authenticatedEmail);
        GamificationEvent event = parseEvent(eventPath);

        GamificationPortfolio portfolio =
                requirePortfolio(user.getId(), event);

        List<GamificationCandleResponse> candles =
                historicalDataService.getCandles(event);

        if (
                portfolio.getVisibleCandleCount()
                >=
                candles.size()
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Fin du défi : plus aucune période à révéler."
            );
        }

        portfolio.setVisibleCandleCount(
                portfolio.getVisibleCandleCount() + 1
        );

        portfolio =
                portfolioRepository.save(portfolio);

        List<GamificationOrderResponse> orders =
                recentOrders(portfolio.getId());

        // Le PnL latent est recalculé sur le nouveau close.
        return toResponse(portfolio, candles, orders);
    }


    @Transactional
    public GamificationPortfolioResponse placeMarketOrder(
            String authenticatedEmail,
            String eventPath,
            GamificationOrderRequest request
    ) {

        User user = findUser(authenticatedEmail);
        GamificationEvent event = parseEvent(eventPath);

        GamificationPortfolio portfolio =
                requirePortfolio(user.getId(), event);

        List<GamificationCandleResponse> candles =
                historicalDataService.getCandles(event);

        BigDecimal quantity =
                request.quantity()
                        .setScale(QTY_SCALE, RoundingMode.HALF_UP);

        if (quantity.signum() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La quantité doit être strictement positive."
            );
        }

        int candleIndex =
                portfolio.getVisibleCandleCount() - 1;

        if (candleIndex < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Aucune bougie visible pour exécuter l'ordre."
            );
        }

        BigDecimal price =
                candles.get(candleIndex).close();

        if (request.side() == OrderSide.BUY) {
            executeBuy(portfolio, quantity, price);
        } else if (request.side() == OrderSide.SELL) {
            executeSell(portfolio, quantity, price);
        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Sens d'ordre non supporté."
            );
        }

        GamificationOrder order =
                new GamificationOrder();

        order.setPortfolioId(portfolio.getId());
        order.setSide(request.side());
        order.setQuantity(quantity);
        order.setExecutionPrice(price);
        order.setCandleIndex(candleIndex);

        orderRepository.save(order);

        portfolio =
                portfolioRepository.save(portfolio);

        return toResponse(
                portfolio,
                candles,
                recentOrders(portfolio.getId())
        );
    }


    private void executeBuy(
            GamificationPortfolio portfolio,
            BigDecimal quantity,
            BigDecimal price
    ) {

        BigDecimal notional =
                quantity.multiply(price, MATH)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        if (
                portfolio.getCashBalance()
                        .compareTo(notional) < 0
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Liquidités insuffisantes pour cet achat."
            );
        }

        BigDecimal previousShares =
                portfolio.getSharesHeld();

        BigDecimal previousCost =
                previousShares.multiply(
                        portfolio.getAverageEntryPrice(),
                        MATH
                );

        BigDecimal newShares =
                previousShares.add(quantity);

        BigDecimal newAverage =
                previousCost
                        .add(notional)
                        .divide(newShares, QTY_SCALE, RoundingMode.HALF_UP);

        portfolio.setCashBalance(
                portfolio.getCashBalance().subtract(notional)
        );
        portfolio.setSharesHeld(
                newShares.setScale(QTY_SCALE, RoundingMode.HALF_UP)
        );
        portfolio.setAverageEntryPrice(newAverage);
    }


    private void executeSell(
            GamificationPortfolio portfolio,
            BigDecimal quantity,
            BigDecimal price
    ) {

        if (
                portfolio.getSharesHeld()
                        .compareTo(quantity) < 0
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Quantité de titres insuffisante."
            );
        }

        BigDecimal proceeds =
                quantity.multiply(price, MATH)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal remaining =
                portfolio.getSharesHeld()
                        .subtract(quantity)
                        .setScale(QTY_SCALE, RoundingMode.HALF_UP);

        portfolio.setCashBalance(
                portfolio.getCashBalance().add(proceeds)
        );
        portfolio.setSharesHeld(remaining);

        if (remaining.signum() == 0) {
            portfolio.setAverageEntryPrice(zeroMoney());
        }
    }


    private GamificationPortfolioResponse toResponse(
            GamificationPortfolio portfolio,
            List<GamificationCandleResponse> candles,
            List<GamificationOrderResponse> orders
    ) {

        int visible =
                portfolio.getVisibleCandleCount();

        int currentIndex =
                Math.max(0, visible - 1);

        GamificationCandleResponse currentCandle =
                candles.get(currentIndex);

        BigDecimal currentPrice =
                currentCandle.close();

        BigDecimal marketValue =
                portfolio.getSharesHeld()
                        .multiply(currentPrice, MATH)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal costBasis =
                portfolio.getSharesHeld()
                        .multiply(
                                portfolio.getAverageEntryPrice(),
                                MATH
                        )
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal unrealizedPnl =
                marketValue.subtract(costBasis);

        BigDecimal equity =
                portfolio.getCashBalance()
                        .add(marketValue)
                        .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal totalReturnPct =
                equity.subtract(INITIAL_CASH)
                        .multiply(new BigDecimal("100"))
                        .divide(
                                INITIAL_CASH,
                                4,
                                RoundingMode.HALF_UP
                        );

        boolean completed =
                visible >= candles.size();

        List<GamificationCandleResponse> visibleCandles =
                candles.subList(0, visible);

        String nextWeekStart =
                currentIndex + 1 < candles.size()
                        ? candles.get(currentIndex + 1).date()
                        : null;

        List<HistoricalEvent> weekEvents =
                gamificationEventService
                        .getEventsForWeek(
                                portfolio.getEvent(),
                                currentCandle.date(),
                                nextWeekStart
                        );

        AiAnalysisReport aiAnalysis =
                quantitativeEngine.analyze(
                        visibleCandles,
                        weekEvents
                );

        WeeklyContext weeklyContext =
                new WeeklyContext(
                        aiAnalysis,
                        weekEvents
                );

        return GamificationPortfolioResponse.of(
                portfolio.getEvent(),
                portfolio.getCashBalance(),
                portfolio.getSharesHeld(),
                portfolio.getAverageEntryPrice(),
                visible,
                candles.size(),
                currentPrice,
                marketValue,
                unrealizedPnl,
                equity,
                totalReturnPct,
                completed,
                portfolio.getStartedAt(),
                portfolio.getUpdatedAt(),
                orders,
                currentCandle,
                weeklyContext
        );
    }


    private List<GamificationOrderResponse> recentOrders(
            Long portfolioId
    ) {

        return orderRepository
                .findByPortfolioIdOrderByCreatedAtDesc(
                        portfolioId
                )
                .stream()
                .limit(20)
                .map(GamificationOrderResponse::from)
                .toList();
    }


    private GamificationPortfolio requirePortfolio(
            Long userId,
            GamificationEvent event
    ) {

        return portfolioRepository
                .findByUserIdAndEvent(userId, event)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Aucun défi démarré pour cet événement. "
                                        + "Appelez /start d'abord."
                        )
                );
    }


    private GamificationEvent parseEvent(
            String eventPath
    ) {

        try {
            return GamificationEvent.fromPath(eventPath);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    exception.getMessage()
            );
        }
    }


    private User findUser(
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

        return userRepository
                .findByEmailIgnoreCase(
                        authenticatedEmail
                                .trim()
                                .toLowerCase(Locale.ROOT)
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Utilisateur introuvable."
                        )
                );
    }


    private static BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE);
    }


    private static BigDecimal zeroQty() {
        return BigDecimal.ZERO.setScale(QTY_SCALE);
    }
}
