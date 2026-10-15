package tn.esprit.simulateurbackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.BuyingPowerResponse;
import tn.esprit.simulateurbackend.dto.CandleResponse;
import tn.esprit.simulateurbackend.dto.PortfolioResponse;
import tn.esprit.simulateurbackend.dto.PortfolioResponse.AllocationSlice;
import tn.esprit.simulateurbackend.dto.PortfolioResponse.ClosedPosition;
import tn.esprit.simulateurbackend.dto.PortfolioResponse.EquityPoint;
import tn.esprit.simulateurbackend.dto.PortfolioResponse.Position;
import tn.esprit.simulateurbackend.dto.PortfolioResponse.Risk;
import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.AssetType;
import tn.esprit.simulateurbackend.entity.CancelReason;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.PortfolioAccount;
import tn.esprit.simulateurbackend.entity.Trade;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.PortfolioAccountRepository;
import tn.esprit.simulateurbackend.repository.TradeRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;
import tn.esprit.simulateurbackend.service.PortfolioCalculator.Fill;
import tn.esprit.simulateurbackend.service.PortfolioCalculator.Holding;
import tn.esprit.simulateurbackend.service.PortfolioCalculator.Ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/*
 * Portefeuille virtuel.
 *
 * Source de vérité : les transactions (ce qui a été exécuté)
 * et les ordres ouverts (ce qui est engagé). Liquidités,
 * positions, résultats et indicateurs en sont déduits à
 * chaque demande : ils ne peuvent jamais diverger de
 * l'historique réel.
 */
@Service
@Transactional(readOnly = true)
public class PortfolioService {

    private static final BigDecimal HUNDRED =
            BigDecimal.valueOf(100);

    private static final List<OrderStatus> OPEN_STATUSES =
            List.of(
                    OrderStatus.PENDING,
                    OrderStatus.PARTIALLY_FILLED
            );

    private static final List<Integer> HISTORY_DAYS =
            List.of(7, 30);

    // Volatilité : rendements entre clôtures de 5 minutes, sur 24 h.
    private static final Duration VOLATILITY_BUCKET =
            Duration.ofMinutes(5);

    private static final Duration VOLATILITY_WINDOW =
            Duration.ofHours(24);

