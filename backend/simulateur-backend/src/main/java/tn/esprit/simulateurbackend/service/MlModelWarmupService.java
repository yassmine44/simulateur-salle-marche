package tn.esprit.simulateurbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import tn.esprit.simulateurbackend.config.MlServiceProperties;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kicks off asynchronous Random Forest pre-training / reload against the
 * Python ML microservice as soon as Spring is ready, so single-player
 * challenges can call instant inference with no live training delay.
 */
@Service
public class MlModelWarmupService {

    private static final Logger log =
            LoggerFactory.getLogger(MlModelWarmupService.class);

    private static final int RETRAIN_CONNECT_TIMEOUT_MS = 5_000;
    private static final int RETRAIN_READ_TIMEOUT_MS = 180_000;

    private final MlServiceProperties properties;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public MlModelWarmupService(MlServiceProperties properties) {
        this.properties = properties;
    }


    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!properties.isEnabled()) {
            log.info("ML warmup skipped (gamification.ml-service.enabled=false)");
            return;
        }
        if (!started.compareAndSet(false, true)) {
            return;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "ml-model-warmup");
            thread.setDaemon(true);
            return thread;
        });

        executor.execute(this::warmupAsync);
        executor.shutdown();
    }


    private void warmupAsync() {
        log.info(
                "Starting async ML pre-train/warmup against {}",
                properties.retrainUrl()
        );

        RestTemplate longRunning = longRunningClient();

        try {
            ResponseEntity<Map> response =
                    longRunning.postForEntity(
                            properties.retrainUrl(),
                            Map.of(
                                    "symbol", "BIAT",
                                    "include_sp500", true,
                                    "include_histo_2018_2026", true
                            ),
                            Map.class
                    );

            log.info(
                    "ML pre-train completed: status={} body={}",
                    response.getStatusCode(),
                    response.getBody()
            );
            return;
        } catch (RestClientException first) {
            log.warn(
                    "ML retrain endpoint unavailable ({}), trying health ping: {}",
                    properties.retrainUrl(),
                    first.getMessage()
            );
        }

        try {
            ResponseEntity<Map> health =
                    longRunning.getForEntity(
                            properties.healthUrl(),
                            Map.class
                    );
            log.info(
                    "ML service health after warmup attempt: {}",
                    health.getBody()
            );
        } catch (RestClientException second) {
            log.warn(
                    "ML service still offline at {}. "
                            + "Start ml-service (uvicorn) so inference is ready.",
                    properties.rootUrl()
            );
        }
    }


    private static RestTemplate longRunningClient() {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(RETRAIN_CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(RETRAIN_READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }
}
