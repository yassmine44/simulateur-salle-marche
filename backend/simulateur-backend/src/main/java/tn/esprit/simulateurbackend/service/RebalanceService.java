package tn.esprit.simulateurbackend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.CreateOrderRequest;
import tn.esprit.simulateurbackend.dto.OrderResponse;
import tn.esprit.simulateurbackend.dto.RebalanceExecutionResponse;
import tn.esprit.simulateurbackend.dto.RebalancePlanResponse;
import tn.esprit.simulateurbackend.dto.RebalancePlanResponse.Line;
import tn.esprit.simulateurbackend.dto.RebalanceRequest;
import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.User;
import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.repository.OrderRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/*
 * Assistant de rééquilibrage.
 *
 * 1. plan : calcule les ordres qui rapprochent le portefeuille de la
 *    répartition cible, sans rien envoyer.
 * 2. execute : recalcule le plan sur l'état du moment et envoie les
 *    ordres au marché, ventes d'abord (elles financent les achats).
 *
 * Chaque ordre passe par les règles et le contrôle habituels.
 */
@Service
public class RebalanceService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    // En dessous de cet écart (en % de la valeur totale), pas d'ordre.
    private static final BigDecimal MIN_DRIFT_PERCENT = new BigDecimal("0.5");

    private static final List<OrderStatus> OPEN_STATUSES =
            List.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED);

    private final PortfolioService portfolioService;
    private final OrderService orderService;
    private final AssetRepository assetRepository;
    private final OrderRepository orderRepository;
    private final MarketDataService marketDataService;
    private final OrderBookLocks orderBookLocks;

    public RebalanceService(
            PortfolioService portfolioService,
            OrderService orderService,
            AssetRepository assetRepository,
            OrderRepository orderRepository,
            MarketDataService marketDataService,
            OrderBookLocks orderBookLocks
    ) {
        this.portfolioService = portfolioService;
        this.orderService = orderService;
        this.assetRepository = assetRepository;
        this.orderRepository = orderRepository;
        this.marketDataService = marketDataService;
        this.orderBookLocks = orderBookLocks;
    }


    // =========================================
    // PLAN
    // =========================================

    @Transactional(readOnly = true)
    public RebalancePlanResponse plan(
            String authenticatedEmail,
            RebalanceRequest request
    ) {

        User user = portfolioService.findUser(authenticatedEmail);

        PortfolioService.Snapshot snapshot = portfolioService.snapshot(user);

        List<Asset> assets = validateTargets(request);

        BigDecimal totalValue = totalValue(snapshot);

        if (totalValue.signum() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La valeur du portefeuille est nulle ou négative : réinitialisez-le "
                            + "avant de le rééquilibrer."
            );
        }

        List<String> warnings = new ArrayList<>();

        List<Line> lines = new ArrayList<>();

        BigDecimal sellProceeds = BigDecimal.ZERO;
        BigDecimal buyCost = BigDecimal.ZERO;

        BigDecimal targetTotalPercent = BigDecimal.ZERO;

        for (int index = 0; index < assets.size(); index++) {

            Asset asset = assets.get(index);

            BigDecimal targetPercent = request.targets().get(index).targetPercent();

            targetTotalPercent = targetTotalPercent.add(targetPercent);

            BigDecimal held = snapshot.ledger().quantity(asset.getSymbol());

            BigDecimal lastPrice =
                    marketDataService.lastPrice(asset).orElse(asset.getReferencePrice());

            BigDecimal currentValue = held.multiply(lastPrice);

            BigDecimal targetValue =
                    totalValue.multiply(targetPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);

            BigDecimal drift = targetValue.subtract(currentValue);

            BigDecimal driftPercent =
                    drift.abs().multiply(HUNDRED).divide(totalValue, 2, RoundingMode.HALF_UP);

            OrderSide side = drift.signum() > 0 ? OrderSide.BUY : OrderSide.SELL;

            BigDecimal price = executionPrice(asset, side, user, lastPrice);

            BigDecimal quantity = BigDecimal.ZERO;

            String note = null;

            if (driftPercent.compareTo(MIN_DRIFT_PERCENT) < 0) {

                note = "Déjà proche de la cible (écart inférieur à "
                        + MIN_DRIFT_PERCENT.stripTrailingZeros().toPlainString() + " %).";

            } else {

                quantity = roundDown(drift.abs().divide(price, 8, RoundingMode.DOWN), asset);

                if (side == OrderSide.SELL) {

                    BigDecimal available =
                            snapshot.availableQuantity(asset.getSymbol()).max(BigDecimal.ZERO);

                    if (quantity.compareTo(available) > 0) {

                        quantity = roundDown(available, asset);

                        note = "Limité aux titres disponibles (une partie est engagée "
                                + "dans des ordres de vente en attente).";
                    }
                }

                if (quantity.signum() == 0) {
                    note = "Écart inférieur à la quantité minimale de l'actif.";
                }
            }

            BigDecimal amount = quantity.multiply(price);

            if (quantity.signum() > 0) {

                if (side == OrderSide.SELL) {
                    sellProceeds = sellProceeds.add(amount);
                } else {
                    buyCost = buyCost.add(amount);
                }
            }

            lines.add(
                    new Line(
                            asset.getSymbol(),
                            asset.getName(),
                            plain(held),
                            money(currentValue),
                            percent(currentValue, totalValue),
                            targetPercent.setScale(2, RoundingMode.HALF_UP),
                            money(targetValue),
                            quantity.signum() > 0 ? side : null,
                            plain(quantity),
                            price,
                            money(amount),
                            note
                    )
            );
        }

        if (targetTotalPercent.compareTo(HUNDRED) > 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La somme des parts cibles fait "
                            + targetTotalPercent.stripTrailingZeros().toPlainString()
                            + " % : elle ne peut pas dépasser 100 %."
            );
        }

        // Les achats ne peuvent pas dépasser les liquidités disponibles
        // plus le produit des ventes : on les réduit proportionnellement.
        BigDecimal budget = snapshot.availableCash().add(sellProceeds);

        if (buyCost.compareTo(budget) > 0 && buyCost.signum() > 0) {

            BigDecimal factor =
                    budget.max(BigDecimal.ZERO).divide(buyCost, 8, RoundingMode.DOWN);

            lines = scaleBuys(lines, factor, assets);

            warnings.add(
                    "Liquidités insuffisantes pour tous les achats : ils sont réduits à "
                            + factor.multiply(HUNDRED).setScale(0, RoundingMode.DOWN)
                            + " % de la cible."
            );

            buyCost =
                    lines.stream()
                            .filter(line -> line.side() == OrderSide.BUY)
                            .map(Line::estimatedAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        if (lines.stream().noneMatch(line -> line.side() != null)) {
            warnings.add("Le portefeuille est déjà conforme à la cible : aucun ordre n'est nécessaire.");
        }

        warnings.add(
                "Estimation sur le carnet actuel : les ordres au marché peuvent s'exécuter "
                        + "à des prix légèrement différents."
        );

        return new RebalancePlanResponse(
                portfolioService.currency(),
                money(totalValue),
                money(snapshot.availableCash()),
                HUNDRED.subtract(targetTotalPercent).setScale(2, RoundingMode.HALF_UP),
                money(snapshot.availableCash().add(sellProceeds).subtract(buyCost)),
                lines,
                warnings
        );
    }


    // =========================================
    // EXECUTE
    // =========================================

    public RebalanceExecutionResponse execute(
            String authenticatedEmail,
            RebalanceRequest request
    ) {

        RebalancePlanResponse plan = plan(authenticatedEmail, request);

        List<Line> orders = new ArrayList<>();

        // Les ventes d'abord : elles libèrent les liquidités des achats.
        plan.lines().stream().filter(line -> line.side() == OrderSide.SELL).forEach(orders::add);
        plan.lines().stream().filter(line -> line.side() == OrderSide.BUY).forEach(orders::add);

        List<RebalanceExecutionResponse.Result> results = new ArrayList<>();

        int filled = 0;

        for (Line line : orders) {

            try {

                OrderResponse order =
                        orderBookLocks.withLock(
                                line.assetSymbol(),
                                () ->
                                        orderService.createOrder(
                                                authenticatedEmail,
                                                new CreateOrderRequest(
                                                        line.assetSymbol(),
                                                        line.side(),
                                                        OrderType.MARKET,
                                                        line.quantity(),
                                                        null
                                                )
                                        )
                        );

                BigDecimal executed = order.quantity().subtract(order.remainingQuantity());

                if (executed.signum() > 0) {
                    filled++;
                }

                results.add(
                        new RebalanceExecutionResponse.Result(
                                line.assetSymbol(),
                                line.side(),
                                line.quantity(),
                                plain(executed),
                                order.id(),
                                order.status(),
                                order.remainingQuantity().signum() > 0
                                        ? "Contrepartie insuffisante : " + plain(order.remainingQuantity())
                                        + " non exécutés."
                                        : null
                        )
                );

            } catch (ResponseStatusException exception) {

                results.add(
                        new RebalanceExecutionResponse.Result(
                                line.assetSymbol(),
                                line.side(),
                                line.quantity(),
                                BigDecimal.ZERO,
                                null,
                                null,
                                exception.getReason()
                        )
                );
            }
        }

        return new RebalanceExecutionResponse(orders.size(), filled, results);
    }


    // =========================================
    // HELPERS
    // =========================================

    private List<Asset> validateTargets(
            RebalanceRequest request
    ) {

        Set<String> seen = new HashSet<>();

        List<Asset> assets = new ArrayList<>();

        for (RebalanceRequest.Target target : request.targets()) {

            String symbol = target.assetSymbol().trim().toUpperCase(Locale.ROOT);

            if (!seen.add(symbol)) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "L'actif " + symbol + " apparaît plusieurs fois dans la cible."
                );
            }

            assets.add(
                    assetRepository
                            .findBySymbolIgnoreCaseAndActiveTrue(symbol)
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.BAD_REQUEST,
                                            "Actif inconnu ou désactivé : " + symbol
                                    )
                            )
            );
        }

        return assets;
    }


    /*
     * Prix estimé d'un ordre au marché : meilleure offre d'un autre
     * utilisateur du côté opposé, sinon dernier cours.
     */
    private BigDecimal executionPrice(
            Asset asset,
            OrderSide side,
            User user,
            BigDecimal lastPrice
    ) {

        List<Order> opposite =
                side == OrderSide.BUY
                        ? orderRepository.findBookAsks(asset.getSymbol(), OPEN_STATUSES)
                        : orderRepository.findBookBids(asset.getSymbol(), OPEN_STATUSES);

        return opposite.stream()
                .filter(order -> !order.getUser().getId().equals(user.getId()))
                .map(Order::getLimitPrice)
                .findFirst()
                .orElse(lastPrice);
    }


    private List<Line> scaleBuys(
            List<Line> lines,
            BigDecimal factor,
            List<Asset> assets
    ) {

        List<Line> scaled = new ArrayList<>();

        for (int index = 0; index < lines.size(); index++) {

            Line line = lines.get(index);

            if (line.side() != OrderSide.BUY) {
                scaled.add(line);
                continue;
            }

            BigDecimal quantity = roundDown(line.quantity().multiply(factor), assets.get(index));

            scaled.add(
                    new Line(
                            line.assetSymbol(), line.assetName(), line.currentQuantity(),
                            line.currentValue(), line.currentPercent(), line.targetPercent(),
                            line.targetValue(),
                            quantity.signum() > 0 ? OrderSide.BUY : null,
                            plain(quantity),
                            line.estimatedPrice(),
                            money(quantity.multiply(line.estimatedPrice())),
                            "Réduit faute de liquidités suffisantes."
                    )
            );
        }

        return scaled;
    }


    private BigDecimal totalValue(
            PortfolioService.Snapshot snapshot
    ) {

        BigDecimal total = snapshot.ledger().cash();

        for (var holding : snapshot.ledger().holdings()) {

            if (holding.isOpen()) {

                BigDecimal price =
                        marketDataService.lastPrice(holding.symbol()).orElse(holding.averagePrice());

                total = total.add(holding.quantity().multiply(price));
            }
        }

        return total;
    }


    // Multiple inférieur de la quantité minimale de l'actif.
    private BigDecimal roundDown(
            BigDecimal quantity,
            Asset asset
    ) {

        BigDecimal step =
                asset.getMinQuantity() != null && asset.getMinQuantity().signum() > 0
                        ? asset.getMinQuantity()
                        : BigDecimal.ONE;

        return quantity.divide(step, 0, RoundingMode.DOWN).multiply(step);
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


    private static BigDecimal plain(BigDecimal value) {

        BigDecimal stripped = value.stripTrailingZeros();

        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