    private final TradeRepository tradeRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final PortfolioAccountRepository accountRepository;
    private final MarketDataService marketDataService;
    private final HistoricalPrices historicalPrices;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.portfolio.initial-capital:100000}")
    private BigDecimal defaultInitialCapital;

    @Value("${app.portfolio.currency:USD}")
    private String currency;

    /*
     * true : un ordre est refusé si les liquidités disponibles
     * (achat) ou les titres disponibles (vente) sont insuffisants.
     */
    @Value("${app.portfolio.enforce-limits:true}")
    private boolean enforceLimits;

    public PortfolioService(
            TradeRepository tradeRepository,
            OrderRepository orderRepository,
            UserRepository userRepository,
            AssetRepository assetRepository,
            PortfolioAccountRepository accountRepository,
            MarketDataService marketDataService,
            HistoricalPrices historicalPrices,
            ApplicationEventPublisher eventPublisher
    ) {
        this.tradeRepository = tradeRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.assetRepository = assetRepository;
        this.accountRepository = accountRepository;
        this.marketDataService = marketDataService;
        this.historicalPrices = historicalPrices;
        this.eventPublisher = eventPublisher;
    }


    // =========================================
    // SNAPSHOT
    // =========================================

    /*
     * État comptable à l'instant présent : liquidités,
     * positions, et ce que les ordres ouverts réservent.
     */
    public record Snapshot(
            BigDecimal initialCapital,
            Instant startedAt,
            int resetCount,
            List<Fill> fills,
            Ledger ledger,
            BigDecimal reservedCash,
            Map<String, BigDecimal> reservedQuantities
    ) {

        public BigDecimal availableCash() {
            return ledger.cash().subtract(reservedCash);
        }

        public BigDecimal reservedQuantity(String symbol) {
            return reservedQuantities.getOrDefault(symbol, BigDecimal.ZERO);
        }

        public BigDecimal availableQuantity(String symbol) {
            return ledger.quantity(symbol).subtract(reservedQuantity(symbol));
        }
    }


    private record OcoReservation(
            String symbol,
            BigDecimal quantity
    ) {
    }


    public Snapshot snapshot(User user) {

        PortfolioAccount account =
                accountRepository
                        .findByUserId(user.getId())
                        .orElse(null);

        BigDecimal initialCapital =
                account == null
                        ? defaultInitialCapital
                        : account.getInitialCapital();

        long resetAfterTradeId =
                account == null
                        ? 0
                        : account.getResetAfterTradeId();

        List<Fill> fills =
                tradeRepository
                        .findAllForUser(user.getId())
                        .stream()
                        .filter(trade -> trade.getId() > resetAfterTradeId)
                        .map(trade -> toFill(trade, user.getId()))
                        .sorted(
                                Comparator
                                        .comparing(Fill::time)
                                        .thenComparingLong(Fill::tradeId)
                        )
                        .toList();

        Ledger ledger =
                PortfolioCalculator.replay(initialCapital, fills, null);

        BigDecimal reservedCash = BigDecimal.ZERO;

        Map<String, BigDecimal> reservedQuantities = new HashMap<>();

        Map<String, OcoReservation> ocoReservations = new HashMap<>();

        for (
                Order order
                : orderRepository.findByUserIdAndStatusIn(
                        user.getId(),
                        OPEN_STATUSES
                )
        ) {

            if (order.getSide() == OrderSide.BUY) {

                // Ordre limite : au plus son prix limite.
                // Stop / take-profit : estimé à son seuil de déclenchement.
                BigDecimal price =
                        order.getType() == OrderType.LIMIT
                                ? order.getLimitPrice()
                                : order.getTriggerPrice();

                if (price != null) {
                    reservedCash =
                            reservedCash.add(
                                    order.getRemainingQuantity().multiply(price)
                            );
                }

            } else if (order.getOcoGroup() != null) {

                // Les branches d'un OCO protègent les mêmes titres :
                // on ne les compte qu'une fois (la plus grande).
                ocoReservations.merge(
                        order.getOcoGroup(),
                        new OcoReservation(order.getAssetSymbol(), order.getRemainingQuantity()),
                        (first, second) ->
                                first.quantity().compareTo(second.quantity()) >= 0 ? first : second
                );

            } else {

                reservedQuantities.merge(
                        order.getAssetSymbol(),
                        order.getRemainingQuantity(),
                        BigDecimal::add
                );
            }
        }

        ocoReservations.values().forEach(reservation ->
                reservedQuantities.merge(
                        reservation.symbol(),
                        reservation.quantity(),
                        BigDecimal::add
                )
        );

        return new Snapshot(
                initialCapital,
                account == null ? null : account.getStartedAt(),
                account == null ? 0 : account.getResetCount(),
                fills,
                ledger,
                reservedCash,
                reservedQuantities
        );
    }


    public boolean isEnforcingLimits() {
        return enforceLimits;
    }


    public String currency() {
        return currency;
    }


    // =========================================
    // BUYING POWER
    // =========================================

    public BuyingPowerResponse getBuyingPower(
            String authenticatedEmail,
            String rawSymbol
    ) {

        Snapshot snapshot = snapshot(findUser(authenticatedEmail));

        String symbol =
                rawSymbol == null
                        ? null
                        : rawSymbol.trim().toUpperCase(Locale.ROOT);

        return new BuyingPowerResponse(
                currency,
                enforceLimits,
                money(snapshot.ledger().cash()),
                money(snapshot.reservedCash()),
                money(snapshot.availableCash()),
                symbol,
                symbol == null ? BigDecimal.ZERO : plain(snapshot.ledger().quantity(symbol)),
                symbol == null ? BigDecimal.ZERO : plain(snapshot.reservedQuantity(symbol)),
                symbol == null ? BigDecimal.ZERO : plain(snapshot.availableQuantity(symbol))
        );
    }


    // =========================================
    // PORTFOLIO
    // =========================================

    public PortfolioResponse getPortfolio(
            String authenticatedEmail,
            Integer requestedHistoryDays
    ) {

        int historyDays =
                requestedHistoryDays != null
                        && HISTORY_DAYS.contains(requestedHistoryDays)
                        ? requestedHistoryDays
                        : HISTORY_DAYS.get(0);

        Snapshot snapshot = snapshot(findUser(authenticatedEmail));

        Ledger ledger = snapshot.ledger();

        Instant now = Instant.now();

        BigDecimal positionsValue = BigDecimal.ZERO;
        BigDecimal unrealizedPnl = BigDecimal.ZERO;
        BigDecimal realizedPnl = BigDecimal.ZERO;

        List<OpenLine> openLines = new ArrayList<>();
        List<ClosedPosition> closedPositions = new ArrayList<>();

        for (Holding holding : ledger.holdings()) {

            Asset asset =
                    assetRepository
                            .findBySymbolIgnoreCase(holding.symbol())
                            .orElse(null);

            realizedPnl = realizedPnl.add(holding.realizedPnl());

            if (!holding.isOpen()) {

                closedPositions.add(
                        new ClosedPosition(
                                holding.symbol(),
                                asset == null ? holding.symbol() : asset.getName(),
                                plain(holding.boughtQuantity()),
                                plain(holding.soldQuantity()),
                                money(holding.realizedPnl())
                        )
                );

                continue;
            }

            // Sans cours connu : valorisé au prix de revient.
            BigDecimal lastPrice =
                    asset == null
                            ? holding.averagePrice()
                            : marketDataService
                            .lastPrice(asset)
                            .orElse(holding.averagePrice());

            BigDecimal marketValue =
                    holding.quantity().multiply(lastPrice);

            BigDecimal costBasis =
                    holding.quantity().multiply(holding.averagePrice());

            positionsValue = positionsValue.add(marketValue);

            unrealizedPnl =
                    unrealizedPnl.add(marketValue.subtract(costBasis));

            openLines.add(
                    new OpenLine(holding, asset, lastPrice, marketValue, costBasis)
            );
        }

        BigDecimal totalValue = ledger.cash().add(positionsValue);

        BigDecimal totalPnl =
                totalValue.subtract(snapshot.initialCapital());

        List<Position> openPositions =
                openLines.stream()
                        .map(line -> toPosition(line, snapshot, totalValue))
                        .sorted(
                                Comparator.comparing(
                                        (Position position) ->
                                                position.marketValue().abs()
                                ).reversed()
                        )
                        .toList();

        List<EquityPoint> history =
                history(snapshot, historyDays, totalValue, now);

        return new PortfolioResponse(
                currency,
                money(snapshot.initialCapital()),
                snapshot.startedAt(),
                snapshot.resetCount(),
                enforceLimits,
                money(ledger.cash()),
                money(snapshot.reservedCash()),
                money(snapshot.availableCash()),
                money(positionsValue),
                money(totalValue),
                money(totalPnl),
                percent(totalPnl, snapshot.initialCapital()),
                money(realizedPnl),
                money(unrealizedPnl),
                snapshot.fills().size(),
                openPositions,
                closedPositions,
                allocation(ledger.cash(), openLines),
                risk(openPositions, totalValue, history, now),
                historyDays,
                history,
                now
        );
    }


    private record OpenLine(
            Holding holding,
            Asset asset,
            BigDecimal lastPrice,
            BigDecimal marketValue,
            BigDecimal costBasis
    ) {
    }


    private Position toPosition(
            OpenLine line,
            Snapshot snapshot,
            BigDecimal totalValue
    ) {

        Holding holding = line.holding();

        BigDecimal unrealized =
                line.marketValue().subtract(line.costBasis());

        return new Position(
                holding.symbol(),
                line.asset() == null ? holding.symbol() : line.asset().getName(),
                line.asset() == null ? null : line.asset().getType().name(),
                plain(holding.quantity()),
                holding.quantity().signum() > 0 ? "LONG" : "SHORT",
                plain(holding.averagePrice()),
                plain(line.lastPrice()),
                money(line.costBasis()),
                money(line.marketValue()),
                money(unrealized),
                percent(unrealized, line.costBasis().abs()),
                money(holding.realizedPnl()),
                percent(line.marketValue().abs(), totalValue),
                plain(snapshot.reservedQuantity(holding.symbol())),
                plain(
                        snapshot.availableQuantity(holding.symbol())
                                .max(BigDecimal.ZERO)
                ),
                holding.openedAt()
        );
    }


    // =========================================
    // ALLOCATION
    // =========================================

    private List<AllocationSlice> allocation(
            BigDecimal cash,
            List<OpenLine> openLines
    ) {

        Map<String, BigDecimal> values = new LinkedHashMap<>();

        for (OpenLine line : openLines) {

            // Une position vendeuse n'est pas un actif détenu.
            if (line.marketValue().signum() <= 0) {
                continue;
            }

            String key =
                    line.asset() == null
                            ? "OTHER"
                            : line.asset().getType().name();

            values.merge(key, line.marketValue(), BigDecimal::add);
        }

        if (cash.signum() > 0) {
            values.put("CASH", cash);
        }

        BigDecimal total =
                values.values().stream()
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AllocationSlice> slices = new ArrayList<>();

        values.forEach((key, value) ->
                slices.add(
                        new AllocationSlice(
                                key,
                                allocationLabel(key),
                                money(value),
                                percent(value, total)
                        )
                )
        );

        slices.sort(
                Comparator.comparing(AllocationSlice::value).reversed()
        );

        return slices;
    }


    private String allocationLabel(String key) {

        if ("CASH".equals(key)) {
            return "Liquidités";
        }

        if ("OTHER".equals(key)) {
            return "Autres";
        }

        return switch (AssetType.valueOf(key)) {
            case STOCK -> "Actions";
            case CRYPTO -> "Cryptomonnaies";
            case FOREX -> "Devises";
            case INDEX -> "Indices";
        };
    }


    // =========================================
    // RISK
    // =========================================

    private Risk risk(
            List<Position> openPositions,
            BigDecimal totalValue,
            List<EquityPoint> history,
            Instant now
    ) {

        BigDecimal grossValue =
                openPositions.stream()
                        .map(position -> position.marketValue().abs())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal maxDrawdown = maxDrawdown(history);

        String drawdownLevel =
                level(maxDrawdown.doubleValue(), 5, 15);

        if (grossValue.signum() == 0) {

            return new Risk(
                    zero(), "LOW",
                    null, zero(), "LOW",
                    zero(), BigDecimal.ZERO, "LOW",
                    zero(), "LOW",
                    maxDrawdown, drawdownLevel,
                    drawdownLevel
            );
        }

        BigDecimal exposure =
                totalValue.signum() > 0
                        ? percent(grossValue, totalValue)
                        : HUNDRED.setScale(2);

        // Positions déjà triées par valeur décroissante.
        Position largest = openPositions.get(0);

        BigDecimal largestPercent = largest.weightPercent();

        double concentration = 0;
        double volatility = 0;

        for (Position position : openPositions) {

            double weight =
                    position.marketValue().abs().doubleValue()
                            / grossValue.doubleValue();

            concentration += weight * weight;

            volatility +=
                    weight * assetVolatility(position.assetSymbol(), now);
        }

        BigDecimal diversification =
                BigDecimal.valueOf((1 - concentration) * 100)
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal effectivePositions =
                BigDecimal.valueOf(1 / concentration)
                        .setScale(1, RoundingMode.HALF_UP);

        BigDecimal volatilityPercent =
                BigDecimal.valueOf(volatility * 100)
                        .setScale(3, RoundingMode.HALF_UP);

        String exposureLevel =
                level(exposure.doubleValue(), 50, 100);

        String concentrationLevel =
                level(largestPercent.doubleValue(), 20, 40);

        // Une diversification élevée réduit le risque.
        String diversificationLevel =
                diversification.doubleValue() >= 60
                        ? "LOW"
                        : diversification.doubleValue() >= 30
                        ? "MEDIUM"
                        : "HIGH";

        String volatilityLevel =
                level(volatilityPercent.doubleValue(), 0.2, 0.5);

        return new Risk(
                exposure,
                exposureLevel,
                largest.assetSymbol(),
                largestPercent,
                concentrationLevel,
                diversification,
                effectivePositions,
                diversificationLevel,
                volatilityPercent,
                volatilityLevel,
                maxDrawdown,
                drawdownLevel,
                worst(
                        exposureLevel,
                        concentrationLevel,
                        volatilityLevel,
                        drawdownLevel
                )
        );
    }


    /*
     * Écart-type des rendements entre deux clôtures
     * successives de 5 minutes, sur les dernières 24 h.
     */
    private double assetVolatility(
            String symbol,
            Instant now
    ) {

        List<CandleResponse> candles =
                MarketDataService.buildCandles(
                        tradeRepository.findTicksSince(
                                symbol,
                                now.minus(VOLATILITY_WINDOW)
                        ),
                        VOLATILITY_BUCKET
                );

        List<Double> returns = new ArrayList<>();

        for (int index = 1; index < candles.size(); index++) {

            double previous = candles.get(index - 1).close().doubleValue();
            double current = candles.get(index).close().doubleValue();

            if (previous > 0) {
                returns.add(current / previous - 1);
            }
        }

        if (returns.size() < 2) {
            return 0;
        }

        double mean =
                returns.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0);

        double variance =
                returns.stream()
                        .mapToDouble(value -> (value - mean) * (value - mean))
                        .sum()
                        / (returns.size() - 1);

        return Math.sqrt(variance);
    }


    // Plus forte baisse, en %, entre un sommet et un point ultérieur.
    private BigDecimal maxDrawdown(
            List<EquityPoint> history
    ) {

        BigDecimal peak = null;
        BigDecimal worst = BigDecimal.ZERO;

        for (EquityPoint point : history) {

            if (peak == null || point.value().compareTo(peak) > 0) {
                peak = point.value();
            }

            if (peak.signum() > 0) {

                BigDecimal drawdown =
                        peak.subtract(point.value())
                                .multiply(HUNDRED)
                                .divide(peak, 2, RoundingMode.HALF_UP);

                worst = worst.max(drawdown);
            }
        }

        return worst.setScale(2, RoundingMode.HALF_UP);
    }


    private String level(
            double value,
            double mediumFrom,
            double highFrom
    ) {

        return value >= highFrom
                ? "HIGH"
                : value >= mediumFrom
                ? "MEDIUM"
                : "LOW";
    }


    private String worst(String... levels) {

        List<String> order = List.of("LOW", "MEDIUM", "HIGH");

        String worst = "LOW";

        for (String level : levels) {

            if (order.indexOf(level) > order.indexOf(worst)) {
                worst = level;
            }
        }

        return worst;
    }


    // =========================================
    // HISTORY
    // =========================================

    /*
     * Valeur totale à la fin de chacun des jours précédents,
     * puis valeur actuelle. Chaque position est valorisée au
     * dernier cours du marché connu à cette date.
     */
    private List<EquityPoint> history(
            Snapshot snapshot,
            int days,
            BigDecimal currentValue,
            Instant now
    ) {

        HistoricalPrices.Lookup prices = historicalPrices.lookup();

        List<EquityPoint> points = new ArrayList<>();

        List<Instant> instants = historyInstants(days, now);

        for (int index = 0; index < instants.size() - 1; index++) {

            Instant endOfDay = instants.get(index);

            points.add(
                    new EquityPoint(endOfDay, money(valueAt(snapshot, endOfDay, prices)))
            );
        }

        points.add(new EquityPoint(now, money(currentValue)));

        return points;
    }


    /*
     * Instants de l'historique : fin de chacun des jours précédents
     * (minuit, fuseau du serveur), puis maintenant.
     */
    public static List<Instant> historyInstants(
            int days,
            Instant now
    ) {

        ZoneId zone = ZoneId.systemDefault();

        LocalDate today = LocalDate.now(zone);

        List<Instant> instants = new ArrayList<>();

        for (int daysAgo = days - 1; daysAgo >= 1; daysAgo--) {

            // Fin de journée = début du jour suivant.
            instants.add(
                    today.minusDays(daysAgo - 1)
                            .atStartOfDay(zone)
                            .toInstant()
            );
        }

        instants.add(now);

        return instants;
    }


    /*
     * Valeur totale du portefeuille à une date passée : liquidités
     * et positions à cette date, valorisées au cours de cette date.
     */
    public BigDecimal valueAt(
            Snapshot snapshot,
            Instant instant,
            HistoricalPrices.Lookup prices
    ) {

        Ledger ledger =
                PortfolioCalculator.replay(
                        snapshot.initialCapital(),
                        snapshot.fills(),
                        instant
                );

        BigDecimal value = ledger.cash();

        for (Holding holding : ledger.holdings()) {

            if (!holding.isOpen()) {
                continue;
            }

            BigDecimal price =
                    prices.priceAt(holding.symbol(), instant, holding.averagePrice());

            value = value.add(holding.quantity().multiply(price));
        }

        return value;
    }


    // =========================================
    // RESET
    // =========================================

    /*
     * Repart de zéro : annule les ordres ouverts et ne compte plus
     * les transactions passées. Rien n'est effacé : l'historique
     * des ordres et des transactions reste consultable.
     */
    @Transactional
    public PortfolioResponse reset(
            String authenticatedEmail
    ) {

        User user = findUser(authenticatedEmail);

        Set<String> touchedSymbols = new LinkedHashSet<>();

        for (
                Order order
                : orderRepository.findByUserIdAndStatusIn(
                        user.getId(),
                        OPEN_STATUSES
                )
        ) {

            order.cancel(CancelReason.RESET);

            orderRepository.save(order);

            touchedSymbols.add(order.getAssetSymbol());
        }

        PortfolioAccount account =
                accountRepository
                        .findByUserId(user.getId())
                        .orElseGet(() -> {

                            PortfolioAccount created = new PortfolioAccount();

                            created.setUserId(user.getId());

                            return created;
                        });

        account.setInitialCapital(defaultInitialCapital);

        account.setResetAfterTradeId(
                tradeRepository
                        .findTopByOrderByIdDesc()
                        .map(Trade::getId)
                        .orElse(0L)
        );

        account.setStartedAt(Instant.now());

        account.setResetCount(account.getResetCount() + 1);

        accountRepository.saveAndFlush(account);

        touchedSymbols.forEach(symbol ->
                eventPublisher.publishEvent(
                        new OrderBookChangedEvent(symbol)
                )
        );

        return getPortfolio(authenticatedEmail, null);
    }


    // =========================================
    // HELPERS
    // =========================================

    private Fill toFill(
            Trade trade,
            Long userId
    ) {

        boolean buyer =
                trade.getBuyOrder()
                        .getUser()
                        .getId()
                        .equals(userId);

        return new Fill(
                trade.getCreatedAt(),
                trade.getId(),
                trade.getAssetSymbol(),
                buyer
                        ? trade.getQuantity()
                        : trade.getQuantity().negate(),
                trade.getExecutionPrice()
        );
    }


    public User findUser(
            String authenticatedEmail
    ) {

        if (
                authenticatedEmail == null
                        || authenticatedEmail.isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Utilisateur non authentifié."
            );
        }

        return userRepository
                .findByEmailIgnoreCase(
                        authenticatedEmail.trim().toLowerCase(Locale.ROOT)
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Utilisateur introuvable."
                        )
                );
    }


    static BigDecimal money(BigDecimal value) {

        return value.setScale(2, RoundingMode.HALF_UP);
    }


    private static BigDecimal plain(BigDecimal value) {

        BigDecimal stripped = value.stripTrailingZeros();

        // Évite la notation scientifique (1E+1) dans le JSON.
        return stripped.scale() < 0
                ? stripped.setScale(0)
                : stripped;
    }


    private static BigDecimal zero() {

        return BigDecimal.ZERO.setScale(2);
    }


    private static BigDecimal percent(
            BigDecimal value,
            BigDecimal base
    ) {

        if (base.signum() == 0) {
            return zero();
        }

        return value
                .multiply(HUNDRED)
                .divide(base, 2, RoundingMode.HALF_UP);
    }
}
