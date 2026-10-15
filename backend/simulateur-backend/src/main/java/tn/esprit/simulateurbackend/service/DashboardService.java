package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.simulateurbackend.dto.DashboardResponse;
import tn.esprit.simulateurbackend.dto.DashboardResponse.Habits;
import tn.esprit.simulateurbackend.dto.DashboardResponse.Headline;
import tn.esprit.simulateurbackend.dto.DashboardResponse.Protection;
import tn.esprit.simulateurbackend.dto.DashboardResponse.WatchedAsset;
import tn.esprit.simulateurbackend.dto.MarketOverviewResponse;
import tn.esprit.simulateurbackend.dto.PerformanceResponse;
import tn.esprit.simulateurbackend.dto.PortfolioResponse;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.TradeRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static tn.esprit.simulateurbackend.service.FrenchFormat.amount;
import static tn.esprit.simulateurbackend.service.FrenchFormat.signedAmount;
import static tn.esprit.simulateurbackend.service.FrenchFormat.signedPercent;

/*
 * Tableau de bord : assemble le portefeuille, la performance et
 * les éléments propres au tableau de bord (phrase du jour, points
 * d'attention, protections, actifs suivis, habitudes, activité).
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private static final int HISTORY_DAYS = 7;

    private static final int MAX_WATCHED_ASSETS = 8;

    private static final int SPARKLINE_POINTS = 24;

    // En dessous, la variation du jour est présentée comme stable.
    private static final BigDecimal FLAT_DAY_PERCENT = new BigDecimal("0.01");

    private final PortfolioService portfolioService;
    private final PortfolioAnalyticsService analyticsService;
    private final MarketDataService marketDataService;
    private final HistoricalPrices historicalPrices;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final ProtectionCoverageService protectionService;
    private final DashboardInsightService insightService;
    private final ActivityFeedService activityFeedService;

    public DashboardService(
            PortfolioService portfolioService,
            PortfolioAnalyticsService analyticsService,
            MarketDataService marketDataService,
            HistoricalPrices historicalPrices,
            OrderRepository orderRepository,
            TradeRepository tradeRepository,
            ProtectionCoverageService protectionService,
            DashboardInsightService insightService,
            ActivityFeedService activityFeedService
    ) {
        this.portfolioService = portfolioService;
        this.analyticsService = analyticsService;
        this.marketDataService = marketDataService;
        this.historicalPrices = historicalPrices;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.protectionService = protectionService;
        this.insightService = insightService;
        this.activityFeedService = activityFeedService;
    }


    public DashboardResponse getDashboard(
            String authenticatedEmail
    ) {

        User user = portfolioService.findUser(authenticatedEmail);

        Instant now = Instant.now();

        PortfolioResponse portfolio =
                portfolioService.getPortfolio(authenticatedEmail, HISTORY_DAYS);

        PerformanceResponse performance =
                analyticsService.getPerformance(authenticatedEmail, HISTORY_DAYS);

        List<Order> orders =
                orderRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        List<Order> openOrders =
                orders.stream().filter(Order::isOpen).toList();

        List<Protection> protections =
                protectionService.protections(portfolio, openOrders);

        return new DashboardResponse(
                portfolio.currency(),
                headline(user, portfolio, performance),
                portfolio,
                performance,
                insightService.evaluate(portfolio, openOrders, protections, now),
                watchlist(portfolio, openOrders),
                protections,
                habits(orders, performance),
                activityFeedService.recent(
                        user.getId(),
                        orders,
                        tradeRepository.findAllForUser(user.getId())
                ),
                now
        );
    }


    // =========================================
    // HEADLINE
    // =========================================

    /*
     * Une ou deux phrases, écrites à partir des chiffres :
     * variation du jour, actif qui l'explique, comparaison
     * à la référence.
     */
    Headline headline(
            User user,
            PortfolioResponse portfolio,
            PerformanceResponse performance
    ) {

        String currency = portfolio.currency();

        if (portfolio.tradeCount() == 0) {

            return new Headline(
                    "Bienvenue : vos " + amount(portfolio.cash()) + " " + currency
                            + " sont en liquidités. Passez un premier ordre dans Trading "
                            + "pour commencer à investir.",
                    ActivityFeedService.NEUTRAL,
                    BigDecimal.ZERO.setScale(2),
                    BigDecimal.ZERO.setScale(2)
            );
        }

        // ---- Variation depuis minuit ----

        Instant startOfDay = LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();

        HistoricalPrices.Lookup prices = historicalPrices.lookup();

        BigDecimal valueAtStart =
                portfolioService.valueAt(portfolioService.snapshot(user), startOfDay, prices);

        BigDecimal dayChange =
                portfolio.totalValue().subtract(valueAtStart).setScale(2, RoundingMode.HALF_UP);

        BigDecimal dayChangePercent =
                valueAtStart.signum() == 0
                        ? BigDecimal.ZERO.setScale(2)
                        : dayChange.multiply(HUNDRED).divide(valueAtStart, 2, RoundingMode.HALF_UP);

        StringBuilder text = new StringBuilder();

        if (portfolio.openPositions().isEmpty()) {

            text.append("Vous êtes entièrement en liquidités : votre résultat depuis le départ est de ")
                    .append(signedAmount(portfolio.totalPnl())).append(' ').append(currency)
                    .append(" (").append(signedPercent(portfolio.totalPnlPercent())).append(").");

        } else if (dayChangePercent.abs().compareTo(FLAT_DAY_PERCENT) < 0) {

            text.append("Votre portefeuille est stable aujourd'hui.");

        } else {

            text.append("Votre portefeuille ")
                    .append(dayChange.signum() > 0 ? "gagne " : "perd ")
                    .append(FrenchFormat.percent(dayChangePercent.abs()))
                    .append(" aujourd'hui (")
                    .append(signedAmount(dayChange)).append(' ').append(currency).append(')');

            appendDriver(text, portfolio, startOfDay, prices, dayChange);

            text.append('.');
        }

        // ---- Référence ----

        BigDecimal gap = performance.benchmark().outperformancePercent();

        if (gap.abs().compareTo(new BigDecimal("0.05")) >= 0) {

            text.append(
                    gap.signum() > 0
                            ? " Vous faites mieux que la référence « Acheter et conserver » de "
                            : " La référence « Acheter et conserver » fait mieux que vous de "
            ).append(FrenchFormat.quantity(gap.abs().setScale(2, RoundingMode.HALF_UP)))
                    .append(" point(s).");
        }

        String tone =
                dayChange.signum() > 0
                        ? ActivityFeedService.POSITIVE
                        : dayChange.signum() < 0 ? ActivityFeedService.NEGATIVE : ActivityFeedService.NEUTRAL;

        return new Headline(text.toString(), tone, dayChange, dayChangePercent);
    }


    /*
     * L'actif qui a le plus contribué à la variation du jour,
     * dans le sens de cette variation.
     */
    private void appendDriver(
            StringBuilder text,
            PortfolioResponse portfolio,
            Instant startOfDay,
            HistoricalPrices.Lookup prices,
            BigDecimal dayChange
    ) {

        String driver = null;
        BigDecimal driverMove = BigDecimal.ZERO;

        for (PortfolioResponse.Position position : portfolio.openPositions()) {

            // Position ouverte aujourd'hui : le gain du jour part du prix d'achat.
            boolean openedToday =
                    position.openedAt() != null && position.openedAt().isAfter(startOfDay);

            BigDecimal priceAtStart =
                    openedToday
                            ? position.averagePrice()
                            : prices.priceAt(position.assetSymbol(), startOfDay, position.averagePrice());

            BigDecimal move =
                    position.lastPrice().subtract(priceAtStart).multiply(position.quantity());

            boolean sameDirection = move.signum() == dayChange.signum();

            if (sameDirection && move.abs().compareTo(driverMove.abs()) > 0) {
                driver = position.assetSymbol();
                driverMove = move;
            }
        }

        if (driver != null) {

            text.append(dayChange.signum() > 0 ? ", porté par " : ", pénalisé par ")
                    .append(driver)
                    .append(" (").append(signedAmount(driverMove)).append(')');
        }
    }


    // =========================================
    // WATCHLIST
    // =========================================

    /*
     * Actifs détenus, puis actifs ayant des ordres en attente.
     */
    private List<WatchedAsset> watchlist(
            PortfolioResponse portfolio,
            List<Order> openOrders
    ) {

        Map<String, PortfolioResponse.Position> symbols = new LinkedHashMap<>();

        portfolio.openPositions().forEach(position -> symbols.put(position.assetSymbol(), position));

        openOrders.forEach(order -> symbols.putIfAbsent(order.getAssetSymbol(), null));

        List<WatchedAsset> watched = new ArrayList<>();

        for (Map.Entry<String, PortfolioResponse.Position> entry : symbols.entrySet()) {

            if (watched.size() >= MAX_WATCHED_ASSETS) {
                break;
            }

            MarketOverviewResponse market;

            try {
                market = marketDataService.getOverview(entry.getKey(), "15m");
            } catch (RuntimeException exception) {
                // Actif désactivé entre-temps : on ne l'affiche pas.
                continue;
            }

            List<BigDecimal> closes =
                    market.candles().stream()
                            .map(candle -> candle.close())
                            .toList();

            List<BigDecimal> sparkline =
                    closes.subList(Math.max(0, closes.size() - SPARKLINE_POINTS), closes.size());

            PortfolioResponse.Position position = entry.getValue();

            watched.add(
                    new WatchedAsset(
                            entry.getKey(),
                            position != null ? position.assetName() : entry.getKey(),
                            market.summary().lastPrice(),
                            market.summary().changePercent(),
                            sparkline,
                            position != null ? position.marketValue() : null,
                            position != null ? position.unrealizedPnl() : null
                    )
            );
        }

        return watched;
    }


    // =========================================
    // HABITS
    // =========================================

    Habits habits(
            List<Order> orders,
            PerformanceResponse performance
    ) {

        int limit = 0;
        int market = 0;
        int conditional = 0;
        int buyEntries = 0;
        int protectedEntries = 0;

        Set<String> countedGroups = new HashSet<>();

        for (Order order : orders) {

            // Les protections créées automatiquement ne sont pas des choix.
            if (order.getParentOrderId() != null) {
                continue;
            }

            if (order.getOcoGroup() != null) {

                if (countedGroups.add(order.getOcoGroup())) {
                    conditional++;
                }

                continue;
            }

            if (order.getTriggerType() != null) {
                conditional++;
                continue;
            }

            if (order.getType() == OrderType.MARKET) {
                market++;
            } else {
                limit++;
            }

            if (order.getSide() == OrderSide.BUY) {

                buyEntries++;

                if (order.hasBracket()) {
                    protectedEntries++;
                }
            }
        }

        int placed = limit + market + conditional;

        PerformanceResponse.TraderStats stats = performance.stats();

        BigDecimal slippage = performance.execution().averageTakerSlippageBps();

        BigDecimal marketPercent = share(market, placed);

        return new Habits(
                placed,
                share(limit, placed),
                marketPercent,
                share(conditional, placed),
                share(protectedEntries, buyEntries),
                stats.closedTrades(),
                stats.winRatePercent(),
                stats.profitFactor(),
                slippage,
                advice(placed, marketPercent, slippage, stats, buyEntries, protectedEntries, conditional)
        );
    }


    private List<String> advice(
            int placed,
            BigDecimal marketPercent,
            BigDecimal slippage,
            PerformanceResponse.TraderStats stats,
            int buyEntries,
            int protectedEntries,
            int conditional
    ) {

        List<String> advice = new ArrayList<>();

        if (placed == 0) {
            advice.add("Passez vos premiers ordres : vos habitudes de trading apparaîtront ici.");
            return advice;
        }

        if (
                marketPercent != null
                        && marketPercent.compareTo(BigDecimal.valueOf(60)) >= 0
                        && slippage != null
                        && slippage.compareTo(BigDecimal.valueOf(5)) >= 0
        ) {

            advice.add(
                    "Vos ordres au marché vous coûtent " + FrenchFormat.quantity(slippage)
                            + " points de base en moyenne par rapport au prix milieu : un ordre limite "
                            + "au meilleur prix affiché évite une partie de ce coût."
            );
        }

        if (buyEntries >= 3 && protectedEntries == 0 && conditional == 0) {

            advice.add(
                    "Aucun de vos achats n'est protégé : cochez « Protéger cet achat » pour fixer "
                            + "à l'avance votre perte maximale et votre objectif de gain."
            );
        }

        if (stats.closedTrades() >= 5 && stats.winRatePercent() != null
                && stats.winRatePercent().compareTo(BigDecimal.valueOf(40)) < 0) {

            advice.add(
                    "Moins de 40 % de vos positions sont gagnantes : définissez votre objectif "
                            + "de gain et votre seuil de perte avant d'entrer sur une position."
            );
        }

        if (
                stats.averageWin() != null
                        && stats.averageLoss() != null
                        && stats.averageLoss().abs().compareTo(stats.averageWin()) > 0
        ) {

            advice.add(
                    "Votre perte moyenne (" + FrenchFormat.amount(stats.averageLoss().abs())
                            + ") dépasse votre gain moyen (" + FrenchFormat.amount(stats.averageWin())
                            + ") : couper les pertes plus tôt améliorerait votre résultat."
            );
        }

        if (stats.profitFactor() != null && stats.profitFactor().compareTo(new BigDecimal("1.5")) >= 0) {

            advice.add(
                    "Vos gains représentent " + FrenchFormat.quantity(stats.profitFactor())
                            + " fois vos pertes : votre méthode fonctionne, gardez la même discipline."
            );
        }

        if (stats.closedTrades() == 0) {

            advice.add(
                    "Fermez une première position (achat puis revente) pour voir votre taux "
                            + "de réussite et votre facteur de profit."
            );
        }

        return advice;
    }


    private static BigDecimal share(int part, int total) {

        if (total == 0) {
            return null;
        }

        return BigDecimal.valueOf(part)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
