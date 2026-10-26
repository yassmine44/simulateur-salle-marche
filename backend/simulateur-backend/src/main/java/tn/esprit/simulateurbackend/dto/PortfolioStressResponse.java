
package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PortfolioStressResponse(

        String currency,

        BigDecimal normalTotalValue,

        // Null si une position ne dispose pas
        // d'une cotation Market Maker exploitable.
        BigDecimal quotedTotalValue,

        BigDecimal valuationDifference,

        BigDecimal valuationDifferencePercent,

        boolean quoteCoverageComplete,

        List<PositionImpact> positions,

        Instant generatedAt

) {

    public record PositionImpact(

            String assetSymbol,

            BigDecimal quantity,

            BigDecimal lastTradePrice,

            BigDecimal quotedMidPrice,

            BigDecimal normalMarketValue,

            BigDecimal quotedMarketValue,

            BigDecimal valuationDifference

    ) {
    }
}
