package tn.esprit.simulateurbackend.service;

import jakarta.annotation.PreDestroy;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.FlashCandleDto;
import tn.esprit.simulateurbackend.dto.FlashMlMetricsDto;
import tn.esprit.simulateurbackend.dto.FlashSessionResponse;
import tn.esprit.simulateurbackend.dto.FlashStartRequest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Admin-driven multiplayer market stream: same playable pace as solo
 * (one candle every {@link #INTERVAL_MS}), synchronized for all clients.
 */
@Service
public class FlashEventService {

    public static final String TOPIC = "/topic/flash/events";
    /** One new candle every 15 seconds — voice fires on each TICK. */
    public static final long INTERVAL_MS = 15_000L;
    public static final long COUNTDOWN_MS = 3_000L;
    public static final int INITIAL_VISIBLE = 10;

    private final HistoCotationLoader histoCotationLoader;
    private final FlashMlMetricsClient mlMetricsClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final FlashPortfolioService portfolioService;
    private final FlashOrderBookService orderBookService;

    private final AtomicReference<LiveSession> current =
            new AtomicReference<>();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "flash-multiplayer-ticker");
                thread.setDaemon(true);
                return thread;
            });

    private ScheduledFuture<?> ticker;


    public FlashEventService(
            HistoCotationLoader histoCotationLoader,
            FlashMlMetricsClient mlMetricsClient,
            SimpMessagingTemplate messagingTemplate,
            FlashPortfolioService portfolioService,
            @org.springframework.context.annotation.Lazy FlashOrderBookService orderBookService
    ) {
        this.histoCotationLoader = histoCotationLoader;
        this.mlMetricsClient = mlMetricsClient;
        this.messagingTemplate = messagingTemplate;
        this.portfolioService = portfolioService;
        this.orderBookService = orderBookService;
    }


    public List<String> listSymbols() {
        return histoCotationLoader.listSymbols();
    }


    public List<FlashCandleDto> getCandles(String symbol) {
        return histoCotationLoader.getCandles(symbol);
    }


    public FlashMlMetricsDto metrics() {
        return mlMetricsClient.fetchMetrics();
    }


    public synchronized FlashSessionResponse status() {
        LiveSession live = current.get();
        if (live == null) {
            return idleStatus();
        }
        return toResponse(live, "STATUS", false);
    }


    public LiveSession requireLive() {
        LiveSession live = current.get();
        if (live == null || !"RUNNING".equals(live.status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Aucun événement multiplayer en cours."
            );
        }
        return live;
    }


    public LiveSession currentOrNull() {
        return current.get();
    }


    public synchronized FlashSessionResponse start(FlashStartRequest request) {
        String requested = request == null ? null : request.symbol();
        String symbol = requested == null || requested.isBlank()
                ? HistoCotationLoader.DEFAULT_SYMBOL
                : requested.trim();
        List<FlashCandleDto> candles =
                histoCotationLoader.getDatasetForSymbol(symbol);
        if (candles.size() < INITIAL_VISIBLE + 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Historique trop court pour démarrer l'événement ("
                            + symbol + ")."
            );
        }

        cancelTicker();
        portfolioService.clearAll();

        long now = System.currentTimeMillis();
        String newSessionId = UUID.randomUUID().toString();
        LiveSession live = new LiveSession(
                newSessionId,
                symbol,
                "RUNNING",
                now + COUNTDOWN_MS,
                INTERVAL_MS,
                INITIAL_VISIBLE,
                candles,
                mlMetricsClient.fetchMetrics()
        );
        current.set(live);
        orderBookService.bindSession(newSessionId);
        // Seed house quotes around the first visible close so students can trade immediately.
        int seedIdx = Math.max(0, INITIAL_VISIBLE - 1);
        orderBookService.seedMarketMaker(candles.get(seedIdx).close());

        FlashSessionResponse startPayload = toResponse(live, "START", true);
        messagingTemplate.convertAndSend(TOPIC, startPayload);

        long delay = Math.max(0L, live.startAtEpochMs - System.currentTimeMillis());
        ticker = scheduler.scheduleAtFixedRate(
                this::tickSafely,
                delay + INTERVAL_MS,
                INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        return startPayload;
    }


    public synchronized FlashSessionResponse stop() {
        cancelTicker();
        LiveSession previous = current.get();
        if (previous == null) {
            return idleStatus();
        }
        LiveSession stopped = previous.withStatus("STOPPED");
        current.set(stopped);
        FlashSessionResponse payload = toResponse(stopped, "STOP", false);
        messagingTemplate.convertAndSend(TOPIC, payload);
        return payload;
    }


    private void tickSafely() {
        try {
            tick();
        } catch (RuntimeException ignored) {
            // Keep scheduler alive; next tick retries.
        }
    }


    private synchronized void tick() {
        LiveSession live = current.get();
        if (live == null || !"RUNNING".equals(live.status)) {
            cancelTicker();
            return;
        }
        if (System.currentTimeMillis() < live.startAtEpochMs) {
            return;
        }
        if (live.visibleCandleCount >= live.candles.size()) {
            cancelTicker();
            LiveSession done = live.withStatus("STOPPED");
            current.set(done);
            messagingTemplate.convertAndSend(
                    TOPIC,
                    toResponse(done, "STOP", false)
            );
            return;
        }

        LiveSession advanced = live.advance();
        current.set(advanced);
        int markIdx = Math.max(0, advanced.visibleCandleCount - 1);
        orderBookService.seedMarketMaker(
                advanced.candles.get(markIdx).close()
        );
        messagingTemplate.convertAndSend(
                TOPIC,
                toResponse(advanced, "TICK", false)
        );

        if (advanced.visibleCandleCount >= advanced.candles.size()) {
            cancelTicker();
            LiveSession done = advanced.withStatus("STOPPED");
            current.set(done);
            messagingTemplate.convertAndSend(
                    TOPIC,
                    toResponse(done, "STOP", false)
            );
        }
    }


    private FlashSessionResponse idleStatus() {
        long now = System.currentTimeMillis();
        return new FlashSessionResponse(
                null,
                null,
                "IDLE",
                now,
                0L,
                INTERVAL_MS,
                0,
                0,
                INITIAL_VISIBLE,
                List.of(),
                null,
                mlMetricsClient.fetchMetrics(),
                "STATUS"
        );
    }


    private FlashSessionResponse toResponse(
            LiveSession live,
            String messageType,
            boolean includeCandles
    ) {
        int idx = Math.max(0, live.visibleCandleCount - 1);
        FlashCandleDto currentCandle =
                live.candles.isEmpty()
                        ? null
                        : live.candles.get(Math.min(idx, live.candles.size() - 1));

        return new FlashSessionResponse(
                live.sessionId,
                live.symbol,
                live.status,
                System.currentTimeMillis(),
                live.startAtEpochMs,
                live.intervalMs,
                live.visibleCandleCount,
                live.candles.size(),
                INITIAL_VISIBLE,
                includeCandles ? List.copyOf(live.candles) : List.of(),
                currentCandle,
                live.mlMetrics,
                messageType
        );
    }


    private void cancelTicker() {
        if (ticker != null) {
            ticker.cancel(false);
            ticker = null;
        }
    }


    @PreDestroy
    void shutdown() {
        cancelTicker();
        scheduler.shutdownNow();
    }


    public static final class LiveSession {
        private final String sessionId;
        private final String symbol;
        private final String status;
        private final long startAtEpochMs;
        private final long intervalMs;
        private final int visibleCandleCount;
        private final List<FlashCandleDto> candles;
        private final FlashMlMetricsDto mlMetrics;

        LiveSession(
                String sessionId,
                String symbol,
                String status,
                long startAtEpochMs,
                long intervalMs,
                int visibleCandleCount,
                List<FlashCandleDto> candles,
                FlashMlMetricsDto mlMetrics
        ) {
            this.sessionId = sessionId;
            this.symbol = symbol;
            this.status = status;
            this.startAtEpochMs = startAtEpochMs;
            this.intervalMs = intervalMs;
            this.visibleCandleCount = visibleCandleCount;
            this.candles = candles;
            this.mlMetrics = mlMetrics;
        }

        LiveSession advance() {
            int next = Math.min(candles.size(), visibleCandleCount + 1);
            return new LiveSession(
                    sessionId, symbol, status, startAtEpochMs, intervalMs,
                    next, candles, mlMetrics
            );
        }

        LiveSession withStatus(String newStatus) {
            return new LiveSession(
                    sessionId, symbol, newStatus, startAtEpochMs, intervalMs,
                    visibleCandleCount, candles, mlMetrics
            );
        }

        public String sessionId() { return sessionId; }
        public String symbol() { return symbol; }
        public String status() { return status; }
        public int visibleCandleCount() { return visibleCandleCount; }
        public List<FlashCandleDto> candles() { return candles; }
        public FlashMlMetricsDto mlMetrics() { return mlMetrics; }
    }
}
