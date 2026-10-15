package tn.esprit.simulateurbackend.entity;

/*
 * Raison de l'annulation (ou du rejet) d'un ordre.
 */
public enum CancelReason {

    // Annulé par l'utilisateur.
    USER,

    // Remplacé par une modification.
    REPLACED,

    // Ordre DAY arrivé à minuit.
    EXPIRED,

    // Reste d'un ordre IOC non exécuté.
    IOC,

    // Ordre FOK qui ne pouvait pas être exécuté en totalité.
    FOK,

    // Branche d'un OCO annulée car l'autre a été exécutée.
    OCO,

    // Portefeuille réinitialisé.
    RESET,

    // Ordre au marché sans contrepartie suffisante.
    NO_LIQUIDITY
}
