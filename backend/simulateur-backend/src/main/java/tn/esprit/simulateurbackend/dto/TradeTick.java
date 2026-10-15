package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeTick(
        Instant time,
        BigDecimal price,
        BigDecimal quantity
) {
}
