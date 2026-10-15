package tn.esprit.simulateurbackend.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.CandleResponse;
import tn.esprit.simulateurbackend.dto.MarketOverviewResponse;
import tn.esprit.simulateurbackend.dto.MarketSummaryResponse;
import tn.esprit.simulateurbackend.dto.MarketTradeResponse;
import tn.esprit.simulateurbackend.dto.TradeTick;
import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.Trade;
import tn.esprit.simulateurbackend.entity.Order;
import tn.esprit.simulateurbackend.entity.OrderStatus;
import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.repository.OrderRepository;
import tn.esprit.simulateurbackend.repository.TradeRepository;
import tn.esprit.simulateurbackend.repository.projection.TradeStatsProjection;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/*
 * Données de marché dérivées des transactions :
 * dernier cours, statistiques 24 h, flux des exécutions
 * et chandeliers.
 */
@Service
@Transactional(readOnly = true)
public class MarketDataService {

    private static final int RECENT_TRADES_LIMIT = 30;

    private static final int CANDLE_LIMIT = 60;

    private static final String DEFAULT_INTERVAL = "1m";

    private static final Map<String, Duration> INTERVALS =
            Map.of(
                    "1m", Duration.ofMinutes(1),
                    "5m", Duration.ofMinutes(5),
                    "15m", Duration.ofMinutes(15)
            );

    private final TradeRepository tradeRepository;
    private final AssetRepository assetRepository;
    private final OrderRepository orderRepository;

    public MarketDataService(
            TradeRepository tradeRepository,
            AssetRepository assetRepository,
            OrderRepository orderRepository
    ) {
        this.tradeRepository = tradeRepository;
        this.assetRepository = assetRepository;
        this.orderRepository = orderRepository;
    }


    // =========================================
    // LAST PRICE
    // =========================================

    /*
     * Dernier cours : prix de la dernière transaction,
     * sinon prix de référence de l'actif.
     */
    public Optional<BigDecimal> lastPrice(
            Asset asset
    ) {

        return tradeRepository
                .findFirstByAssetSymbolOrderByCreatedAtDescIdDesc(
                        asset.getSymbol()
                )
                .map(Trade::getExecutionPrice)
                .or(() ->
                        Optional.ofNullable(
                                asset.getReferencePrice()
                        )
                );
    }


    /*
     * Prix milieu du carnet : moyenne du meilleur achat et de la
     * meilleure vente. Si un côté est vide : dernier cours.
     */
    public Optional<BigDecimal> midPrice(
            Asset asset
    ) {

        List<OrderStatus> open =
                List.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED);

        BigDecimal bestBid =
                orderRepository
                        .findBookBids(asset.getSymbol(), open)
                        .stream()
                        .map(Order::getLimitPrice)
                        .findFirst()
                        .orElse(null);

        BigDecimal bestAsk =
                orderRepository
                        .findBookAsks(asset.getSymbol(), open)
                        .stream()
                        .map(Order::getLimitPrice)
                        .findFirst()
                        .orElse(null);

