package tn.esprit.simulateurbackend.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import tn.esprit.simulateurbackend.dto.OrderBookResponse;
import tn.esprit.simulateurbackend.service.OrderBookService;
import tn.esprit.simulateurbackend.service.OrderBookStreamService;

@RestController
@RequestMapping("/api/order-book")
public class OrderBookController {

    private final OrderBookService orderBookService;
    private final OrderBookStreamService orderBookStreamService;

    public OrderBookController(
            OrderBookService orderBookService,
            OrderBookStreamService orderBookStreamService
    ) {
        this.orderBookService =
                orderBookService;

        this.orderBookStreamService =
                orderBookStreamService;
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<OrderBookResponse> getOrderBook(
            @PathVariable String symbol,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                orderBookService.getOrderBook(
                        symbol,
                        authentication == null
                                ? null
                                : authentication.getName()
                )
        );
    }

    /*
     * Flux temps réel (SSE).
     *
     * Un événement "order-book-changed" est envoyé
     * à chaque modification du carnet de l'actif.
     */
    @GetMapping(
            value = "/{symbol}/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter streamOrderBook(
            @PathVariable String symbol
    ) {

        return orderBookStreamService.subscribe(
                orderBookService.resolveActiveSymbol(symbol)
        );
    }
}
