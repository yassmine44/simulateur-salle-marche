package tn.esprit.simulateurbackend.service;
import tn.esprit.simulateurbackend.dto.SuspiciousIpResponse;
import tn.esprit.simulateurbackend.repository.projection.SuspiciousIpProjection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import tn.esprit.simulateurbackend.dto.SecurityEventDistributionResponse;
import tn.esprit.simulateurbackend.repository.projection.SecurityEventDistributionProjection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tn.esprit.simulateurbackend.dto.LoginAuditResponse;
import tn.esprit.simulateurbackend.dto.LoginMethod;
import tn.esprit.simulateurbackend.dto.SecurityActivityResponse;
import tn.esprit.simulateurbackend.repository.projection.SecurityActivityProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.data.jpa.domain.Specification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.simulateurbackend.dto.SecurityAuditLogResponse;
import tn.esprit.simulateurbackend.dto.SecurityAuditStatsResponse;

import tn.esprit.simulateurbackend.entity.AuditEventType;
import tn.esprit.simulateurbackend.entity.SecurityAuditLog;

import tn.esprit.simulateurbackend.repository.SecurityAuditLogRepository;


@Service
@Transactional(readOnly = true)
public class AdminAuditService {

    private static final int MAX_PAGE_SIZE =
            100;


    private final SecurityAuditLogRepository repository;


    public AdminAuditService(
            SecurityAuditLogRepository repository
    ) {

        this.repository =
                repository;
    }


    // ==================================================
    // AUDIT LOGS
    // ==================================================

    public Page<SecurityAuditLogResponse> getAuditLogs(

            String search,

            AuditEventType eventType,

            Boolean success,

            LocalDateTime from,

            LocalDateTime to,

            int page,

            int size

    ) {

        /*
         * =========================================
         * SAFE PAGINATION
         * =========================================
         */

        int safePage =
                Math.max(
                        page,
                        0
                );


        int safeSize =
                Math.min(

                        Math.max(
                                size,
                                1
                        ),

                        MAX_PAGE_SIZE
                );


        Pageable pageable =
                PageRequest.of(

                        safePage,

                        safeSize,

                        Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                        )
                );


        /*
         * =========================================
         * BASE SPECIFICATION
         * =========================================
         */

        Specification<SecurityAuditLog> specification =
                (
                        root,
                        query,
                        criteriaBuilder
                ) ->
                        criteriaBuilder.conjunction();


        /*
         * =========================================
         * SEARCH EMAIL
         * =========================================
         */

        if (
                search != null
                        &&
                        !search.isBlank()
        ) {

            String normalizedSearch =
                    "%"
                            + search
                            .trim()
                            .toLowerCase(
                                    Locale.ROOT
                            )
                            + "%";


            specification =
                    specification.and(

                            (
                                    root,
                                    query,
                                    criteriaBuilder
                            ) ->

                                    criteriaBuilder.or(

                                            criteriaBuilder.like(

                                                    criteriaBuilder.lower(
                                                            root.get(
                                                                    "email"
                                                            )
                                                    ),

                                                    normalizedSearch
                                            ),

                                            criteriaBuilder.like(

                                                    criteriaBuilder.lower(
                                                            root.get(
                                                                    "actorEmail"
                                                            )
                                                    ),

                                                    normalizedSearch
                                            )

                                    )

                    );
        }


        /*
         * =========================================
         * EVENT TYPE
         * =========================================
         */

        if (
                eventType != null
        ) {

            specification =
                    specification.and(

                            (
                                    root,
                                    query,
                                    criteriaBuilder
                            ) ->

                                    criteriaBuilder.equal(
                                            root.get(
                                                    "eventType"
                                            ),
                                            eventType
                                    )

                    );
        }


        /*
         * =========================================
         * SUCCESS / FAILURE
         * =========================================
         */

        if (
                success != null
        ) {

            specification =
                    specification.and(

                            (
                                    root,
                                    query,
                                    criteriaBuilder
                            ) ->

                                    criteriaBuilder.equal(
                                            root.get(
                                                    "success"
                                            ),
                                            success
                                    )

                    );
        }


        /*
         * =========================================
         * FROM DATE
         * =========================================
         */

        if (
                from != null
        ) {

            specification =
                    specification.and(

                            (
                                    root,
                                    query,
                                    criteriaBuilder
                            ) ->

                                    criteriaBuilder
                                            .greaterThanOrEqualTo(

                                                    root.get(
                                                            "createdAt"
                                                    ),

                                                    from
                                            )

                    );
        }


        /*
         * =========================================
         * TO DATE
         * =========================================
         */

