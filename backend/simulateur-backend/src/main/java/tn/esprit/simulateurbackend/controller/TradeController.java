package tn.esprit.simulateurbackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import tn.esprit.simulateurbackend.dto.TradeResponse;
import tn.esprit.simulateurbackend.service.TradeService;

import java.util.List;

@RestController
@RequestMapping("/api/trades")
public class TradeController {

    private final TradeService tradeService;

    public TradeController(
            TradeService tradeService
    ) {
        this.tradeService =
                tradeService;
    }


    @GetMapping("/me")
    public ResponseEntity<List<TradeResponse>> getMyTrades(
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                tradeService.getMyTrades(
                        authentication.getName()
                )
        );
    }
}