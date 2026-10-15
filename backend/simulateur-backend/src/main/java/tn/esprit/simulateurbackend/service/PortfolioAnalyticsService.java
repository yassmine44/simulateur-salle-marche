package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.simulateurbackend.dto.PerformanceResponse;
import tn.esprit.simulateurbackend.dto.PerformanceResponse.Benchmark;
import tn.esprit.simulateurbackend.dto.PerformanceResponse.Contribution;
import tn.esprit.simulateurbackend.dto.PerformanceResponse.ExecutionQuality;
import tn.esprit.simulateurbackend.dto.PerformanceResponse.TradeHighlight;
import tn.esprit.simulateurbackend.dto.PerformanceResponse.TraderStats;
import tn.esprit.simulateurbackend.dto.PortfolioResponse.EquityPoint;
import tn.esprit.simulateurbackend.dto.TradeResponse;
import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.repository.TradeRepository;
import tn.esprit.simulateurbackend.service.PortfolioCalculator.Holding;
import tn.esprit.simulateurbackend.service.PortfolioCalculator.RoundTrip;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * Analyse de la performance du portefeuille.
 */
@Service
@Transactional(readOnly = true)
public class PortfolioAnalyticsService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final BigDecimal BPS = BigDecimal.valueOf(10_000);

    private static final List<Integer> HISTORY_DAYS = List.of(7, 30);

    private final PortfolioService portfolioService;
    private final MarketDataService marketDataService;
    private final AssetRepository assetRepository;
    private final TradeRepository tradeRepository;
    private final HistoricalPrices historicalPrices;

    public PortfolioAnalyticsService(
            PortfolioService portfolioService,
            MarketDataService marketDataService,
            AssetRepository assetRepository,
            TradeRepository tradeRepository,
            HistoricalPrices historicalPrices
    ) {
        this.portfolioService = portfolioService;
        this.marketDataService = marketDataService;
        this.assetRepository = assetRepository;
        this.tradeRepository = tradeRepository;
        this.historicalPrices = historicalPrices;
    }


    public PerformanceResponse getPerformance(
            String authenticatedEmail,
            Integer requestedHistoryDays
    ) {

        int historyDays =
                requestedHistoryDays != null && HISTORY_DAYS.contains(requestedHistoryDays)
                        ? requestedHistoryDays
                        : HISTORY_DAYS.get(0);

        User user = portfolioService.findUser(authenticatedEmail);

        PortfolioService.Snapshot snapshot = portfolioService.snapshot(user);

        Instant now = Instant.now();

        List<Contribution> contributions = contributions(snapshot);

        BigDecimal totalPnl =
                contributions.stream()
                        .map(Contribution::totalPnl)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal portfolioReturn = percent(totalPnl, snapshot.initialCapital());

        return new PerformanceResponse(
                portfolioService.currency(),
                stats(snapshot.ledger().roundTrips()),
                contributions,
                executionQuality(user, snapshot),
                benchmark(user, snapshot, historyDays, portfolioReturn, now)
        );
    }


    // =========================================
    // TRADER STATISTICS
    // =========================================

    TraderStats stats(
            List<RoundTrip> trips
    ) {

        List<RoundTrip> wins = trips.stream().filter(RoundTrip::isWin).toList();

        List<RoundTrip> losses =
                trips.stream()
                        .filter(trip -> trip.realizedPnl().signum() < 0)
                        .toList();

        BigDecimal grossWin = sum(wins);

        BigDecimal grossLoss = sum(losses).abs();

        BigDecimal total = sum(trips);

        Long averageHolding =
                trips.isEmpty()
                        ? null
                        : Math.round(
                        trips.stream()
                                .mapToLong(trip ->
                                        Duration.between(trip.openedAt(), trip.closedAt()).toMinutes()
                                )
                                .average()
                                .orElse(0)
                );

        return new TraderStats(
                trips.size(),
                wins.size(),
                losses.size(),
                trips.isEmpty()
                        ? null
                        : BigDecimal.valueOf(wins.size())
                        .multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(trips.size()), 1, RoundingMode.HALF_UP),
                average(grossWin, wins.size()),
                losses.isEmpty() ? null : average(grossLoss, losses.size()).negate(),
                grossLoss.signum() == 0
                        ? null
                        : grossWin.divide(grossLoss, 2, RoundingMode.HALF_UP),
                average(total, trips.size()),
                averageHolding,
                highlight(trips.stream().max(Comparator.comparing(RoundTrip::realizedPnl)).orElse(null)),
                highlight(trips.stream().min(Comparator.comparing(RoundTrip::realizedPnl)).orElse(null))
        );
    }


    private TradeHighlight highlight(RoundTrip trip) {

        if (trip == null) {
            return null;
        }

        return new TradeHighlight(
                trip.symbol(),
                money(trip.realizedPnl()),
                trip.returnPercent(),
                trip.closedAt()
        );
    }


    // =========================================
    // CONTRIBUTIONS
    // =========================================

    private List<Contribution> contributions(
            PortfolioService.Snapshot snapshot
    ) {

        List<Contribution> contributions = new ArrayList<>();

        for (Holding holding : snapshot.ledger().holdings()) {

            Asset asset =
                    assetRepository
                            .findBySymbolIgnoreCase(holding.symbol())
                            .orElse(null);

            BigDecimal unrealized = BigDecimal.ZERO;

            if (holding.isOpen()) {

                BigDecimal lastPrice =
                        asset == null
                                ? holding.averagePrice()
                                : marketDataService.lastPrice(asset).orElse(holding.averagePrice());

                unrealized =
                        lastPrice.subtract(holding.averagePrice())
                                .multiply(holding.quantity());
            }

            BigDecimal total = holding.realizedPnl().add(unrealized);

            contributions.add(
                    new Contribution(
                            holding.symbol(),
                            asset == null ? holding.symbol() : asset.getName(),
                            money(holding.realizedPnl()),
                            money(unrealized),
                            money(total),
                            percent(total, snapshot.initialCapital()),
                            holding.isOpen()
                    )
            );
        }

        contributions.sort(Comparator.comparing(Contribution::totalPnl).reversed());

        return contributions;
    }


    // =========================================
    // EXECUTION QUALITY
    // =========================================

    private ExecutionQuality executionQuality(
            User user,
            PortfolioService.Snapshot snapshot
    ) {

        Set<Long> countedTrades = new HashSet<>();

        snapshot.fills().forEach(fill -> countedTrades.add(fill.tradeId()));

        int takerFills = 0;
        int makerFills = 0;

        BigDecimal weightedBps = BigDecimal.ZERO;
        BigDecimal takerNotional = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (var trade : tradeRepository.findAllForUser(user.getId())) {

            if (!countedTrades.contains(trade.getId())) {
                continue;
            }

            TradeResponse fill = TradeResponse.from(trade, user.getId());

            if ("MAKER".equals(fill.liquidity())) {
                makerFills++;
            }

            if (fill.slippageAmount() != null) {
                totalCost = totalCost.add(fill.slippageAmount());
            }

            if (!"TAKER".equals(fill.liquidity())) {
                continue;
            }

            takerFills++;

            if (fill.slippageBps() != null) {

                BigDecimal notional = fill.executionPrice().multiply(fill.quantity());

                weightedBps = weightedBps.add(fill.slippageBps().multiply(notional));

                takerNotional = takerNotional.add(notional);
            }
        }

        return new ExecutionQuality(
                takerFills,
                makerFills,
                takerNotional.signum() == 0
                        ? null
                        : weightedBps.divide(takerNotional, 1, RoundingMode.HALF_UP),
                money(totalCost)
        );
    }


    // =========================================
    // BENCHMARK
    // =========================================

    /*
     * « Acheter et ne rien toucher » : au début du portefeuille, le
     * capital est réparti à parts égales sur tous les actifs actifs,
     * puis conservé sans aucune transaction.
     */
    private Benchmark benchmark(
            User user,
            PortfolioService.Snapshot snapshot,
            int historyDays,
            BigDecimal portfolioReturn,
            Instant now
    ) {

        Instant start =
                snapshot.startedAt() != null
                        ? snapshot.startedAt()
                        : user.getCreatedAt() != null
                        ? user.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()
                        : now;

        List<Asset> universe =
                assetRepository.findAllByActiveTrueOrderBySymbolAsc()
                        .stream()
                        .filter(asset -> asset.getReferencePrice() != null)
                        .toList();

        BigDecimal capital = snapshot.initialCapital();

        HistoricalPrices.Lookup prices = historicalPrices.lookup();

        List<BigDecimal> units = new ArrayList<>();

        if (!universe.isEmpty()) {

            BigDecimal perAsset =
                    capital.divide(BigDecimal.valueOf(universe.size()), 8, RoundingMode.HALF_UP);

            for (Asset asset : universe) {

                BigDecimal startPrice =
                        prices.priceAt(asset.getSymbol(), start, asset.getReferencePrice());

                units.add(perAsset.divide(startPrice, 8, RoundingMode.HALF_UP));
            }
        }

        List<EquityPoint> history = new ArrayList<>();

        for (Instant instant : PortfolioService.historyInstants(historyDays, now)) {

            history.add(
                    new EquityPoint(
                            instant,
                            money(
                                    instant.isAfter(start)
                                            ? valueAt(universe, units, instant, prices, capital)
                                            : capital
                            )
                    )
            );
        }

        // Valeur actuelle : derniers cours connus, sans filtre de date
        // (une transaction à l'instant même doit compter).
        BigDecimal currentValue = capital;

        if (!universe.isEmpty()) {

            currentValue = BigDecimal.ZERO;

            for (int index = 0; index < universe.size(); index++) {

                Asset asset = universe.get(index);

                currentValue =
                        currentValue.add(
                                units.get(index).multiply(
                                        marketDataService.lastPrice(asset).orElse(asset.getReferencePrice())
                                )
                        );
            }

            // Le dernier point de l'historique est la valeur actuelle.
            history.set(
                    history.size() - 1,
                    new EquityPoint(history.get(history.size() - 1).time(), money(currentValue))
            );
        }

        BigDecimal benchmarkReturn =
                percent(currentValue.subtract(capital), capital);

        return new Benchmark(
                "Acheter et conserver",
                "Capital réparti à parts égales sur les " + universe.size()
                        + " actifs au début du portefeuille, sans aucune transaction ensuite.",
                start,
                universe.size(),
                money(currentValue),
                benchmarkReturn,
                portfolioReturn,
                portfolioReturn.subtract(benchmarkReturn),
                history
        );
    }


    private BigDecimal valueAt(
            List<Asset> universe,
            List<BigDecimal> units,
            Instant instant,
            HistoricalPrices.Lookup prices,
            BigDecimal capital
    ) {

        if (universe.isEmpty()) {
            return capital;
        }

        BigDecimal value = BigDecimal.ZERO;

        for (int index = 0; index < universe.size(); index++) {

            Asset asset = universe.get(index);

            value =
                    value.add(
                            units.get(index).multiply(
                                    prices.priceAt(asset.getSymbol(), instant, asset.getReferencePrice())
                            )
                    );
        }

        return value;
    }


    // =========================================
    // HELPERS
    // =========================================

    private static BigDecimal sum(List<RoundTrip> trips) {

        return trips.stream()
                .map(RoundTrip::realizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }


    private static BigDecimal average(BigDecimal total, int count) {

        if (count == 0) {
            return null;
        }

        return total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }


    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }


    private static BigDecimal percent(BigDecimal value, BigDecimal base) {

        if (base.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }

        return value.multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP);
    }
}