        if (
                to != null
        ) {

            specification =
                    specification.and(

                            (
                                    root,
                                    query,
                                    criteriaBuilder
                            ) ->

                                    criteriaBuilder
                                            .lessThanOrEqualTo(

                                                    root.get(
                                                            "createdAt"
                                                    ),

                                                    to
                                            )

                    );
        }


        /*
         * =========================================
         * QUERY
         * =========================================
         */

        return repository
                .findAll(
                        specification,
                        pageable
                )
                .map(
                        SecurityAuditLogResponse::from
                );
    }


    // ==================================================
    // SECURITY CENTER STATISTICS
    // ==================================================

    public SecurityAuditStatsResponse getStats() {

        LocalDate today =
                LocalDate.now();


        LocalDateTime from =
                today.atStartOfDay();


        LocalDateTime to =
                today
                        .plusDays(1)
                        .atStartOfDay();


        /*
         * =========================================
         * TOTAL EVENTS TODAY
         * =========================================
         */

        long totalEvents =
                repository
                        .countByCreatedAtBetween(
                                from,
                                to
                        );


        /*
         * =========================================
         * SUCCESSFUL LOGINS
         *
         * Local + Google
         * =========================================
         */

        long localLoginSuccess =
                repository
                        .countByEventTypeAndCreatedAtBetween(
                                AuditEventType.LOGIN_SUCCESS,
                                from,
                                to
                        );


        long googleLoginSuccess =
                repository
                        .countByEventTypeAndCreatedAtBetween(
                                AuditEventType.GOOGLE_LOGIN_SUCCESS,
                                from,
                                to
                        );


        long successfulLogins =
                localLoginSuccess
                        + googleLoginSuccess;


        /*
         * =========================================
         * FAILED LOGINS
         *
         * Local + Google
         * =========================================
         */

        long localLoginFailed =
                repository
                        .countByEventTypeAndCreatedAtBetween(
                                AuditEventType.LOGIN_FAILED,
                                from,
                                to
                        );


        long googleLoginFailed =
                repository
                        .countByEventTypeAndCreatedAtBetween(
                                AuditEventType.GOOGLE_LOGIN_FAILED,
                                from,
                                to
                        );


        long failedLogins =
                localLoginFailed
                        + googleLoginFailed;


        /*
         * =========================================
         * RATE LIMIT
         * =========================================
         */

        long rateLimitExceeded =
                repository
                        .countByEventTypeAndCreatedAtBetween(
                                AuditEventType.RATE_LIMIT_EXCEEDED,
                                from,
                                to
                        );


        /*
         * =========================================
         * RECAPTCHA
         * =========================================
         */

        long recaptchaRejected =
                repository
                        .countByEventTypeAndCreatedAtBetween(
                                AuditEventType.RECAPTCHA_REJECTED,
                                from,
                                to
                        );


        /*
         * =========================================
         * ADMIN ACTIONS
         * =========================================
         */

        long userEnabled =
                repository
                        .countByEventTypeAndCreatedAtBetween(
                                AuditEventType.USER_ENABLED,
                                from,
                                to
                        );


        long userDisabled =
                repository
                        .countByEventTypeAndCreatedAtBetween(
                                AuditEventType.USER_DISABLED,
                                from,
                                to
                        );


        long roleChanged =
                repository
                        .countByEventTypeAndCreatedAtBetween(
                                AuditEventType.ROLE_CHANGED,
                                from,
                                to
                        );


        long adminActions =
                userEnabled
                        + userDisabled
                        + roleChanged;


        /*
         * =========================================
         * RESPONSE
         * =========================================
         */

        return new SecurityAuditStatsResponse(

                totalEvents,

                successfulLogins,

                failedLogins,

                rateLimitExceeded,

                recaptchaRejected,

                adminActions
        );
    }
    public List<SecurityActivityResponse> getActivity(
            int days
    ) {

        /*
         * On limite volontairement la période.
         *
         * Minimum : 1 jour
         * Maximum : 30 jours
         */
        int safeDays =
                Math.min(
                        Math.max(days, 1),
                        30
                );


        /*
         * Aujourd'hui inclus.
         *
         * Exemple days = 7 :
         *
         * aujourd'hui - 6 jours
         * jusqu'à demain 00:00
         */
        LocalDate today =
                LocalDate.now();


        LocalDate startDate =
                today.minusDays(
                        safeDays - 1L
                );


        LocalDateTime from =
                startDate.atStartOfDay();


        LocalDateTime to =
                today
                        .plusDays(1)
                        .atStartOfDay();


        /*
         * Agrégation PostgreSQL
         */
        List<SecurityActivityProjection> databaseResults =
                repository
                        .findSecurityActivity(
                                from,
                                to
                        );


        /*
         * Indexation par date.
         */
        Map<LocalDate, SecurityActivityProjection> byDate =
                new HashMap<>();


        for (
                SecurityActivityProjection row :
                databaseResults
        ) {

            byDate.put(
                    row.getDay(),
                    row
            );
        }


        /*
         * On crée systématiquement tous
         * les jours demandés.
         *
         * Même lorsqu'un jour n'a
         * aucun événement.
         */
        return java.util.stream.IntStream
                .range(
                        0,
                        safeDays
                )
                .mapToObj(index -> {

                    LocalDate date =
                            startDate.plusDays(
                                    index
                            );


                    SecurityActivityProjection row =
                            byDate.get(
                                    date
                            );


                    if (
                            row == null
                    ) {

                        return new SecurityActivityResponse(

                                date,

                                0,

                                0,

                                0,

                                0,

                                0,

                                0
                        );
                    }


                    return new SecurityActivityResponse(

                            date,

                            safeLong(
                                    row.getTotalEvents()
                            ),

                            safeLong(
                                    row.getSuccessfulLogins()
                            ),

                            safeLong(
                                    row.getFailedLogins()
                            ),

                            safeLong(
                                    row.getRateLimitExceeded()
                            ),

                            safeLong(
                                    row.getRecaptchaRejected()
                            ),

                            safeLong(
                                    row.getAdminActions()
                            )
                    );

                })
                .toList();
    }
    private long safeLong(
            Long value
    ) {

        return value == null
                ? 0L
                : value;
    }
    public List<SecurityEventDistributionResponse> getDistribution(
            int days
    ) {

        int safeDays =
                Math.min(
                        Math.max(days, 1),
                        30
                );


        LocalDate today =
                LocalDate.now();


        LocalDate startDate =
                today.minusDays(
                        safeDays - 1L
                );


        LocalDateTime from =
                startDate.atStartOfDay();


        LocalDateTime to =
                today
                        .plusDays(1)
                        .atStartOfDay();


        List<SecurityEventDistributionProjection> rows =
                repository
                        .findEventDistribution(
                                from,
                                to
                        );


        long total =
                rows.stream()
                        .mapToLong(
                                row ->
                                        safeLong(
                                                row.getEventCount()
                                        )
                        )
                        .sum();


        return rows.stream()
                .map(row -> {

                    long count =
                            safeLong(
                                    row.getEventCount()
                            );


                    double percentage =
                            total == 0
                                    ? 0.0
                                    : (
                                    count * 100.0
                            ) / total;


                    return new SecurityEventDistributionResponse(

                            AuditEventType.valueOf(
                                    row.getEventType()
                            ),

                            count,

                            Math.round(
                                    percentage * 10.0
                            ) / 10.0
                    );
                })
                .toList();
    }
    public Page<LoginAuditResponse> getLoginHistory(

            String search,

            String ipAddress,

            LoginMethod method,

            Boolean success,

            LocalDateTime from,

            LocalDateTime to,

            int page,

            int size

    ) {

        int safePage =
                Math.max(
                        page,
                        0
                );


        int safeSize =
                Math.min(
                        Math.max(size, 1),
                        MAX_PAGE_SIZE
                );


        Pageable pageable =
                PageRequest.of(

                        safePage,

                        safeSize,

                        Sort.by(
                                Sort.Direction.DESC,
                                "createdAt"
                        )
                );


        /*
         * =========================================
         * LOGIN EVENTS ONLY
         * =========================================
         */

        Specification<SecurityAuditLog> specification =
                (
                        root,
                        query,
                        criteriaBuilder
                ) ->

                        root.get(
                                        "eventType"
                                )
                                .in(
                                        AuditEventType.LOGIN_SUCCESS,
                                        AuditEventType.LOGIN_FAILED,
                                        AuditEventType.GOOGLE_LOGIN_SUCCESS,
                                        AuditEventType.GOOGLE_LOGIN_FAILED
                                );


        /*
         * =========================================
         * EMAIL SEARCH
         * =========================================
         */

        if (
                search != null
                        &&
                        !search.isBlank()
        ) {

            String normalizedSearch =
                    "%"
                            + search
                            .trim()
                            .toLowerCase(
                                    Locale.ROOT
                            )
                            + "%";


            specification =
                    specification.and(

                            (
                                    root,
                                    query,
                                    criteriaBuilder
                            ) ->

                                    criteriaBuilder.like(

                                            criteriaBuilder.lower(
                                                    root.get(
                                                            "email"
                                                    )
                                            ),

                                            normalizedSearch
                                    )

                    );
        }


        /*
         * =========================================
         * IP ADDRESS
         * =========================================
         */

        if (
                ipAddress != null
                        &&
                        !ipAddress.isBlank()
        ) {

            String normalizedIp =
                    "%"
                            + ipAddress.trim()
                            + "%";


            specification =
                    specification.and(

                            (
                                    root,
                                    query,
                                    criteriaBuilder
                            ) ->

                                    criteriaBuilder.like(

                                            root.get(
                                                    "ipAddress"
                                            ),

                                            normalizedIp
                                    )

                    );
        }


        /*
         * =========================================
         * LOGIN METHOD
         * =========================================
         */

        if (
                method == LoginMethod.LOCAL
        ) {

            specification =
                    specification.and(

                            (
                                    root,
                                    query,
                                    criteriaBuilder
                            ) ->

                                    root.get(
                                                    "eventType"
                                            )
                                            .in(
                                                    AuditEventType.LOGIN_SUCCESS,
                                                    AuditEventType.LOGIN_FAILED
                                            )

                    );
        }


        if (
                method == LoginMethod.GOOGLE
        ) {

            specification =
                    specification.and(

                            (
                                    root,
                                    query,
                                    criteriaBuilder
                            ) ->

                                    root.get(
                                                    "eventType"
                                            )
                                            .in(
                                                    AuditEventType.GOOGLE_LOGIN_SUCCESS,
                                                    AuditEventType.GOOGLE_LOGIN_FAILED
                                            )

                    );
        }


        /*
         * =========================================
         * SUCCESS / FAILED
         * =========================================
         */

        if (
                success != null
        ) {

            specification =
                    specification.and(

                            (
                                    root,
                                    query,
                                    criteriaBuilder
                            ) ->

                                    criteriaBuilder.equal(

                                            root.get(
                                                    "success"
                                            ),

                                            success
                                    )

                    );
        }


        /*
         * =========================================
         * FROM
         * =========================================
         */

        if (
                from != null
        ) {

            specification =
                    specification.and(

                            (
                                    root,
                                    query,
                                    criteriaBuilder
                            ) ->

                                    criteriaBuilder
                                            .greaterThanOrEqualTo(

                                                    root.get(
                                                            "createdAt"
                                                    ),

                                                    from
                                            )

                    );
        }


        /*
         * =========================================
         * TO
         * =========================================
         */

        if (
                to != null
        ) {

            specification =
                    specification.and(

                            (
                                    root,
                                    query,
                                    criteriaBuilder
                            ) ->

                                    criteriaBuilder
                                            .lessThanOrEqualTo(

                                                    root.get(
                                                            "createdAt"
                                                    ),

                                                    to
                                            )

                    );
        }


        return repository
                .findAll(
                        specification,
                        pageable
                )
                .map(
                        LoginAuditResponse::from
                );
    }
    public List<SuspiciousIpResponse> getSuspiciousIps(
            int hours,
            int threshold
    ) {

        int safeHours =
                Math.min(
                        Math.max(hours, 1),
                        168
                );


        int safeThreshold =
                Math.min(
                        Math.max(threshold, 2),
                        100
                );


        LocalDateTime to =
                LocalDateTime.now();


        LocalDateTime from =
                to.minusHours(
                        safeHours
                );


        List<SuspiciousIpProjection> rows =
                repository.findSuspiciousIps(
                        from,
                        to,
                        safeThreshold
                );


        return rows.stream()
                .map(row -> {

                    long failedAttempts =
                            safeLong(
                                    row.getFailedAttempts()
                            );


                    long distinctEmails =
                            safeLong(
                                    row.getDistinctEmails()
                            );


                    return new SuspiciousIpResponse(

                            row.getIpAddress(),

                            failedAttempts,

                            distinctEmails,

                            row.getFirstAttempt(),

                            row.getLastAttempt(),

                            resolveRiskLevel(
                                    failedAttempts,
                                    distinctEmails
                            )
                    );
                })
                .toList();
    }
    private String resolveRiskLevel(
            long failedAttempts,
            long distinctEmails
    ) {

        if (
                failedAttempts >= 20
                        ||
                        distinctEmails >= 10
        ) {

            return "CRITICAL";
        }


        if (
                failedAttempts >= 10
                        ||
                        distinctEmails >= 5
        ) {

            return "HIGH";
        }


        if (
                failedAttempts >= 5
        ) {

            return "MEDIUM";
        }


        return "LOW";
    }
}