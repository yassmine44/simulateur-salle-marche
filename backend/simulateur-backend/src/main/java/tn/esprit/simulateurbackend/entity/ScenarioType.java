
package tn.esprit.simulateurbackend.entity;

public enum ScenarioType {

    // Scénario historique : choc baissier.
    CRASH,

    // Forte réduction de liquidité et élargissement du spread.
    LIQUIDITY_CRISIS,

    // Pression vendeuse et déséquilibre du carnet.
    SELLING_PANIC,

    // Pression acheteuse et hausse du marché.
    BULL_RALLY,

    // Chute brutale et récupération rapide.
    FLASH_CRASH
}
