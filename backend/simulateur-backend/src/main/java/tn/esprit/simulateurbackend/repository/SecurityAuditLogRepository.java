package tn.esprit.simulateurbackend.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.simulateurbackend.entity.AuditEventType;
import tn.esprit.simulateurbackend.entity.SecurityAuditLog;

import tn.esprit.simulateurbackend.repository.projection.SecurityActivityProjection;
import tn.esprit.simulateurbackend.repository.projection.SecurityEventDistributionProjection;
import tn.esprit.simulateurbackend.repository.projection.SuspiciousIpProjection;


public interface SecurityAuditLogRepository
        extends JpaRepository<SecurityAuditLog, Long>,
        JpaSpecificationExecutor<SecurityAuditLog> {


    long countByCreatedAtBetween(
            LocalDateTime from,
            LocalDateTime to
    );


    long countByEventTypeAndCreatedAtBetween(
            AuditEventType eventType,
            LocalDateTime from,
            LocalDateTime to
    );


    @Query(
            value = """
                    SELECT
                        CAST(created_at AS DATE) AS "day",

                        COUNT(*) AS "totalEvents",

                        SUM(
                            CASE
                                WHEN event_type IN (
                                    'LOGIN_SUCCESS',
                                    'GOOGLE_LOGIN_SUCCESS'
                                )
                                THEN 1
                                ELSE 0
                            END
                        ) AS "successfulLogins",

                        SUM(
                            CASE
                                WHEN event_type IN (
                                    'LOGIN_FAILED',
                                    'GOOGLE_LOGIN_FAILED'
                                )
                                THEN 1
                                ELSE 0
                            END
                        ) AS "failedLogins",

                        SUM(
                            CASE
                                WHEN event_type = 'RATE_LIMIT_EXCEEDED'
                                THEN 1
                                ELSE 0
                            END
                        ) AS "rateLimitExceeded",

                        SUM(
                            CASE
                                WHEN event_type = 'RECAPTCHA_REJECTED'
                                THEN 1
                                ELSE 0
                            END
                        ) AS "recaptchaRejected",

                        SUM(
                            CASE
                                WHEN event_type IN (
                                    'USER_ENABLED',
                                    'USER_DISABLED',
                                    'ROLE_CHANGED'
                                )
                                THEN 1
                                ELSE 0
                            END
                        ) AS "adminActions"

                    FROM security_audit_logs

                    WHERE created_at >= :from
                      AND created_at < :to

                    GROUP BY
                        CAST(created_at AS DATE)

                    ORDER BY
                        CAST(created_at AS DATE) ASC
                    """,
            nativeQuery = true
    )
    List<SecurityActivityProjection> findSecurityActivity(
            @Param("from")
            LocalDateTime from,

            @Param("to")
            LocalDateTime to
    );

    @Query(
            value = """
                SELECT
                    event_type AS "eventType",
                    COUNT(*) AS "eventCount"

                FROM security_audit_logs

                WHERE created_at >= :from
                  AND created_at < :to

                GROUP BY event_type

                ORDER BY COUNT(*) DESC
                """,
            nativeQuery = true
    )
    List<SecurityEventDistributionProjection>
    findEventDistribution(

            @Param("from")
            LocalDateTime from,

            @Param("to")
            LocalDateTime to
    );
    @Query(
            value = """
                SELECT
                    ip_address AS "ipAddress",

                    COUNT(*) AS "failedAttempts",

                    COUNT(
                        DISTINCT LOWER(email)
                    ) AS "distinctEmails",

                    MIN(created_at) AS "firstAttempt",

                    MAX(created_at) AS "lastAttempt"

                FROM security_audit_logs

                WHERE created_at >= :from
                  AND created_at < :to

                  AND event_type IN (
                      'LOGIN_FAILED',
                      'GOOGLE_LOGIN_FAILED'
                  )

                  AND ip_address IS NOT NULL
                  AND ip_address <> ''

                GROUP BY ip_address

                HAVING COUNT(*) >= :threshold

                ORDER BY
                    COUNT(*) DESC,
                    MAX(created_at) DESC
                """,
            nativeQuery = true
    )
    List<SuspiciousIpProjection> findSuspiciousIps(

            @Param("from")
            LocalDateTime from,

            @Param("to")
            LocalDateTime to,

            @Param("threshold")
            long threshold
    );
}