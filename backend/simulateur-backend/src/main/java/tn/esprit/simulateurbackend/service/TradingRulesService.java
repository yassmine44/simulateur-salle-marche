package tn.esprit.simulateurbackend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.entity.Asset;
import tn.esprit.simulateurbackend.entity.OrderSide;

import java.math.BigDecimal;
import java.math.RoundingMode;

/*
 * Règles de marché appliquées à chaque ordre :
 * pas de cotation, quantité minimale et
 * écart maximal par rapport au dernier cours.
 */
@Service
public class TradingRulesService {

    private static final BigDecimal HUNDRED =
            BigDecimal.valueOf(100);

    // Borne haute d'une colonne NUMERIC(19, 8).
    private static final BigDecimal NO_UPPER_BOUND =
            new BigDecimal("99999999999");

    /*
     * Écart maximal autorisé, en %, entre le prix d'un ordre
     * et le dernier cours. 0 désactive le contrôle.
     */
    @Value("${app.trading.price-band-percent:20}")
    private BigDecimal priceBandPercent;


    // =========================================
    // ORDER VALIDATION
    // =========================================

    public void validateQuantity(
            Asset asset,
            BigDecimal quantity
    ) {

        BigDecimal minQuantity = asset.getMinQuantity();

        if (
                minQuantity != null
                        && quantity.compareTo(minQuantity) < 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La quantité "
                            + plain(quantity)
                            + " est trop petite : sur "
                            + asset.getSymbol()
                            + ", un ordre doit porter sur au moins "
                            + plain(minQuantity)
                            + "."
            );
        }
    }


    /*
     * Vérifie le pas de cotation et l'écart au dernier cours.
     *
     * label : "prix limite" ou "prix de déclenchement".
     */
    public void validatePrice(
            Asset asset,
            BigDecimal price,
            BigDecimal lastPrice,
            String label
    ) {

        BigDecimal tickSize = asset.getTickSize();

        if (
                tickSize != null
                        && tickSize.signum() > 0
                        && price.remainder(tickSize).signum() != 0
        ) {

            // Les deux prix valides qui encadrent le prix saisi.
            BigDecimal below =
                    price
                            .divide(tickSize, 0, RoundingMode.FLOOR)
                            .multiply(tickSize);

            BigDecimal above = below.add(tickSize);

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le "
                            + label
                            + " "
                            + plain(price)
                            + " n'est pas valide : sur "
                            + asset.getSymbol()
                            + ", les prix avancent par pas de "
                            + plain(tickSize)
                            + ". Prix valides les plus proches : "
                            + plain(below)
                            + " ou "
                            + plain(above)
                            + "."
            );
        }

        if (
                !isPriceBandEnabled()
                        || lastPrice == null
        ) {
            return;
        }

        BigDecimal lowest = lowerBound(lastPrice);
        BigDecimal highest = upperBound(lastPrice);

        if (
                price.compareTo(lowest) < 0
                        || price.compareTo(highest) > 0
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le "
                            + label
                            + " "
                            + plain(price)
                            + " est trop éloigné du dernier cours ("
                            + plain(lastPrice)
                            + "). Pour éviter les erreurs de saisie, un prix doit rester à "
                            + plain(priceBandPercent)
                            + " % au plus du dernier cours : entre "
                            + plain(lowest)
                            + " et "
                            + plain(highest)
                            + "."
            );
        }
    }


    // =========================================
    // MARKET ORDER PROTECTION
    // =========================================

    /*
     * Un ordre au marché ne s'exécute jamais au-delà
     * de la plage autorisée autour du dernier cours :
     * il ne peut pas vider le carnet à un prix aberrant.
     */
    public BigDecimal marketPriceLimit(
            OrderSide side,
            BigDecimal lastPrice
    ) {

        boolean protectedOrder =
                isPriceBandEnabled()
                        && lastPrice != null;

        if (side == OrderSide.BUY) {

            return protectedOrder
                    ? upperBound(lastPrice)
                    : NO_UPPER_BOUND;
        }

        return protectedOrder
                ? lowerBound(lastPrice)
                : BigDecimal.ZERO;
    }


    private static String plain(BigDecimal value) {

        return value
                .stripTrailingZeros()
                .toPlainString();
    }


    private boolean isPriceBandEnabled() {

        return priceBandPercent != null
                && priceBandPercent.signum() > 0;
    }


    private BigDecimal lowerBound(BigDecimal lastPrice) {

        return lastPrice
                .multiply(HUNDRED.subtract(priceBandPercent))
                .divide(HUNDRED, 8, RoundingMode.DOWN);
    }


    private BigDecimal upperBound(BigDecimal lastPrice) {

        return lastPrice
                .multiply(HUNDRED.add(priceBandPercent))
                .divide(HUNDRED, 8, RoundingMode.UP);
    }
}
