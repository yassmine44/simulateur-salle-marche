
package tn.esprit.simulateurbackend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import tn.esprit.simulateurbackend.dto.PortfolioStressResponse;
import tn.esprit.simulateurbackend.service.PortfolioStressService;

@RestController
@RequestMapping("/api/portfolio/me")
public class PortfolioStressController {

    private final PortfolioStressService portfolioStressService;

    public PortfolioStressController(
            PortfolioStressService portfolioStressService
    ) {
        this.portfolioStressService = portfolioStressService;
    }

    @GetMapping("/stress")
    public ResponseEntity<PortfolioStressResponse> getMyStress(
            Authentication authentication
    ) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentification requise"
            );
        }

        return ResponseEntity.ok(
                portfolioStressService.getStress(
                        authentication.getName()
                )
        );
    }
}
