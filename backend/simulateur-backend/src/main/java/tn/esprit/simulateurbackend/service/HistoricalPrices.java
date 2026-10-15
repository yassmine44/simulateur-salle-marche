package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Component;

import tn.esprit.simulateurbackend.entity.Trade;
import tn.esprit.simulateurbackend.repository.AssetRepository;
import tn.esprit.simulateurbackend.repository.TradeRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/*
 * Cours d'un actif à une date passée : prix de la dernière
 * transaction avant cette date, sinon prix de référence de l'actif.
 *
 * Une valorisation historique interroge souvent le même couple
 * (actif, date) : chaque calcul utilise son propre Lookup, qui
 * mémorise les réponses le temps du calcul.
 */
@Component
public class HistoricalPrices {

    private final TradeRepository tradeRepository;
    private final AssetRepository assetRepository;

    public HistoricalPrices(
            TradeRepository tradeRepository,
            AssetRepository assetRepository
    ) {
        this.tradeRepository = tradeRepository;
        this.assetRepository = assetRepository;
    }


    public Lookup lookup() {
        return new Lookup();
    }


    public final class Lookup {

        private final Map<String, BigDecimal> cache = new HashMap<>();

        private Lookup() {
        }

        /*
         * fallback : utilisé si l'actif n'a ni transaction
         * avant cette date ni prix de référence.
         */
        public BigDecimal priceAt(
                String symbol,
                Instant instant,
                BigDecimal fallback
        ) {

            String key = symbol + "@" + instant.toEpochMilli();

            BigDecimal cached = cache.get(key);

            if (cached != null) {
                return cached;
            }

            BigDecimal price =
                    tradeRepository
                            .findFirstByAssetSymbolAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
                                    symbol,
                                    instant
                            )
                            .map(Trade::getExecutionPrice)
                            .or(() ->
                                    assetRepository
                                            .findBySymbolIgnoreCase(symbol)
                                            .map(asset -> asset.getReferencePrice())
                            )
                            .orElse(fallback);

            if (price != null) {
                cache.put(key, price);
            }

            return price;
        }
    }
}
