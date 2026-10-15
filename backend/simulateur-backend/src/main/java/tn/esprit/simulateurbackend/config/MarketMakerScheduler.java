package tn.esprit.simulateurbackend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import tn.esprit.simulateurbackend.service.MarketMakerService;
import tn.esprit.simulateurbackend.service.OrderBookLocks;

/*
 * Fait tourner le teneur de marché à intervalle régulier.
 *
 * Désactivable avec app.market-maker.enabled=false
 * (c'est le cas pendant les tests).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "app.market-maker.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MarketMakerScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(MarketMakerScheduler.class);

    private final MarketMakerService marketMakerService;
    private final OrderBookLocks orderBookLocks;

    public MarketMakerScheduler(
            MarketMakerService marketMakerService,
            OrderBookLocks orderBookLocks
    ) {
        this.marketMakerService = marketMakerService;
        this.orderBookLocks = orderBookLocks;
    }

    @Scheduled(
            initialDelayString = "${app.market-maker.initial-delay-ms:8000}",
            fixedDelayString = "${app.market-maker.interval-ms:5000}"
    )
    public void run() {

        for (String symbol : marketMakerService.quotableSymbols()) {

            try {

                orderBookLocks.withLock(
                        symbol,
                        () -> {
                            marketMakerService.quoteAsset(symbol);
                            return null;
                        }
                );

            } catch (RuntimeException exception) {

                // Un actif en erreur ne doit pas arrêter les autres.
                log.warn(
                        "Teneur de marché : cycle en échec pour {} : {}",
                        symbol,
                        exception.getMessage()
                );
            }
        }
    }
}
