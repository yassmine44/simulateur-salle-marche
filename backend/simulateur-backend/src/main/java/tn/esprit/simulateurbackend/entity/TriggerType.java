package tn.esprit.simulateurbackend.entity;

/*
 * Ordres conditionnels : ils attendent hors du carnet
 * et s'exécutent au marché quand le dernier cours
 * atteint le prix de déclenchement.
 */
public enum TriggerType {
    STOP_LOSS,
    TAKE_PROFIT
}
