package tn.esprit.simulateurbackend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/*
 * Mise en forme des nombres dans les phrases destinées à l'utilisateur :
 * « 1 234,56 », « +0,42 % », « −120,00 ».
 */
final class FrenchFormat {

    private FrenchFormat() {
    }


    static String amount(BigDecimal value) {

        return format(value, 2);
    }


    static String signedAmount(BigDecimal value) {

        return sign(value) + format(value.abs(), 2);
    }


    static String percent(BigDecimal value) {

        return format(value, 2) + " %";
    }


    static String signedPercent(BigDecimal value) {

        return sign(value) + format(value.abs(), 2) + " %";
    }


    static String quantity(BigDecimal value) {

        BigDecimal stripped = value.stripTrailingZeros();

        return format(stripped, Math.max(0, stripped.scale()));
    }


    static String price(BigDecimal value) {

        BigDecimal stripped = value.stripTrailingZeros();

        return format(stripped, Math.max(2, stripped.scale()));
    }


    private static String sign(BigDecimal value) {

        return value.signum() > 0 ? "+" : value.signum() < 0 ? "−" : "";
    }


    private static String format(BigDecimal value, int digits) {

        NumberFormat format = NumberFormat.getNumberInstance(Locale.FRANCE);

        format.setMinimumFractionDigits(digits);
        format.setMaximumFractionDigits(digits);

        return format.format(value.setScale(digits, RoundingMode.HALF_UP));
    }
}
