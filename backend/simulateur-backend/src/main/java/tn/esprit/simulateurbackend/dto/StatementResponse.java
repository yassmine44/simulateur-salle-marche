package tn.esprit.simulateurbackend.dto;

import tn.esprit.simulateurbackend.entity.OrderSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/*
 * Relevé de compte sur une période : valeur d'ouverture et de
 * clôture, opérations de la période, positions en fin de période.
 */
public record StatementResponse(

        String statementNumber,

        String accountNumber,

        String holderName,

        String holderEmail,

        String currency,

        LocalDate periodStart,

        LocalDate periodEnd,

        Instant generatedAt,

        // Le portefeuille a été réinitialisé à cette date :
        // les opérations antérieures n'y figurent pas.
        Instant portfolioStartedAt,

        BigDecimal openingValue,

        BigDecimal closingValue,

        BigDecimal netChange,

        BigDecimal netChangePercent,

        BigDecimal realizedPnl,

        BigDecimal closingCash,

        int tradeCount,

        BigDecimal totalBought,

        BigDecimal totalSold,

        List<StatementTrade> trades,

        List<StatementPosition> positions
) {

    public record StatementTrade(

            Long tradeId,

            Instant executedAt,

            String assetSymbol,

            OrderSide side,

            BigDecimal quantity,

            BigDecimal price,

            BigDecimal amount
    ) {
    }

    public record StatementPosition(

            String assetSymbol,

            String assetName,

            BigDecimal quantity,

            BigDecimal averagePrice,

            BigDecimal closingPrice,

            BigDecimal value
    ) {
    }
}
