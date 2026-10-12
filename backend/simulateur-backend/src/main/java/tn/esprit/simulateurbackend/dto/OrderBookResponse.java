package tn.esprit.simulateurbackend.dto;

import java.time.Instant;
import java.util.List;

public record OrderBookResponse(
        String assetSymbol,
        List<OrderBookLevelResponse> bids,
        List<OrderBookLevelResponse> asks,
        Instant generatedAt
) {
}