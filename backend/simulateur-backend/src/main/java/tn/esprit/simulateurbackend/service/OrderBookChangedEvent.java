package tn.esprit.simulateurbackend.service;

/*
 * Publié quand le carnet d'un actif change :
 * nouvel ordre, exécution ou annulation.
 */
public record OrderBookChangedEvent(
        String assetSymbol
) {
}
