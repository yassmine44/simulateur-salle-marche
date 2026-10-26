package tn.esprit.simulateurbackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.ScenarioType;
import tn.esprit.simulateurbackend.dto.ScenarioStatusResponse;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.entity.OrderType;
import tn.esprit.simulateurbackend.entity.Role;
import tn.esprit.simulateurbackend.entity.Trade;
import tn.esprit.simulateurbackend.entity.User;

import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.TradeRepository;
import tn.esprit.simulateurbackend.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Teneur de marché automatique.
 *
 * Il maintient en permanence des ordres d'achat et de vente
 * autour d'un prix qui évolue lentement près du prix de
 * référence de l'actif : un utilisateur seul trouve ainsi
 * toujours une contrepartie.
 *
 * Un second compte simule le flux du marché en envoyant de
 * petits ordres au marché, ce qui alimente le dernier cours,
 * le flux des transactions et les chandeliers.
 *
 * Les deux comptes sont des utilisateurs désactivés :
 * personne ne peut s'y connecter.
 */
@Service
public class MarketMakerService {

    public static final String MAKER_EMAIL =
            "market-maker@simulateur.local";

    public static final String FLOW_EMAIL =
            "market-flow@simulateur.local";

    private static final List<OrderStatus> OPEN_STATUSES =
            List.of(
                    OrderStatus.PENDING,
                    OrderStatus.PARTIALLY_FILLED
            );

    // Demi-écart BID/ASK et écart entre deux niveaux,
    // en proportion du prix.
    private static final BigDecimal HALF_SPREAD_RATIO =
            new BigDecimal("0.0005");

    private static final BigDecimal LEVEL_STEP_RATIO =
            new BigDecimal("0.0005");

    // Valeur approximative du premier niveau, en devise de cotation.
    private static final double BASE_NOTIONAL = 5_000;

    // Amplitude du mouvement aléatoire et force de rappel
    // vers le prix de référence, à chaque cycle.
    private static final double VOLATILITY = 0.0006;

    private static final double MEAN_REVERSION = 0.02;


    // Retrait maximal de liquidité pendant un CRASH.
    private static final double MAX_BID_WITHDRAWAL = 0.80;
    private static final double MAX_ASK_WITHDRAWAL = 0.30;
    /*
     * Impact de marché (loi en racine carrée).
     *
     * Le déplacement du prix vaut
     *   IMPACT_COEFFICIENT x racine(flux net / taille typique),
     * plafonné à MAX_IMPACT par cycle : quadrupler le volume
     * ne fait que doubler l'impact, comme observé sur les
     * marchés réels.
     */
    private static final double IMPACT_COEFFICIENT = 0.0015;

    private static final double MAX_IMPACT = 0.02;

    // Flux net (en tailles typiques) à partir duquel
    // l'écart BID/ASK est élargi au maximum (x3).
    private static final double FULL_STRESS_FLOW = 10;

    private static final double MAX_SPREAD_WIDENING = 2;

    // Le prix coté reste dans ±30 % du prix de référence.
    private static final BigDecimal MIN_MID_RATIO =
            new BigDecimal("0.7");

    private static final BigDecimal MAX_MID_RATIO =
            new BigDecimal("1.3");

    private final OrderRepository orderRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final MatchingEngineService matchingEngineService;
    private final MarketDataService marketDataService;
    private final TradeRepository tradeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ScenarioService scenarioService;

    private final Map<String, BigDecimal> midPrices =
            new ConcurrentHashMap<>();

    // Dernière transaction déjà prise en compte, par actif.
    private final Map<String, Long> lastSeenTradeIds =
            new ConcurrentHashMap<>();

    private final Random random = new Random();

    @Value("${app.market-maker.levels:5}")
    private int levels;

    /*
     * Probabilité, à chaque cycle et pour chaque actif,
     * qu'une transaction de marché soit simulée.
     */
    @Value("${app.market-maker.trade-probability:0.25}")
    private double tradeProbability;

    public MarketMakerService(
            OrderRepository orderRepository,
            AssetRepository assetRepository,
            UserRepository userRepository,
            MatchingEngineService matchingEngineService,
            MarketDataService marketDataService,
            TradeRepository tradeRepository,
            ApplicationEventPublisher eventPublisher,
            ScenarioService scenarioService
    ) {
        this.tradeRepository = tradeRepository;
        this.orderRepository = orderRepository;
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.matchingEngineService = matchingEngineService;
        this.marketDataService = marketDataService;
        this.eventPublisher = eventPublisher;
        this.scenarioService = scenarioService;
    }


    // =========================================
    // QUOTABLE ASSETS
    // =========================================