        if (bestBid != null && bestAsk != null) {

            return Optional.of(
                    bestBid.add(bestAsk)
                            .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP)
            );
        }

        return lastPrice(asset);
    }


    public Optional<BigDecimal> lastPrice(
            String symbol
    ) {

        return assetRepository
                .findBySymbolIgnoreCase(symbol)
                .flatMap(this::lastPrice);
    }


    // =========================================
    // OVERVIEW
    // =========================================

    public MarketOverviewResponse getOverview(
            String rawSymbol,
            String rawInterval
    ) {

        Asset asset = requireActiveAsset(rawSymbol);

        String symbol = asset.getSymbol();

        String interval =
                rawInterval != null
                        && INTERVALS.containsKey(rawInterval)
                        ? rawInterval
                        : DEFAULT_INTERVAL;

        Instant now = Instant.now();

        List<MarketTradeResponse> recentTrades =
                tradeRepository
                        .findRecent(
                                symbol,
                                PageRequest.of(0, RECENT_TRADES_LIMIT)
                        )
                        .stream()
                        .map(MarketTradeResponse::from)
                        .toList();

        Duration bucket = INTERVALS.get(interval);

        List<CandleResponse> candles =
                buildCandles(
                        tradeRepository.findTicksSince(
                                symbol,
                                now.minus(
                                        bucket.multipliedBy(CANDLE_LIMIT)
                                )
                        ),
                        bucket
                );

        return new MarketOverviewResponse(
                buildSummary(asset, now),
                recentTrades,
                interval,
                candles
        );
    }


    private MarketSummaryResponse buildSummary(
            Asset asset,
            Instant now
    ) {

        String symbol = asset.getSymbol();

        Instant since = now.minus(Duration.ofHours(24));

        BigDecimal lastPrice =
                lastPrice(asset).orElse(null);

        TradeStatsProjection stats =
                tradeRepository.statsSince(symbol, since);

        // Variation : par rapport à la première transaction
        // des dernières 24 h, sinon au prix de référence.
        BigDecimal openPrice =
                tradeRepository
                        .findFirstByAssetSymbolAndCreatedAtGreaterThanEqualOrderByCreatedAtAscIdAsc(
                                symbol,
                                since
                        )
                        .map(Trade::getExecutionPrice)
                        .orElse(asset.getReferencePrice());

        BigDecimal changePercent = null;

        if (
                lastPrice != null
                        && openPrice != null
                        && openPrice.signum() > 0
        ) {

            changePercent =
                    lastPrice
                            .subtract(openPrice)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(openPrice, 2, RoundingMode.HALF_UP);
        }

        return new MarketSummaryResponse(
                symbol,
                lastPrice,
                changePercent,
                stats.getHigh(),
                stats.getLow(),
                stats.getVolume(),
                stats.getTradeCount()
        );
    }


    // =========================================
    // CANDLES
    // =========================================

    /*
     * Regroupe les transactions (triées par date)
     * en chandeliers OHLCV. Les intervalles sans
     * transaction ne produisent pas de chandelier.
     */
    static List<CandleResponse> buildCandles(
            List<TradeTick> ticks,
            Duration bucket
    ) {

        long bucketMs = bucket.toMillis();

        List<CandleResponse> candles = new ArrayList<>();

        long currentStart = Long.MIN_VALUE;

        BigDecimal open = null;
        BigDecimal high = null;
        BigDecimal low = null;
        BigDecimal close = null;
        BigDecimal volume = BigDecimal.ZERO;

        for (TradeTick tick : ticks) {

            long start =
                    Math.floorDiv(
                            tick.time().toEpochMilli(),
                            bucketMs
                    ) * bucketMs;

            if (start != currentStart) {

                if (open != null) {

                    candles.add(
                            new CandleResponse(
                                    Instant.ofEpochMilli(currentStart),
                                    open,
                                    high,
                                    low,
                                    close,
                                    volume
                            )
                    );
                }

                currentStart = start;
                open = tick.price();
                high = tick.price();
                low = tick.price();
                volume = BigDecimal.ZERO;
            }

            high = high.max(tick.price());
            low = low.min(tick.price());
            close = tick.price();
            volume = volume.add(tick.quantity());
        }

        if (open != null) {

            candles.add(
                    new CandleResponse(
                            Instant.ofEpochMilli(currentStart),
                            open,
                            high,
                            low,
                            close,
                            volume
                    )
            );
        }

        return candles;
    }


    private Asset requireActiveAsset(
            String rawSymbol
    ) {

        String symbol =
                rawSymbol == null
                        ? ""
                        : rawSymbol
                        .trim()
                        .toUpperCase(Locale.ROOT);

        return assetRepository
                .findBySymbolIgnoreCaseAndActiveTrue(symbol)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Actif inconnu ou désactivé : "
                                        + symbol
                        )
                );
    }
}
