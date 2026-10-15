package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Service;

import tn.esprit.simulateurbackend.dto.DashboardResponse.Insight;
import tn.esprit.simulateurbackend.dto.DashboardResponse.Protection;
import tn.esprit.simulateurbackend.dto.PortfolioResponse;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.TimeInForce;
import tn.esprit.simulateurbackend.entity.TriggerType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static tn.esprit.simulateurbackend.service.FrenchFormat.percent;
import static tn.esprit.simulateurbackend.service.FrenchFormat.price;

/*
 * « Ce qui demande votre attention » : des règles simples et
 * explicables, appliquées aux données de l'utilisateur.
 * Chaque point dit ce qui se passe et ce qu'il peut faire.
 */
@Service
public class DashboardInsightService {

    static final String CRITICAL = "CRITICAL";
    static final String WARNING = "WARNING";
    static final String INFO = "INFO";

    private static final Map<String, Integer> SEVERITY_ORDER =
            Map.of(CRITICAL, 0, WARNING, 1, INFO, 2);

    private static final int MAX_INSIGHTS = 5;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    // ---- Seuils ----

    private static final BigDecimal STOP_NEAR_PERCENT = BigDecimal.valueOf(2);

    private static final BigDecimal CONCENTRATION_WARNING = BigDecimal.valueOf(40);

    private static final BigDecimal CONCENTRATION_CRITICAL = BigDecimal.valueOf(60);

    private static final BigDecimal UNPROTECTED_WEIGHT = BigDecimal.valueOf(10);

    private static final BigDecimal LOSING_POSITION_PERCENT = BigDecimal.valueOf(-10);

    private static final BigDecimal FAR_ORDER_PERCENT = BigDecimal.valueOf(5);

    private static final Duration STALE_ORDER_AGE = Duration.ofHours(24);

    private static final BigDecimal LOW_CASH_PERCENT = BigDecimal.valueOf(5);

    private static final BigDecimal DRAWDOWN_WARNING = BigDecimal.valueOf(5);

    private final MarketDataService marketDataService;

    public DashboardInsightService(
            MarketDataService marketDataService
    ) {
        this.marketDataService = marketDataService;
    }


    public List<Insight> evaluate(
            PortfolioResponse portfolio,
            List<Order> openOrders,
            List<Protection> protections,
            Instant now
    ) {

        List<Insight> insights = new ArrayList<>();

        negativeCash(portfolio, insights);
        concentration(portfolio, insights);
        stopsNearPrice(openOrders, insights);
        losingPositions(portfolio, insights);
        unprotectedPositions(protections, insights);
        drawdown(portfolio, insights);
        dayOrdersExpiring(openOrders, insights);
        staleOrders(openOrders, now, insights);
        lowCash(portfolio, insights);

        return insights.stream()
                .sorted(Comparator.comparing(insight -> SEVERITY_ORDER.get(insight.severity())))
                .limit(MAX_INSIGHTS)
                .toList();
    }


    // =========================================
    // RULES
    // =========================================

    private void negativeCash(
            PortfolioResponse portfolio,
            List<Insight> insights
    ) {

        if (portfolio.cash().signum() >= 0) {
            return;
        }

        insights.add(
                new Insight(
                        CRITICAL,
                        "CASH",
                        "Liquidités négatives",
                        "Des transactions passées avant le contrôle des fonds ont rendu vos liquidités "
                                + "négatives : vous ne pouvez plus acheter. Réinitialisez votre "
                                + "portefeuille pour repartir du capital de départ.",
                        null,
                        null
                )
        );
    }


