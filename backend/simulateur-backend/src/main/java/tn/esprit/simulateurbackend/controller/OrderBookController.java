package tn.esprit.simulateurbackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import tn.esprit.simulateurbackend.dto.OrderBookResponse;
import tn.esprit.simulateurbackend.service.OrderBookService;

@RestController
@RequestMapping("/api/order-book")
public class OrderBookController {

    private final OrderBookService orderBookService;

    public OrderBookController(
            OrderBookService orderBookService
    ) {
        this.orderBookService =
                orderBookService;
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<OrderBookResponse> getOrderBook(
            @PathVariable String symbol
    ) {

        return ResponseEntity.ok(
                orderBookService.getOrderBook(symbol)
        );
    }
}