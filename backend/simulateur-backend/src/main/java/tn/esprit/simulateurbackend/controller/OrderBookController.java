package tn.esprit.simulateurbackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.esprit.simulateurbackend.dto.OrderBookTemporalMetricsResponse;
import tn.esprit.simulateurbackend.service.OrderBookTemporalMetricsService;
import tn.esprit.simulateurbackend.dto.OrderBookMetricsResponse;
import tn.esprit.simulateurbackend.dto.OrderBookResponse;
import tn.esprit.simulateurbackend.dto.SlippageEstimateResponse;

import tn.esprit.simulateurbackend.entity.OrderSide;
import tn.esprit.simulateurbackend.service.OrderBookService;

import java.math.BigDecimal;


@RestController
@RequestMapping("/api/order-book")
public class OrderBookController {

    private final OrderBookService orderBookService;
    private final OrderBookTemporalMetricsService temporalMetricsService;

    public OrderBookController(

            OrderBookService orderBookService,

            OrderBookTemporalMetricsService temporalMetricsService
    ) {

        this.orderBookService =
                orderBookService;

        this.temporalMetricsService =
                temporalMetricsService;
    }

    // =========================================
    // ORDER BOOK
    // =========================================

    @GetMapping("/{symbol}")
    public ResponseEntity<OrderBookResponse> getOrderBook(
            @PathVariable String symbol
    ) {

        return ResponseEntity.ok(
                orderBookService.getOrderBook(symbol)
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
}