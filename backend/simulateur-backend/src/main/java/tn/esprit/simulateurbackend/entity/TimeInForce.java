package tn.esprit.simulateurbackend.entity;

/*
 * Durée de validité d'un ordre.
 *
 * GTC : valable jusqu'à annulation (par défaut).
 * DAY : annulé automatiquement à minuit.
 * IOC : exécute immédiatement ce qui peut l'être, le reste est annulé.
 * FOK : exécuté entièrement et immédiatement, sinon annulé en totalité.
 */
public enum TimeInForce {
    GTC,
    DAY,
    IOC,
    FOK
}
