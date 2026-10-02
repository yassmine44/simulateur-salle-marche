package tn.esprit.simulateurbackend.repository.projection;

import java.time.LocalDateTime;

public interface SuspiciousIpProjection {

    String getIpAddress();

    Long getFailedAttempts();

    Long getDistinctEmails();

    LocalDateTime getFirstAttempt();

    LocalDateTime getLastAttempt();
}