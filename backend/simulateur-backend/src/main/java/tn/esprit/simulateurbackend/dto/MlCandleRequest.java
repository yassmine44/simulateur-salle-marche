package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;

public record MlCandleRequest(
        String date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {

    public static MlCandleRequest from(
            GamificationCandleResponse candle
    ) {

        return new MlCandleRequest(
                candle.date(),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume()
        );
    }
}
