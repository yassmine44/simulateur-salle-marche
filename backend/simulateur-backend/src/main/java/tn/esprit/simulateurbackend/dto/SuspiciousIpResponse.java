package tn.esprit.simulateurbackend.dto;

import java.time.LocalDateTime;

public record SuspiciousIpResponse(

        String ipAddress,

        long failedAttempts,

        long distinctEmails,

        LocalDateTime firstAttempt,

        LocalDateTime lastAttempt,

        String riskLevel

) {
}