package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderBookStreamService {

    public static final String CHANGED_EVENT =
            "order-book-changed";

    /*
     * Le navigateur (EventSource) se reconnecte
     * automatiquement après expiration.
     */
    private static final long EMITTER_TIMEOUT_MS =
            10 * 60 * 1000L;

    private final Map<String, Set<SseEmitter>> emittersBySymbol =
            new ConcurrentHashMap<>();


    // =========================================
    // SUBSCRIBE
    // =========================================

    public SseEmitter subscribe(
            String symbol
    ) {

        SseEmitter emitter =
                new SseEmitter(EMITTER_TIMEOUT_MS);

        Set<SseEmitter> emitters =
                emittersBySymbol.computeIfAbsent(
                        symbol,
                        ignored ->
                                ConcurrentHashMap.newKeySet()
                );

        emitters.add(emitter);

        emitter.onCompletion(
                () -> emitters.remove(emitter)
        );

        emitter.onTimeout(
                emitter::complete
        );

        emitter.onError(
                ignored -> emitters.remove(emitter)
        );

        // Premier événement : confirme l'ouverture du flux.
        send(
                emitters,
                emitter,
                "connected",
                symbol
        );

        return emitter;
    }


    // =========================================
    // PUBLISH
    // =========================================

    /*
     * AFTER_COMMIT : les clients ne sont notifiés
     * que lorsque les modifications sont visibles en base.
     */
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onOrderBookChanged(
            OrderBookChangedEvent event
    ) {

        Set<SseEmitter> emitters =
                emittersBySymbol.get(
                        event.assetSymbol()
                );

        if (emitters == null) {
            return;
        }

        for (SseEmitter emitter : emitters) {

            send(
                    emitters,
                    emitter,
                    CHANGED_EVENT,
                    event.assetSymbol()
            );
        }
    }


    public int subscriberCount(
            String symbol
    ) {

        Set<SseEmitter> emitters =
                emittersBySymbol.get(symbol);

        return emitters == null
                ? 0
                : emitters.size();
    }


    private void send(
            Set<SseEmitter> emitters,
            SseEmitter emitter,
            String eventName,
            String data
    ) {

        try {

            emitter.send(
                    SseEmitter.event()
                            .name(eventName)
                            .data(data)
            );

        } catch (IOException | IllegalStateException exception) {

            // Client déconnecté.
            emitters.remove(emitter);
        }
    }
}
