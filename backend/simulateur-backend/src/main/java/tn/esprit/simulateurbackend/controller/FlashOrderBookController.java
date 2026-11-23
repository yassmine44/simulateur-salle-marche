package tn.esprit.simulateurbackend.controller;

import java.security.Principal;

import jakarta.validation.Valid;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import tn.esprit.simulateurbackend.dto.FlashOrderRequest;
import tn.esprit.simulateurbackend.service.FlashOrderBookService;

/**
 * STOMP entry for multiplayer limit orders.
 * Client publishes to /app/multiplayer/order
 */
@Controller
public class FlashOrderBookController {

    private final FlashOrderBookService orderBookService;

    public FlashOrderBookController(FlashOrderBookService orderBookService) {
        this.orderBookService = orderBookService;
    }

    @MessageMapping("/multiplayer/order")
    public void placeOrder(
            @Valid @Payload FlashOrderRequest request,
            Principal principal
    ) {
        if (principal == null || principal.getName() == null) {
            return;
        }
        orderBookService.placeLimitOrder(principal.getName(), request);
    }
}
