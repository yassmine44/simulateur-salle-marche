package tn.esprit.simulateurbackend.dto;

public record SecurityAuditStatsResponse(

        long totalEventsToday,

        long successfulLoginsToday,

        long failedLoginsToday,

        long rateLimitExceededToday,

        long recaptchaRejectedToday,

        long adminActionsToday

) {
}