    private void concentration(
            PortfolioResponse portfolio,
            List<Insight> insights
    ) {

        String symbol = portfolio.risk().largestPositionSymbol();

        BigDecimal weight = portfolio.risk().largestPositionPercent();

        if (symbol == null || weight.compareTo(CONCENTRATION_WARNING) < 0) {
            return;
        }

        insights.add(
                new Insight(
                        weight.compareTo(CONCENTRATION_CRITICAL) >= 0 ? CRITICAL : WARNING,
                        "CONCENTRATION",
                        percent(weight) + " du portefeuille sur " + symbol,
                        "Si " + symbol + " baisse de 10 %, votre portefeuille perd environ "
                                + percent(weight.divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP))
                                + ". Répartir sur plusieurs actifs réduit ce risque "
                                + "(l'assistant de rééquilibrage peut vous aider).",
                        symbol,
                        null
                )
        );
    }


    private void stopsNearPrice(
            List<Order> openOrders,
            List<Insight> insights
    ) {

        for (Order order : openOrders) {

            if (order.getTriggerType() != TriggerType.STOP_LOSS || !order.isWaitingTrigger()) {
                continue;
            }

            BigDecimal last =
                    marketDataService.lastPrice(order.getAssetSymbol()).orElse(null);

            if (last == null || last.signum() == 0) {
                continue;
            }

            BigDecimal distance =
                    last.subtract(order.getTriggerPrice()).abs()
                            .multiply(HUNDRED)
                            .divide(last, 2, RoundingMode.HALF_UP);

            if (distance.compareTo(STOP_NEAR_PERCENT) > 0) {
                continue;
            }

            insights.add(
                    new Insight(
                            WARNING,
                            "STOP_NEAR",
                            "Stop sur " + order.getAssetSymbol() + " à " + percent(distance) + " du cours",
                            "Votre stop à " + price(order.getTriggerPrice()) + " est proche du dernier cours ("
                                    + price(last) + ") : une petite variation suffira à déclencher la vente.",
                            order.getAssetSymbol(),
                            order.getId()
                    )
            );
        }
    }


    private void losingPositions(
            PortfolioResponse portfolio,
            List<Insight> insights
    ) {

        for (PortfolioResponse.Position position : portfolio.openPositions()) {

            if (position.unrealizedPnlPercent().compareTo(LOSING_POSITION_PERCENT) > 0) {
                continue;
            }

            insights.add(
                    new Insight(
                            WARNING,
                            "LOSING_POSITION",
                            position.assetSymbol() + " perd " + percent(position.unrealizedPnlPercent().abs())
                                    + " depuis l'achat",
                            "Prix de revient " + price(position.averagePrice()) + ", cours "
                                    + price(position.lastPrice()) + ". Décidez si vous conservez la position "
                                    + "et, si oui, jusqu'où vous acceptez la baisse (stop-loss).",
                            position.assetSymbol(),
                            null
                    )
            );
        }
    }


    private void unprotectedPositions(
            List<Protection> protections,
            List<Insight> insights
    ) {

        for (Protection protection : protections) {

            if (
                    !"UNPROTECTED".equals(protection.status())
                            || protection.weightPercent().compareTo(UNPROTECTED_WEIGHT) < 0
            ) {
                continue;
            }

            insights.add(
                    new Insight(
                            WARNING,
                            "UNPROTECTED",
                            protection.assetSymbol() + " (" + percent(protection.weightPercent())
                                    + " du portefeuille) sans stop-loss",
                            "Aucune vente automatique ne limite la perte si le cours chute. "
                                    + "Un ordre OCO ou un stop suiveur protège la position.",
                            protection.assetSymbol(),
                            null
                    )
            );
        }
    }


    private void drawdown(
            PortfolioResponse portfolio,
            List<Insight> insights
    ) {

        BigDecimal drawdown = portfolio.risk().maxDrawdownPercent();

        if (drawdown.compareTo(DRAWDOWN_WARNING) < 0) {
            return;
        }

        insights.add(
                new Insight(
                        WARNING,
                        "DRAWDOWN",
                        "Baisse de " + percent(drawdown) + " depuis un sommet",
                        "Sur les " + portfolio.historyDays() + " derniers jours, la valeur de votre "
                                + "portefeuille a reculé de " + percent(drawdown)
                                + " depuis son plus haut. C'est le moment de revoir vos positions.",
                        null,
                        null
                )
        );
    }


    private void dayOrdersExpiring(
            List<Order> openOrders,
            List<Insight> insights
    ) {

        List<Order> dayOrders =
                openOrders.stream()
                        .filter(order -> order.effectiveTimeInForce() == TimeInForce.DAY)
                        .toList();

        if (dayOrders.isEmpty()) {
            return;
        }

        insights.add(
                new Insight(
                        INFO,
                        "EXPIRING",
                        dayOrders.size() == 1
                                ? "Un ordre « journée » expire à minuit"
                                : dayOrders.size() + " ordres « journée » expirent à minuit",
                        "S'ils ne sont pas exécutés d'ici là, ils seront annulés automatiquement. "
                                + "Choisissez « jusqu'à annulation » pour qu'un ordre reste en place.",
                        dayOrders.get(0).getAssetSymbol(),
                        dayOrders.get(0).getId()
                )
        );
    }


    private void staleOrders(
            List<Order> openOrders,
            Instant now,
            List<Insight> insights
    ) {

        for (Order order : openOrders) {

            if (
                    order.getType() != OrderType.LIMIT
                            || order.getOcoGroup() != null
                            || Duration.between(order.getCreatedAt(), now).compareTo(STALE_ORDER_AGE) < 0
            ) {
                continue;
            }

            BigDecimal last =
                    marketDataService.lastPrice(order.getAssetSymbol()).orElse(null);

            if (last == null || last.signum() == 0) {
                continue;
            }

            BigDecimal distance =
                    order.getLimitPrice().subtract(last).abs()
                            .multiply(HUNDRED)
                            .divide(last, 1, RoundingMode.HALF_UP);

            if (distance.compareTo(FAR_ORDER_PERCENT) < 0) {
                continue;
            }

            long days = Duration.between(order.getCreatedAt(), now).toDays();

            insights.add(
                    new Insight(
                            INFO,
                            "STALE_ORDER",
                            "Ordre #" + order.getId() + " en attente depuis "
                                    + (days >= 1 ? days + " jour(s)" : "plus de 24 h"),
                            "Son prix (" + price(order.getLimitPrice()) + ") est à " + percent(distance)
                                    + " du marché : il a peu de chances d'être exécuté. "
                                    + "Modifiez-le ou annulez-le.",
                            order.getAssetSymbol(),
                            order.getId()
                    )
            );
        }
    }


    private void lowCash(
            PortfolioResponse portfolio,
            List<Insight> insights
    ) {

        if (
                portfolio.totalValue().signum() <= 0
                        || portfolio.cash().signum() < 0
        ) {
            return;
        }

        BigDecimal cashPercent =
                portfolio.availableCash().max(BigDecimal.ZERO)
                        .multiply(HUNDRED)
                        .divide(portfolio.totalValue(), 2, RoundingMode.HALF_UP);

        if (cashPercent.compareTo(LOW_CASH_PERCENT) >= 0) {
            return;
        }

        insights.add(
                new Insight(
                        INFO,
                        "LOW_CASH",
                        "Moins de " + percent(LOW_CASH_PERCENT) + " de liquidités disponibles",
                        "Il vous reste " + FrenchFormat.amount(portfolio.availableCash().max(BigDecimal.ZERO))
                                + " " + portfolio.currency() + " pour de nouveaux achats"
                                + (portfolio.reservedCash().signum() > 0
                                ? ", le reste étant réservé par vos ordres d'achat en attente."
                                : "."),
                        null,
                        null
                )
        );
    }
}
