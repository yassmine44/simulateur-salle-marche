package tn.esprit.simulateurbackend.repository.projection;

public interface SecurityEventDistributionProjection {

    String getEventType();

    Long getEventCount();
}