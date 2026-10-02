package tn.esprit.simulateurbackend.repository.projection;

import java.time.LocalDate;

public interface SecurityActivityProjection {

    LocalDate getDay();

    Long getTotalEvents();

    Long getSuccessfulLogins();

    Long getFailedLogins();

    Long getRateLimitExceeded();

    Long getRecaptchaRejected();

    Long getAdminActions();
}