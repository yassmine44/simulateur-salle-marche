package tn.esprit.simulateurbackend.dto;

import java.math.BigDecimal;

/*
 * Ce que l'utilisateur peut engager sur un actif :
 * liquidités disponibles pour acheter, titres disponibles pour vendre.
 */
public record BuyingPowerResponse(

        String currency,

        boolean limitsEnforced,

        BigDecimal cash,

        BigDecimal reservedCash,

        BigDecimal availableCash,

        String assetSymbol,

        BigDecimal heldQuantity,

        BigDecimal reservedQuantity,

        BigDecimal availableQuantity
) {
}
