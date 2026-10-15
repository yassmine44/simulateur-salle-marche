package tn.esprit.simulateurbackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tn.esprit.simulateurbackend.dto.OrderBookTemporalMetricsResponse;
import tn.esprit.simulateurbackend.service.OrderBookTemporalMetricsService;
import tn.esprit.simulateurbackend.dto.OrderBookMetricsResponse;
import tn.esprit.simulateurbackend.dto.OrderBookResponse;
import tn.esprit.simulateurbackend.dto.SlippageEstimateResponse;

import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.service.OrderBookService;
import tn.esprit.simulateurbackend.service.OrderBookStreamService;

import java.math.BigDecimal;


@RestController
@RequestMapping("/api/order-book")
public class OrderBookController {

    private final OrderBookService orderBookService;
    private final OrderBookTemporalMetricsService temporalMetricsService;
    private final OrderBookStreamService orderBookStreamService;

    public OrderBookController(

            OrderBookService orderBookService,

            OrderBookTemporalMetricsService temporalMetricsService,
            OrderBookStreamService orderBookStreamService
    ) {

        this.orderBookService =
                orderBookService;

        this.temporalMetricsService =
                temporalMetricsService;
        this.orderBookStreamService = orderBookStreamService;
    }

    // =========================================
    // ORDER BOOK
    // =========================================

    @GetMapping("/{symbol}")
    public ResponseEntity<OrderBookResponse> getOrderBook(
            @PathVariable String symbol,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                orderBookService.getOrderBook(
                        symbol,
                        authentication == null ? null : authentication.getName()
                )
        );
    }

// =========================================
// TEMPORAL METRICS
// =========================================

    @GetMapping("/{symbol}/temporal-metrics")
    public ResponseEntity<OrderBookTemporalMetricsResponse>
    getTemporalMetrics(
            @PathVariable String symbol
    ) {

        return ResponseEntity.ok(

                temporalMetricsService
                        .getTemporalMetrics(
                                symbol
                        )
        );
    }
    // =========================================
    // MICROSTRUCTURE METRICS
    // =========================================

    @GetMapping("/{symbol}/metrics")
    public ResponseEntity<OrderBookMetricsResponse> getOrderBookMetrics(
            @PathVariable String symbol
    ) {

        return ResponseEntity.ok(
                orderBookService.getOrderBookMetrics(symbol)
        );
    }


    // =========================================
    // ESTIMATED SLIPPAGE
    // =========================================

    @GetMapping("/{symbol}/slippage")
    public ResponseEntity<SlippageEstimateResponse> estimateSlippage(

            @PathVariable String symbol,

            @RequestParam OrderSide side,

            @RequestParam BigDecimal quantity
    ) {

        return ResponseEntity.ok(
                orderBookService.estimateSlippage(
                        symbol,
                        side,
                        quantity
                )
        );
    }

    @GetMapping(value = "/{symbol}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOrderBook(@PathVariable String symbol) {
        return orderBookStreamService.subscribe(orderBookService.resolveActiveSymbol(symbol));
    }
}
