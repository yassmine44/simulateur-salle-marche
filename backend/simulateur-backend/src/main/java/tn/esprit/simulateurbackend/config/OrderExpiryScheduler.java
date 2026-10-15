package tn.esprit.simulateurbackend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import tn.esprit.simulateurbackend.service.OrderExpiryService;

import java.time.Instant;

/*
 * Vérifie régulièrement l'échéance des ordres DAY.
 * Désactivable avec app.trading.expiry-enabled=false (tests).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "app.trading.expiry-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OrderExpiryScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(OrderExpiryScheduler.class);

    private final OrderExpiryService orderExpiryService;

    public OrderExpiryScheduler(
            OrderExpiryService orderExpiryService
    ) {
        this.orderExpiryService = orderExpiryService;
    }

    @Scheduled(
            initialDelayString = "${app.trading.expiry-initial-delay-ms:10000}",
            fixedDelayString = "${app.trading.expiry-interval-ms:30000}"
    )
    public void run() {

        try {

            int expired = orderExpiryService.expireDueOrders(Instant.now());

            if (expired > 0) {
                log.info("{} ordre(s) DAY arrivé(s) à échéance annulé(s).", expired);
            }

        } catch (RuntimeException exception) {

            log.warn("Échéance des ordres DAY : échec du cycle : {}", exception.getMessage());
        }
    }
}