    /** Last calculated quote, or null before the first market-maker cycle. */
    public BigDecimal currentQuotedMidPrice(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return midPrices.get(symbol.trim().toUpperCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public List<String> quotableSymbols() {

        return assetRepository
                .findAllByActiveTrueOrderBySymbolAsc()
                .stream()
                .filter(this::isQuotable)
                .map(Asset::getSymbol)
                .toList();
    }


    private boolean isQuotable(Asset asset) {

        return isPositive(asset.getReferencePrice())
                && isPositive(asset.getTickSize())
                && isPositive(asset.getMinQuantity());
    }


    private boolean isPositive(BigDecimal value) {

        return value != null
                && value.signum() > 0;
    }


    // =========================================
    // ONE CYCLE FOR ONE ASSET
    // =========================================

    @Transactional
    public void quoteAsset(
            String symbol
    ) {

        Asset asset =
                assetRepository
                        .findBySymbolIgnoreCaseAndActiveTrue(symbol)
                        .orElse(null);

        if (
                asset == null
                        || !isQuotable(asset)
        ) {
            return;
        }

        User maker =
                systemUser(MAKER_EMAIL, "Maker");

        // Pression acheteuse (> 0) ou vendeuse (< 0) exercée
        // par les utilisateurs depuis le cycle précédent,
        // exprimée en nombre de tailles typiques.
        double flowPressure = flowPressure(asset);

        // One snapshot per quote cycle keeps price and spread consistent at expiry.


        ScenarioStatusResponse scenario = scenarioService.status();

// Le krach est-il actif sur CET actif ?
        boolean crashActive =
                scenario.active()
                        && scenario.type() == ScenarioType.CRASH
                        && asset.getSymbol().equalsIgnoreCase(
                        scenario.symbol()
                );

// Intensité du choc baissier :
// uniquement pendant CRASH.
        double crashIntensity = crashActive
                ? scenario.intensity().doubleValue()
                : 0.0;

// Intensité du stress de liquidité :
// CRASH actif ou récupération progressive.
        double liquidityIntensity = crashActive
                ? crashIntensity
                : scenarioService.recoveryIntensity(
                asset.getSymbol()
        );

// IMPORTANT : la récupération ne doit pas
// prolonger la dérive baissière du prix.
        BigDecimal mid = nextMidPrice(
                asset,
                flowPressure,
                crashIntensity
        );

        BigDecimal tick = asset.getTickSize();

        // Après un flux important, le teneur de marché se protège
        // en élargissant temporairement son écart BID/ASK.
        double stress =
                Math.min(
                        1,
                        Math.abs(flowPressure) / FULL_STRESS_FLOW
                );

        BigDecimal halfSpread =
                mid.multiply(HALF_SPREAD_RATIO)
                        .multiply(
                                BigDecimal.valueOf(
                                        (1 + MAX_SPREAD_WIDENING * stress) * (1 + 2 * liquidityIntensity)
                                )
                        )
                        .max(tick);

        BigDecimal step =
                mid.multiply(LEVEL_STEP_RATIO).max(tick);

        List<Order> bids =
                orderRepository.findOpenLimitOrdersOfUser(
                        maker.getId(),
                        asset.getSymbol(),
                        OrderSide.BUY,
                        OPEN_STATUSES
                );

        List<Order> asks =
                orderRepository.findOpenLimitOrdersOfUser(
                        maker.getId(),
                        asset.getSymbol(),
                        OrderSide.SELL,
                        OPEN_STATUSES
                );

        List<Order> quotes = new ArrayList<>();

        BigDecimal firstLevelQuantity = null;

        for (int level = 0; level < levels; level++) {

            BigDecimal offset =
                    halfSpread.add(
                            step.multiply(BigDecimal.valueOf(level))
                    );

            BigDecimal bidPrice =
                    roundToTick(
                            mid.subtract(offset),
                            tick,
                            RoundingMode.FLOOR
                    );

            BigDecimal askPrice =
                    roundToTick(
                            mid.add(offset),
                            tick,
                            RoundingMode.CEILING
                    );

            BigDecimal quantity =
                    levelQuantity(asset, mid, level);
            BigDecimal bidQuantity =
                    stressedQuoteQuantity(
                            asset,
                            quantity,
                            OrderSide.BUY,
                            liquidityIntensity
                    );

            BigDecimal askQuantity =
                    stressedQuoteQuantity(
                            asset,
                            quantity,
                            OrderSide.SELL,
                            liquidityIntensity
                    );

            if (firstLevelQuantity == null) {
                firstLevelQuantity = quantity;
            }

            if (bidPrice.signum() > 0) {

                quotes.add(
                        quote(
                                bids,
                                level,
                                maker,
                                asset.getSymbol(),
                                OrderSide.BUY,
                                bidPrice,
                                bidQuantity
                        )
                );
            }

            quotes.add(
                    quote(
                            asks,
                            level,
                            maker,
                            asset.getSymbol(),
                            OrderSide.SELL,
                            askPrice,
                            askQuantity
                    )
            );
        }

        cancelExtraQuotes(bids);
        cancelExtraQuotes(asks);

        orderRepository.saveAllAndFlush(quotes);

        // Les nouvelles cotations peuvent croiser les ordres
        // au repos des utilisateurs : elles les exécutent.
        for (Order quote : quotes) {
            matchingEngineService.matchOrder(quote.getId());
        }

        if (
                firstLevelQuantity != null
                        && random.nextDouble() < tradeProbability
        ) {
            simulateMarketFlow(asset, firstLevelQuantity);
        }

        matchingEngineService.triggerConditionalOrders(
                asset.getSymbol()
        );

        // Les transactions produites par ce cycle (recotation,
        // flux simulé) ne comptent pas comme pression des utilisateurs.
        markTradesAsSeen(asset.getSymbol());

        eventPublisher.publishEvent(
                new OrderBookChangedEvent(
                        asset.getSymbol()
                )
        );
    }


    // =========================================
    // QUOTES
    // =========================================

    /*
     * Réutilise l'ordre déjà placé à ce niveau plutôt que
     * d'en créer un nouveau à chaque cycle : le nombre de
     * lignes en base reste stable.
     */
    private Order quote(
            List<Order> existingOrders,
            int level,
            User maker,
            String symbol,
            OrderSide side,
            BigDecimal price,
            BigDecimal quantity
    ) {

        Order order;

        if (level < existingOrders.size()) {

            order = existingOrders.get(level);

            // On conserve la quantité déjà exécutée :
            // l'ordre reste cohérent avec ses transactions.
            BigDecimal executed =
                    order.getQuantity()
                            .subtract(order.getRemainingQuantity());

            order.setQuantity(executed.add(quantity));

        } else {

            order = new Order();

            order.setUser(maker);
            order.setAssetSymbol(symbol);
            order.setSide(side);
            order.setType(OrderType.LIMIT);
            order.setStatus(OrderStatus.PENDING);
            order.setQuantity(quantity);
        }

        order.setLimitPrice(price);
        order.setRemainingQuantity(quantity);

        return order;
    }


    private void cancelExtraQuotes(
            List<Order> existingOrders
    ) {

        for (
                int index = levels;
                index < existingOrders.size();
                index++
        ) {

            Order extra = existingOrders.get(index);

            extra.setStatus(OrderStatus.CANCELLED);

            orderRepository.save(extra);
        }
    }


    // =========================================
    // SIMULATED MARKET FLOW
    // =========================================

    private void simulateMarketFlow(
            Asset asset,
            BigDecimal firstLevelQuantity
    ) {

        User flow =
                systemUser(FLOW_EMAIL, "Flow");

        BigDecimal quantity =
                roundQuantity(
                        asset,
                        firstLevelQuantity.doubleValue()
                                * (0.1 + 0.4 * random.nextDouble())
                );

        Order order = new Order();

        order.setUser(flow);
        order.setAssetSymbol(asset.getSymbol());

        order.setSide(
                random.nextBoolean()
                        ? OrderSide.BUY
                        : OrderSide.SELL
        );

        order.setType(OrderType.MARKET);
        order.setStatus(OrderStatus.PENDING);
        order.setQuantity(quantity);
        order.setRemainingQuantity(quantity);

        Order saved =
                orderRepository.saveAndFlush(order);

        matchingEngineService.matchOrder(saved.getId());
    }


    // =========================================
    // PRICE MODEL
    // =========================================

    /*
     * Flux net des transactions survenues depuis le dernier cycle :
     * quantités achetées par les agresseurs moins quantités vendues,
     * rapportées à la taille typique d'un niveau du carnet.
     */
    private double flowPressure(
            Asset asset
    ) {

        String symbol = asset.getSymbol();

        Long lastSeenId = lastSeenTradeIds.get(symbol);

        // Premier cycle : l'historique n'a pas d'impact.
        if (lastSeenId == null) {
            return 0;
        }

        double netQuantity = 0;

        for (
                Trade trade
                : tradeRepository.findByAssetSymbolAndIdGreaterThan(
                        symbol,
                        lastSeenId
                )
        ) {

            if (trade.getAggressorSide() == OrderSide.BUY) {
                netQuantity += trade.getQuantity().doubleValue();
            } else if (trade.getAggressorSide() == OrderSide.SELL) {
                netQuantity -= trade.getQuantity().doubleValue();
            }
        }

        double typicalQuantity =
                BASE_NOTIONAL
                        / marketDataService
                        .lastPrice(asset)
                        .orElse(asset.getReferencePrice())
                        .doubleValue();

        return netQuantity / typicalQuantity;
    }


    private void markTradesAsSeen(
            String symbol
    ) {

        lastSeenTradeIds.put(
                symbol,
                tradeRepository
                        .findFirstByAssetSymbolOrderByCreatedAtDescIdDesc(symbol)
                        .map(Trade::getId)
                        .orElse(0L)
        );
    }


    /*
     * Trois forces déplacent le prix coté :
     *
     * 1. l'impact des ordres des utilisateurs : une pression
     *    acheteuse le fait monter, vendeuse le fait baisser ;
     * 2. un petit mouvement aléatoire ;
     * 3. un rappel vers le prix de référence : l'impact se
     *    résorbe progressivement et le cours ne dérive pas.
     */
    private BigDecimal nextMidPrice(
            Asset asset,
            double flowPressure,
            double intensity
    ) {

        BigDecimal reference = asset.getReferencePrice();

        BigDecimal current =
                midPrices.computeIfAbsent(
                        asset.getSymbol().toUpperCase(Locale.ROOT),
                        ignored ->
                                marketDataService
                                        .lastPrice(asset)
                                        .orElse(reference)
                );

        double mid = current.doubleValue();

        double impact =
                Math.signum(flowPressure)
                        * Math.min(
                        MAX_IMPACT,
                        IMPACT_COEFFICIENT
                                * Math.sqrt(Math.abs(flowPressure))
                );

        double next =
                mid
                        + mid * impact
                        + mid * VOLATILITY * (1 + 2 * intensity) * random.nextGaussian()
                        + (reference.doubleValue() - mid) * MEAN_REVERSION
                        - mid * 0.006 * intensity;

        BigDecimal bounded =
                BigDecimal.valueOf(next)
                        .max(reference.multiply(MIN_MID_RATIO))
                        .min(reference.multiply(MAX_MID_RATIO))
                        .setScale(8, RoundingMode.HALF_UP);

        midPrices.put(asset.getSymbol().toUpperCase(Locale.ROOT), bounded);

        return bounded;
    }

    /**
     * Fraction de liquidité maintenue par le Market Maker.
     *
     * Hors crise : facteur = 1.
     * Pendant CRASH : retrait plus fort côté BID.
     *
     * intensity est bornée entre 0 et 1.
     */
    static double liquidityFactor(
            OrderSide side,
            double intensity
    ) {

        double safeIntensity =
                Math.max(0.0, Math.min(1.0, intensity));

        // Decimal arithmetic avoids rounding 100 * 0.36 down to 35.
        double withdrawal = side == OrderSide.BUY ? MAX_BID_WITHDRAWAL : MAX_ASK_WITHDRAWAL;
        return BigDecimal.ONE.subtract(BigDecimal.valueOf(withdrawal)
                .multiply(BigDecimal.valueOf(safeIntensity))).doubleValue();
    }

    /**
     * Ajuste une quantité de cotation à la liquidité disponible.
     * Respecte le pas minimal de quantité de l'actif.
     */
    private BigDecimal stressedQuoteQuantity(
            Asset asset,
            BigDecimal normalQuantity,
            OrderSide side,
            double intensity
    ) {

        BigDecimal factor = BigDecimal.valueOf(
                liquidityFactor(side, intensity)
        );

        BigDecimal minimum = asset.getMinQuantity();

        return normalQuantity
                .multiply(factor)
                .divide(minimum, 0, RoundingMode.FLOOR)
                .multiply(minimum)
                .max(minimum);
    }



    private BigDecimal levelQuantity(
            Asset asset,
            BigDecimal mid,
            int level
    ) {

        double notional =
                BASE_NOTIONAL
                        * (1 + 0.6 * level)
                        * (0.7 + 0.6 * random.nextDouble());

        return roundQuantity(
                asset,
                notional / mid.doubleValue()
        );
    }


    // Multiple de la quantité minimale, au moins égal à celle-ci.
    private BigDecimal roundQuantity(
            Asset asset,
            double rawQuantity
    ) {

        BigDecimal minQuantity = asset.getMinQuantity();

        return BigDecimal.valueOf(rawQuantity)
                .divide(minQuantity, 0, RoundingMode.FLOOR)
                .multiply(minQuantity)
                .max(minQuantity);
    }


    private BigDecimal roundToTick(
            BigDecimal price,
            BigDecimal tick,
            RoundingMode roundingMode
    ) {

        return price
                .divide(tick, 0, roundingMode)
                .multiply(tick);
    }


    // =========================================
    // SYSTEM USERS
    // =========================================

    private User systemUser(
            String email,
            String lastName
    ) {

        return userRepository
                .findByEmailIgnoreCase(email)
                .orElseGet(() -> {

                    User user = new User();

                    user.setFirstName("Market");
                    user.setLastName(lastName);
                    user.setEmail(email);
                    user.setRole(Role.USER);

                    // Compte technique : connexion impossible.
                    user.setEnabled(false);

                    return userRepository.saveAndFlush(user);
                });
    }
}
