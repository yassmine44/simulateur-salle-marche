package tn.esprit.simulateurbackend.dto;

import java.time.LocalDate;

public record SecurityActivityResponse(

        LocalDate date,

        long totalEvents,

        long successfulLogins,

        long failedLogins,

        long rateLimitExceeded,

        long recaptchaRejected,

        long adminActions

) {
}