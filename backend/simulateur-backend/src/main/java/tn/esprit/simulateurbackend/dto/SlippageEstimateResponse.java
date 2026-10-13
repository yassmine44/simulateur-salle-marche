package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;

public record SlippageEstimateResponse(

        String assetSymbol,

        OrderSide side,

        BigDecimal requestedQuantity,

        BigDecimal executableQuantity,

        BigDecimal remainingQuantity,

        boolean fullyExecutable,

        BigDecimal bestPrice,

        BigDecimal expectedExecutionPrice,

        BigDecimal absoluteSlippage,

        BigDecimal relativeSlippage,

        BigDecimal slippagePercent,

        int levelsConsumed,

        Instant generatedAt

) {
}