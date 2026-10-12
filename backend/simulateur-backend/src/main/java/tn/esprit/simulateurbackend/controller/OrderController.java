package tn.esprit.simulateurbackend.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import tn.esprit.simulateurbackend.dto.CreateOrderRequest;
import tn.esprit.simulateurbackend.dto.OrderResponse;
import tn.esprit.simulateurbackend.service.OrderService;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(
            OrderService orderService
    ) {
        this.orderService = orderService;
    }


    // =========================================
    // CREATE ORDER
    // =========================================

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(

            @Valid
            @RequestBody
            CreateOrderRequest request,

            Authentication authentication
    ) {

        OrderResponse response =
                orderService.createOrder(
                        authentication.getName(),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
    @GetMapping("/me")
    public ResponseEntity<List<OrderResponse>> getMyOrders(
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                orderService.getMyOrders(
                        authentication.getName()
                )
        );
    }
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(

            @PathVariable Long id,

            Authentication authentication
    ) {

        OrderResponse response =
                orderService.cancelOrder(
                        id,
                        authentication.getName()
                );

        return ResponseEntity.ok(
                response
        );
    }
}