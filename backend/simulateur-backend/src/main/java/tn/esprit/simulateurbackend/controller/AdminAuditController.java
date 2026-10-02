package tn.esprit.simulateurbackend.controller;

import java.time.LocalDateTime;
import java.util.List;

import tn.esprit.simulateurbackend.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tn.esprit.simulateurbackend.entity.AuditEventType;
import tn.esprit.simulateurbackend.service.AdminAuditService;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditController {

    private final AdminAuditService adminAuditService;


    public AdminAuditController(
            AdminAuditService adminAuditService
    ) {

        this.adminAuditService =
                adminAuditService;
    }


    @GetMapping
    public Page<SecurityAuditLogResponse> getAuditLogs(

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            AuditEventType eventType,

            @RequestParam(required = false)
            Boolean success,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME
            )
            LocalDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME
            )
            LocalDateTime to,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size

    ) {

        return adminAuditService
                .getAuditLogs(
                        search,
                        eventType,
                        success,
                        from,
                        to,
                        page,
                        size
                );
    }


    @GetMapping("/stats")
    public SecurityAuditStatsResponse getStats() {

        return adminAuditService
                .getStats();
    }
    @GetMapping("/activity")
    public List<SecurityActivityResponse> getActivity(

            @RequestParam(defaultValue = "7")
            int days

    ) {

        return adminAuditService
                .getActivity(
                        days
                );
    }
    @GetMapping("/distribution")
    public List<SecurityEventDistributionResponse> getDistribution(

            @RequestParam(defaultValue = "7")
            int days

    ) {

        return adminAuditService
                .getDistribution(
                        days
                );
    }
    @GetMapping("/logins")
    public Page<LoginAuditResponse> getLoginHistory(

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            String ipAddress,

            @RequestParam(required = false)
            LoginMethod method,

            @RequestParam(required = false)
            Boolean success,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME
            )
            LocalDateTime from,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME
            )
            LocalDateTime to,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size

    ) {

        return adminAuditService
                .getLoginHistory(

                        search,

                        ipAddress,

                        method,

                        success,

                        from,

                        to,

                        page,

                        size
                );
    }
    @GetMapping("/suspicious-ips")
    public List<SuspiciousIpResponse> getSuspiciousIps(

            @RequestParam(defaultValue = "24")
            int hours,

            @RequestParam(defaultValue = "5")
            int threshold

    ) {

        return adminAuditService
                .getSuspiciousIps(
                        hours,
                        threshold
                );
    }
}