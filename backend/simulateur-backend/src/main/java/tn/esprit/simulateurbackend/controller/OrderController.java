package tn.esprit.simulateurbackend.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import tn.esprit.simulateurbackend.dto.CreateOcoRequest;
import tn.esprit.simulateurbackend.dto.CreateOrderRequest;
import tn.esprit.simulateurbackend.dto.ModifyOrderRequest;
import tn.esprit.simulateurbackend.dto.OrderPreviewResponse;
import tn.esprit.simulateurbackend.dto.OrderResponse;
import tn.esprit.simulateurbackend.service.OrderBookLocks;
import tn.esprit.simulateurbackend.service.OrderPreviewService;
import tn.esprit.simulateurbackend.service.OrderService;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderBookLocks orderBookLocks;
    private final OrderPreviewService orderPreviewService;

    public OrderController(
            OrderService orderService,
            OrderBookLocks orderBookLocks,
            OrderPreviewService orderPreviewService
    ) {
        this.orderService = orderService;
        this.orderBookLocks = orderBookLocks;
        this.orderPreviewService = orderPreviewService;
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

        // Un seul ordre à la fois par utilisateur (le contrôle des
        // fonds ne peut pas être contourné par deux ordres simultanés),
        // puis un seul appariement à la fois par carnet.
        // Toujours dans cet ordre : utilisateur, puis carnet.

        String email = authentication.getName();

        OrderResponse response =
                orderBookLocks.withLock(
                        userLockKey(email),
                        () ->
                                orderBookLocks.withLock(
                                        request.assetSymbol(),
                                        () ->
                                                orderService.createOrder(
                                                        email,
                                                        request
                                                )
                                )
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // =========================================
    // PREVIEW
    // =========================================

    /*
     * Simule l'ordre sans l'enregistrer : exécution estimée,
     * coût, effet sur le portefeuille, avertissements.
     */
    @PostMapping("/preview")
    public ResponseEntity<OrderPreviewResponse> previewOrder(

            @Valid
            @RequestBody
            CreateOrderRequest request,

            Authentication authentication
    ) {

        return ResponseEntity.ok(
                orderPreviewService.preview(
                        authentication.getName(),
                        request
                )
        );
    }


    // =========================================
    // OCO
    // =========================================

    /*
     * Protège des titres détenus : take-profit + stop-loss liés.
     */
    @PostMapping("/oco")
    public ResponseEntity<List<OrderResponse>> createOco(

            @Valid
            @RequestBody
            CreateOcoRequest request,

            Authentication authentication
    ) {

        String email = authentication.getName();

        List<OrderResponse> legs =
                orderBookLocks.withLock(
                        userLockKey(email),
                        () ->
                                orderBookLocks.withLock(
                                        request.assetSymbol(),
                                        () -> orderService.createOco(email, request)
                                )
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(legs);
    }


    // Les symboles d'actifs ne contiennent jamais ":" : pas de collision.
    public static String userLockKey(String email) {

        return "user:" + email;
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


    // =========================================
    // MODIFY ORDER
    // =========================================

    /*
     * Annule l'ordre et le remplace par un nouvel ordre
     * (nouveau prix, nouvelle quantité, nouvel identifiant).
     */
    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> modifyOrder(

            @PathVariable Long id,

            @Valid
            @RequestBody
            ModifyOrderRequest request,

            Authentication authentication
    ) {

        String email = authentication.getName();

        OrderResponse response =
                orderBookLocks.withLock(
                        userLockKey(email),
                        () ->
                                orderBookLocks.withLock(
                                        orderService.findOrderSymbol(id),
                                        () ->
                                                orderService.modifyOrder(
                                                        id,
                                                        email,
                                                        request
                                                )
                                )
                );

        return ResponseEntity.ok(
                response
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
