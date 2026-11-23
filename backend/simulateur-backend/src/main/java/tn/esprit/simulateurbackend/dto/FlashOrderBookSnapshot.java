package tn.esprit.simulateurbackend.dto;

import java.util.List;

public record FlashOrderBookSnapshot(
        String sessionId,
        String symbol,
        long serverNow,
        List<FlashOrderBookLevelDto> bids,
        List<FlashOrderBookLevelDto> asks
) {
}
