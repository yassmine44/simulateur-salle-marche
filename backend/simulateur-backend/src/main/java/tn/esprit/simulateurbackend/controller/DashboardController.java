package tn.esprit.simulateurbackend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tn.esprit.simulateurbackend.dto.DashboardResponse;
import tn.esprit.simulateurbackend.service.DashboardService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(
            DashboardService dashboardService
    ) {
        this.dashboardService = dashboardService;
    }

    /*
     * Tout le tableau de bord de l'utilisateur connecté, en un appel.
     */
    @GetMapping("/me")
    public ResponseEntity<DashboardResponse> getMyDashboard(
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                dashboardService.getDashboard(authentication.getName())
        );
    }
}
