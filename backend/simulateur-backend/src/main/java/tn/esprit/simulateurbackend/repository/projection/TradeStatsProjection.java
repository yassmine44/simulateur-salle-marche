package tn.esprit.simulateurbackend.repository.projection;

import java.math.BigDecimal;

public interface TradeStatsProjection {

    BigDecimal getLow();

    BigDecimal getHigh();

    BigDecimal getVolume();

    long getTradeCount();
}
